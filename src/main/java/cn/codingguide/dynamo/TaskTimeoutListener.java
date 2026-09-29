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
 * Listener interface for task-level timeout detection.
 * <p>
 * Register an implementation via
 * {@link DynamicThreadPoolExecutor.Builder#addTaskTimeoutListener(TaskTimeoutListener)} together
 * with {@link DynamicThreadPoolExecutor.Builder#runTimeout(long)} and/or
 * {@link DynamicThreadPoolExecutor.Builder#queueTimeout(long)} to be notified when an individual
 * task waits in the queue for too long or runs for too long.
 * <p>
 * <b>Important:</b> {@link #onTimeout(TaskTimeoutEvent)} is invoked synchronously on a pool worker
 * thread (for run timeouts) or on the thread that is about to execute the task (for queue
 * timeouts). Keep implementations fast and non-blocking; offload heavy work (such as sending
 * alerts) to another thread.
 *
 * @author itlemon
 * @since 1.0.0
 */
@FunctionalInterface
public interface TaskTimeoutListener {

    /**
     * Called when a task exceeds a configured timeout threshold.
     *
     * @param event the timeout event containing pool name, timeout type, task name,
     *              elapsed time, threshold, and timestamp
     */
    void onTimeout(TaskTimeoutEvent event);
}
