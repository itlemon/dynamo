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

/**
 * Dynamo - The Dynamic Thread Pool for Java.
 * <p>
 * This package provides a {@link cn.codingguide.dynamo.DynamicThreadPoolExecutor} that extends
 * JDK's {@link java.util.concurrent.ThreadPoolExecutor} with runtime-adjustable parameters.
 * <p>
 * <b>Key features:</b>
 * <ul>
 *   <li>Zero dependencies (JDK 8+ only)</li>
 *   <li>Supplier-based dynamic values (decouple from config sources)</li>
 *   <li>Auto-detect logging backend (slf4j / log4j2 / JUL)</li>
 *   <li>Metrics exposure and parameter change events</li>
 * </ul>
 * <p>
 * <b>Quick start:</b>
 * <pre>{@code
 * DynamicThreadPoolExecutor pool = DynamicThreadPoolExecutor.dynamic(
 *     () -> configCenter.getInt("pool.core", 4),
 *     () -> configCenter.getInt("pool.max", 32)
 * );
 * pool.execute(() -> doWork());
 * }</pre>
 *
 * @author itlemon
 * @since 1.0.0
 */
package cn.codingguide.dynamo;
