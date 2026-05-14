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
