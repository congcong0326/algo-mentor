package org.congcong.algomentor.mentor.application.prompt;

/** 一个稳定的固定系统提示词片段。 */
public record ManagedSystemPromptSectionDefinition(
    String key,
    String displayNameZh,
    String displayNameEn,
    String descriptionZh,
    String descriptionEn,
    int displayOrder,
    boolean required,
    int maxLength,
    String defaultText
) {
}
