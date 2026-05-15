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
 * Snapshot of thread pool runtime metrics and parameter values.
 * <p>
 * This immutable class captures both operational metrics (active threads, queue size, etc.)
 * and current parameter settings (core size, max size, etc.) at a specific point in time.
 * <p>
 * Usage example:
 * <pre>{@code
 * DynamicThreadPoolExecutor pool = ...;
 * ThreadPoolMetrics metrics = pool.getMetrics();
 * logger.info("Pool utilization: {:.2f}%", metrics.poolUtilization() * 100);
 * }</pre>
 *
 * @author itlemon
 * @since 1.0.0
 */
public final class ThreadPoolMetrics {

    private final String poolName;
    private final long timestamp;

    // Runtime metrics
    private final int activeCount;
    private final long taskCount;
    private final long completedTaskCount;
    private final int queueSize;
    private final int queueRemainingCapacity;
    private final long rejectedCount;

    // Runtime parameter snapshot
    private final int corePoolSize;
    private final int maximumPoolSize;
    private final long keepAliveSeconds;
    private final int queueCapacity;

    public ThreadPoolMetrics(String poolName, long timestamp,
                             int activeCount, long taskCount, long completedTaskCount,
                             int queueSize, int queueRemainingCapacity, long rejectedCount,
                             int corePoolSize, int maximumPoolSize,
                             long keepAliveSeconds, int queueCapacity) {
        this.poolName = poolName;
        this.timestamp = timestamp;
        this.activeCount = activeCount;
        this.taskCount = taskCount;
        this.completedTaskCount = completedTaskCount;
        this.queueSize = queueSize;
        this.queueRemainingCapacity = queueRemainingCapacity;
        this.rejectedCount = rejectedCount;
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.keepAliveSeconds = keepAliveSeconds;
        this.queueCapacity = queueCapacity;
    }

    /**
     * Queue utilization ratio (0.0 to 1.0).
     * Returns 0.0 if queue capacity is unlimited or custom queue without known capacity.
     *
     * @return queue utilization ratio
     */
    public double queueUtilization() {
        if (queueCapacity <= 0) {
            return 0.0;
        }
        return (double) queueSize / queueCapacity;
    }

    /**
     * Thread pool utilization ratio (0.0 to 1.0).
     * Calculated as active threads / maximum pool size.
     *
     * @return pool utilization ratio
     */
    public double poolUtilization() {
        if (maximumPoolSize <= 0) {
            return 0.0;
        }
        return (double) activeCount / maximumPoolSize;
    }

    public String getPoolName() {
        return poolName;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getActiveCount() {
        return activeCount;
    }

    public long getTaskCount() {
        return taskCount;
    }

    public long getCompletedTaskCount() {
        return completedTaskCount;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public int getQueueRemainingCapacity() {
        return queueRemainingCapacity;
    }

    public long getRejectedCount() {
        return rejectedCount;
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public long getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    @Override
    public String toString() {
        return String.format(
                "ThreadPoolMetrics{pool='%s', active=%d/%d, queue=%d/%d(%.2f%%), rejected=%d, completed=%d}",
                poolName, activeCount, maximumPoolSize, queueSize, queueCapacity,
                queueUtilization() * 100, rejectedCount, completedTaskCount);
    }
}
