# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/), and this project adheres to
[Semantic Versioning](https://semver.org/).

## [1.1.0]

Backward-compatible feature release. All additions are opt-in and require no changes to existing code.

### Added

- **Task-level timeout detection.** Detect tasks that run too long (`RUN_TIMEOUT`) or wait in the
  queue too long (`QUEUE_TIMEOUT`) via the executor's own `beforeExecute`/`afterExecute` hooks —
  no extra thread or timer. Configure with `Builder.runTimeout(millis)` /
  `Builder.queueTimeout(millis)` and react with `TaskTimeoutListener` (`TaskTimeoutEvent`,
  `TaskTimeoutType`). Both checks are off by default and add zero overhead until enabled.
- **Historical peak metrics.** `ThreadPoolMetrics` now exposes `getLargestPoolSize()`,
  `getLargestQueueSize()`, and `getMaxTaskTimeMillis()` to support capacity reviews.
- **Load level.** `ThreadPoolMetrics.loadLevel()` returns a derived `LoadLevel`
  (`NORMAL` / `WARN` / `CRITICAL`) for alerting. Dynamo reports the level; the alerting action
  stays with the caller.
- **Built-in change auditing.** `Builder.logChanges()` (backed by `LoggingChangeListener`) logs
  every parameter change as a structured `[DTP-CHANGE]` line, out of the box.
- **Read-only registry.** `DynamoRegistry` enumerates all live pools via weak references
  (never leaks memory), with `pools()`, `collectMetrics()`, and `size()` for building a
  monitoring endpoint.
- **Near-real-time refresh (push-pull hybrid).** `refreshNow()` applies the current `Supplier`
  values immediately instead of waiting for the next poll — wire it into a config-center change
  callback for instant effect while polling remains a safety net. Thread-safe and serialized with
  the poller.
- **Slow-refresh guard.** A refresh cycle slower than the threshold (default 1000ms, configurable
  via `Builder.slowRefreshThreshold(millis)`) logs a `[DTP-SLOW-REFRESH]` warning, surfacing a
  blocking `Supplier` that would otherwise silently stall the shared refresher thread.

### Changed

- The scheduled refresh is now serialized with a lock so the poller and `refreshNow()` never apply
  changes concurrently.
- Documentation clarifies that the default 5s interval is the *polling period*, not the
  change-to-effect latency.

## [1.0.2]

- Initial published release: dynamic `corePoolSize` / `maximumPoolSize` / `keepAliveTime` /
  `queueCapacity` via `Supplier`, zero runtime dependencies, reflection-free resizable queue,
  metrics snapshot, parameter change listeners, and pluggable rejection policies.
