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

/**
 * A coarse load level derived from a thread pool's utilization, intended to guide alerting.
 * <p>
 * Dynamo deliberately only reports the level; it does not send alerts itself. This keeps the
 * "library decides, user reacts" contract: inspect {@link ThreadPoolMetrics#loadLevel()} and
 * decide whether to log, alert, or scale.
 *
 * @author itlemon
 * @since 1.0.0
 */
public enum LoadLevel {

    /**
     * The pool is operating comfortably below its thresholds.
     */
    NORMAL,

    /**
     * The pool is under noticeable pressure and worth watching.
     */
    WARN,

    /**
     * The pool is close to saturation; rejections are likely imminent or already happening.
     */
    CRITICAL
}
