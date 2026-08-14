package org.congcong.algomentor.mentor.application.practice;

/** Practice Chat 启动命令成功受理后的控制面订阅信息。 */
public record PracticeChatRunSubscription(
    long taskId,
    String runUuid,
    String status
) {

  /** HTTP 控制面受理标记，不映射为持久化 run 状态。 */
  public static final String ACCEPTED = "ACCEPTED";

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
  }
}
