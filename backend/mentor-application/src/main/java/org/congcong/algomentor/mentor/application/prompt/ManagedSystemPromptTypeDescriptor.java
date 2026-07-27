package org.congcong.algomentor.mentor.application.prompt;

/** 系统提示词类型的只读展示元数据。 */
public record ManagedSystemPromptTypeDescriptor(
    String categoryCode,
    String displayNameZh,
    String displayNameEn,
    String descriptionZh,
    String descriptionEn
) {
}
