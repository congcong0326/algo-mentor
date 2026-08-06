package org.congcong.algomentor.api.learningplan.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftMetadataKeys;

/**
 * 普通用户学习计划响应允许返回的运行态 metadata。
 */
final class LearningPlanPublicMetadataMapper {

  private static final Set<String> ALLOWED_KEYS = Set.of(
      LearningPlanDraftMetadataKeys.DAILY_PROBLEM_COUNT,
      LearningPlanDraftMetadataKeys.TRAINING_DAYS_PER_WEEK,
      LearningPlanDraftMetadataKeys.COVERAGE_POLICY,
      LearningPlanDraftMetadataKeys.LOAD_SUMMARY);

  private LearningPlanPublicMetadataMapper() {
  }

  static Map<String, Object> project(Map<String, Object> metadata) {
    if (metadata == null || metadata.isEmpty()) {
      return Map.of();
    }
    Map<String, Object> visible = new LinkedHashMap<>();
    for (String key : ALLOWED_KEYS) {
      Object value = metadata.get(key);
      if (value != null) {
        visible.put(key, value);
      }
    }
    return Map.copyOf(visible);
  }
}
