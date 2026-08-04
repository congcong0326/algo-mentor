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
  /** Agent 的稳定业务定义标识。 */
  public static final String AGENT_KEY = "agentKey";
  /** Agent loop 内的模型调用步数，从 1 开始。 */
  public static final String STEP_INDEX = "stepIndex";
  /** Agent run 允许执行的最大模型 step 数。 */
  public static final String MAX_STEPS = "maxSteps";
  /** 工具调用标识，用于关联开始、结束和失败事件。 */
  public static final String TOOL_CALL_ID = "toolCallId";
  /** 工具名称字段。 */
  public static final String TOOL_NAME = "toolName";
  /** 经白名单提炼的工具入参摘要，不包含自由文本和敏感值。 */
  public static final String TOOL_ARGUMENTS = "toolArguments";
  /** 工具响应的结构和规模摘要，不包含响应正文。 */
  public static final String TOOL_RESULT = "toolResult";
  /** 本次模型请求中的消息数量。 */
  public static final String MESSAGE_COUNT = "messageCount";
  /** 本次模型请求声明的工具数量。 */
  public static final String DECLARED_TOOL_COUNT = "declaredToolCount";
  /** 实际 provider 标识，以流的 MessageStart 为准。 */
  public static final String PROVIDER = "provider";
  /** 实际 model 标识，以流的 MessageStart 为准。 */
  public static final String MODEL = "model";
  /** 从请求发出到收到第一个模型流事件的耗时毫秒。 */
  public static final String TIME_TO_FIRST_EVENT_MS = "timeToFirstEventMs";
  /** 当前 step 在发出模型请求前的上下文准备耗时毫秒。 */
  public static final String CONTEXT_PREPARATION_MS = "contextPreparationMs";
  /** 模型 step 的结束原因。 */
  public static final String FINISH_REASON = "finishReason";
  /** 模型 step 产生的工具调用数量。 */
  public static final String TOOL_CALL_COUNT = "toolCallCount";
  /** 模型 step 输出正文的字符数。 */
  public static final String OUTPUT_CHAR_COUNT = "outputCharCount";
  /** provider 回传的输入 token 数。 */
  public static final String INPUT_TOKENS = "inputTokens";
  /** provider 回传的输出 token 数。 */
  public static final String OUTPUT_TOKENS = "outputTokens";
  /** provider 回传的缓存 token 数。 */
  public static final String CACHED_TOKENS = "cachedTokens";
  /** provider 回传的推理 token 数。 */
  public static final String REASONING_TOKENS = "reasoningTokens";
  /** provider 回传的总 token 数。 */
  public static final String TOTAL_TOKENS = "totalTokens";
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
