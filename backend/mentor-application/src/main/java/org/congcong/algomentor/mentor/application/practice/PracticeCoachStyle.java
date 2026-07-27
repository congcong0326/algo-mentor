package org.congcong.algomentor.mentor.application.practice;

import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptDefinitions;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPrompts;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptResolutionSource;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptSectionKeys;

/** 题目聊天教练风格白名单，具体 system 指令由受管理 definition 提供。 */
public enum PracticeCoachStyle {
  GUIDED("引导型教练"),
  DIRECT("直给型教练");

  private final String label;

  PracticeCoachStyle(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }

  /**
   * 仅兼容既有读取方；业务 Prompt 组装必须从当前 run 的 resolved snapshot 读取。
   */
  public String instruction() {
    String key = this == DIRECT
        ? SystemPromptSectionKeys.PRACTICE_COACH_DIRECT
        : SystemPromptSectionKeys.PRACTICE_COACH_GUIDED;
    return ManagedSystemPrompts.defaultRegistry()
        .codeDefaultSnapshot(ManagedSystemPromptDefinitions.PRACTICE_CHAT,
            SystemPromptResolutionSource.CODE_POLICY_UNAVAILABLE)
        .requireSection(key)
        .text();
  }

  public static PracticeCoachStyle defaultStyle() {
    return GUIDED;
  }

  public static PracticeCoachStyle from(Object value) {
    if (value instanceof PracticeCoachStyle style) {
      return style;
    }
    if (value instanceof String text && !text.isBlank()) {
      try {
        return PracticeCoachStyle.valueOf(text.trim());
      } catch (IllegalArgumentException ignored) {
        return defaultStyle();
      }
    }
    return defaultStyle();
  }
}
