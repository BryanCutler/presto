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
package com.facebook.plugin.arrow;

import com.facebook.presto.common.type.ArrayType;
import com.facebook.presto.common.type.BigintType;
import com.facebook.presto.common.type.BooleanType;
import com.facebook.presto.common.type.CharType;
import com.facebook.presto.common.type.DateType;
import com.facebook.presto.common.type.DecimalType;
import com.facebook.presto.common.type.DoubleType;
import com.facebook.presto.common.type.IntegerType;
import com.facebook.presto.common.type.MapType;
import com.facebook.presto.common.type.RealType;
import com.facebook.presto.common.type.RowType;
import com.facebook.presto.common.type.SmallintType;
import com.facebook.presto.common.type.TimeType;
import com.facebook.presto.common.type.TimeWithTimeZoneType;
import com.facebook.presto.common.type.TimestampType;
import com.facebook.presto.common.type.TimestampWithTimeZoneType;
import com.facebook.presto.common.type.TinyintType;
import com.facebook.presto.common.type.Type;
import com.facebook.presto.common.type.VarbinaryType;
import com.facebook.presto.spi.ColumnMetadata;
import com.google.common.collect.ImmutableList;
import io.airlift.slice.Slice;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.complex.BaseRepeatedValueVector;
import org.apache.arrow.vector.complex.MapVector;
import org.apache.arrow.vector.types.Types;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.facebook.plugin.arrow.ArrowErrorCode.ARROW_FLIGHT_TYPE_ERROR;
import static com.facebook.presto.common.type.StandardTypes.ARRAY;
import static com.facebook.presto.common.type.StandardTypes.MAP;
import static com.facebook.presto.common.type.StandardTypes.ROW;
import static com.facebook.presto.common.type.Varchars.isVarcharType;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.lang.String.format;

public class ArrowWriterFactory
{
    private ArrowWriterFactory()
    {
    }

    public static Field prestoToArrowField(ColumnMetadata column)
    {
        Field field;
        Map<String, String> metadata = column.getProperties().entrySet().stream().collect(toImmutableMap(Map.Entry::getKey, Object::toString));
        if (column.getType().getTypeSignature().getBase().equals(ARRAY)) {
            ArrayType arrayType = (ArrayType) column.getType();
            Field childField = prestoToArrowField(ColumnMetadata.builder().setName(BaseRepeatedValueVector.DATA_VECTOR_NAME).setType(arrayType.getElementType()).build());
            field = new Field(column.getName(), new FieldType(column.isNullable(), ArrowType.List.INSTANCE, null, metadata), ImmutableList.of(childField));
        }
        else if (column.getType().getTypeSignature().getBase().equals(MAP)) {
            MapType mapType = (MapType) column.getType();
            // NOTE: Arrow key type must be non-nullable
            Field keyField = prestoToArrowField(ColumnMetadata.builder().setName(MapVector.KEY_NAME).setType(mapType.getKeyType()).setNullable(false).build());
            Field valueField = prestoToArrowField(ColumnMetadata.builder().setName(MapVector.VALUE_NAME).setType(mapType.getValueType()).build());
            Field entriesField = new Field(MapVector.DATA_VECTOR_NAME, FieldType.notNullable(ArrowType.Struct.INSTANCE), ImmutableList.of(keyField, valueField));
            field = new Field(column.getName(), new FieldType(column.isNullable(), new ArrowType.Map(false), null, metadata), ImmutableList.of(entriesField));
        }
        else if (column.getType().getTypeSignature().getBase().equals(ROW)) {
            RowType rowType = (RowType) column.getType();
            List<RowType.Field> rowFields = rowType.getFields();

            AtomicInteger childCount = new AtomicInteger();
            List<Field> childFields = rowFields.stream().map(f -> prestoToArrowField(
                            ColumnMetadata.builder().setName(f.getName().orElse(format("$child%s$", childCount.incrementAndGet()))).setType(f.getType()).build()))
                    .collect(toImmutableList());

            field = new Field(column.getName(), new FieldType(column.isNullable(), ArrowType.Struct.INSTANCE, null, metadata), childFields);
        }
        else {
            ArrowType arrowType = prestoToArrowType(column.getType());
            field = new Field(column.getName(), new FieldType(column.isNullable(), arrowType, null, metadata), ImmutableList.of());
        }

        return field;
    }

    public static ArrowType prestoToArrowType(Type type)
    {
        if (type.equals(BooleanType.BOOLEAN)) {
            return ArrowType.Bool.INSTANCE;
        }
        else if (type.equals(TinyintType.TINYINT)) {
            return Types.MinorType.TINYINT.getType();
        }
        else if (type.equals(SmallintType.SMALLINT)) {
            return Types.MinorType.SMALLINT.getType();
        }
        else if (type.equals(IntegerType.INTEGER)) {
            return Types.MinorType.INT.getType();
        }
        else if (type.equals(BigintType.BIGINT)) {
            return Types.MinorType.BIGINT.getType();
        }
        else if (type.equals(RealType.REAL)) {
            return Types.MinorType.FLOAT4.getType();
        }
        else if (type.equals(DoubleType.DOUBLE)) {
            return Types.MinorType.FLOAT8.getType();
        }
        else if (type instanceof DecimalType) {
            DecimalType decimalType = (DecimalType) type;
            return new ArrowType.Decimal(decimalType.getPrecision(), decimalType.getScale(), 128);
        }
        else if (type.equals(VarbinaryType.VARBINARY)) {
            return ArrowType.Binary.INSTANCE;
        }
        else if (type instanceof CharType) {
            return ArrowType.Utf8.INSTANCE;
        }
        else if (isVarcharType(type)) {
            return ArrowType.Utf8.INSTANCE;
        }
        else if (type instanceof DateType) {
            return Types.MinorType.DATEDAY.getType();
        }
        else if (type instanceof TimeType) {
            return Types.MinorType.TIMEMILLI.getType();
        }
        else if (type instanceof TimeWithTimeZoneType) {
            // Unpack time from packed time with timezone
            return Types.MinorType.TIMEMILLI.getType();
        }
        else if (type instanceof TimestampType) {
            return Types.MinorType.TIMESTAMPMILLI.getType();
        }
        else if (type instanceof TimestampWithTimeZoneType) {
            // Read as plain timestamp and unpack to UTC, timezone not supplied with type
            return Types.MinorType.TIMESTAMPMILLI.getType();
        }
        throw new ArrowException(ARROW_FLIGHT_TYPE_ERROR, "Unsupported type: " + type);
    }

    /**
     * Creates a writer for the builder's vector, checking that the vector matches the Presto type.
     * The builder supplies the writer for each vector type from its own value source.
     */
    public static <T> ArrowVectorWriter<T> createArrowWriter(ArrowWriterBuilder<T> builder)
    {
        Type type = builder.getType();
        Class<?> javaType = type.getJavaType();

        switch (builder.getVector().getMinorType()) {
            case BIT:
                checkArgument(javaType == boolean.class, "Unexpected type for BitVector: %s", type);
                return builder.buildArrowBitWriter();
            case TINYINT:
                checkArgument(javaType == long.class, "Unexpected type for TinyIntVector: %s", type);
                return builder.buildArrowTinyIntWriter();
            case SMALLINT:
                checkArgument(javaType == long.class, "Unexpected type for SmallIntVector: %s", type);
                return builder.buildArrowSmallIntWriter();
            case INT:
                checkArgument(javaType == long.class, "Unexpected type for IntVector: %s", type);
                return builder.buildArrowIntWriter();
            case BIGINT:
                checkArgument(javaType == long.class, "Unexpected type for BigIntVector: %s", type);
                return builder.buildArrowLongWriter();
            case FLOAT4:
                checkArgument(javaType == long.class, "Unexpected type for Float4Vector: %s", type);
                return builder.buildArrowRealWriter();
            case FLOAT8:
                checkArgument(javaType == double.class, "Unexpected type for Float8Vector: %s", type);
                return builder.buildArrowDoubleWriter();
            case DECIMAL:
                checkArgument(type instanceof DecimalType, "Expected DecimalType but got %s", type);
                if (javaType == long.class) {
                    return builder.buildArrowShortDecimalWriter();
                }
                if (javaType == Slice.class) {
                    return builder.buildArrowLongDecimalWriter();
                }
                throw new ArrowException(ARROW_FLIGHT_TYPE_ERROR, "Unexpected type for DecimalVector: " + type);
            case VARBINARY:
            case VARCHAR:
                checkArgument(javaType == Slice.class, "Unexpected type for BaseVariableWidthVector: %s", type);
                return builder.buildArrowVariableWidthWriter();
            case DATEDAY:
                checkArgument(javaType == long.class, "Unexpected type for DateDayVector: %s", type);
                return builder.buildArrowDateWriter();
            case TIMEMILLI:
                checkArgument(javaType == long.class, "Unexpected type for TimeMilliVector: %s", type);
                if (type instanceof TimeWithTimeZoneType) {
                    return builder.buildArrowTimeWithTimeZoneWriter();
                }
                return builder.buildArrowTimeWriter();
            case TIMESTAMPMILLI:
                checkArgument(javaType == long.class, "Unexpected type for TimeStampVector: %s", type);
                if (type instanceof TimestampWithTimeZoneType) {
                    return builder.buildArrowTimeStampWithTimeZoneWriter();
                }
                return builder.buildArrowTimeStampWriter();
            case LIST:
                checkArgument(type instanceof ArrayType, "Unexpected type for ListVector: %s", type);
                return builder.buildArrowListWriter();
            case MAP:
                checkArgument(type instanceof MapType, "Unexpected type for MapVector: %s", type);
                return builder.buildArrowMapWriter();
            case STRUCT:
                checkArgument(type instanceof RowType, "Unexpected type for StructVector: %s", type);
                return builder.buildArrowStructWriter();
            default:
                throw new ArrowException(ARROW_FLIGHT_TYPE_ERROR, "Unsupported Arrow type: " + builder.getVector().getMinorType().name());
        }
    }

    /**
     * Supplies the writer for each vector type for one value source. {@link #createArrowWriter}
     * calls the method matching the vector after checking it against the Presto type, so
     * implementations may cast {@link #vector} to the vector class named by the method.
     */
    public abstract static class ArrowWriterBuilder<T>
    {
        protected final FieldVector vector;
        protected final Type type;

        public ArrowWriterBuilder(FieldVector vector, Type type)
        {
            this.vector = vector;
            this.type = type;
        }

        public FieldVector getVector()
        {
            return vector;
        }

        public Type getType()
        {
            return type;
        }

        protected abstract ArrowVectorWriter<T> buildArrowBitWriter();

        protected abstract ArrowVectorWriter<T> buildArrowTinyIntWriter();

        protected abstract ArrowVectorWriter<T> buildArrowSmallIntWriter();

        protected abstract ArrowVectorWriter<T> buildArrowIntWriter();

        protected abstract ArrowVectorWriter<T> buildArrowLongWriter();

        protected abstract ArrowVectorWriter<T> buildArrowRealWriter();

        protected abstract ArrowVectorWriter<T> buildArrowDoubleWriter();

        /** Decimal with a {@code long} Java type: the unscaled value. */
        protected abstract ArrowVectorWriter<T> buildArrowShortDecimalWriter();

        /** Decimal with a {@code Slice} Java type: the encoded 128-bit unscaled value. */
        protected abstract ArrowVectorWriter<T> buildArrowLongDecimalWriter();

        /** VARCHAR, CHAR or VARBINARY into a {@code BaseVariableWidthVector}. */
        protected abstract ArrowVectorWriter<T> buildArrowVariableWidthWriter();

        protected abstract ArrowVectorWriter<T> buildArrowDateWriter();

        protected abstract ArrowVectorWriter<T> buildArrowTimeWriter();

        protected abstract ArrowVectorWriter<T> buildArrowTimeWithTimeZoneWriter();

        /** TIMESTAMP into a {@code TimeStampVector}. */
        protected abstract ArrowVectorWriter<T> buildArrowTimeStampWriter();

        /** TIMESTAMP WITH TIME ZONE into a {@code TimeStampVector}. */
        protected abstract ArrowVectorWriter<T> buildArrowTimeStampWithTimeZoneWriter();

        protected abstract ArrowVectorWriter<T> buildArrowListWriter();

        protected abstract ArrowVectorWriter<T> buildArrowMapWriter();

        protected abstract ArrowVectorWriter<T> buildArrowStructWriter();
    }
}
