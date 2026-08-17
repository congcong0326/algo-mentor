package org.congcong.algomentor.mentor.application.practice;

/** Practice Chat 启动命令成功受理后的控制面订阅信息。 */
public record PracticeChatRunSubscription(
    long taskId,
    String runUuid,
    String status,
    int realtimeProtocolVersion
) {

  /** HTTP 控制面受理标记，不映射为持久化 run 状态。 */
  public static final String ACCEPTED = "ACCEPTED";
  /** 新建 run 的连续公开 realtime 协议。 */
  public static final int REALTIME_PROTOCOL_VERSION = 2;
  /** 历史或 idempotency replay run 使用 PostgreSQL 回读收束。 */
  public static final int LEGACY_REALTIME_PROTOCOL_VERSION = 1;

  public PracticeChatRunSubscription(long taskId, String runUuid, String status) {
    this(taskId, runUuid, status, REALTIME_PROTOCOL_VERSION);
  }

  public PracticeChatRunSubscription {
    if (taskId < 1) {
      throw new IllegalArgumentException("Practice chat task id must be positive");
    }
    if (runUuid == null || runUuid.isBlank()) {
      throw new IllegalArgumentException("Practice chat run uuid must not be blank");
    }
    if (!ACCEPTED.equals(status)) {
      throw new IllegalArgumentException("Practice chat subscription status must be ACCEPTED");
    }
    if (realtimeProtocolVersion != LEGACY_REALTIME_PROTOCOL_VERSION
        && realtimeProtocolVersion != REALTIME_PROTOCOL_VERSION) {
      throw new IllegalArgumentException("Practice chat realtime protocol version is unsupported");
    }
  }
}
