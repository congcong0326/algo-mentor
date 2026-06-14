package org.congcong.algomentor.llm.core;

public interface LlmStreamHandler {

  void onChunk(String content);

  void onComplete();

  void onError(Throwable error);
}
