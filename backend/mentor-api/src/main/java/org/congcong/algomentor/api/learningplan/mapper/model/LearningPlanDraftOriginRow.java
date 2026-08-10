package org.congcong.algomentor.api.learningplan.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;

/** 学习计划草稿第一次形成完整计划时冻结的来源无关快照。 */
public record LearningPlanDraftOriginRow(
    JsonNode originBriefJson,
    JsonNode originPlanJson
) {
}
