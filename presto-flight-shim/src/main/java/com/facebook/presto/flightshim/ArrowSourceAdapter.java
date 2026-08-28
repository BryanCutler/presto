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
package com.facebook.presto.flightshim;

import com.facebook.plugin.arrow.ArrowBatchSource;
import com.facebook.plugin.arrow.ConnectorArrowSourceAdapter;
import com.facebook.presto.spi.connector.ConnectorArrowSource;
import org.apache.arrow.vector.VectorSchemaRoot;

import java.io.IOException;

public abstract class ArrowSourceAdapter
        implements AutoCloseable
{
    static ArrowSourceAdapter create(ConnectorArrowSource connectorArrowSource)
    {
        return new ArrowSourceWrapper(connectorArrowSource);
    }

    static ArrowSourceAdapter create(ArrowBatchSource arrowBatchSource)
    {
        return new ArrowBatchWrapper(arrowBatchSource);
    }

    public abstract VectorSchemaRoot getVectorSchemaRoot();

    public abstract boolean nextArrowBatch();

    static class ArrowSourceWrapper
            extends ArrowSourceAdapter
    {
        private final ConnectorArrowSource connectorArrowSource;

        public ArrowSourceWrapper(ConnectorArrowSource connectorArrowSource)
        {
            this.connectorArrowSource = connectorArrowSource;
        }

        @Override
        public VectorSchemaRoot getVectorSchemaRoot()
        {
            if (!(connectorArrowSource.getVectorSchemaRootHolder() instanceof ConnectorArrowSourceAdapter.VectorSchemaRootHolderImpl)) {
                throw new IllegalArgumentException("Expected ConnectorArrowSourceAdapter.VectorSchemaRootHolderImpl");
            }

            return ((ConnectorArrowSourceAdapter.VectorSchemaRootHolderImpl) connectorArrowSource.getVectorSchemaRootHolder()).getVectorSchemaRoot();
        }

        @Override
        public boolean nextArrowBatch()
        {
            return connectorArrowSource.nextArrowBatch();
        }

        @Override
        public void close()
                throws IOException
        {
            connectorArrowSource.close();
        }
    }

    static class ArrowBatchWrapper
            extends ArrowSourceAdapter
    {
        private final ArrowBatchSource arrowBatchSource;

        public ArrowBatchWrapper(ArrowBatchSource arrowBatchSource)
        {
            this.arrowBatchSource = arrowBatchSource;
        }

        @Override
        public VectorSchemaRoot getVectorSchemaRoot()
        {
            return arrowBatchSource.getVectorSchemaRoot();
        }

        @Override
        public boolean nextArrowBatch()
        {
            return arrowBatchSource.nextBatch();
        }

        @Override
        public void close()
                throws IOException
        {
            arrowBatchSource.close();
        }
    }
}
