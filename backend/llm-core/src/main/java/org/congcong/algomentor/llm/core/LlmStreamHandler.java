package org.congcong.algomentor.llm.core;

/**
 * Compatibility API retained during migration to the provider/gateway completion contract.
 */
@Deprecated(forRemoval = false)
public interface LlmStreamHandler {

  void onChunk(String content);

  void onComplete();

  void onError(Throwable error);
}
