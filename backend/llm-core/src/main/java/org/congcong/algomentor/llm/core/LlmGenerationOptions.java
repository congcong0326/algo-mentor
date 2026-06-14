package org.congcong.algomentor.llm.core;

import java.time.Duration;
import java.util.List;

public record LlmGenerationOptions(
    Double temperature,
    Double topP,
    Integer maxOutputTokens,
    List<String> stop,
    Long seed,
    Duration timeout
) {

  public LlmGenerationOptions {
    if (temperature != null && (temperature < 0 || temperature > 2)) {
      throw new IllegalArgumentException("LLM temperature must be between 0 and 2");
    }
    if (topP != null && (topP < 0 || topP > 1)) {
      throw new IllegalArgumentException("LLM topP must be between 0 and 1");
    }
    if (maxOutputTokens != null && maxOutputTokens <= 0) {
      throw new IllegalArgumentException("LLM max output tokens must be positive");
    }
    if (timeout != null && (timeout.isZero() || timeout.isNegative())) {
      throw new IllegalArgumentException("LLM timeout must be positive");
    }
    if (stop != null && stop.stream().anyMatch(sequence -> sequence == null || sequence.isBlank())) {
      throw new IllegalArgumentException("LLM stop sequences must not be blank");
    }
    stop = stop == null ? List.of() : List.copyOf(stop);
  }

  public static LlmGenerationOptions defaults() {
    return new LlmGenerationOptions(null, null, null, List.of(), null, null);
  }
}
