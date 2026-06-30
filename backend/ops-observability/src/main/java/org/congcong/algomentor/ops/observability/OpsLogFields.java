package org.congcong.algomentor.ops.observability;

public final class OpsLogFields {

  /** 日志事件类型字段。 */
  public static final String EVENT_TYPE = "eventType";
  /** 请求链路标识字段。 */
  public static final String REQUEST_ID = "requestId";
  /** HTTP 方法字段。 */
  public static final String METHOD = "method";
  /** 低基数路径模板字段。 */
  public static final String PATH_TEMPLATE = "pathTemplate";
  /** 生命周期或结果状态字段。 */
  public static final String STATUS = "status";
  /** 稳定错误码字段。 */
  public static final String ERROR_CODE = "errorCode";
  /** 异常类型字段。 */
  public static final String EXCEPTION_TYPE = "exceptionType";
  /** 耗时毫秒字段。 */
  public static final String DURATION_MS = "durationMs";
  /** SSE 流类型字段。 */
  public static final String SSE_STREAM_TYPE = "sseStreamType";
  /** Agent 运行标识字段。 */
  public static final String AGENT_RUN_ID = "agentRunId";
  /** Agent 来源字段。 */
  public static final String AGENT_SOURCE = "agentSource";
  /** 工具名称字段。 */
  public static final String TOOL_NAME = "toolName";
  /** 失败分类字段。 */
  public static final String FAILURE_TYPE = "failureType";
  /** Authorization 头字段，格式化时必须脱敏。 */
  public static final String AUTHORIZATION = "authorization";
  /** Cookie 头字段，格式化时必须脱敏。 */
  public static final String COOKIE = "cookie";
  /** Token 字段，格式化时必须脱敏。 */
  public static final String TOKEN = "token";

  private OpsLogFields() {
  }

}
