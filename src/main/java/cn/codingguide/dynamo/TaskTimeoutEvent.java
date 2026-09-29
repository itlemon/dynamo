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

import java.util.Date;

/**
 * Event object representing a task that exceeded a timeout threshold, either by waiting too long
 * in the queue ({@link TaskTimeoutType#QUEUE_TIMEOUT}) or by running too long
 * ({@link TaskTimeoutType#RUN_TIMEOUT}).
 *
 * @author itlemon
 * @since 1.0.0
 */
public final class TaskTimeoutEvent {

    private final String poolName;
    private final TaskTimeoutType type;
    private final String taskName;
    private final long elapsedMillis;
    private final long thresholdMillis;
    private final long timestamp;

    public TaskTimeoutEvent(String poolName, TaskTimeoutType type, String taskName,
                            long elapsedMillis, long thresholdMillis, long timestamp) {
        this.poolName = poolName;
        this.type = type;
        this.taskName = taskName;
        this.elapsedMillis = elapsedMillis;
        this.thresholdMillis = thresholdMillis;
        this.timestamp = timestamp;
    }

    public String getPoolName() {
        return poolName;
    }

    public TaskTimeoutType getType() {
        return type;
    }

    /**
     * A best-effort name of the offending task, derived from its {@code toString()}.
     *
     * @return task name
     */
    public String getTaskName() {
        return taskName;
    }

    /**
     * The measured elapsed time in milliseconds (queue wait time or run time depending on
     * {@link #getType()}).
     *
     * @return elapsed milliseconds
     */
    public long getElapsedMillis() {
        return elapsedMillis;
    }

    /**
     * The configured threshold in milliseconds that was exceeded.
     *
     * @return threshold milliseconds
     */
    public long getThresholdMillis() {
        return thresholdMillis;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format(
                "TaskTimeoutEvent{pool='%s', type=%s, task='%s', elapsed=%dms, threshold=%dms, at=%s}",
                poolName, type, taskName, elapsedMillis, thresholdMillis, new Date(timestamp));
    }
}
