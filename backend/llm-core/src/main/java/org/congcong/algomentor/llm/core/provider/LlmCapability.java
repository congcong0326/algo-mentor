package org.congcong.algomentor.llm.core.provider;

/**
 * 功能标志用于将请求与模型及提供商的支持能力进行匹配。
 */
public enum LlmCapability {
  CHAT_COMPLETION,
  STREAMING,
  TOOL_CALLING,
  STRUCTURED_OUTPUT,
  JSON_SCHEMA_OUTPUT,
  VISION_INPUT,
  FILE_INPUT,
  REASONING_EFFORT,
  TOKEN_USAGE,
  CACHED_TOKEN_USAGE,
  EMBEDDING
}
