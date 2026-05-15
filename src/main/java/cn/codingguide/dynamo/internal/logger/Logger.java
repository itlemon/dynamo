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
