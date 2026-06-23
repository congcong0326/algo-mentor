package org.congcong.algomentor.api.learningplan.service;

import org.congcong.algomentor.api.learningplan.model.LearningPlanResponseMapper;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftEvent;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftStreamEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class LearningPlanDraftStreamSseMapper {

  public SseEmitter.SseEventBuilder toSseEvent(LearningPlanDraftStreamEvent event) {
    if (event instanceof LearningPlanDraftStreamEvent.Work work) {
      return SseEmitter.event().name(work.eventName()).data(work.event());
    }
    if (event instanceof LearningPlanDraftStreamEvent.Draft draft) {
      if (draft.event() instanceof LearningPlanDraftEvent.DraftReady ready) {
        return SseEmitter.event()
            .name(draft.eventName())
            .data(LearningPlanResponseMapper.toDraftResponse(ready.draft()));
      }
      if (draft.event() instanceof LearningPlanDraftEvent.DraftError error) {
        return SseEmitter.event().name(draft.eventName()).data(error);
      }
    }
    throw new IllegalArgumentException("Unsupported learning plan draft stream event: " + event.getClass().getName());
  }
}
