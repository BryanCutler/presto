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
package com.facebook.plugin.arrow.block;

import com.facebook.plugin.arrow.ArrowException;
import com.facebook.plugin.arrow.BlockArrowWriter;
import com.facebook.presto.common.Page;
import com.facebook.presto.common.block.Block;
import com.facebook.presto.common.block.BlockBuilder;
import com.facebook.presto.common.block.PageBuilderStatus;
import com.facebook.presto.common.type.Type;
import com.facebook.presto.spi.ColumnMetadata;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.SmallIntVector;
import org.apache.arrow.vector.TinyIntVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.Schema;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.facebook.plugin.arrow.ArrowErrorCode.ARROW_FLIGHT_TYPE_ERROR;
import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public class ArrowPageBuilder
{
    // We choose default initial size to be 8 for PageBuilder and BlockBuilder
    // so the underlying data is larger than the object overhead, and the size is power of 2.
    //
    // This could be any other small number.
    private static final int DEFAULT_INITIAL_EXPECTED_ENTRIES = 8;

    private final ArrowVectorBuilder[] blockBuilders;
    //private final List<Type> types;
    private PageBuilderStatus pageBuilderStatus;
    private int declaredPositions;
    //private final VectorSchemaRoot root;

    /**
     * Create a PageBuilder with given types.
     * <p>
     * A PageBuilder instance created with this constructor has no estimation about bytes per entry,
     * therefore it can resize frequently while appending new rows.
     * <p>
     * This constructor should only be used to get the initial PageBuilder.
     * Once the PageBuilder is full use reset() or newPageBuilderLike() to create a new
     * PageBuilder instance with its size estimated based on previous data.
     */
    /*public ArrowPageBuilder(List<? extends Type> types)
    {
        this(DEFAULT_INITIAL_EXPECTED_ENTRIES, types);
    }

    public ArrowPageBuilder(int initialExpectedEntries, List<? extends Type> types)
    {
        this(initialExpectedEntries, DEFAULT_MAX_PAGE_SIZE_IN_BYTES, types, Optional.empty());
    }

    public static ArrowPageBuilder withMaxPageSize(int maxPageBytes, List<? extends Type> types)
    {
        return new ArrowPageBuilder(DEFAULT_INITIAL_EXPECTED_ENTRIES, maxPageBytes, types, Optional.empty());
    }*/

    //private ArrowPageBuilder(int initialExpectedEntries, int maxPageBytes, List<? extends Type> types, Optional<BlockBuilder[]> templateBlockBuilders)
    public ArrowPageBuilder(BufferAllocator allocator, List<ColumnMetadata> columns)
    {
        requireNonNull(allocator, "allocator is null");
        requireNonNull(columns, "columns is null");
        /*this.types = unmodifiableList(new ArrayList<>(requireNonNull(types, "types is null")));

        pageBuilderStatus = new PageBuilderStatus(maxPageBytes);
        blockBuilders = new BlockBuilder[types.size()];

        if (templateBlockBuilders.isPresent()) {
            BlockBuilder[] templates = templateBlockBuilders.get();
            checkArgument(templates.length == types.size(), "Size of templates and types should match");
            for (int i = 0; i < blockBuilders.length; i++) {
                blockBuilders[i] = templates[i].newBlockBuilderLike(pageBuilderStatus.createBlockBuilderStatus());
            }
        }
        else {
            for (int i = 0; i < blockBuilders.length; i++) {
                blockBuilders[i] = types.get(i).createBlockBuilder(pageBuilderStatus.createBlockBuilderStatus(), initialExpectedEntries);
            }
        }*/
        this.blockBuilders = null;
    }

    public void reset()
    {
        if (isEmpty()) {
            return;
        }
        pageBuilderStatus = new PageBuilderStatus(pageBuilderStatus.getMaxPageSizeInBytes());

        declaredPositions = 0;

        //for (int i = 0; i < blockBuilders.length; i++) {
        //    blockBuilders[i] = blockBuilders[i].newBlockBuilderLike(pageBuilderStatus.createBlockBuilderStatus());
        //}
    }

    private void createArrowVectorBuilders(BufferAllocator allocator, List<ColumnMetadata> columns)
    {
        List<Field> fields = columns.stream().map(BlockArrowWriter::prestoToArrowField).collect(Collectors.toList());
        Schema schema = new Schema(fields);
        VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator);

        final List<FieldVector> vectors = root.getFieldVectors();
        checkArgument(vectors.size() == columns.size(), "Unexpected list of vectors: %s", schema);
        for (int i = 0; i < vectors.size(); i++) {
            ColumnMetadata columnMetadata = columns.get(i);
            blockBuilders[i] = createArrowVectorBuilder(vectors.get(i), columnMetadata.getType());
        }
    }
    
    private ArrowVectorBuilder createArrowVectorBuilder(FieldVector vector, Type type)
    {
        //Class<?> javaType = type.getJavaType();
        
        switch (vector.getMinorType()) {
            case BIT:
                //checkArgument(javaType == boolean.class, "Unexpected type for BitVector: %s", type);
                return new ArrowBitVectorBuilder((BitVector) vector);
            case TINYINT:
                //checkArgument(javaType == long.class, "Unexpected type for TinyIntVector: %s", type);
                return new ArrowTinyIntVectorBuilder((TinyIntVector) vector);
            case SMALLINT:
                //checkArgument(javaType == long.class, "Unexpected type for SmallIntVector: %s", type);
                return new ArrowSmallIntVectorBuilder((SmallIntVector) vector);
            case INT:
                //checkArgument(javaType == long.class, "Unexpected type for IntVector: %s", type);
                return new ArrowIntVectorBuilder((IntVector) vector);
            case BIGINT:
                //checkArgument(javaType == long.class, "Unexpected type for BigIntVector: %s", type);
                return new ArrowBigIntVectorBuilder((BigIntVector) vector);
            /*case FLOAT4:
                checkArgument(javaType == long.class, "Unexpected type for Float4Vector: %s", type);
                return new ArrowRealWriter((Float4Vector) vector, new BlockPrimitiveGetter(type));
            case FLOAT8:
                checkArgument(javaType == double.class, "Unexpected type for Float8Vector: %s", type);
                return new ArrowDoubleWriter((Float8Vector) vector, new BlockPrimitiveGetter(type));
            case DECIMAL:
                checkArgument(type instanceof DecimalType, "Expected DecimalType but got %s", type);
                return new ArrowDecimalWriter((DecimalVector) vector, createDecimalBlockGetter((DecimalType) type, javaType));
            case VARBINARY:
            case VARCHAR:
                checkArgument(javaType == Slice.class, "Unexpected type for BaseVariableWidthVector: %s", type);
                return new ArrowVariableWidthWriter((BaseVariableWidthVector) vector, new BlockSliceGetter(type));
            case DATEDAY:
                checkArgument(javaType == long.class, "Unexpected type for DateDayVector: %s", type);
                return new ArrowDateWriter((DateDayVector) vector, new BlockPrimitiveGetter(type));
            case TIMEMILLI:
                checkArgument(javaType == long.class, "Unexpected type for TimeMilliVector: %s", type);
                if (type instanceof TimeWithTimeZoneType) {
                    return new ArrowTimeWithTimeZoneWriter((TimeMilliVector) vector, new BlockPrimitiveGetter(type));
                }
                return new ArrowTimeWriter((TimeMilliVector) vector, new BlockPrimitiveGetter(type));
            case TIMESTAMPMILLI:
                checkArgument(javaType == long.class, "Unexpected type for TimeStampVector: %s", type);
                if (type instanceof TimestampWithTimeZoneType) {
                    return new ArrowTimeStampWithTimeZoneWriter((TimeStampVector) vector, new BlockPrimitiveGetter(type));
                }
                return new ArrowTimeStampWriter((TimeStampVector) vector, new BlockPrimitiveGetter(type));
            case LIST:
                checkArgument(type instanceof ArrayType, "Unexpected type for ListVector: %s", type);
                return new ArrowListWriter((ListVector) vector, new BlockArrayGetter((ArrayType) type));
            case MAP:
                checkArgument(type instanceof MapType, "Unexpected type for MapVector: %s", type);
                return new ArrowMapWriter((MapVector) vector, new BlockMapGetter((MapType) type));
            case STRUCT:
                checkArgument(type instanceof RowType, "Unexpected type for StructVector: %s", type);
                return new ArrowStructWriter((StructVector) vector, new BlockRowGetter((RowType) type));*/
            default:
                throw new ArrowException(ARROW_FLIGHT_TYPE_ERROR, "Unsupported Arrow type: " + vector.getMinorType().name());
        } 
    }

    public ArrowPageBuilder newPageBuilderLike()
    {
        throw new UnsupportedOperationException();
        //return new ArrowPageBuilder(declaredPositions, pageBuilderStatus.getMaxPageSizeInBytes(), types, Optional.of(blockBuilders));
    }

    public BlockBuilder getBlockBuilder(int channel)
    {
        return blockBuilders[channel];
    }

    public Type getType(int channel)
    {
        return null;//types.get(channel);
    }

    public void declarePosition()
    {
        declaredPositions++;
    }

    public void declarePositions(int positions)
    {
        declaredPositions += positions;
    }

    public boolean isFull()
    {
        return declaredPositions == Integer.MAX_VALUE || pageBuilderStatus.isFull();
    }

    public boolean isEmpty()
    {
        return declaredPositions == 0;
    }

    public int getPositionCount()
    {
        return declaredPositions;
    }

    public long getSizeInBytes()
    {
        return pageBuilderStatus.getSizeInBytes();
    }

    public long getRetainedSizeInBytes()
    {
        // We use a foreach loop instead of streams
        // as it has much better performance.
        long retainedSizeInBytes = 0;
        for (BlockBuilder blockBuilder : blockBuilders) {
            retainedSizeInBytes += blockBuilder.getRetainedSizeInBytes();
        }
        return retainedSizeInBytes;
    }

    public Page build()
    {
        if (blockBuilders.length == 0) {
            return new Page(declaredPositions);
        }

        Block[] blocks = new Block[blockBuilders.length];
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = blockBuilders[i].build();
            if (blocks[i].getPositionCount() != declaredPositions) {
                throw new IllegalStateException(String.format("Declared positions (%s) does not match block %s's number of entries (%s)", declaredPositions, i, blocks[i].getPositionCount()));
            }
        }

        return Page.wrapBlocksWithoutCopy(declaredPositions, blocks);
    }
}
