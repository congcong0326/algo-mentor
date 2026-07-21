package org.congcong.algomentor.queue.dispatch;

/** 当前 topic 下已满足本轮派发阈值的 key。 */
public record QueueEligibleKey(String key, long oldestMessageId) {
}
