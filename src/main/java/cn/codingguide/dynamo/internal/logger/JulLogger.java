package cn.codingguide.dynamo.internal.logger;

/**
 * Logger implementation using java.util.logging (JDK built-in, fallback).
 *
 * @author itlemon
 * @since 1.0.0
 */
final class JulLogger implements Logger {

    private final java.util.logging.Logger delegate;

    JulLogger(String name) {
        this.delegate = java.util.logging.Logger.getLogger(name);
    }

    @Override
    public void warn(String m) {
        delegate.warning(m);
    }

    @Override
    public void warn(String m, Throwable t) {
        delegate.log(java.util.logging.Level.WARNING, m, t);
    }

    @Override
    public void info(String m) {
        delegate.info(m);
    }
}
