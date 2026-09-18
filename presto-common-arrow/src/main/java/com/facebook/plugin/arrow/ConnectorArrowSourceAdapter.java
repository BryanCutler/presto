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

import com.facebook.presto.spi.connector.ConnectorArrowSource;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.VectorSchemaRoot;

import static java.util.Objects.requireNonNull;

public abstract class ConnectorArrowSourceAdapter
        extends ConnectorArrowSource
{
    protected final BufferAllocator allocator;

    public ConnectorArrowSourceAdapter(BufferAllocatorHolder bufferAllocatorHolder)
    {
        requireNonNull(bufferAllocatorHolder, "bufferAllocatorHolder is null");
        if (!(bufferAllocatorHolder instanceof ConnectorArrowSourceAdapter.BufferAllocatorHolderImpl)) {
            throw new IllegalArgumentException("Expected ConnectorArrowSourceAdapter.BufferAllocatorHolderImpl");
        }
        this.allocator = ((ConnectorArrowSourceAdapter.BufferAllocatorHolderImpl) bufferAllocatorHolder).getBufferAllocator();
    }

    public abstract VectorSchemaRoot getVectorSchemaRoot();

    @Override
    public VectorSchemaRootHolder getVectorSchemaRootHolder()
    {
        return new ConnectorArrowSourceAdapter.VectorSchemaRootHolderImpl(getVectorSchemaRoot());
    }

    public static class BufferAllocatorHolderImpl
            implements ConnectorArrowSource.BufferAllocatorHolder
    {
        protected final BufferAllocator allocator;

        public BufferAllocatorHolderImpl(BufferAllocator allocator)
        {
            this.allocator = allocator;
        }

        public BufferAllocator getBufferAllocator()
        {
            return allocator;
        }
    }

    public static class VectorSchemaRootHolderImpl
            implements ConnectorArrowSource.VectorSchemaRootHolder
    {
        private final VectorSchemaRoot root;

        public VectorSchemaRootHolderImpl(VectorSchemaRoot root)
        {
            this.root = root;
        }

        public VectorSchemaRoot getVectorSchemaRoot()
        {
            return root;
        }
    }
}
