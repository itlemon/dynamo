package cn.codingguide.dynamo.internal.logger;

/**
 * Minimal logging abstraction for internal use.
 * <p>
 * This interface is intentionally package-private (internal) and should not be used by external code.
 * Dynamo automatically detects slf4j / log4j2 / JUL on the classpath and routes logs accordingly.
 * </p>
 *
 * @author itlemon
 * @since 1.0.0
 */
public interface Logger {

    /**
     * Log a warning message.
     *
     * @param message the message
     */
    void warn(String message);

    /**
     * Log a warning message with an exception.
     *
     * @param message the message
     * @param t       the throwable
     */
    void warn(String message, Throwable t);

    /**
     * Log an info message.
     *
     * @param message the message
     */
    void info(String message);
}
