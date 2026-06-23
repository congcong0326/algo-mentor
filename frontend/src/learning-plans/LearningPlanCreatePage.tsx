import { ArrowLeft } from 'lucide-react';
import { useState } from 'react';
import {
  confirmLearningPlanDraft,
  createLearningPlanDraft,
  sendLearningPlanDraftMessage,
} from '../services/api';
import type {
  LearningPlanConfirmResponse,
  LearningPlanCreateDraftRequest,
  LearningPlanDraftResponse,
} from '../types/api';
import LearningPlanCreateForm from './LearningPlanCreateForm';
import LearningPlanDraftPanel from './LearningPlanDraftPanel';

type LearningPlanCreateState = 'editing' | 'generating' | 'collecting' | 'previewing' | 'confirming';

interface LearningPlanCreatePageProps {
  onBackToPlans: () => void;
  onSaved: (confirmed: LearningPlanConfirmResponse) => void;
}

function apiData<T>(response: { success: boolean; data?: T; error?: { message: string } }, fallback: string): T {
  if (!response.success || response.data === undefined) {
    throw new Error(response.error?.message ?? fallback);
  }
  return response.data;
}

export default function LearningPlanCreatePage({ onBackToPlans, onSaved }: LearningPlanCreatePageProps) {
  const [formKey, setFormKey] = useState(0);
  const [draft, setDraft] = useState<LearningPlanDraftResponse>();
  const [flowState, setFlowState] = useState<LearningPlanCreateState>('editing');
  const [error, setError] = useState('');

  async function submitDraft(request: LearningPlanCreateDraftRequest) {
    setFlowState('generating');
    setError('');
    try {
      const nextDraft = apiData(await createLearningPlanDraft(request), '学习计划方案生成失败');
      setDraft(nextDraft);
      setFlowState(nextDraft.status === 'COLLECTING' ? 'collecting' : 'previewing');
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : '学习计划方案生成失败');
      setFlowState('editing');
    }
  }

  async function sendFollowUp(message: string) {
    if (!draft || !message.trim()) {
      return false;
    }
    setFlowState('generating');
    setError('');
    try {
      const nextDraft = apiData(
        await sendLearningPlanDraftMessage(draft.draftId, { message: message.trim() }),
        '学习计划追问提交失败',
      );
      setDraft(nextDraft);
      setFlowState(nextDraft.status === 'COLLECTING' ? 'collecting' : 'previewing');
      return true;
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : '学习计划追问提交失败');
      setFlowState('collecting');
      return false;
    }
  }

  async function regenerateFromGoal(goal: string) {
    return sendFollowUp(`请按新的目标摘要重新生成学习计划：${goal}`);
  }

  async function confirmDraft() {
    if (!draft) {
      return;
    }
    setFlowState('confirming');
    setError('');
    try {
      const confirmed = apiData(await confirmLearningPlanDraft(draft.draftId), '学习计划保存失败');
      setDraft(undefined);
      onSaved(confirmed);
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : '学习计划保存失败');
      setFlowState('previewing');
    }
  }

  function retryCreateDraft() {
    setDraft(undefined);
    setError('');
    setFlowState('editing');
    setFormKey((current) => current + 1);
  }

  return (
    <section className="learning-shell learning-create-shell" aria-label="新建学习计划">
      <div className="learning-create-heading">
        <button
          className="secondary-button compact"
          disabled={flowState === 'generating' || flowState === 'confirming'}
          onClick={onBackToPlans}
          type="button"
        >
          <ArrowLeft aria-hidden="true" />
          <span>返回计划页</span>
        </button>
      </div>

      {draft ? (
        <>
          {error && <p className="error-text">{error}</p>}
          <LearningPlanDraftPanel
            draft={draft}
            loading={flowState === 'generating' || flowState === 'confirming'}
            onConfirm={confirmDraft}
            onRegenerateGoal={regenerateFromGoal}
            onRetryCreate={retryCreateDraft}
            onSendFollowUp={sendFollowUp}
          />
        </>
      ) : (
        <article className="learning-panel create-plan-page-panel">
          <LearningPlanCreateForm
            error={error}
            key={formKey}
            loading={flowState === 'generating'}
            onCancel={onBackToPlans}
            onSubmit={submitDraft}
            submitLabel="生成计划方案"
          />
        </article>
      )}
    </section>
  );
}
