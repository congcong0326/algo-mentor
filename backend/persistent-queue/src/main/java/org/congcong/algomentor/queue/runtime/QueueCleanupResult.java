package org.congcong.algomentor.queue.runtime;

import java.time.Duration;

/** 一次队列清理的结果。 */
public record QueueCleanupResult(int deletedCount, Duration duration) {
}
