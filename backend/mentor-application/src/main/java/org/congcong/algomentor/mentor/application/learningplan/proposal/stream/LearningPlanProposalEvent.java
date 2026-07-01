package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanDraftRevisionResult;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionResult;

/**
 * 学习计划提案流式接口中的业务结果事件。
 */
public sealed interface LearningPlanProposalEvent
    permits LearningPlanProposalEvent.DraftRevisionReady,
    LearningPlanProposalEvent.PlanExtensionReady,
    LearningPlanProposalEvent.ProposalError {

  record DraftRevisionReady(LearningPlanDraftRevisionResult result) implements LearningPlanProposalEvent {
  }

  record PlanExtensionReady(LearningPlanExtensionResult result) implements LearningPlanProposalEvent {
  }

  record ProposalError(String code, String message, boolean retryable) implements LearningPlanProposalEvent {
  }
}
