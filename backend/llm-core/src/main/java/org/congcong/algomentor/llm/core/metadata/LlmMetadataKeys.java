package org.congcong.algomentor.llm.core.metadata;

/**
 * LLM 请求、响应、错误和 provider 能力描述中共享的 metadata 字段名。
 */
public final class LlmMetadataKeys {

  /**
   * LLM provider 标识。
   */
  public static final String PROVIDER = "provider";

  /**
   * LLM model 标识。
   */
  public static final String MODEL = "model";

  /**
   * 上游 provider 返回的响应 ID。
   */
  public static final String RESPONSE_ID = "responseId";

  /**
   * 上游 provider 返回的响应状态。
   */
  public static final String STATUS = "status";

  /**
   * Provider 或网关使用的 API 类型。
   */
  public static final String API = "api";

  /**
   * HTTP 或 provider 层错误状态码。
   */
  public static final String STATUS_CODE = "statusCode";

  /**
   * Provider 返回的错误码。
   */
  public static final String ERROR_CODE = "errorCode";

  /**
   * 项目内部错误载荷中的通用错误码字段。
   */
  public static final String CODE = "code";

  /**
   * Provider 返回的错误类型。
   */
  public static final String ERROR_TYPE = "errorType";

  /**
   * Provider 返回的错误参数名。
   */
  public static final String ERROR_PARAM = "errorParam";

  /**
   * 流式事件序列号。
   */
  public static final String SEQUENCE_NUMBER = "sequenceNumber";

  /**
   * LLM 使用量中的输入 token 数。
   */
  public static final String INPUT_TOKENS = "inputTokens";

  /**
   * LLM 使用量中的输出 token 数。
   */
  public static final String OUTPUT_TOKENS = "outputTokens";

  /**
   * LLM 使用量中的缓存 token 数。
   */
  public static final String CACHED_TOKENS = "cachedTokens";

  /**
   * LLM 使用量中的推理 token 数。
   */
  public static final String REASONING_TOKENS = "reasoningTokens";

  /**
   * LLM 使用量中的总 token 数。
   */
  public static final String TOTAL_TOKENS = "totalTokens";

  private LlmMetadataKeys() {
  }
}
