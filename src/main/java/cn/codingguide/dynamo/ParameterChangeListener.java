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
