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

import com.facebook.airlift.log.Logger;
import com.facebook.plugin.arrow.ConnectorArrowSource;
import com.facebook.presto.spi.ConnectorSession;
import com.facebook.presto.spi.PrestoException;
import org.apache.arrow.adapter.jdbc.ArrowVectorIterator;
import org.apache.arrow.adapter.jdbc.JdbcToArrowConfig;
import org.apache.arrow.adapter.jdbc.JdbcToArrowConfigBuilder;
import org.apache.arrow.adapter.jdbc.JdbcToArrowUtils;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.VectorLoader;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.VectorUnloader;
import org.apache.arrow.vector.ipc.message.ArrowRecordBatch;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.Schema;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Collectors;

import static com.facebook.plugin.arrow.BlockArrowWriter.prestoToArrowField;
import static com.facebook.presto.plugin.jdbc.JdbcErrorCode.JDBC_ERROR;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

public class JdbcArrowSource extends ConnectorArrowSource
{
    private static final Logger log = Logger.get(JdbcArrowSource.class);

    private final JdbcColumnHandle[] columnHandles;
    private final Schema schema;
    ArrowVectorIterator vectorIterator;
    VectorSchemaRoot root;
    VectorLoader loader;
    boolean hasNext;

    private final JdbcClient jdbcClient;
    private final Connection connection;
    private final PreparedStatement statement;
    private final ResultSet resultSet;
    private boolean closed;

    public JdbcArrowSource(JdbcClient jdbcClient, ConnectorSession session, JdbcSplit split, List<JdbcColumnHandle> columnHandleList, int recordBatchSize, Object holder)
    {
        this.jdbcClient = requireNonNull(jdbcClient, "jdbcClient is null");
        this.columnHandles = columnHandleList.toArray(new JdbcColumnHandle[0]);

        List<Field> fields = columnHandleList.stream().map(columnHandle -> prestoToArrowField(columnHandle.getColumnMetadata())).collect(Collectors.toList());
        this.schema = new Schema(fields);

        try {
            connection = jdbcClient.getConnection(session, JdbcIdentity.from(session), split);
            statement = jdbcClient.buildSql(session, connection, split, columnHandleList);
            log.debug("Executing: %s", statement.toString());
            resultSet = statement.executeQuery();
        }
        catch (SQLException | RuntimeException e) {
            throw handleSqlException(e);
        }

        if (!(holder instanceof BufferAllocator)) {
            throw new IllegalArgumentException("Expected ConnectorArrowSourceImpl.BufferAllocatorHolder");
        }

        BufferAllocator allocator = ((BufferAllocator) holder);

        this.root = VectorSchemaRoot.create(schema, allocator);
        // TODO need to make sure schemas equal
        this.loader = new VectorLoader(root);

        try {
            JdbcToArrowConfig config =
                    new JdbcToArrowConfigBuilder(allocator, JdbcToArrowUtils.getUtcCalendar()).setTargetBatchSize(recordBatchSize).setReuseVectorSchemaRoot(true)
                            //.setArraySubTypeByColumnNameMap(ARRAY_SUB_TYPE_BY_COLUMN_NAME_MAP)
                            .build();

            vectorIterator = ArrowVectorIterator.create(resultSet, config);
        }
        catch (SQLException | RuntimeException e) {
            throw handleSqlException(e);
        }
    }

    @Override
    public VectorSchemaRoot getVectorSchemaRoot()
    {
        return root;
    }

    @Override
    public boolean nextArrowBatch()
    {
        if (vectorIterator.hasNext()) {
            final VectorUnloader unloader = new VectorUnloader(vectorIterator.next());
            try (ArrowRecordBatch batch = unloader.getRecordBatch()) {
                loader.load(batch);
            }
            return true;
        }

        return false;
    }

    @Override
    public long getReadTimeNanos()
    {
        return 0;
    }

    @Override
    public long getCompletedBytes()
    {
        return 0;
    }

    /*@Override
    public Type getType(int field)
    {
        return columnHandles[field].getColumnType();
    }

    @Override
    public boolean advanceNextPosition()
    {
        if (closed) {
            return false;
        }

        try {
            return resultSet.next();
        }
        catch (SQLException | RuntimeException e) {
            throw handleSqlException(e);
        }
    }

    @Override
    public boolean getBoolean(int field)
    {
        checkState(!closed, "cursor is closed");
        try {
            return booleanReadFunctions[field].readBoolean(resultSet, field + 1);
        }
        catch (SQLException | RuntimeException e) {
            throw handleSqlException(e);
        }
    }

    @Override
    public long getLong(int field)
    {
        checkState(!closed, "cursor is closed");
        try {
            return longReadFunctions[field].readLong(resultSet, field + 1);
        }
        catch (SQLException | RuntimeException e) {
            throw handleSqlException(e);
        }
    }

    @Override
    public double getDouble(int field)
    {
        checkState(!closed, "cursor is closed");
        try {
            return doubleReadFunctions[field].readDouble(resultSet, field + 1);
        }
        catch (SQLException | RuntimeException e) {
            throw handleSqlException(e);
        }
    }

    @Override
    public Slice getSlice(int field)
    {
        checkState(!closed, "cursor is closed");
        try {
            return sliceReadFunctions[field].readSlice(resultSet, field + 1);
        }
        catch (SQLException | RuntimeException e) {
            throw handleSqlException(e);
        }
    }

    @Override
    public Object getObject(int field)
    {
        checkState(!closed, "cursor is closed");
        try {
            return objectReadFunctions[field].readObject(resultSet, field + 1);
        }
        catch (SQLException | RuntimeException e) {
            throw handleSqlException(e);
        }
    }

    @Override
    public boolean isNull(int field)
    {
        checkState(!closed, "cursor is closed");
        checkArgument(field < columnHandles.length, "Invalid field index");

        try {
            // JDBC is kind of dumb: we need to read the field and then ask
            // if it was null, which means we are wasting effort here.
            // We could save the result of the field access if it matters.
            resultSet.getObject(field + 1);

            return resultSet.wasNull();
        }
        catch (SQLException | RuntimeException e) {
            throw handleSqlException(e);
        }
    }*/

    @SuppressWarnings("UnusedDeclaration")
    @Override
    public void close()
    {
        if (closed) {
            return;
        }
        closed = true;

        if (vectorIterator != null) {
            vectorIterator.close();
        }

        // use try with resources to close everything properly
        try (Connection connection = this.connection;
                Statement statement = this.statement;
                ResultSet resultSet = this.resultSet) {
            jdbcClient.abortReadConnection(connection);
        }
        catch (SQLException e) {
            // ignore exception from close
        }
    }

    private RuntimeException handleSqlException(Exception e)
    {
        try {
            close();
        }
        catch (Exception closeException) {
            // Self-suppression not permitted
            if (e != closeException) {
                e.addSuppressed(closeException);
            }
        }
        return new PrestoException(JDBC_ERROR, e);
    }
}
