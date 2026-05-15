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

package cn.codingguide.dynamo.internal.logger;

/**
 * Logger factory that auto-detects logging backend on classpath.
 * <p>
 * Detection order:
 * <ol>
 *   <li>slf4j (if org.slf4j.LoggerFactory exists)</li>
 *   <li>log4j2 (if org.apache.logging.log4j.LogManager exists)</li>
 *   <li>JUL (java.util.logging, fallback)</li>
 * </ol>
 *
 * @author itlemon
 * @since 1.0.0
 */
public final class Loggers {

    private Loggers() {
    }

    /**
     * Supported logging backends, in priority order.
     */
    private enum Backend {
        SLF4J("org.slf4j.LoggerFactory") {
            @Override
            Logger create(String name) throws Exception {
                return new Slf4jReflectiveLogger(name);
            }
        },
        LOG4J2("org.apache.logging.log4j.LogManager") {
            @Override
            Logger create(String name) throws Exception {
                return new Log4j2ReflectiveLogger(name);
            }
        };

        final String probeClass;

        Backend(String probeClass) {
            this.probeClass = probeClass;
        }

        abstract Logger create(String name) throws Exception;
    }

    /**
     * Detected backend at class-loading time.
     * Null means fallback to JUL.
     */
    private static final Backend DETECTED = detect();

    private static Backend detect() {
        ClassLoader cl = Loggers.class.getClassLoader();
        for (Backend b : Backend.values()) {
            try {
                Class.forName(b.probeClass, false, cl);
                return b;
            } catch (Throwable ignored) {
                // Continue to next backend
            }
        }
        return null;
    }

    /**
     * Get a logger instance for the given name.
     *
     * @param name logger name
     * @return logger instance
     */
    public static Logger get(String name) {
        if (DETECTED != null) {
            try {
                return DETECTED.create(name);
            } catch (Throwable t) {
                // Detection succeeded but instantiation failed (rare), fallback to JUL
            }
        }
        return new JulLogger(name);
    }

    /**
     * Get the name of the detected backend (for debugging).
     *
     * @return backend name ("SLF4J", "LOG4J2", or "JUL")
     */
    static String detectedBackend() {
        return DETECTED == null ? "JUL" : DETECTED.name();
    }
}
