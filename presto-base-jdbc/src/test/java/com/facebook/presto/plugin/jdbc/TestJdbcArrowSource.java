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

import com.facebook.plugin.arrow.ArrowBlockBuilder;
import com.facebook.plugin.arrow.ConnectorArrowSourceAdapter;
import com.facebook.presto.common.block.Block;
import com.facebook.presto.common.predicate.TupleDomain;
import com.facebook.presto.common.type.CharType;
import com.facebook.presto.common.type.RowType;
import com.facebook.presto.common.type.Type;
import com.facebook.presto.spi.ConnectorSession;
import com.facebook.presto.spi.PrestoException;
import com.facebook.presto.spi.RecordCursor;
import com.google.common.collect.ImmutableList;
import io.airlift.slice.Slice;
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

import static com.facebook.presto.SystemSessionProperties.LEGACY_TIMESTAMP;
import static com.facebook.presto.common.type.BigintType.BIGINT;
import static com.facebook.presto.common.type.VarcharType.createUnboundedVarcharType;
import static com.facebook.presto.testing.TestingEnvironment.FUNCTION_AND_TYPE_MANAGER;
import static com.facebook.presto.testing.TestingSession.testSessionBuilder;
import static java.time.ZoneOffset.UTC;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public class TestJdbcArrowSource
{
    private static final ConnectorSession SESSION = testSessionBuilder().build().toConnectorSession();
    private static final LocalDateTime TIMESTAMP = LocalDateTime.of(2026, 1, 2, 3, 4, 5, 678_000_000);
    private static final List<String> ALL_TYPES_COLUMNS = ImmutableList.of(
            "c_bigint", "c_integer", "c_double", "c_real", "c_decimal", "c_char", "c_varchar", "c_varbinary", "c_date", "c_time", "c_timestamp");

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

            statement.execute("CREATE TABLE example.all_types(" +
                    "c_bigint bigint, c_integer integer, c_double double, c_real real, c_decimal decimal(10, 2), c_char char(5), " +
                    "c_varchar varchar(20), c_varbinary varbinary(8), c_date date, c_time time, c_timestamp timestamp)");
            statement.execute("INSERT INTO example.all_types VALUES " +
                    "(9007199254740993, -42, 2.5, 1.25, 12345678.91, 'ab', 'hello', X'0102ff', DATE '2026-01-02', TIME '03:04:05', TIMESTAMP '2026-01-02 03:04:05.678'), " +
                    "(NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL), " +
                    "(0, 0, -0.0, 0.0, -0.01, 'abcde', '', X'', DATE '1969-12-31', TIME '23:59:59', TIMESTAMP '1969-12-31 23:59:59.999')");
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
    public void testValuesMatchRecordCursor()
    {
        // Each writer must produce the value the connector's read mapping gives the record cursor
        ConnectorSession session = testSessionBuilder()
                .setSystemProperty(LEGACY_TIMESTAMP, "false")
                .build()
                .toConnectorSession();
        Map<String, JdbcColumnHandle> columnsByName = database.getColumnHandles("example", "all_types");
        List<JdbcColumnHandle> columns = ALL_TYPES_COLUMNS.stream().map(columnsByName::get).collect(ImmutableList.toImmutableList());
        JdbcSplit split = database.getSplit("example", "all_types");

        List<List<Object>> expected = new ArrayList<>();
        try (RecordCursor cursor = new JdbcRecordSet(database.getJdbcClient(), session, split, columns).cursor()) {
            while (cursor.advanceNextPosition()) {
                List<Object> row = new ArrayList<>();
                for (int column = 0; column < columns.size(); column++) {
                    row.add(cursorValue(cursor, column, columns.get(column).getColumnType()));
                }
                expected.add(row);
            }
        }

        try (BufferAllocator testAllocator = allocator.newChildAllocator("testValuesMatchRecordCursor", 0, Long.MAX_VALUE)) {
            List<List<Object>> actual = new ArrayList<>();
            ArrowBlockBuilder blockBuilder = new ArrowBlockBuilder(FUNCTION_AND_TYPE_MANAGER);
            // A batch size of 2 makes the rows span batches
            try (JdbcArrowSource source = new JdbcArrowSource(database.getJdbcClient(), session, split, columns, 2, new ConnectorArrowSourceAdapter.BufferAllocatorHolderImpl(testAllocator))) {
                while (source.nextArrowBatch()) {
                    VectorSchemaRoot root = source.getVectorSchemaRoot();
                    List<Block> blocks = new ArrayList<>();
                    for (int column = 0; column < columns.size(); column++) {
                        blocks.add(blockBuilder.buildBlockFromFieldVector(root.getVector(column), decodeType(columns.get(column).getColumnType()), null));
                    }
                    for (int position = 0; position < root.getRowCount(); position++) {
                        List<Object> row = new ArrayList<>();
                        for (int column = 0; column < columns.size(); column++) {
                            row.add(blockValue(blocks.get(column), position, decodeType(columns.get(column).getColumnType())));
                        }
                        actual.add(row);
                    }
                }
            }

            assertEquals(actual, expected);
            assertEquals(testAllocator.getAllocatedMemory(), 0);
        }
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

    // ArrowBlockBuilder reads Utf8 vectors as varchar; CHAR values are compared as their raw slices
    private static Type decodeType(Type type)
    {
        return type instanceof CharType ? createUnboundedVarcharType() : type;
    }

    private static Object cursorValue(RecordCursor cursor, int field, Type type)
    {
        if (cursor.isNull(field)) {
            return null;
        }
        Class<?> javaType = type.getJavaType();
        if (javaType == long.class) {
            return cursor.getLong(field);
        }
        if (javaType == double.class) {
            return cursor.getDouble(field);
        }
        if (javaType == boolean.class) {
            return cursor.getBoolean(field);
        }
        if (javaType == Slice.class) {
            return cursor.getSlice(field);
        }
        throw new IllegalArgumentException("Unexpected type " + type);
    }

    private static Object blockValue(Block block, int position, Type type)
    {
        if (block.isNull(position)) {
            return null;
        }
        Class<?> javaType = type.getJavaType();
        if (javaType == long.class) {
            return type.getLong(block, position);
        }
        if (javaType == double.class) {
            return type.getDouble(block, position);
        }
        if (javaType == boolean.class) {
            return type.getBoolean(block, position);
        }
        if (javaType == Slice.class) {
            return type.getSlice(block, position);
        }
        throw new IllegalArgumentException("Unexpected type " + type);
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
