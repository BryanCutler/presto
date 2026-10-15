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

/**
 * Writes values read by a {@link WriterValueGetter} into one Arrow vector, converting each value
 * from its Presto encoding to Arrow. Create writers with {@link ArrowWriterFactory#createArrowWriter}.
 * Fixed-width vectors must be allocated for the batch before writing; variable-width and nested
 * vectors grow as needed.
 *
 * @param <T> the value source, for example a {@link com.facebook.presto.common.block.Block} or a JDBC result set
 */
public abstract class ArrowVectorWriter<T>
{
    public abstract void write(int index, T source, int position);

    //protected abstract void writeNull(int index);

    //protected abstract void writeValue(int index, T source, int position);
}
