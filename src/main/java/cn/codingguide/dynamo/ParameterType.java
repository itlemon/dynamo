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
