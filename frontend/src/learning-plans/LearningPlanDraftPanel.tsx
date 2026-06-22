import { Check, FileText, MessageSquare, Send } from 'lucide-react';
import { useState } from 'react';
import type { LearningPlanDraftResponse } from '../types/api';
import PlanPreview from './PlanPreview';

interface LearningPlanDraftPanelProps {
  draft: LearningPlanDraftResponse;
  loading: boolean;
  onConfirm: () => void;
  onReturnToWizard: () => void;
  onSendFollowUp: (message: string) => Promise<boolean>;
}

export default function LearningPlanDraftPanel({
  draft,
  loading,
  onConfirm,
  onReturnToWizard,
  onSendFollowUp,
}: LearningPlanDraftPanelProps) {
  const [followUp, setFollowUp] = useState('');
  const followUpId = `learning-plan-draft-${draft.draftId}-follow-up`;

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
        <p className="empty-log">草案生成失败或已过期，请返回向导调整后重新生成。</p>
        <button className="secondary-button" disabled={loading} onClick={onReturnToWizard} type="button">
          返回向导
        </button>
      </article>
    );
  }

  return (
    <article className="learning-panel">
      <p className="empty-log">草案暂不可预览，请返回向导调整后重新生成。</p>
      <button className="secondary-button" disabled={loading} onClick={onReturnToWizard} type="button">
        返回向导
      </button>
    </article>
  );
}
