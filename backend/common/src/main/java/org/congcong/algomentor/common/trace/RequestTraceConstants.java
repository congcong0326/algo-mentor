package org.congcong.algomentor.common.trace;

/**
 * HTTP 请求追踪上下文的稳定契约字段。
 */
public final class RequestTraceConstants {

  /**
   * 前端和其他 HTTP 客户端传递单次请求串号的请求头名。
   */
  public static final String REQUEST_ID_HEADER = "X-Request-Id";

  /**
   * Logback MDC 中保存请求串号的字段名。
   */
  public static final String REQUEST_ID_MDC_KEY = "requestId";

  private RequestTraceConstants() {
  }
}
