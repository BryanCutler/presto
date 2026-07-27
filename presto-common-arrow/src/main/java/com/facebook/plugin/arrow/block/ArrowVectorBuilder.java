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

import com.facebook.presto.common.block.Block;
import com.facebook.presto.common.block.BlockBuilder;
import com.facebook.presto.common.block.BlockBuilderStatus;
import io.airlift.slice.Slice;
import io.airlift.slice.SliceInput;
import io.airlift.slice.SliceOutput;

import java.util.OptionalInt;
import java.util.function.ObjLongConsumer;

public abstract class ArrowVectorBuilder
        implements BlockBuilder
{
    protected int positionCount;

    @Override
    public BlockBuilder writeBytes(Slice source, int sourceIndex, int length)
    {
        return BlockBuilder.super.writeBytes(source, sourceIndex, length);
    }

    @Override
    public BlockBuilder beginBlockEntry()
    {
        return BlockBuilder.super.beginBlockEntry();
    }

    @Override
    public BlockBuilder closeEntry()
    {
        return null;
    }

    @Override
    public void writePositionTo(int position, BlockBuilder blockBuilder)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writePositionTo(int position, SliceOutput output)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Block getSingleValueBlock(int position)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getPositionCount()
    {
        return positionCount;
    }

    @Override
    public long getSizeInBytes()
    {
        return 0;
    }

    @Override
    public long getRegionSizeInBytes(int position, int length)
    {
        return 0;
    }

    @Override
    public OptionalInt fixedSizeInBytesPerPosition()
    {
        return OptionalInt.empty();
    }

    @Override
    public long getPositionsSizeInBytes(boolean[] positions, int usedPositionCount)
    {
        return 0;
    }

    @Override
    public long getRetainedSizeInBytes()
    {
        return 0;
    }

    @Override
    public long getEstimatedDataSizeForStats(int position)
    {
        return 0;
    }

    @Override
    public void retainedBytesForEachPart(ObjLongConsumer<Object> consumer)
    {

        throw new UnsupportedOperationException();
    }

    @Override
    public String getEncodingName()
    {
        return "arrow";
    }

    @Override
    public Block copyPositions(int[] positions, int offset, int length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Block getRegion(int positionOffset, int length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Block copyRegion(int position, int length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isNull(int position)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public BlockBuilder readPositionFrom(SliceInput input)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Block build()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public BlockBuilder newBlockBuilderLike(BlockBuilderStatus blockBuilderStatus)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public BlockBuilder newBlockBuilderLike(BlockBuilderStatus blockBuilderStatus, int expectedEntries)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isNullUnchecked(int internalPosition)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getOffsetBase()
    {
        throw new UnsupportedOperationException();
    }
}
