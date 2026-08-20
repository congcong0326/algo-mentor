package org.congcong.algomentor.api.learningplan.model;

import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftStatus;

/** 首次 AI 草案创建成功受理后的最小控制面响应。 */
public record LearningPlanDraftGenerationResponse(
    long draftId,
    LearningPlanDraftStatus status,
    String eventsUrl,
    String initialAfter,
    int realtimeProtocolVersion
) {
}
