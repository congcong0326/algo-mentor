import { Check, FileText, MessageSquare, Send } from 'lucide-react';
import { useEffect, useState } from 'react';
import type { LearningPlanDraftResponse } from '../types/api';
import PlanPreview from './PlanPreview';

interface LearningPlanDraftPanelProps {
  draft: LearningPlanDraftResponse;
  loading: boolean;
  onConfirm: () => void;
  onRegenerateGoal?: (goal: string) => void;
  onRetryCreate?: () => void;
  onReturnToWizard?: () => void;
  onSendFollowUp: (message: string) => Promise<boolean>;
}

export default function LearningPlanDraftPanel({
  draft,
  loading,
  onConfirm,
  onRegenerateGoal,
  onRetryCreate,
  onReturnToWizard,
  onSendFollowUp,
}: LearningPlanDraftPanelProps) {
  const [followUp, setFollowUp] = useState('');
  const [editingGoal, setEditingGoal] = useState(false);
  const [goalDraft, setGoalDraft] = useState(draft.draftPlan?.goal ?? '');
  const followUpId = `learning-plan-draft-${draft.draftId}-follow-up`;
  const retryCreate = onRetryCreate ?? onReturnToWizard;

  useEffect(() => {
    setGoalDraft(draft.draftPlan?.goal ?? '');
    setEditingGoal(false);
  }, [draft.draftId, draft.draftPlan?.goal]);

  if (draft.status === 'COLLECTING') {
    return (
      <article className="learning-panel">
        <div className="panel-title">
          <MessageSquare aria-hidden="true" />
          <h2>Agent 追问</h2>
        </div>
        <p>{draft.assistantMessage}</p>
        <label className="topic-field" htmlFor={followUpId}>
          <span>补充回答</span>
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
          <span>发送补充</span>
        </button>
      </article>
    );
  }

  if (draft.status === 'GENERATED' && draft.draftPlan) {
    return (
      <article className="learning-panel">
        <div className="panel-title">
          <FileText aria-hidden="true" />
          <h2>草案预览</h2>
        </div>
        {editingGoal ? (
          <div className="goal-editor">
            <label className="topic-field">
              <span>目标摘要</span>
              <textarea
                aria-label="目标摘要"
                disabled={loading}
                onChange={(event) => setGoalDraft(event.target.value)}
                rows={3}
                value={goalDraft}
              />
            </label>
            <button
              className="secondary-button"
              disabled={loading || !goalDraft.trim()}
              onClick={() => onRegenerateGoal?.(goalDraft.trim())}
              type="button"
            >
              按新目标重新生成
            </button>
          </div>
        ) : (
          <div className="goal-summary">
            <p>{draft.draftPlan.goal}</p>
            {onRegenerateGoal && (
              <button
                className="secondary-button compact"
                disabled={loading}
                onClick={() => setEditingGoal(true)}
                type="button"
              >
                编辑目标摘要
              </button>
            )}
          </div>
        )}
        <PlanPreview plan={draft.draftPlan} />
        <button className="primary-button" disabled={loading} onClick={onConfirm} type="button">
          <Check aria-hidden="true" />
          <span>确认保存</span>
        </button>
      </article>
    );
  }

  if (draft.status === 'GENERATION_FAILED' || draft.status === 'EXPIRED') {
    return (
      <article className="learning-panel">
        <p className="empty-log">草案生成失败或已过期，请重新填写问卷后生成。</p>
        <button className="secondary-button" disabled={loading} onClick={retryCreate} type="button">
          重新填写问卷
        </button>
      </article>
    );
  }

  return (
    <article className="learning-panel">
      <p className="empty-log">草案暂不可预览，请重新填写问卷后生成。</p>
      <button className="secondary-button" disabled={loading} onClick={retryCreate} type="button">
        重新填写问卷
      </button>
    </article>
  );
}
