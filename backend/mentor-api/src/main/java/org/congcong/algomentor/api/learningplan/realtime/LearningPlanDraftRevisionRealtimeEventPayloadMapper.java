package org.congcong.algomentor.api.learningplan.realtime;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanDraftRevisionGenerationConstants;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanDraftRevisionGenerationEvent;
import org.springframework.stereotype.Component;

/** revision 业务事件到低敏 Redis/SSE 载荷的唯一投影边界。 */
@Component
public final class LearningPlanDraftRevisionRealtimeEventPayloadMapper {

  public Optional<Payload> map(long draftId, long revisionId, LearningPlanDraftRevisionGenerationEvent event) {
    if (draftId < 1 || revisionId < 1 || event == null) {
      return Optional.empty();
    }
    if (event instanceof LearningPlanDraftRevisionGenerationEvent.WorkStarted) {
      return Optional.of(new Payload(LearningPlanDraftRevisionRealtimeProtocol.WORK_START,
          identity(draftId, revisionId).put(LearningPlanDraftRevisionRealtimeProtocol.DATA_MESSAGE,
              LearningPlanDraftRevisionGenerationConstants.WORK_STARTED_MESSAGE)));
    }
    if (event instanceof LearningPlanDraftRevisionGenerationEvent.WorkProgress) {
      return Optional.of(new Payload(LearningPlanDraftRevisionRealtimeProtocol.WORK_PROGRESS,
          identity(draftId, revisionId).put(LearningPlanDraftRevisionRealtimeProtocol.DATA_MESSAGE,
              LearningPlanDraftRevisionGenerationConstants.WORK_PROGRESS_MESSAGE)));
    }
    if (event instanceof LearningPlanDraftRevisionGenerationEvent.Completed) {
      return Optional.of(new Payload(LearningPlanDraftRevisionRealtimeProtocol.REVISION_COMPLETED,
          identity(draftId, revisionId)));
    }
    if (event instanceof LearningPlanDraftRevisionGenerationEvent.Failed failed
        && LearningPlanDraftRevisionGenerationConstants.PUBLIC_FAILURE_CODES.contains(failed.code())) {
      return Optional.of(new Payload(LearningPlanDraftRevisionRealtimeProtocol.REVISION_FAILED,
          identity(draftId, revisionId).put(LearningPlanDraftRevisionRealtimeProtocol.DATA_CODE, failed.code())));
    }
    if (event instanceof LearningPlanDraftRevisionGenerationEvent.Superseded superseded
        && LearningPlanDraftRevisionGenerationConstants.PUBLIC_FAILURE_CODES.contains(superseded.code())) {
      return Optional.of(new Payload(LearningPlanDraftRevisionRealtimeProtocol.REVISION_SUPERSEDED,
          identity(draftId, revisionId).put(LearningPlanDraftRevisionRealtimeProtocol.DATA_CODE, superseded.code())));
    }
    return Optional.empty();
  }

  private ObjectNode identity(long draftId, long revisionId) {
    return JsonNodeFactory.instance.objectNode()
        .put(LearningPlanDraftRevisionRealtimeProtocol.DATA_DRAFT_ID, draftId)
        .put(LearningPlanDraftRevisionRealtimeProtocol.DATA_REVISION_ID, revisionId);
  }

  record Payload(String eventName, ObjectNode data) {
  }
}
