package org.congcong.algomentor.llm.core;

public interface LlmClient {

  LlmResponse complete(LlmRequest request);

  void stream(LlmRequest request, LlmStreamHandler handler);
}
