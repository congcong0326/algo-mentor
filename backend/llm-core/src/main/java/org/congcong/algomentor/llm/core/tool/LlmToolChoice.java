package org.congcong.algomentor.llm.core.tool;

/**
 * Controls whether the model may, must, or must not call tools.
 */
public record LlmToolChoice(Mode mode, String toolName) {

  public LlmToolChoice {
    if (mode == null) {
      throw new IllegalArgumentException("LLM tool choice mode must not be null");
    }
    if (mode == Mode.SPECIFIC && (toolName == null || toolName.isBlank())) {
      throw new IllegalArgumentException("LLM specific tool choice must include tool name");
    }
    if (mode != Mode.SPECIFIC) {
      toolName = null;
    }
  }

  public static LlmToolChoice auto() {
    return new LlmToolChoice(Mode.AUTO, null);
  }

  public static LlmToolChoice none() {
    return new LlmToolChoice(Mode.NONE, null);
  }

  public static LlmToolChoice required() {
    return new LlmToolChoice(Mode.REQUIRED, null);
  }

  public static LlmToolChoice specific(String toolName) {
    return new LlmToolChoice(Mode.SPECIFIC, toolName);
  }

  /**
   * Tool selection modes supported by completion requests.
   */
  public enum Mode {
    AUTO,
    NONE,
    REQUIRED,
    SPECIFIC
  }
}
