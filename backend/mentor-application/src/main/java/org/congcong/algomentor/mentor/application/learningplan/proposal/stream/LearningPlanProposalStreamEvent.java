package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import org.congcong.algomentor.agent.core.work.AgentWorkStatusEvent;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanStreamConstants;

/**
 * 学习计划提案流式接口对 API 层发布的事件。
 */
public sealed interface LearningPlanProposalStreamEvent
    permits LearningPlanProposalStreamEvent.Work, LearningPlanProposalStreamEvent.Proposal {

  String eventName();

  record Work(AgentWorkStatusEvent event) implements LearningPlanProposalStreamEvent {
    @Override
    public String eventName() {
      return event.eventName();
    }
  }

  record Proposal(ProposalProfile profile, LearningPlanProposalEvent event) implements LearningPlanProposalStreamEvent {
    public Proposal {
      if (profile == null) {
        throw new IllegalArgumentException("Learning plan proposal stream profile must not be null");
      }
      if (event == null) {
        throw new IllegalArgumentException("Learning plan proposal stream event must not be null");
      }
    }

    @Override
    public String eventName() {
      if (event instanceof LearningPlanProposalEvent.DraftRevisionReady) {
        return LearningPlanStreamConstants.DRAFT_REVISION_READY;
      }
      if (event instanceof LearningPlanProposalEvent.PlanExtensionReady) {
        return LearningPlanStreamConstants.PLAN_EXTENSION_READY;
      }
      return switch (profile) {
        case DRAFT_REVISION -> LearningPlanStreamConstants.DRAFT_REVISION_ERROR;
        case PLAN_EXTENSION -> LearningPlanStreamConstants.PLAN_EXTENSION_ERROR;
      };
    }
  }

  enum ProposalProfile {
    DRAFT_REVISION,
    PLAN_EXTENSION
  }
}
