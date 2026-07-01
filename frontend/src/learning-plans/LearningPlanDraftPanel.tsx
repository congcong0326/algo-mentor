import { Check, FileText, MessageSquare, Send } from 'lucide-react';
import { useState } from 'react';
import { useI18n } from '../i18n/I18nProvider';
import type { AgentWorkStatusEvent, LearningPlanDraftResponse } from '../types/api';
import PlanPreview from './PlanPreview';

interface LearningPlanDraftPanelProps {
  draft: LearningPlanDraftResponse;
  loading: boolean;
  workEvent?: AgentWorkStatusEvent;
  onConfirm: () => void;
  onRetryCreate?: () => void;
  onReturnToWizard?: () => void;
  onSendFollowUp: (message: string) => Promise<boolean>;
  onReviseDraft: (instruction: string) => Promise<boolean>;
}

export default function LearningPlanDraftPanel({
  draft,
  loading,
  workEvent,
  onConfirm,
  onRetryCreate,
  onReturnToWizard,
  onSendFollowUp,
  onReviseDraft,
}: LearningPlanDraftPanelProps) {
  const { resources } = useI18n();
  const [followUp, setFollowUp] = useState('');
  const [revisionInstruction, setRevisionInstruction] = useState('');
  const followUpId = `learning-plan-draft-${draft.draftId}-follow-up`;
  const revisionInstructionId = `learning-plan-draft-${draft.draftId}-revision`;
  const retryCreate = onRetryCreate ?? onReturnToWizard;

  if (draft.status === 'COLLECTING') {
    return (
      <article className="learning-panel">
        <div className="panel-title">
          <MessageSquare aria-hidden="true" />
          <h2>{resources.learningPlans.draftQuestion}</h2>
        </div>
        <p>{draft.assistantMessage}</p>
        <label className="topic-field" htmlFor={followUpId}>
          <span>{resources.learningPlans.followUpAnswer}</span>
          <textarea
            disabled={loading}
            id={followUpId}
            onChange={(event) => setFollowUp(event.target.value)}
            rows={3}
            value={followUp}
          />
        </label>
        <button
          className="primary-button"
          disabled={loading || !followUp.trim()}
          onClick={() => {
            void onSendFollowUp(followUp.trim()).then((sent) => {
              if (sent) {
                setFollowUp('');
              }
            });
          }}
          type="button"
        >
          <Send aria-hidden="true" />
          <span>{resources.learningPlans.sendFollowUp}</span>
        </button>
      </article>
    );
  }

  if (draft.status === 'GENERATED' && draft.draftPlan) {
    return (
      <article className="learning-panel">
        <div className="panel-title">
          <FileText aria-hidden="true" />
          <h2>{resources.learningPlans.draftPreview}</h2>
        </div>
        <div className="goal-summary">
          <p>{draft.draftPlan.goal}</p>
        </div>
        <PlanPreview plan={draft.draftPlan} />
        <div className="draft-revision-panel">
          <label className="topic-field" htmlFor={revisionInstructionId}>
            <span>{resources.learningPlans.revisionInstructionLabel}</span>
            <textarea
              disabled={loading}
              id={revisionInstructionId}
              onChange={(event) => setRevisionInstruction(event.target.value)}
              rows={3}
              value={revisionInstruction}
            />
          </label>
          {workEvent?.message && <p className="empty-log">{workEvent.message}</p>}
          <div className="draft-action-row">
            <button
              className="secondary-button"
              disabled={loading || !revisionInstruction.trim()}
              onClick={() => {
                void onReviseDraft(revisionInstruction.trim()).then((revised) => {
                  if (revised) {
                    setRevisionInstruction('');
                  }
                });
              }}
              type="button"
            >
              <Send aria-hidden="true" />
              <span>{resources.learningPlans.reviseDraft}</span>
            </button>
            <button className="primary-button" disabled={loading} onClick={onConfirm} type="button">
              <Check aria-hidden="true" />
              <span>{resources.learningPlans.savePlan}</span>
            </button>
          </div>
        </div>
      </article>
    );
  }

  if (draft.status === 'GENERATION_FAILED' || draft.status === 'EXPIRED') {
    return (
      <article className="learning-panel">
        <p className="empty-log">{resources.learningPlans.draftUnavailableFailed}</p>
        <button className="secondary-button" disabled={loading} onClick={retryCreate} type="button">
          {resources.learningPlans.restartWizard}
        </button>
      </article>
    );
  }

  return (
    <article className="learning-panel">
      <p className="empty-log">{resources.learningPlans.draftUnavailable}</p>
      <button className="secondary-button" disabled={loading} onClick={retryCreate} type="button">
        {resources.learningPlans.restartWizard}
      </button>
    </article>
  );
}
