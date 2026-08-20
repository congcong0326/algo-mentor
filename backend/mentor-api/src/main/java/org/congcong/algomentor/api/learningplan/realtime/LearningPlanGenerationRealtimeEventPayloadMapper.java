package org.congcong.algomentor.api.learningplan.realtime;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftGenerationEvent;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftGenerationConstants;
import org.springframework.stereotype.Component;

/** 首次草案 Agent 领域事件到 Redis/SSE 公开载荷的唯一安全边界。 */
@Component
public final class LearningPlanGenerationRealtimeEventPayloadMapper {

  public Optional<LearningPlanGenerationRealtimePayload> map(
      long draftId,
      LearningPlanDraftGenerationEvent event
  ) {
    if (draftId < 1 || event == null) {
      return Optional.empty();
    }
    if (event instanceof LearningPlanDraftGenerationEvent.WorkStarted started
        && LearningPlanDraftGenerationConstants.WORK_STARTED_MESSAGE.equals(started.message())) {
      return Optional.of(payload(LearningPlanGenerationRealtimeProtocol.WORK_START,
          withDraftId(draftId).put(LearningPlanGenerationRealtimeProtocol.DATA_MESSAGE, started.message())));
    }
    if (event instanceof LearningPlanDraftGenerationEvent.WorkProgress progress
        && LearningPlanDraftGenerationConstants.WORK_PROGRESS_MESSAGE.equals(progress.message())) {
      return Optional.of(payload(LearningPlanGenerationRealtimeProtocol.WORK_PROGRESS,
          withDraftId(draftId).put(LearningPlanGenerationRealtimeProtocol.DATA_MESSAGE, progress.message())));
    }
    if (event instanceof LearningPlanDraftGenerationEvent.WorkToolStarted started
        && LearningPlanDraftGenerationConstants.PUBLIC_TOOL_NAMES.contains(started.toolName())) {
      return Optional.of(payload(LearningPlanGenerationRealtimeProtocol.WORK_TOOL_START,
          withDraftId(draftId).put(LearningPlanGenerationRealtimeProtocol.DATA_TOOL_NAME, started.toolName())));
    }
    if (event instanceof LearningPlanDraftGenerationEvent.WorkToolEnded ended
        && LearningPlanDraftGenerationConstants.PUBLIC_TOOL_NAMES.contains(ended.toolName())) {
      return Optional.of(payload(LearningPlanGenerationRealtimeProtocol.WORK_TOOL_END,
          withDraftId(draftId).put(LearningPlanGenerationRealtimeProtocol.DATA_TOOL_NAME, ended.toolName())));
    }
    if (event instanceof LearningPlanDraftGenerationEvent.Completed) {
      return Optional.of(payload(LearningPlanGenerationRealtimeProtocol.DRAFT_COMPLETED, withDraftId(draftId)));
    }
    if (event instanceof LearningPlanDraftGenerationEvent.Failed failed
        && LearningPlanDraftGenerationConstants.PUBLIC_FAILURE_CODES.contains(failed.code())) {
      return Optional.of(payload(LearningPlanGenerationRealtimeProtocol.DRAFT_FAILED,
          withDraftId(draftId).put(LearningPlanGenerationRealtimeProtocol.DATA_CODE, failed.code())));
    }
    return Optional.empty();
  }

  private LearningPlanGenerationRealtimePayload payload(String eventName, ObjectNode data) {
    return new LearningPlanGenerationRealtimePayload(eventName, data);
  }

  private ObjectNode withDraftId(long draftId) {
    return JsonNodeFactory.instance.objectNode()
        .put(LearningPlanGenerationRealtimeProtocol.DATA_DRAFT_ID, draftId);
  }
}
