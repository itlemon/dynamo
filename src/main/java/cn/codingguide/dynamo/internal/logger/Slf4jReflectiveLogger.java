package cn.codingguide.dynamo.internal.logger;

import java.lang.reflect.Method;

/**
 * Logger implementation that uses slf4j via reflection (no compile-time dependency).
 *
 * @author itlemon
 * @since 1.0.0
 */
final class Slf4jReflectiveLogger implements Logger {

    private final Object slf4jLogger;
    private final Method warnMsg;
    private final Method warnMsgThrowable;
    private final Method infoMsg;

    Slf4jReflectiveLogger(String name) throws Exception {
        ClassLoader cl = Loggers.class.getClassLoader();
        Class<?> factoryClass = Class.forName("org.slf4j.LoggerFactory", true, cl);
        Class<?> loggerClass = Class.forName("org.slf4j.Logger", true, cl);

        Method getLogger = factoryClass.getMethod("getLogger", String.class);
        this.slf4jLogger = getLogger.invoke(null, name);

        this.warnMsg = loggerClass.getMethod("warn", String.class);
        this.warnMsgThrowable = loggerClass.getMethod("warn", String.class, Throwable.class);
        this.infoMsg = loggerClass.getMethod("info", String.class);
    }

    @Override
    public void warn(String m) {
        invoke(warnMsg, m);
    }

    @Override
    public void warn(String m, Throwable t) {
        invoke(warnMsgThrowable, m, t);
    }

    @Override
    public void info(String m) {
        invoke(infoMsg, m);
    }

    private void invoke(Method m, Object... args) {
        try {
            m.invoke(slf4jLogger, args);
        } catch (Throwable ignore) {
            // Reflection invocation failure should not break the application
        }
    }
}
