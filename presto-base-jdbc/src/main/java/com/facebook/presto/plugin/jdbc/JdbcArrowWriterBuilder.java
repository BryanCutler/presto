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

import com.facebook.plugin.arrow.ArrowVectorWriter;
import com.facebook.plugin.arrow.ArrowWriterFactory;
import com.facebook.presto.common.type.DecimalType;
import com.facebook.presto.common.type.Type;
import com.facebook.presto.plugin.jdbc.mapping.ReadMapping;
import com.facebook.presto.plugin.jdbc.mapping.functions.BooleanReadFunction;
import com.facebook.presto.plugin.jdbc.mapping.functions.DoubleReadFunction;
import com.facebook.presto.plugin.jdbc.mapping.functions.LongReadFunction;
import com.facebook.presto.plugin.jdbc.mapping.functions.SliceReadFunction;
import com.facebook.presto.spi.PrestoException;
import io.airlift.slice.Slice;
import org.apache.arrow.vector.BaseVariableWidthVector;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.DateDayVector;
import org.apache.arrow.vector.DecimalVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.Float4Vector;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.SmallIntVector;
import org.apache.arrow.vector.TimeMilliVector;
import org.apache.arrow.vector.TimeStampVector;
import org.apache.arrow.vector.TinyIntVector;

import java.math.BigDecimal;
import java.math.MathContext;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.facebook.presto.common.type.DateTimeEncoding.unpackMillisUtc;
import static com.facebook.presto.common.type.Decimals.decodeUnscaledValue;
import static com.facebook.presto.plugin.jdbc.JdbcErrorCode.JDBC_ERROR;
import static java.lang.Float.intBitsToFloat;
import static java.lang.Math.toIntExact;

/**
 * Builds writers that read each value of the current row with the connector's {@link ReadMapping}
 * and set it in the column's vector. The position passed to {@code write} is the 1-based result set
 * column index. A null value is left unset, relying on the vector being newly allocated for each batch.
 *
 * <p>Writers for primitive types read the value first and then check {@code wasNull()}, since the
 * primitive JDBC getters return 0 or false for NULL. The other read functions dereference the driver's
 * result (for example {@code utf8Slice(getString())} or {@code getDate().getTime()}) and cannot be called
 * on NULL, so those writers check for NULL first, as {@link JdbcRecordCursor} does.
 */
class JdbcArrowWriterBuilder
        extends ArrowWriterFactory.ArrowWriterBuilder<ResultSet>
{
    private final ReadMapping readMapping;

    public JdbcArrowWriterBuilder(FieldVector vector, Type type, ReadMapping readMapping)
    {
        super(vector, type);
        this.readMapping = readMapping;
    }

    @Override
    protected ArrowVectorWriter<ResultSet> buildArrowBitWriter()
    {
        return new JdbcArrowBitWriter((BitVector) vector, (BooleanReadFunction) readMapping.getReadFunction());
    }

    @Override
    protected ArrowVectorWriter<ResultSet> buildArrowTinyIntWriter()
    {
        return new JdbcArrowTinyIntWriter((TinyIntVector) vector, (LongReadFunction) readMapping.getReadFunction());
    }

    @Override
    protected ArrowVectorWriter<ResultSet> buildArrowSmallIntWriter()
    {
        return new JdbcArrowSmallIntWriter((SmallIntVector) vector, (LongReadFunction) readMapping.getReadFunction());
    }

    @Override
    protected ArrowVectorWriter<ResultSet> buildArrowIntWriter()
    {
        return new JdbcArrowIntWriter((IntVector) vector, (LongReadFunction) readMapping.getReadFunction());
    }

    @Override
    protected ArrowVectorWriter<ResultSet> buildArrowLongWriter()
    {
        return new JdbcArrowLongWriter((BigIntVector) vector, (LongReadFunction) readMapping.getReadFunction());
    }

    @Override
    protected ArrowVectorWriter<ResultSet> buildArrowRealWriter()
    {
        return new JdbcArrowRealWriter((Float4Vector) vector, (LongReadFunction) readMapping.getReadFunction());
    }

    @Override
    protected ArrowVectorWriter<ResultSet> buildArrowDoubleWriter()
    {
        return new JdbcArrowDoubleWriter((Float8Vector) vector, (DoubleReadFunction) readMapping.getReadFunction());
    }

    @Override
    protected ArrowVectorWriter<ResultSet> buildArrowShortDecimalWriter()
    {
        return new JdbcArrowShortDecimalWriter((DecimalVector) vector, (LongReadFunction) readMapping.getReadFunction());
    }

    @Override
    protected ArrowVectorWriter<ResultSet> buildArrowLongDecimalWriter()
    {
        return new JdbcArrowLongDecimalWriter((DecimalVector) vector, (DecimalType) type, (SliceReadFunction) readMapping.getReadFunction());
    }

    @Override
    protected ArrowVectorWriter<ResultSet> buildArrowVariableWidthWriter()
    {
        return new JdbcArrowVariableWidthWriter((BaseVariableWidthVector) vector, (SliceReadFunction) readMapping.getReadFunction());
    }

    @Override
    protected ArrowVectorWriter<ResultSet> buildArrowDateWriter()
    {
        return new JdbcArrowDateWriter((DateDayVector) vector, (LongReadFunction) readMapping.getReadFunction());
    }

    @Override
    protected ArrowVectorWriter<ResultSet> buildArrowTimeWriter()
    {
        return new JdbcArrowTimeWriter((TimeMilliVector) vector, (LongReadFunction) readMapping.getReadFunction());
    }

    @Override
    protected ArrowVectorWriter<ResultSet> buildArrowTimeWithTimeZoneWriter()
    {
        return new JdbcArrowTimeWithTimeZoneWriter((TimeMilliVector) vector, (LongReadFunction) readMapping.getReadFunction());
    }

    @Override
    protected ArrowVectorWriter<ResultSet> buildArrowTimeStampWriter()
    {
        return new JdbcArrowTimeStampWriter((TimeStampVector) vector, (LongReadFunction) readMapping.getReadFunction());
    }

    @Override
    protected ArrowVectorWriter<ResultSet> buildArrowTimeStampWithTimeZoneWriter()
    {
        return new JdbcArrowTimeStampWithTimeZoneWriter((TimeStampVector) vector, (LongReadFunction) readMapping.getReadFunction());
    }

    // Nested values arrive from the read mapping as a Block; their elements need Block-based child writers
    @Override
    protected ArrowVectorWriter<ResultSet> buildArrowListWriter()
    {
        throw new UnsupportedOperationException("ARRAY columns are not supported by JdbcArrowSource: " + type);
    }

    @Override
    protected ArrowVectorWriter<ResultSet> buildArrowMapWriter()
    {
        throw new UnsupportedOperationException("MAP columns are not supported by JdbcArrowSource: " + type);
    }

    @Override
    protected ArrowVectorWriter<ResultSet> buildArrowStructWriter()
    {
        throw new UnsupportedOperationException("ROW columns are not supported by JdbcArrowSource: " + type);
    }

    // Same null check as JdbcRecordCursor.isNull, for read functions that cannot be called on NULL
    private static boolean isNull(ResultSet resultSet, int position)
            throws SQLException
    {
        resultSet.getObject(position);
        return resultSet.wasNull();
    }

    public static class JdbcArrowBitWriter
            extends ArrowVectorWriter<ResultSet>
    {
        private final BitVector vector;
        private final BooleanReadFunction readFunction;

        public JdbcArrowBitWriter(BitVector vector, BooleanReadFunction readFunction)
        {
            this.vector = vector;
            this.readFunction = readFunction;
        }

        @Override
        public void write(int index, ResultSet resultSet, int position)
        {
            try {
                boolean value = readFunction.readBoolean(resultSet, position);
                if (!resultSet.wasNull()) {
                    vector.setSafe(index, value ? 1 : 0);
                }
            }
            catch (SQLException e) {
                throw new PrestoException(JDBC_ERROR, e);
            }
        }
    }

    public static class JdbcArrowTinyIntWriter
            extends ArrowVectorWriter<ResultSet>
    {
        private final TinyIntVector vector;
        private final LongReadFunction readFunction;

        public JdbcArrowTinyIntWriter(TinyIntVector vector, LongReadFunction readFunction)
        {
            this.vector = vector;
            this.readFunction = readFunction;
        }

        @Override
        public void write(int index, ResultSet resultSet, int position)
        {
            try {
                long value = readFunction.readLong(resultSet, position);
                if (!resultSet.wasNull()) {
                    vector.setSafe(index, (int) value);
                }
            }
            catch (SQLException e) {
                throw new PrestoException(JDBC_ERROR, e);
            }
        }
    }

    public static class JdbcArrowSmallIntWriter
            extends ArrowVectorWriter<ResultSet>
    {
        private final SmallIntVector vector;
        private final LongReadFunction readFunction;

        public JdbcArrowSmallIntWriter(SmallIntVector vector, LongReadFunction readFunction)
        {
            this.vector = vector;
            this.readFunction = readFunction;
        }

        @Override
        public void write(int index, ResultSet resultSet, int position)
        {
            try {
                long value = readFunction.readLong(resultSet, position);
                if (!resultSet.wasNull()) {
                    vector.setSafe(index, (int) value);
                }
            }
            catch (SQLException e) {
                throw new PrestoException(JDBC_ERROR, e);
            }
        }
    }

    public static class JdbcArrowIntWriter
            extends ArrowVectorWriter<ResultSet>
    {
        private final IntVector vector;
        private final LongReadFunction readFunction;

        public JdbcArrowIntWriter(IntVector vector, LongReadFunction readFunction)
        {
            this.vector = vector;
            this.readFunction = readFunction;
        }

        @Override
        public void write(int index, ResultSet resultSet, int position)
        {
            try {
                long value = readFunction.readLong(resultSet, position);
                if (!resultSet.wasNull()) {
                    vector.setSafe(index, (int) value);
                }
            }
            catch (SQLException e) {
                throw new PrestoException(JDBC_ERROR, e);
            }
        }
    }

    public static class JdbcArrowLongWriter
            extends ArrowVectorWriter<ResultSet>
    {
        private final BigIntVector vector;
        private final LongReadFunction readFunction;

        public JdbcArrowLongWriter(BigIntVector vector, LongReadFunction readFunction)
        {
            this.vector = vector;
            this.readFunction = readFunction;
        }

        @Override
        public void write(int index, ResultSet resultSet, int position)
        {
            try {
                long value = readFunction.readLong(resultSet, position);
                if (!resultSet.wasNull()) {
                    vector.setSafe(index, value);
                }
            }
            catch (SQLException e) {
                throw new PrestoException(JDBC_ERROR, e);
            }
        }
    }

    public static class JdbcArrowRealWriter
            extends ArrowVectorWriter<ResultSet>
    {
        private final Float4Vector vector;
        private final LongReadFunction readFunction;

        public JdbcArrowRealWriter(Float4Vector vector, LongReadFunction readFunction)
        {
            this.vector = vector;
            this.readFunction = readFunction;
        }

        @Override
        public void write(int index, ResultSet resultSet, int position)
        {
            try {
                // Presto encodes REAL as the float's bits in a long
                long value = readFunction.readLong(resultSet, position);
                if (!resultSet.wasNull()) {
                    vector.setSafe(index, intBitsToFloat(toIntExact(value)));
                }
            }
            catch (SQLException e) {
                throw new PrestoException(JDBC_ERROR, e);
            }
        }
    }

    public static class JdbcArrowDoubleWriter
            extends ArrowVectorWriter<ResultSet>
    {
        private final Float8Vector vector;
        private final DoubleReadFunction readFunction;

        public JdbcArrowDoubleWriter(Float8Vector vector, DoubleReadFunction readFunction)
        {
            this.vector = vector;
            this.readFunction = readFunction;
        }

        @Override
        public void write(int index, ResultSet resultSet, int position)
        {
            try {
                double value = readFunction.readDouble(resultSet, position);
                if (!resultSet.wasNull()) {
                    vector.setSafe(index, value);
                }
            }
            catch (SQLException e) {
                throw new PrestoException(JDBC_ERROR, e);
            }
        }
    }

    public static class JdbcArrowShortDecimalWriter
            extends ArrowVectorWriter<ResultSet>
    {
        private final DecimalVector vector;
        private final LongReadFunction readFunction;

        public JdbcArrowShortDecimalWriter(DecimalVector vector, LongReadFunction readFunction)
        {
            this.vector = vector;
            this.readFunction = readFunction;
        }

        @Override
        public void write(int index, ResultSet resultSet, int position)
        {
            try {
                if (!isNull(resultSet, position)) {
                    // Both Presto and Arrow store the unscaled value, so no BigDecimal is needed
                    vector.setSafe(index, readFunction.readLong(resultSet, position));
                }
            }
            catch (SQLException e) {
                throw new PrestoException(JDBC_ERROR, e);
            }
        }
    }

    public static class JdbcArrowLongDecimalWriter
            extends ArrowVectorWriter<ResultSet>
    {
        private final DecimalVector vector;
        private final DecimalType type;
        private final SliceReadFunction readFunction;

        public JdbcArrowLongDecimalWriter(DecimalVector vector, DecimalType type, SliceReadFunction readFunction)
        {
            this.vector = vector;
            this.type = type;
            this.readFunction = readFunction;
        }

        @Override
        public void write(int index, ResultSet resultSet, int position)
        {
            try {
                if (!isNull(resultSet, position)) {
                    Slice value = readFunction.readSlice(resultSet, position);
                    vector.setSafe(index, new BigDecimal(decodeUnscaledValue(value), type.getScale(), new MathContext(type.getPrecision())));
                }
            }
            catch (SQLException e) {
                throw new PrestoException(JDBC_ERROR, e);
            }
        }
    }

    public static class JdbcArrowVariableWidthWriter
            extends ArrowVectorWriter<ResultSet>
    {
        private final BaseVariableWidthVector vector;
        private final SliceReadFunction readFunction;

        public JdbcArrowVariableWidthWriter(BaseVariableWidthVector vector, SliceReadFunction readFunction)
        {
            this.vector = vector;
            this.readFunction = readFunction;
        }

        @Override
        public void write(int index, ResultSet resultSet, int position)
        {
            try {
                if (!isNull(resultSet, position)) {
                    Slice value = readFunction.readSlice(resultSet, position);
                    if (value.hasByteArray()) {
                        // Copy straight from the slice's backing array instead of through a new byte[]
                        vector.setSafe(index, value.byteArray(), value.byteArrayOffset(), value.length());
                    }
                    else {
                        vector.setSafe(index, value.getBytes(0, value.length()));
                    }
                }
            }
            catch (SQLException e) {
                throw new PrestoException(JDBC_ERROR, e);
            }
        }
    }

    public static class JdbcArrowDateWriter
            extends ArrowVectorWriter<ResultSet>
    {
        private final DateDayVector vector;
        private final LongReadFunction readFunction;

        public JdbcArrowDateWriter(DateDayVector vector, LongReadFunction readFunction)
        {
            this.vector = vector;
            this.readFunction = readFunction;
        }

        @Override
        public void write(int index, ResultSet resultSet, int position)
        {
            try {
                if (!isNull(resultSet, position)) {
                    // Value is days since epoch UTC
                    vector.setSafe(index, (int) readFunction.readLong(resultSet, position));
                }
            }
            catch (SQLException e) {
                throw new PrestoException(JDBC_ERROR, e);
            }
        }
    }

    public static class JdbcArrowTimeWriter
            extends ArrowVectorWriter<ResultSet>
    {
        private final TimeMilliVector vector;
        private final LongReadFunction readFunction;

        public JdbcArrowTimeWriter(TimeMilliVector vector, LongReadFunction readFunction)
        {
            this.vector = vector;
            this.readFunction = readFunction;
        }

        @Override
        public void write(int index, ResultSet resultSet, int position)
        {
            try {
                if (!isNull(resultSet, position)) {
                    // Value is elapsed time since midnight
                    vector.setSafe(index, (int) readFunction.readLong(resultSet, position));
                }
            }
            catch (SQLException e) {
                throw new PrestoException(JDBC_ERROR, e);
            }
        }
    }

    public static class JdbcArrowTimeWithTimeZoneWriter
            extends ArrowVectorWriter<ResultSet>
    {
        private final TimeMilliVector vector;
        private final LongReadFunction readFunction;

        public JdbcArrowTimeWithTimeZoneWriter(TimeMilliVector vector, LongReadFunction readFunction)
        {
            this.vector = vector;
            this.readFunction = readFunction;
        }

        @Override
        public void write(int index, ResultSet resultSet, int position)
        {
            try {
                if (!isNull(resultSet, position)) {
                    vector.setSafe(index, (int) unpackMillisUtc(readFunction.readLong(resultSet, position)));
                }
            }
            catch (SQLException e) {
                throw new PrestoException(JDBC_ERROR, e);
            }
        }
    }

    public static class JdbcArrowTimeStampWriter
            extends ArrowVectorWriter<ResultSet>
    {
        private final TimeStampVector vector;
        private final LongReadFunction readFunction;

        public JdbcArrowTimeStampWriter(TimeStampVector vector, LongReadFunction readFunction)
        {
            this.vector = vector;
            this.readFunction = readFunction;
        }

        @Override
        public void write(int index, ResultSet resultSet, int position)
        {
            try {
                if (!isNull(resultSet, position)) {
                    vector.setSafe(index, readFunction.readLong(resultSet, position));
                }
            }
            catch (SQLException e) {
                throw new PrestoException(JDBC_ERROR, e);
            }
        }
    }

    public static class JdbcArrowTimeStampWithTimeZoneWriter
            extends ArrowVectorWriter<ResultSet>
    {
        private final TimeStampVector vector;
        private final LongReadFunction readFunction;

        public JdbcArrowTimeStampWithTimeZoneWriter(TimeStampVector vector, LongReadFunction readFunction)
        {
            this.vector = vector;
            this.readFunction = readFunction;
        }

        @Override
        public void write(int index, ResultSet resultSet, int position)
        {
            try {
                if (!isNull(resultSet, position)) {
                    vector.setSafe(index, unpackMillisUtc(readFunction.readLong(resultSet, position)));
                }
            }
            catch (SQLException e) {
                throw new PrestoException(JDBC_ERROR, e);
            }
        }
    }
}
