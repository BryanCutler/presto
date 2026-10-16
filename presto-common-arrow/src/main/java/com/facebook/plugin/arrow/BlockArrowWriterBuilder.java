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

import com.facebook.presto.common.block.Block;
import com.facebook.presto.common.block.IntArrayBlock;
import com.facebook.presto.common.type.ArrayType;
import com.facebook.presto.common.type.DecimalType;
import com.facebook.presto.common.type.MapType;
import com.facebook.presto.common.type.RowType;
import com.facebook.presto.common.type.Type;
import com.google.common.collect.ImmutableList;
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
import org.apache.arrow.vector.complex.ListVector;
import org.apache.arrow.vector.complex.MapVector;
import org.apache.arrow.vector.complex.StructVector;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

import static com.facebook.presto.common.type.DateTimeEncoding.unpackMillisUtc;
import static com.facebook.presto.common.type.Decimals.decodeUnscaledValue;
import static java.lang.Float.intBitsToFloat;
import static java.lang.Math.toIntExact;

/**
 * Builds writers that set the value at a {@link Block} position in the column's vector, converting it
 * from its Presto encoding to Arrow. Nulls are written explicitly, including for the elements of
 * nested values, so vectors do not need to be zeroed before a batch.
 */
public class BlockArrowWriterBuilder
        extends ArrowWriterFactory.ArrowWriterBuilder<Block>
{
    public BlockArrowWriterBuilder(FieldVector vector, Type type)
    {
        super(vector, type);
    }

    @Override
    protected ArrowVectorWriter<Block> buildArrowBitWriter()
    {
        return new BlockArrowBitWriter((BitVector) vector, type);
    }

    @Override
    protected ArrowVectorWriter<Block> buildArrowTinyIntWriter()
    {
        return new BlockArrowTinyIntWriter((TinyIntVector) vector, type);
    }

    @Override
    protected ArrowVectorWriter<Block> buildArrowSmallIntWriter()
    {
        return new BlockArrowSmallIntWriter((SmallIntVector) vector, type);
    }

    @Override
    protected ArrowVectorWriter<Block> buildArrowIntWriter()
    {
        return new BlockArrowIntWriter((IntVector) vector, type);
    }

    @Override
    protected ArrowVectorWriter<Block> buildArrowLongWriter()
    {
        return new BlockArrowLongWriter((BigIntVector) vector, type);
    }

    @Override
    protected ArrowVectorWriter<Block> buildArrowRealWriter()
    {
        return new BlockArrowRealWriter((Float4Vector) vector, type);
    }

    @Override
    protected ArrowVectorWriter<Block> buildArrowDoubleWriter()
    {
        return new BlockArrowDoubleWriter((Float8Vector) vector, type);
    }

    @Override
    protected ArrowVectorWriter<Block> buildArrowShortDecimalWriter()
    {
        return new BlockArrowShortDecimalWriter((DecimalVector) vector, type);
    }

    @Override
    protected ArrowVectorWriter<Block> buildArrowLongDecimalWriter()
    {
        return new BlockArrowLongDecimalWriter((DecimalVector) vector, (DecimalType) type);
    }

    @Override
    protected ArrowVectorWriter<Block> buildArrowVariableWidthWriter()
    {
        return new BlockArrowVariableWidthWriter((BaseVariableWidthVector) vector, type);
    }

    @Override
    protected ArrowVectorWriter<Block> buildArrowDateWriter()
    {
        return new BlockArrowDateWriter((DateDayVector) vector, type);
    }

    @Override
    protected ArrowVectorWriter<Block> buildArrowTimeWriter()
    {
        return new BlockArrowTimeWriter((TimeMilliVector) vector, type);
    }

    @Override
    protected ArrowVectorWriter<Block> buildArrowTimeWithTimeZoneWriter()
    {
        return new BlockArrowTimeWithTimeZoneWriter((TimeMilliVector) vector, type);
    }

    @Override
    protected ArrowVectorWriter<Block> buildArrowTimeStampWriter()
    {
        return new BlockArrowTimeStampWriter((TimeStampVector) vector, type);
    }

    @Override
    protected ArrowVectorWriter<Block> buildArrowTimeStampWithTimeZoneWriter()
    {
        return new BlockArrowTimeStampWithTimeZoneWriter((TimeStampVector) vector, type);
    }

    @Override
    protected ArrowVectorWriter<Block> buildArrowListWriter()
    {
        return new BlockArrowListWriter((ListVector) vector, (ArrayType) type);
    }

    @Override
    protected ArrowVectorWriter<Block> buildArrowMapWriter()
    {
        return new BlockArrowMapWriter((MapVector) vector, (MapType) type);
    }

    @Override
    protected ArrowVectorWriter<Block> buildArrowStructWriter()
    {
        return new BlockArrowStructWriter((StructVector) vector, (RowType) type);
    }

    private static ArrowVectorWriter<Block> createChildWriter(FieldVector vector, Type type)
    {
        return ArrowWriterFactory.createArrowWriter(new BlockArrowWriterBuilder(vector, type));
    }

    // Kept from the former BlockPrimitiveGetter: IntArrayBlock values are widened with toLong
    private static long getLong(Type type, Block block, int position)
    {
        if (block instanceof IntArrayBlock) {
            return block.toLong(position);
        }
        return type.getLong(block, position);
    }

    public static class BlockArrowBitWriter
            extends ArrowVectorWriter<Block>
    {
        private final BitVector vector;
        private final Type type;

        public BlockArrowBitWriter(BitVector vector, Type type)
        {
            this.vector = vector;
            this.type = type;
        }

        @Override
        public void write(int index, Block block, int position)
        {
            if (block.isNull(position)) {
                vector.setNull(index);
            }
            else {
                vector.setSafe(index, type.getBoolean(block, position) ? 1 : 0);
            }
        }
    }

    public static class BlockArrowTinyIntWriter
            extends ArrowVectorWriter<Block>
    {
        private final TinyIntVector vector;
        private final Type type;

        public BlockArrowTinyIntWriter(TinyIntVector vector, Type type)
        {
            this.vector = vector;
            this.type = type;
        }

        @Override
        public void write(int index, Block block, int position)
        {
            if (block.isNull(position)) {
                vector.setNull(index);
            }
            else {
                vector.setSafe(index, (int) getLong(type, block, position));
            }
        }
    }

    public static class BlockArrowSmallIntWriter
            extends ArrowVectorWriter<Block>
    {
        private final SmallIntVector vector;
        private final Type type;

        public BlockArrowSmallIntWriter(SmallIntVector vector, Type type)
        {
            this.vector = vector;
            this.type = type;
        }

        @Override
        public void write(int index, Block block, int position)
        {
            if (block.isNull(position)) {
                vector.setNull(index);
            }
            else {
                vector.setSafe(index, (int) getLong(type, block, position));
            }
        }
    }

    public static class BlockArrowIntWriter
            extends ArrowVectorWriter<Block>
    {
        private final IntVector vector;
        private final Type type;

        public BlockArrowIntWriter(IntVector vector, Type type)
        {
            this.vector = vector;
            this.type = type;
        }

        @Override
        public void write(int index, Block block, int position)
        {
            if (block.isNull(position)) {
                vector.setNull(index);
            }
            else {
                vector.setSafe(index, (int) getLong(type, block, position));
            }
        }
    }

    public static class BlockArrowLongWriter
            extends ArrowVectorWriter<Block>
    {
        private final BigIntVector vector;
        private final Type type;

        public BlockArrowLongWriter(BigIntVector vector, Type type)
        {
            this.vector = vector;
            this.type = type;
        }

        @Override
        public void write(int index, Block block, int position)
        {
            if (block.isNull(position)) {
                vector.setNull(index);
            }
            else {
                vector.setSafe(index, getLong(type, block, position));
            }
        }
    }

    public static class BlockArrowRealWriter
            extends ArrowVectorWriter<Block>
    {
        private final Float4Vector vector;
        private final Type type;

        public BlockArrowRealWriter(Float4Vector vector, Type type)
        {
            this.vector = vector;
            this.type = type;
        }

        @Override
        public void write(int index, Block block, int position)
        {
            if (block.isNull(position)) {
                vector.setNull(index);
            }
            else {
                // Presto encodes REAL as the float's bits in a long
                vector.setSafe(index, intBitsToFloat(toIntExact(getLong(type, block, position))));
            }
        }
    }

    public static class BlockArrowDoubleWriter
            extends ArrowVectorWriter<Block>
    {
        private final Float8Vector vector;
        private final Type type;

        public BlockArrowDoubleWriter(Float8Vector vector, Type type)
        {
            this.vector = vector;
            this.type = type;
        }

        @Override
        public void write(int index, Block block, int position)
        {
            if (block.isNull(position)) {
                vector.setNull(index);
            }
            else {
                vector.setSafe(index, type.getDouble(block, position));
            }
        }
    }

    public static class BlockArrowShortDecimalWriter
            extends ArrowVectorWriter<Block>
    {
        private final DecimalVector vector;
        private final Type type;

        public BlockArrowShortDecimalWriter(DecimalVector vector, Type type)
        {
            this.vector = vector;
            this.type = type;
        }

        @Override
        public void write(int index, Block block, int position)
        {
            if (block.isNull(position)) {
                vector.setNull(index);
            }
            else {
                // Both Presto and Arrow store the unscaled value, so no BigDecimal is needed
                vector.setSafe(index, type.getLong(block, position));
            }
        }
    }

    public static class BlockArrowLongDecimalWriter
            extends ArrowVectorWriter<Block>
    {
        private final DecimalVector vector;
        private final DecimalType type;

        public BlockArrowLongDecimalWriter(DecimalVector vector, DecimalType type)
        {
            this.vector = vector;
            this.type = type;
        }

        @Override
        public void write(int index, Block block, int position)
        {
            if (block.isNull(position)) {
                vector.setNull(index);
            }
            else {
                Slice value = type.getSlice(block, position);
                vector.setSafe(index, new BigDecimal(decodeUnscaledValue(value), type.getScale(), new MathContext(type.getPrecision())));
            }
        }
    }

    public static class BlockArrowVariableWidthWriter
            extends ArrowVectorWriter<Block>
    {
        private final BaseVariableWidthVector vector;
        private final Type type;

        public BlockArrowVariableWidthWriter(BaseVariableWidthVector vector, Type type)
        {
            this.vector = vector;
            this.type = type;
        }

        @Override
        public void write(int index, Block block, int position)
        {
            if (block.isNull(position)) {
                vector.setNull(index);
            }
            else {
                Slice value = type.getSlice(block, position);
                if (value.hasByteArray()) {
                    // Copy straight from the slice's backing array instead of through a new byte[]
                    vector.setSafe(index, value.byteArray(), value.byteArrayOffset(), value.length());
                }
                else {
                    vector.setSafe(index, value.getBytes(0, value.length()));
                }
            }
        }
    }

    public static class BlockArrowDateWriter
            extends ArrowVectorWriter<Block>
    {
        private final DateDayVector vector;
        private final Type type;

        public BlockArrowDateWriter(DateDayVector vector, Type type)
        {
            this.vector = vector;
            this.type = type;
        }

        @Override
        public void write(int index, Block block, int position)
        {
            if (block.isNull(position)) {
                vector.setNull(index);
            }
            else {
                // Value is days since epoch UTC
                vector.setSafe(index, (int) getLong(type, block, position));
            }
        }
    }

    public static class BlockArrowTimeWriter
            extends ArrowVectorWriter<Block>
    {
        private final TimeMilliVector vector;
        private final Type type;

        public BlockArrowTimeWriter(TimeMilliVector vector, Type type)
        {
            this.vector = vector;
            this.type = type;
        }

        @Override
        public void write(int index, Block block, int position)
        {
            if (block.isNull(position)) {
                vector.setNull(index);
            }
            else {
                // Value is elapsed time since midnight
                vector.setSafe(index, (int) getLong(type, block, position));
            }
        }
    }

    public static class BlockArrowTimeWithTimeZoneWriter
            extends ArrowVectorWriter<Block>
    {
        private final TimeMilliVector vector;
        private final Type type;

        public BlockArrowTimeWithTimeZoneWriter(TimeMilliVector vector, Type type)
        {
            this.vector = vector;
            this.type = type;
        }

        @Override
        public void write(int index, Block block, int position)
        {
            if (block.isNull(position)) {
                vector.setNull(index);
            }
            else {
                vector.setSafe(index, (int) unpackMillisUtc(getLong(type, block, position)));
            }
        }
    }

    public static class BlockArrowTimeStampWriter
            extends ArrowVectorWriter<Block>
    {
        private final TimeStampVector vector;
        private final Type type;

        public BlockArrowTimeStampWriter(TimeStampVector vector, Type type)
        {
            this.vector = vector;
            this.type = type;
        }

        @Override
        public void write(int index, Block block, int position)
        {
            if (block.isNull(position)) {
                vector.setNull(index);
            }
            else {
                vector.setSafe(index, getLong(type, block, position));
            }
        }
    }

    public static class BlockArrowTimeStampWithTimeZoneWriter
            extends ArrowVectorWriter<Block>
    {
        private final TimeStampVector vector;
        private final Type type;

        public BlockArrowTimeStampWithTimeZoneWriter(TimeStampVector vector, Type type)
        {
            this.vector = vector;
            this.type = type;
        }

        @Override
        public void write(int index, Block block, int position)
        {
            if (block.isNull(position)) {
                vector.setNull(index);
            }
            else {
                vector.setSafe(index, unpackMillisUtc(getLong(type, block, position)));
            }
        }
    }

    public static class BlockArrowListWriter
            extends ArrowVectorWriter<Block>
    {
        private final ListVector vector;
        private final ArrayType type;
        private final ArrowVectorWriter<Block> elementWriter;

        public BlockArrowListWriter(ListVector vector, ArrayType type)
        {
            this.vector = vector;
            this.type = type;
            this.elementWriter = createChildWriter(vector.getDataVector(), type.getElementType());
        }

        @Override
        public void write(int index, Block block, int position)
        {
            if (block.isNull(position)) {
                vector.setNull(index);
                return;
            }
            Block elements = type.getObject(block, position);
            int dataIndex = vector.startNewValue(index);
            for (int i = 0; i < elements.getPositionCount(); i++) {
                elementWriter.write(dataIndex + i, elements, i);
            }
            vector.endValue(index, elements.getPositionCount());
        }
    }

    public static class BlockArrowMapWriter
            extends ArrowVectorWriter<Block>
    {
        private final MapVector vector;
        private final MapType type;
        private final StructVector entriesVector;
        private final ArrowVectorWriter<Block> keyWriter;
        private final ArrowVectorWriter<Block> valueWriter;

        public BlockArrowMapWriter(MapVector vector, MapType type)
        {
            this.vector = vector;
            this.type = type;
            this.entriesVector = (StructVector) vector.getDataVector();
            this.keyWriter = createChildWriter((FieldVector) entriesVector.getChildByOrdinal(0), type.getKeyType());
            this.valueWriter = createChildWriter((FieldVector) entriesVector.getChildByOrdinal(1), type.getValueType());
        }

        @Override
        public void write(int index, Block block, int position)
        {
            if (block.isNull(position)) {
                vector.setNull(index);
                return;
            }
            // A single map block holds keys and values interleaved: key at 2 * i, value at 2 * i + 1
            Block singleMapBlock = type.getObject(block, position);
            int dataIndex = vector.startNewValue(index);
            int entryCount = singleMapBlock.getPositionCount() / 2;
            for (int i = 0; i < entryCount; i++) {
                keyWriter.write(dataIndex + i, singleMapBlock, i * 2);
                valueWriter.write(dataIndex + i, singleMapBlock, (i * 2) + 1);
                entriesVector.setIndexDefined(dataIndex + i);
            }
            vector.endValue(index, entryCount);
        }
    }

    public static class BlockArrowStructWriter
            extends ArrowVectorWriter<Block>
    {
        private final StructVector vector;
        private final RowType type;
        private final List<ArrowVectorWriter<Block>> fieldWriters;

        public BlockArrowStructWriter(StructVector vector, RowType type)
        {
            this.vector = vector;
            this.type = type;
            ImmutableList.Builder<ArrowVectorWriter<Block>> writerBuilder = ImmutableList.builder();
            List<FieldVector> fieldVectors = vector.getChildrenFromFields();
            for (int i = 0; i < fieldVectors.size(); i++) {
                writerBuilder.add(createChildWriter(fieldVectors.get(i), type.getTypeParameters().get(i)));
            }
            this.fieldWriters = writerBuilder.build();
        }

        @Override
        public void write(int index, Block block, int position)
        {
            if (block.isNull(position)) {
                vector.setNull(index);
                return;
            }
            Block singleRowBlock = type.getObject(block, position);
            for (int i = 0; i < fieldWriters.size(); i++) {
                fieldWriters.get(i).write(index, singleRowBlock, i);
            }
            vector.setIndexDefined(index);
        }
    }
}
