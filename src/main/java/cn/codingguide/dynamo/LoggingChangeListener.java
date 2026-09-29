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

import cn.codingguide.dynamo.internal.logger.Logger;
import cn.codingguide.dynamo.internal.logger.Loggers;

/**
 * A built-in {@link ParameterChangeListener} that logs every parameter change as a
 * structured audit line.
 * <p>
 * This provides out-of-the-box change auditing without requiring users to write their own
 * listener. Register it via
 * {@link DynamicThreadPoolExecutor.Builder#addChangeListener(ParameterChangeListener)}, or
 * enable it directly with {@link DynamicThreadPoolExecutor.Builder#logChanges()}.
 * <p>
 * Example log output:
 * <pre>{@code
 * [DTP-CHANGE] pool=dynamic-OrderService type=CORE_POOL_SIZE 4 -> 8
 * }</pre>
 *
 * @author itlemon
 * @since 1.0.0
 */
public final class LoggingChangeListener implements ParameterChangeListener {

    private final Logger log;

    /**
     * Create a listener using the default Dynamo logger.
     */
    public LoggingChangeListener() {
        this(Loggers.get("dtp"));
    }

    /**
     * Create a listener using the given logger.
     *
     * @param log the logger to write audit lines to
     */
    public LoggingChangeListener(Logger log) {
        this.log = log;
    }

    @Override
    public void onChange(ParameterChangeEvent event) {
        log.info("[DTP-CHANGE] pool=" + event.getPoolName()
                + " type=" + event.getType()
                + " " + event.getOldValue() + " -> " + event.getNewValue());
    }
}
