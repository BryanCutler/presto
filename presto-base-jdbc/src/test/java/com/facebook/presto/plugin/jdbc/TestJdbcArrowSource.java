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
import com.facebook.presto.spi.ConnectorSession;
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

import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.facebook.presto.testing.TestingSession.testSessionBuilder;
import static java.time.ZoneOffset.UTC;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestJdbcArrowSource
{
    private static final ConnectorSession SESSION = testSessionBuilder().build().toConnectorSession();
    private static final LocalDateTime TIMESTAMP = LocalDateTime.of(2026, 1, 2, 3, 4, 5, 678_000_000);

    private TestingDatabase database;
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
        Map<String, JdbcColumnHandle> columns = database.getColumnHandles("example", "timestamps");
        List<JdbcColumnHandle> columnHandles = ImmutableList.of(columns.get("id"), columns.get("ts"));

        List<Long> timestamps = new ArrayList<>();
        try (JdbcArrowSource source = new JdbcArrowSource(
                database.getJdbcClient(),
                SESSION,
                database.getSplit("example", "timestamps"),
                columnHandles,
                10,
                new ConnectorArrowSourceAdapter.BufferAllocatorHolderImpl(allocator))) {
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
    }
}
