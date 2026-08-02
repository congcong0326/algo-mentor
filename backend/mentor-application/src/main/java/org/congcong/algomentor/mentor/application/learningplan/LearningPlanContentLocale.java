package org.congcong.algomentor.mentor.application.learningplan;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 学习计划正文支持的固定语言。 */
public enum LearningPlanContentLocale {
  ZH_CN("zh-CN"),
  EN_US("en-US");

  private static final List<Locale> SUPPORTED = List.of(
      Locale.forLanguageTag(ZH_CN.languageTag),
      Locale.forLanguageTag(EN_US.languageTag));

  private final String languageTag;

  LearningPlanContentLocale(String languageTag) {
    this.languageTag = languageTag;
  }

  @JsonValue
  public String languageTag() {
    return languageTag;
  }

  @JsonCreator
  public static LearningPlanContentLocale fromValue(String value) {
    if (value == null || value.isBlank()) {
      return ZH_CN;
    }
    for (LearningPlanContentLocale locale : values()) {
      if (locale.languageTag.equalsIgnoreCase(value) || locale.name().equalsIgnoreCase(value)) {
        return locale;
      }
    }
    return ZH_CN;
  }

  public static LearningPlanContentLocale fromAcceptLanguage(String acceptLanguage) {
    if (acceptLanguage == null || acceptLanguage.isBlank()) {
      return ZH_CN;
    }
    try {
      Locale matched = Locale.lookup(Locale.LanguageRange.parse(acceptLanguage), SUPPORTED);
      return matched != null && EN_US.languageTag.equalsIgnoreCase(matched.toLanguageTag()) ? EN_US : ZH_CN;
    } catch (IllegalArgumentException exception) {
      return ZH_CN;
    }
  }

  public static LearningPlanContentLocale fromMetadata(Map<String, Object> metadata) {
    if (metadata == null) {
      return ZH_CN;
    }
    Object value = metadata.get(LearningPlanDraftMetadataKeys.CONTENT_LOCALE);
    return fromValue(value == null ? null : value.toString());
  }
}
