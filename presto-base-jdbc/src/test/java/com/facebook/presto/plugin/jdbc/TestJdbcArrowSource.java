/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.plugin.jdbc;

import com.facebook.plugin.arrow.ConnectorArrowSourceAdapter;
import com.facebook.presto.common.predicate.TupleDomain;
import com.facebook.presto.common.type.RowType;
import com.facebook.presto.spi.ConnectorSession;
import com.facebook.presto.spi.PrestoException;
import com.google.common.collect.ImmutableList;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.TimeStampMilliVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.TimeUnit;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.facebook.presto.common.type.BigintType.BIGINT;
import static com.facebook.presto.testing.TestingSession.testSessionBuilder;
import static java.time.ZoneOffset.UTC;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public class TestJdbcArrowSource
{
    private static final ConnectorSession SESSION = testSessionBuilder().build().toConnectorSession();
    private static final LocalDateTime TIMESTAMP = LocalDateTime.of(2026, 1, 2, 3, 4, 5, 678_000_000);

    private TestingDatabase database;
    // Test methods run in parallel, so each test checks a child allocator of its own
    private BufferAllocator allocator;

    @BeforeClass
    public void setUp()
            throws Exception
    {
        database = new TestingDatabase();
        try (Statement statement = database.getConnection().createStatement()) {
            statement.execute("CREATE TABLE example.timestamps(id bigint primary key, ts timestamp)");
            statement.execute("INSERT INTO example.timestamps VALUES (1, TIMESTAMP '2026-01-02 03:04:05.678'), (2, NULL)");
        }
        allocator = new RootAllocator();
    }

    @AfterClass(alwaysRun = true)
    public void tearDown()
            throws Exception
    {
        allocator.close();
        database.close();
    }

    @Test
    public void testTimestampUsesTimeZoneNaiveVector()
    {
        try (BufferAllocator testAllocator = allocator.newChildAllocator("testTimestampUsesTimeZoneNaiveVector", 0, Long.MAX_VALUE)) {
            Map<String, JdbcColumnHandle> columns = database.getColumnHandles("example", "timestamps");
            List<JdbcColumnHandle> columnHandles = ImmutableList.of(columns.get("id"), columns.get("ts"));

            List<Long> timestamps = new ArrayList<>();
            try (JdbcArrowSource source = new JdbcArrowSource(
                    database.getJdbcClient(),
                    SESSION,
                    database.getSplit("example", "timestamps"),
                    columnHandles,
                    10,
                    new ConnectorArrowSourceAdapter.BufferAllocatorHolderImpl(testAllocator))) {
                while (source.nextArrowBatch()) {
                    VectorSchemaRoot root = source.getVectorSchemaRoot();
                    FieldVector vector = root.getVector(1);
                    // Presto TIMESTAMP has no time zone, so the Arrow type must not carry one
                    assertEquals(vector.getField().getType(), new ArrowType.Timestamp(TimeUnit.MILLISECOND, null));
                    assertTrue(vector instanceof TimeStampMilliVector);
                    for (int row = 0; row < root.getRowCount(); row++) {
                        timestamps.add(vector.isNull(row) ? null : ((TimeStampMilliVector) vector).get(row));
                    }
                }
            }

            assertEquals(timestamps, Arrays.asList(TIMESTAMP.toInstant(UTC).toEpochMilli(), null));
            assertEquals(testAllocator.getAllocatedMemory(), 0);
        }
    }

    @Test
    public void testUnsupportedColumnFailsBeforeOpeningConnection()
    {
        try (BufferAllocator testAllocator = allocator.newChildAllocator("testUnsupportedColumnFailsBeforeOpeningConnection", 0, Long.MAX_VALUE)) {
            // arrow-jdbc has no consumer for ROW (Arrow Struct); the shim falls back on UnsupportedOperationException
            JdbcColumnHandle id = database.getColumnHandles("example", "timestamps").get("id");
            JdbcColumnHandle row = new JdbcColumnHandle(id.getConnectorId(), "r", id.getJdbcTypeHandle(), RowType.anonymous(ImmutableList.of(BIGINT)), true, Optional.empty());
            AtomicInteger connections = new AtomicInteger();

            assertThrows(PrestoException.class, () -> new JdbcArrowSource(
                    countingConnections(database.getJdbcClient(), connections),
                    SESSION,
                    database.getSplit("example", "timestamps"),
                    ImmutableList.of(id, row),
                    10,
                    new ConnectorArrowSourceAdapter.BufferAllocatorHolderImpl(testAllocator)));

            assertEquals(connections.get(), 0);
            assertEquals(testAllocator.getAllocatedMemory(), 0);
        }
    }

    @Test
    public void testQueryFailureReleasesVectors()
    {
        try (BufferAllocator testAllocator = allocator.newChildAllocator("testQueryFailureReleasesVectors", 0, Long.MAX_VALUE)) {
            JdbcSplit split = database.getSplit("example", "timestamps");
            JdbcSplit missingTable = new JdbcSplit(split.getConnectorId(), split.getCatalogName(), split.getSchemaName(), "missing", TupleDomain.all(), Optional.empty());
            List<JdbcColumnHandle> columnHandles = ImmutableList.of(database.getColumnHandles("example", "timestamps").get("id"));

            assertThrows(PrestoException.class, () -> new JdbcArrowSource(
                    database.getJdbcClient(),
                    SESSION,
                    missingTable,
                    columnHandles,
                    10,
                    new ConnectorArrowSourceAdapter.BufferAllocatorHolderImpl(testAllocator)));

            assertEquals(testAllocator.getAllocatedMemory(), 0);
        }
    }

    private static JdbcClient countingConnections(JdbcClient delegate, AtomicInteger connections)
    {
        return (JdbcClient) Proxy.newProxyInstance(
                JdbcClient.class.getClassLoader(),
                new Class<?>[] {JdbcClient.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getConnection")) {
                        connections.incrementAndGet();
                    }
                    try {
                        return method.invoke(delegate, args);
                    }
                    catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }
}
