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

import org.apache.arrow.vector.VectorSchemaRoot;

public interface ArrowBatchIterator extends AutoCloseable
{
    /**
     * Returns the VectorSchemaRoot for the Arrow batches.
     */
    VectorSchemaRoot getVectorSchemaRoot();

    /**
     * Loads the next record batch from the source.
     * Returns false if there are no more batches from the source.
     */
    boolean nextBatch();
}
