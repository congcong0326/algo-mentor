package org.congcong.algomentor.api.practice.model;

/** Practice Chat 启动命令的控制面响应。 */
public record PracticeChatRunSubscriptionResponse(
    String type,
    long taskId,
    String runUuid,
    String status,
    String eventsUrl,
    String initialAfter,
    int realtimeProtocolVersion
) {

  public static final String TYPE_ACCEPTED = "accepted";
}
