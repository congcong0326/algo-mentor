import { ArrowLeft } from 'lucide-react';
import { useState } from 'react';
import {
  confirmLearningPlanDraft,
  sendLearningPlanDraftMessage,
  streamLearningPlanDraft,
} from '../services/api';
import type {
  AgentWorkStatusEvent,
  LearningPlanConfirmResponse,
  LearningPlanCreateDraftRequest,
  LearningPlanDraftErrorEvent,
  LearningPlanDraftResponse,
  SseStreamEvent,
} from '../types/api';
import { useI18n } from '../i18n/I18nProvider';
import AgentWorkIndicator from './AgentWorkIndicator';
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
  const { resources } = useI18n();
  const [formKey, setFormKey] = useState(0);
  const [draft, setDraft] = useState<LearningPlanDraftResponse>();
  const [workEvent, setWorkEvent] = useState<AgentWorkStatusEvent>();
  const [flowState, setFlowState] = useState<LearningPlanCreateState>('editing');
  const [error, setError] = useState('');

  async function submitDraft(request: LearningPlanCreateDraftRequest) {
    setFlowState('generating');
    setError('');
    setDraft(undefined);
    setWorkEvent({ message: resources.learningPlans.generateStart });
    try {
      await streamLearningPlanDraft(request, {
        onEvent: handleDraftStreamEvent,
      });
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : resources.learningPlans.generateFailed);
      setFlowState('editing');
    }
  }

  function handleDraftStreamEvent(event: SseStreamEvent) {
    if (event.eventName.startsWith('work_')) {
      const nextWorkEvent = event.data as AgentWorkStatusEvent;
      setWorkEvent(nextWorkEvent);
      if (event.eventName === 'work_error') {
        setError(nextWorkEvent.message || resources.learningPlans.generateFailed);
      }
      return;
    }
    if (event.eventName === 'draft_ready') {
      const nextDraft = event.data as LearningPlanDraftResponse;
      setDraft(nextDraft);
      setFlowState(nextDraft.status === 'COLLECTING' ? 'collecting' : 'previewing');
      return;
    }
    if (event.eventName === 'draft_error') {
      const draftError = event.data as LearningPlanDraftErrorEvent;
      setError(draftError.message || resources.learningPlans.generateFailed);
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
        resources.learningPlans.followUpFailed,
      );
      setDraft(nextDraft);
      setFlowState(nextDraft.status === 'COLLECTING' ? 'collecting' : 'previewing');
      return true;
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : resources.learningPlans.followUpFailed);
      setFlowState('collecting');
      return false;
    }
  }

  async function regenerateFromGoal(goal: string) {
    return sendFollowUp(resources.learningPlans.followUpRegeneratePrefix(goal));
  }

  async function confirmDraft() {
    if (!draft) {
      return;
    }
    setFlowState('confirming');
    setError('');
    try {
      const confirmed = apiData(await confirmLearningPlanDraft(draft.draftId), resources.learningPlans.saveFailed);
      setDraft(undefined);
      onSaved(confirmed);
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : resources.learningPlans.saveFailed);
      setFlowState('previewing');
    }
  }

  function retryCreateDraft() {
    setDraft(undefined);
    setWorkEvent(undefined);
    setError('');
    setFlowState('editing');
    setFormKey((current) => current + 1);
  }

  return (
    <section className="learning-shell learning-create-shell" aria-label={resources.learningPlans.createAriaLabel}>
      <div className="learning-create-heading">
        <button
          className="secondary-button compact"
          disabled={flowState === 'generating' || flowState === 'confirming'}
          onClick={onBackToPlans}
          type="button"
        >
          <ArrowLeft aria-hidden="true" />
          <span>{resources.learningPlans.backToPlans}</span>
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
          {flowState === 'generating' && (
            <AgentWorkIndicator active event={workEvent} error={error} />
          )}
          <LearningPlanCreateForm
            error={error}
            key={formKey}
            loading={flowState === 'generating'}
            onCancel={onBackToPlans}
            onSubmit={submitDraft}
            submitLabel={resources.learningPlans.generatePlan}
          />
        </article>
      )}
    </section>
  );
}
