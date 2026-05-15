/*
 * Copyright 2024 itlemon
 *
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

package cn.codingguide.dynamo;

/**
 * Listener interface for thread pool parameter changes.
 * <p>
 * Implementations of this interface can be registered via {@link DynamicThreadPoolExecutor.Builder#addChangeListener}
 * to receive notifications when thread pool parameters are adjusted at runtime.
 * </p>
 * <p>
 * <b>Important:</b> Listener callbacks are executed synchronously in the refresh thread.
 * Implementations should be fast and non-blocking. If you need to perform time-consuming operations,
 * consider offloading them to a separate thread pool.
 * </p>
 *
 * @author itlemon
 * @since 1.0.0
 */
@FunctionalInterface
public interface ParameterChangeListener {

    /**
     * Called when a thread pool parameter changes.
     *
     * @param event the parameter change event containing pool name, parameter type,
     *              old value, new value, and timestamp
     */
    void onChange(ParameterChangeEvent event);
}
