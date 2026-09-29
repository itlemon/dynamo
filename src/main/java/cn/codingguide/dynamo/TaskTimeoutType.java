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
 * The kind of task timeout that was detected.
 *
 * @author itlemon
 * @since 1.0.0
 */
public enum TaskTimeoutType {

    /**
     * The task waited in the queue longer than the configured queue-timeout threshold
     * before it started executing.
     */
    QUEUE_TIMEOUT,

    /**
     * The task ran longer than the configured run-timeout threshold.
     */
    RUN_TIMEOUT
}
