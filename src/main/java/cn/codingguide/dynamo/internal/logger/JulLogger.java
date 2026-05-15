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
