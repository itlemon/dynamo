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
 * Thread pool parameter types that can be dynamically adjusted.
 *
 * @author itlemon
 * @since 1.0.0
 */
public enum ParameterType {
    /**
     * Core pool size parameter
     */
    CORE_POOL_SIZE,

    /**
     * Maximum pool size parameter
     */
    MAXIMUM_POOL_SIZE,

    /**
     * Keep alive time in seconds
     */
    KEEP_ALIVE_SECONDS,

    /**
     * Queue capacity parameter
     */
    QUEUE_CAPACITY
}
