package org.congcong.algomentor.mentor.application.prompt;

import java.util.Map;

/** 数据库策略仅保存相对于代码默认值的 section 覆盖。 */
public record ManagedSystemPromptPolicyContent(Map<String, String> sectionOverrides) {

  public ManagedSystemPromptPolicyContent {
    sectionOverrides = sectionOverrides == null ? Map.of() : Map.copyOf(sectionOverrides);
  }
}
