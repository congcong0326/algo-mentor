package org.congcong.algomentor.api.learningplan.service;

import org.congcong.algomentor.api.learningplan.model.LearningPlanDraftRevisionReadyResponse;
import org.congcong.algomentor.api.learningplan.model.LearningPlanExtensionReadyResponse;
import org.congcong.algomentor.api.learningplan.model.LearningPlanResponseMapper;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanDraftRevisionResult;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionResult;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanProposalEvent;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanProposalStreamEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class LearningPlanProposalStreamSseMapper {

  public SseEmitter.SseEventBuilder toSseEvent(LearningPlanProposalStreamEvent event) {
    if (event instanceof LearningPlanProposalStreamEvent.Work work) {
      return SseEmitter.event().name(work.eventName()).data(work.event());
    }
    if (event instanceof LearningPlanProposalStreamEvent.Proposal proposal) {
      if (proposal.event() instanceof LearningPlanProposalEvent.DraftRevisionReady ready) {
        return SseEmitter.event().name(proposal.eventName()).data(toDraftRevisionReadyResponse(ready.result()));
      }
      if (proposal.event() instanceof LearningPlanProposalEvent.PlanExtensionReady ready) {
        return SseEmitter.event().name(proposal.eventName()).data(toExtensionReadyResponse(ready.result()));
      }
      if (proposal.event() instanceof LearningPlanProposalEvent.ProposalError error) {
        return SseEmitter.event().name(proposal.eventName()).data(error);
      }
    }
    throw new IllegalArgumentException("Unsupported learning plan proposal stream event: " + event.getClass().getName());
  }

  private LearningPlanDraftRevisionReadyResponse toDraftRevisionReadyResponse(LearningPlanDraftRevisionResult result) {
    return new LearningPlanDraftRevisionReadyResponse(
        result.proposalGroupId(),
        result.proposalId(),
        result.draftId(),
        result.revisionNo(),
        result.status(),
        result.supersededProposalIds(),
        LearningPlanResponseMapper.toDraftResponse(result.draft()));
  }

  private LearningPlanExtensionReadyResponse toExtensionReadyResponse(LearningPlanExtensionResult result) {
    return new LearningPlanExtensionReadyResponse(
        result.proposalGroupId(),
        result.proposalId(),
        result.planId(),
        result.revisionNo(),
        result.status(),
        result.supersededProposalIds(),
        result.summary(),
        result.extensionDraft());
  }
}
