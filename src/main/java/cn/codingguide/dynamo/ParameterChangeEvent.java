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

import java.util.Date;

/**
 * Event object representing a thread pool parameter change.
 *
 * @author itlemon
 * @since 1.0.0
 */
public final class ParameterChangeEvent {

    private final String poolName;
    private final ParameterType type;
    private final Object oldValue;
    private final Object newValue;
    private final long timestamp;

    public ParameterChangeEvent(String poolName, ParameterType type,
                                Object oldValue, Object newValue, long timestamp) {
        this.poolName = poolName;
        this.type = type;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.timestamp = timestamp;
    }

    public String getPoolName() {
        return poolName;
    }

    public ParameterType getType() {
        return type;
    }

    public Object getOldValue() {
        return oldValue;
    }

    public Object getNewValue() {
        return newValue;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("ParameterChangeEvent{pool='%s', type=%s, %s -> %s, at=%s}",
                poolName, type, oldValue, newValue, new Date(timestamp));
    }
}
