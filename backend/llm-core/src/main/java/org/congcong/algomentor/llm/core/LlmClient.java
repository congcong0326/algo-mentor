package org.congcong.algomentor.llm.core;

/**
 * Compatibility API retained during migration to the provider/gateway completion contract.
 */
@Deprecated(forRemoval = false)
public interface LlmClient {

  LlmResponse complete(LlmRequest request);

  void stream(LlmRequest request, LlmStreamHandler handler);
}
