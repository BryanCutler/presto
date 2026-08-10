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

import com.facebook.presto.common.Page;
import com.facebook.presto.spi.connector.ConnectorArrowSourceBase;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.VectorSchemaRoot;

import java.io.IOException;

public abstract class ConnectorArrowSource
        extends ConnectorArrowSourceBase
{
    public static final String CONNECTOR_ARROW_SOURCE_ENABLED = "connector-arrow-source-enabled";

    public abstract VectorSchemaRoot getVectorSchemaRoot();

    @Override
    public long getCompletedBytes()
    {
        return 0;
    }

    @Override
    public long getCompletedPositions()
    {
        return 0;
    }

    @Override
    public long getReadTimeNanos()
    {
        return 0;
    }

    @Override
    public boolean isFinished()
    {
        return false;
    }

    @Override
    public Page getNextPage()
    {
        return null;
    }

    @Override
    public long getSystemMemoryUsage()
    {
        return 0;
    }

    @Override
    public void close()
            throws IOException
    {

    }

    public static class BufferAllocatorHolder extends ConnectorArrowSourceBase.BufferAllocatorHolder
    {
        private final BufferAllocator allocator;

        public BufferAllocatorHolder(BufferAllocator allocator)
        {
            this.allocator = allocator;
        }

        public BufferAllocator getBufferAllocator()
        {
            return allocator;
        }
    }

    /*public static class VectorSchemaRootHolder extends ConnectorArrowSource.VectorSchemaRootHolder
    {

        public VectorSchemaRoot getVectorSchemaRoot()
        {

        }
    }*/
}
