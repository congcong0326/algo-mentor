package org.congcong.algomentor.api.learningplan.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record LearningPlanTemplateRow(
    Long id,
    String templateId,
    String title,
    String summary,
    String intent,
    String goal,
    Integer defaultDurationWeeks,
    String level,
    Integer defaultWeeklyHours,
    String programmingLanguage,
    String difficultyPreference,
    Boolean interviewOriented,
    JsonNode topicPreferencesJson,
    String targetAudience,
    JsonNode difficultyMixJson,
    JsonNode prerequisitesJson,
    JsonNode recommendedForJson,
    JsonNode notRecommendedForJson,
    String expectedOutcome,
    String sourceName,
    String sourceUrl,
    String sourceCommit,
    String sourceDataPath,
    String sourceDescription,
    String curationNotes,
    String licenseNotice,
    Integer problemCount,
    Integer matchedProblemCount,
    Integer missingProblemCount,
    JsonNode metadataJson,
    Instant createdAt,
    Instant updatedAt
) {
}
