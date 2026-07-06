import { ArrowLeft } from 'lucide-react';
import { useState } from 'react';
import {
  createLearningPlanDraftFromTemplate,
  confirmLearningPlanDraft,
  requireApiData,
  sendLearningPlanDraftMessage,
  streamLearningPlanDraft,
  streamLearningPlanDraftRevision,
} from '../services/api';
import type {
  AgentWorkStatusEvent,
  LearningPlanConfirmResponse,
  LearningPlanCreateDraftRequest,
  LearningPlanDraftErrorEvent,
  LearningPlanDraftRevisionReadyEvent,
  LearningPlanDraftResponse,
  LearningPlanTemplateDraftRequest,
  SseStreamEvent,
} from '../types/api';
import { useI18n } from '../i18n/I18nProvider';
import AgentWorkIndicator from './AgentWorkIndicator';
import LearningPlanCreateForm from './LearningPlanCreateForm';
import LearningPlanDraftPanel from './LearningPlanDraftPanel';
import LearningPlanTemplateCreatePanel from './LearningPlanTemplateCreatePanel';

type LearningPlanCreateState = 'editing' | 'generating' | 'collecting' | 'previewing' | 'confirming';
type LearningPlanCreateMode = 'ai' | 'template';

interface LearningPlanCreatePageProps {
  onBackToPlans: () => void;
  onSaved: (confirmed: LearningPlanConfirmResponse) => void;
}

export default function LearningPlanCreatePage({ onBackToPlans, onSaved }: LearningPlanCreatePageProps) {
  const { resources } = useI18n();
  const [formKey, setFormKey] = useState(0);
  const [draft, setDraft] = useState<LearningPlanDraftResponse>();
  const [workEvent, setWorkEvent] = useState<AgentWorkStatusEvent>();
  const [flowState, setFlowState] = useState<LearningPlanCreateState>('editing');
  const [createMode, setCreateMode] = useState<LearningPlanCreateMode>('ai');
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

  async function submitTemplateDraft(request: LearningPlanTemplateDraftRequest) {
    setFlowState('generating');
    setError('');
    setDraft(undefined);
    setWorkEvent({ message: resources.learningPlans.templateGenerateStart });
    try {
      const nextDraft = requireApiData(
        await createLearningPlanDraftFromTemplate(request),
        resources.learningPlans.templateGenerateFailed,
      );
      setDraft(nextDraft);
      setWorkEvent(undefined);
      setFlowState(nextDraft.status === 'COLLECTING' ? 'collecting' : 'previewing');
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : resources.learningPlans.templateGenerateFailed);
      setWorkEvent(undefined);
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
      setWorkEvent(undefined);
      setFlowState(nextDraft.status === 'COLLECTING' ? 'collecting' : 'previewing');
      return;
    }
    if (event.eventName === 'draft_revision_ready') {
      const revision = event.data as LearningPlanDraftRevisionReadyEvent;
      setDraft(revision.draft);
      setWorkEvent(undefined);
      setFlowState('previewing');
      return;
    }
    if (event.eventName === 'draft_error') {
      const draftError = event.data as LearningPlanDraftErrorEvent;
      setError(draftError.message || resources.learningPlans.generateFailed);
      setWorkEvent(undefined);
      setFlowState('editing');
      return;
    }
    if (event.eventName === 'draft_revision_error') {
      const draftError = event.data as LearningPlanDraftErrorEvent;
      setError(draftError.message || resources.learningPlans.revisionFailed);
      setWorkEvent(undefined);
      setFlowState('previewing');
    }
  }

  async function sendFollowUp(message: string) {
    if (!draft || !message.trim()) {
      return false;
    }
    setFlowState('generating');
    setError('');
    try {
      const nextDraft = requireApiData(
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

  async function reviseDraft(instruction: string) {
    if (!draft || !instruction.trim()) {
      return false;
    }
    setFlowState('generating');
    setError('');
    setWorkEvent({ message: resources.learningPlans.reviseDraft });
    try {
      let revisionReady = false;
      let revisionFailed = false;
      await streamLearningPlanDraftRevision(draft.draftId, { instruction: instruction.trim() }, {
        onEvent: (event) => {
          if (event.eventName === 'draft_revision_ready') {
            revisionReady = true;
          }
          if (event.eventName === 'draft_revision_error') {
            revisionFailed = true;
          }
          handleDraftStreamEvent(event);
        },
      });
      if (!revisionReady && !revisionFailed) {
        setError(resources.learningPlans.revisionFailed);
        setWorkEvent(undefined);
        setFlowState('previewing');
        return false;
      }
      return revisionReady && !revisionFailed;
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : resources.learningPlans.revisionFailed);
      setWorkEvent(undefined);
      setFlowState('previewing');
      return false;
    }
  }

  async function confirmDraft() {
    if (!draft) {
      return;
    }
    setFlowState('confirming');
    setError('');
    try {
      const confirmed = requireApiData(await confirmLearningPlanDraft(draft.draftId), resources.learningPlans.saveFailed);
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
            workEvent={flowState === 'generating' ? workEvent : undefined}
            onConfirm={confirmDraft}
            onRetryCreate={retryCreateDraft}
            onReviseDraft={reviseDraft}
            onSendFollowUp={sendFollowUp}
          />
        </>
      ) : (
        <article className="learning-panel create-plan-page-panel">
          <section className="question-block">
            <strong>{resources.learningPlans.createMode}</strong>
            <div className="segmented-grid create-mode-switch">
              <button
                aria-pressed={createMode === 'ai'}
                className={createMode === 'ai' ? 'selected' : ''}
                disabled={flowState === 'generating'}
                onClick={() => {
                  setCreateMode('ai');
                  setError('');
                }}
                type="button"
              >
                {resources.learningPlans.createWithAi}
              </button>
              <button
                aria-pressed={createMode === 'template'}
                className={createMode === 'template' ? 'selected' : ''}
                disabled={flowState === 'generating'}
                onClick={() => {
                  setCreateMode('template');
                  setError('');
                }}
                type="button"
              >
                {resources.learningPlans.createFromTemplate}
              </button>
            </div>
          </section>
          {flowState === 'generating' && (
            <AgentWorkIndicator active event={workEvent} error={error} />
          )}
          {createMode === 'ai' ? (
            <LearningPlanCreateForm
              error={error}
              key={formKey}
              loading={flowState === 'generating'}
              onCancel={onBackToPlans}
              onSubmit={submitDraft}
              submitLabel={resources.learningPlans.generatePlan}
            />
          ) : (
            <LearningPlanTemplateCreatePanel
              error={error}
              loading={flowState === 'generating'}
              onCancel={onBackToPlans}
              onSubmit={submitTemplateDraft}
            />
          )}
        </article>
      )}
    </section>
  );
}
