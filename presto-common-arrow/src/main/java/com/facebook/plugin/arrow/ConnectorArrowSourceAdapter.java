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

    static public class BufferAllocatorHolderImpl
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

    static public class VectorSchemaRootHolderImpl
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
