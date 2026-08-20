import { ArrowLeft, LayoutTemplate, Sparkles } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import {
  createLearningPlanDraftFromTemplate,
  confirmLearningPlanDraft,
  getLearningPlanDraft,
  getLearningPlanDraftRevision,
  readLearningPlanDraftGenerationEvents,
  readLearningPlanDraftRevisionEvents,
  requireApiData,
  sendLearningPlanDraftMessage,
  startLearningPlanDraftGeneration,
  startLearningPlanDraftRevisionGeneration,
} from '../services/api';
import type {
  AgentWorkStatusEvent,
  LearningPlanConfirmResponse,
  LearningPlanCreateDraftRequest,
  LearningPlanDraftErrorEvent,
  LearningPlanDraftGenerationEventData,
  LearningPlanDraftGenerationResponse,
  LearningPlanDraftGenerationStartResponse,
  LearningPlanDraftRevisionEventData,
  LearningPlanDraftResponse,
  LearningPlanTemplateDraftRequest,
  LearningPlanAiRevisionCapabilities,
  SseStreamEvent,
} from '../types/api';
import { useI18n } from '../i18n/I18nProvider';
import AiCapacityUnavailableDialog from '../components/AiCapacityUnavailableDialog';
import AiOperationErrorDialog from '../components/AiOperationErrorDialog';
import { isAgentExecutorOverloaded } from '../services/agentCapacity';
import AgentWorkIndicator from './AgentWorkIndicator';
import LearningPlanCreateForm from './LearningPlanCreateForm';
import LearningPlanDraftPanel from './LearningPlanDraftPanel';
import LearningPlanTemplateCreatePanel from './LearningPlanTemplateCreatePanel';

type LearningPlanCreateState = 'editing' | 'generating' | 'collecting' | 'previewing' | 'confirming';
type LearningPlanCreateMode = 'ai' | 'template';

const pendingGenerationStorageKey = 'learning-plan.pending-generation';
const pendingRevisionStorageKey = 'learning-plan.pending-revision';
const initialGenerationAfter = '0-0';
const generationEventNames = new Set(['work_start', 'work_progress', 'work_tool_start', 'work_tool_end', 'draft_completed', 'draft_failed']);
const revisionEventNames = new Set(['work_start', 'work_progress', 'revision_completed', 'revision_failed', 'revision_superseded']);

interface PendingGeneration {
  draftId: number;
  idempotencyKey: string;
  lastEventId: string;
}

interface PendingRevision {
  draftId: number;
  revisionId: number;
  idempotencyKey: string;
  lastEventId: string;
  protocolVersion: number;
}

interface AiOperationError {
  message: string;
  reason?: string;
}

function isGenerationSubscription(
  response: LearningPlanDraftGenerationStartResponse,
): response is LearningPlanDraftGenerationResponse {
  return response.status === 'GENERATING' && 'eventsUrl' in response;
}

function isContinuousCursor(value: unknown): value is string {
  return typeof value === 'string' && /^(?:0|[1-9][0-9]*)-0$/.test(value);
}

function hasCursorGap(previous: string, next: string): boolean {
  return Number(next.slice(0, -2)) > Number(previous.slice(0, -2)) + 1;
}

function generationEventsUrl(draftId: number): string {
  return `/api/learning-plans/drafts/${draftId}/events`;
}

function newIdempotencyKey(): string {
  return globalThis.crypto?.randomUUID?.()
    ?? `learning-plan-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

interface LearningPlanCreatePageProps {
  onBackToPlans: () => void;
  onSaved: (confirmed: LearningPlanConfirmResponse) => void;
  capabilities?: LearningPlanAiRevisionCapabilities;
}

export default function LearningPlanCreatePage({ onBackToPlans, onSaved, capabilities }: LearningPlanCreatePageProps) {
  const { resources } = useI18n();
  const [formKey, setFormKey] = useState(0);
  const [draft, setDraft] = useState<LearningPlanDraftResponse>();
  const [workEvent, setWorkEvent] = useState<AgentWorkStatusEvent>();
  const [flowState, setFlowState] = useState<LearningPlanCreateState>('editing');
  const [createMode, setCreateMode] = useState<LearningPlanCreateMode>('template');
  const [error, setError] = useState('');
  const [capacityUnavailable, setCapacityUnavailable] = useState(false);
  const [operationError, setOperationError] = useState<AiOperationError>();
  const [revisionCompletedVersion, setRevisionCompletedVersion] = useState(0);
  const observerAbortRef = useRef<AbortController | undefined>(undefined);
  const observerRetryRef = useRef<number | undefined>(undefined);
  const observerVersionRef = useRef(0);
  const lastEventIdRef = useRef(initialGenerationAfter);
  const activeIdempotencyKeyRef = useRef<string | undefined>(undefined);
  const revisionObserverAbortRef = useRef<AbortController | undefined>(undefined);
  const revisionObserverRetryRef = useRef<number | undefined>(undefined);
  const revisionObserverVersionRef = useRef(0);
  const revisionLastEventIdRef = useRef(initialGenerationAfter);

  function showCapacityUnavailable() {
    setError('');
    setCapacityUnavailable(true);
  }

  function showAiOperationError(nextError: unknown, fallbackMessage: string) {
    if (isAgentExecutorOverloaded(nextError)) {
      showCapacityUnavailable();
      return;
    }
    const errorEvent = nextError as LearningPlanDraftErrorEvent | undefined;
    setError('');
    setOperationError({
      message: errorEvent?.message || (nextError instanceof Error ? nextError.message : fallbackMessage),
      reason: errorEvent?.reason,
    });
  }

  function clearObserver() {
    observerVersionRef.current += 1;
    observerAbortRef.current?.abort();
    observerAbortRef.current = undefined;
    if (observerRetryRef.current !== undefined) {
      window.clearTimeout(observerRetryRef.current);
      observerRetryRef.current = undefined;
    }
  }

  function clearRevisionObserver() {
    revisionObserverVersionRef.current += 1;
    revisionObserverAbortRef.current?.abort();
    revisionObserverAbortRef.current = undefined;
    if (revisionObserverRetryRef.current !== undefined) {
      window.clearTimeout(revisionObserverRetryRef.current);
      revisionObserverRetryRef.current = undefined;
    }
  }

  function pendingGeneration(): PendingGeneration | undefined {
    try {
      const raw = window.sessionStorage.getItem(pendingGenerationStorageKey);
      if (!raw) {
        return undefined;
      }
      const value = JSON.parse(raw) as Partial<PendingGeneration>;
      if (typeof value.draftId !== 'number' || value.draftId < 1
          || typeof value.idempotencyKey !== 'string' || !value.idempotencyKey
          || !isContinuousCursor(value.lastEventId)) {
        window.sessionStorage.removeItem(pendingGenerationStorageKey);
        return undefined;
      }
      return value as PendingGeneration;
    } catch {
      return undefined;
    }
  }

  function pendingRevision(): PendingRevision | undefined {
    try {
      const raw = window.sessionStorage.getItem(pendingRevisionStorageKey);
      if (!raw) {
        return undefined;
      }
      const value = JSON.parse(raw) as Partial<PendingRevision>;
      if (typeof value.draftId !== 'number' || value.draftId < 1
          || typeof value.revisionId !== 'number' || value.revisionId < 1
          || typeof value.idempotencyKey !== 'string' || !value.idempotencyKey
          || !isContinuousCursor(value.lastEventId) || value.protocolVersion !== 1) {
        window.sessionStorage.removeItem(pendingRevisionStorageKey);
        return undefined;
      }
      return value as PendingRevision;
    } catch {
      window.sessionStorage.removeItem(pendingRevisionStorageKey);
      return undefined;
    }
  }

  function savePendingGeneration(draftId: number, idempotencyKey: string, lastEventId: string) {
    window.sessionStorage.setItem(pendingGenerationStorageKey, JSON.stringify({ draftId, idempotencyKey, lastEventId }));
  }

  function clearPendingGeneration() {
    activeIdempotencyKeyRef.current = undefined;
    window.sessionStorage.removeItem(pendingGenerationStorageKey);
  }

  function savePendingRevision(pending: PendingRevision) {
    window.sessionStorage.setItem(pendingRevisionStorageKey, JSON.stringify(pending));
  }

  function clearPendingRevision() {
    window.sessionStorage.removeItem(pendingRevisionStorageKey);
  }

  function revisionEventsUrl(draftId: number, revisionId: number) {
    return `/api/learning-plans/drafts/${draftId}/revisions/${revisionId}/events`;
  }

  function updateCursor(draftId: number, idempotencyKey: string, nextCursor: string) {
    lastEventIdRef.current = nextCursor;
    savePendingGeneration(draftId, idempotencyKey, nextCursor);
  }

  function updateRevisionCursor(pending: PendingRevision, nextCursor: string) {
    revisionLastEventIdRef.current = nextCursor;
    savePendingRevision({ ...pending, lastEventId: nextCursor });
  }

  function completeFromDraft(nextDraft: LearningPlanDraftResponse) {
    clearObserver();
    clearPendingGeneration();
    setWorkEvent(undefined);
    if (nextDraft.status === 'GENERATED') {
      setDraft(nextDraft);
      setFlowState('previewing');
      return;
    }
    if (nextDraft.status === 'COLLECTING') {
      setDraft(nextDraft);
      setFlowState('collecting');
      return;
    }
    setDraft(undefined);
    setFlowState('editing');
    showAiOperationError({
      code: nextDraft.generationErrorCode,
      message: nextDraft.generationErrorMessage ?? nextDraft.assistantMessage,
    }, resources.learningPlans.generateFailed);
  }

  async function recoverRevision(
      draftId: number,
      revisionId: number,
      eventsUrl: string,
      observerVersion: number) {
    try {
      const revision = requireApiData(
        await getLearningPlanDraftRevision(draftId, revisionId),
        resources.learningPlans.revisionFailed,
      );
      if (observerVersion !== revisionObserverVersionRef.current) {
        return;
      }
      if (revision.status === 'READY') {
        const nextDraft = requireApiData(await getLearningPlanDraft(draftId), resources.learningPlans.revisionFailed);
        if (observerVersion !== revisionObserverVersionRef.current) {
          return;
        }
        clearRevisionObserver();
        clearPendingRevision();
        setRevisionCompletedVersion((current) => current + 1);
        setDraft(nextDraft);
        setWorkEvent(undefined);
        setFlowState('previewing');
        return;
      }
      if (revision.status === 'FAILED') {
        clearRevisionObserver();
        clearPendingRevision();
        setWorkEvent(undefined);
        setFlowState('previewing');
        showAiOperationError({ message: revision.errorMessage }, resources.learningPlans.revisionFailed);
        return;
      }
      if (revision.status === 'SUPERSEDED') {
        clearRevisionObserver();
        clearPendingRevision();
        try {
          const nextDraft = requireApiData(await getLearningPlanDraft(draftId), resources.learningPlans.revisionFailed);
          setDraft(nextDraft);
        } catch {
          // SUPERSEDED 的 revision 状态仍是权威结果；草案回读失败保留当前预览。
        }
        setWorkEvent(undefined);
        setFlowState('previewing');
        showAiOperationError({ message: revision.errorMessage }, resources.learningPlans.revisionFailed);
        return;
      }
      if (revision.status !== 'GENERATING') {
        clearRevisionObserver();
        clearPendingRevision();
        setWorkEvent(undefined);
        setFlowState('previewing');
        return;
      }
    } catch {
      if (observerVersion !== revisionObserverVersionRef.current) {
        return;
      }
    }
    if (observerVersion === revisionObserverVersionRef.current) {
      revisionObserverRetryRef.current = window.setTimeout(() => {
        observeRevision(draftId, revisionId, eventsUrl, revisionLastEventIdRef.current);
      }, 1000);
    }
  }

  function observeRevision(draftId: number, revisionId: number, eventsUrl: string, after: string) {
    clearRevisionObserver();
    const observerVersion = ++revisionObserverVersionRef.current;
    const controller = new AbortController();
    revisionObserverAbortRef.current = controller;
    let recoveryRequested = false;
    void readLearningPlanDraftRevisionEvents(eventsUrl, {
      after,
      signal: controller.signal,
      onEvent: (event) => {
        if (observerVersion !== revisionObserverVersionRef.current) {
          return;
        }
        const pending = pendingRevision();
        const data = event.data as Partial<LearningPlanDraftRevisionEventData>;
        if (!pending || pending.draftId !== draftId || pending.revisionId !== revisionId
            || !revisionEventNames.has(event.eventName) || !event.id || !isContinuousCursor(event.id)
            || data.draftId !== draftId || data.revisionId !== revisionId
            || hasCursorGap(revisionLastEventIdRef.current, event.id)) {
          recoveryRequested = true;
          controller.abort();
          void recoverRevision(draftId, revisionId, eventsUrl, observerVersion);
          return;
        }
        updateRevisionCursor(pending, event.id);
        if (event.eventName === 'work_start' || event.eventName === 'work_progress') {
          setWorkEvent(data);
          return;
        }
        recoveryRequested = true;
        void recoverRevision(draftId, revisionId, eventsUrl, observerVersion);
      },
    }).then(
      () => {
        if (!recoveryRequested) {
          void recoverRevision(draftId, revisionId, eventsUrl, observerVersion);
        }
      },
      () => {
        if (!recoveryRequested) {
          void recoverRevision(draftId, revisionId, eventsUrl, observerVersion);
        }
      },
    );
  }

  async function recoverGeneration(draftId: number, eventsUrl: string, observerVersion: number) {
    try {
      const nextDraft = requireApiData(
        await getLearningPlanDraft(draftId),
        resources.learningPlans.generateFailed,
      );
      if (observerVersion !== observerVersionRef.current) {
        return;
      }
      if (nextDraft.status !== 'GENERATING') {
        completeFromDraft(nextDraft);
        return;
      }
    } catch {
      if (observerVersion !== observerVersionRef.current) {
        return;
      }
    }
    if (observerVersion !== observerVersionRef.current) {
      return;
    }
    observerRetryRef.current = window.setTimeout(() => {
      observeGeneration(draftId, eventsUrl, lastEventIdRef.current);
    }, 1000);
  }

  function observeGeneration(draftId: number, eventsUrl: string, after: string) {
    clearObserver();
    const observerVersion = ++observerVersionRef.current;
    const controller = new AbortController();
    observerAbortRef.current = controller;
    let recoveryRequested = false;
    void readLearningPlanDraftGenerationEvents(eventsUrl, {
      after,
      signal: controller.signal,
      onEvent: (event) => {
        if (observerVersion !== observerVersionRef.current) {
          return;
        }
        const pending = pendingGeneration();
        const data = event.data as Partial<LearningPlanDraftGenerationEventData>;
        if (!pending || pending.draftId !== draftId || !generationEventNames.has(event.eventName)
            || !event.id || !isContinuousCursor(event.id) || data.draftId !== draftId
            || hasCursorGap(lastEventIdRef.current, event.id)) {
          recoveryRequested = true;
          controller.abort();
          void recoverGeneration(draftId, eventsUrl, observerVersion);
          return;
        }
        updateCursor(draftId, pending.idempotencyKey, event.id);
        if (event.eventName.startsWith('work_')) {
          setWorkEvent(data);
          return;
        }
        recoveryRequested = true;
        void recoverGeneration(draftId, eventsUrl, observerVersion);
      },
    }).then(
      () => {
        if (!recoveryRequested) {
          void recoverGeneration(draftId, eventsUrl, observerVersion);
        }
      },
      () => {
        if (!recoveryRequested) {
          void recoverGeneration(draftId, eventsUrl, observerVersion);
        }
      },
    );
  }

  useEffect(() => {
    const generation = pendingGeneration();
    if (generation) {
      lastEventIdRef.current = generation.lastEventId;
      activeIdempotencyKeyRef.current = generation.idempotencyKey;
      setFlowState('generating');
      setWorkEvent({ message: resources.learningPlans.generateStart });
      observeGeneration(generation.draftId, generationEventsUrl(generation.draftId), generation.lastEventId);
    }
    const revision = pendingRevision();
    if (!generation && revision) {
      revisionLastEventIdRef.current = revision.lastEventId;
      setFlowState('generating');
      setWorkEvent({ message: resources.learningPlans.reviseDraft });
      void getLearningPlanDraft(revision.draftId).then((response) => {
        const restored = requireApiData(response, resources.learningPlans.revisionFailed);
        setDraft(restored);
        observeRevision(
          revision.draftId,
          revision.revisionId,
          revisionEventsUrl(revision.draftId, revision.revisionId),
          revision.lastEventId,
        );
      }).catch(() => {
        void recoverRevision(
          revision.draftId,
          revision.revisionId,
          revisionEventsUrl(revision.draftId, revision.revisionId),
          revisionObserverVersionRef.current,
        );
      });
    }
    return () => {
      clearObserver();
      clearRevisionObserver();
    };
  }, []);

  async function submitDraft(request: LearningPlanCreateDraftRequest) {
    setFlowState('generating');
    setError('');
    setOperationError(undefined);
    setDraft(undefined);
    setWorkEvent({ message: resources.learningPlans.generateStart });
    const idempotencyKey = activeIdempotencyKeyRef.current ?? newIdempotencyKey();
    activeIdempotencyKeyRef.current = idempotencyKey;
    try {
      const result = await startLearningPlanDraftGeneration(request, { idempotencyKey });
      if (isGenerationSubscription(result)) {
        lastEventIdRef.current = result.initialAfter;
        savePendingGeneration(result.draftId, idempotencyKey, result.initialAfter);
        observeGeneration(result.draftId, result.eventsUrl, result.initialAfter);
        return;
      }
      completeFromDraft(result);
    } catch (nextError) {
      showAiOperationError(nextError, resources.learningPlans.generateFailed);
      setFlowState('editing');
    }
  }

  async function submitTemplateDraft(request: LearningPlanTemplateDraftRequest) {
    setFlowState('generating');
    setError('');
    setOperationError(undefined);
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
        showAiOperationError(nextWorkEvent, resources.learningPlans.generateFailed);
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
    if (event.eventName === 'draft_error') {
      const draftError = event.data as LearningPlanDraftErrorEvent;
      showAiOperationError(draftError, resources.learningPlans.generateFailed);
      setWorkEvent(undefined);
      setFlowState('editing');
      return;
    }
    if (event.eventName === 'draft_revision_error') {
      const draftError = event.data as LearningPlanDraftErrorEvent;
      showAiOperationError(draftError, resources.learningPlans.revisionFailed);
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
    setOperationError(undefined);
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
    setOperationError(undefined);
    setWorkEvent({ message: resources.learningPlans.reviseDraft });
    try {
      const idempotencyKey = newIdempotencyKey();
      const started = await startLearningPlanDraftRevisionGeneration(draft.draftId, {
        instruction: instruction.trim(),
      }, {
        idempotencyKey,
      });
      const pending: PendingRevision = {
        draftId: started.draftId,
        revisionId: started.revisionId,
        idempotencyKey,
        lastEventId: started.initialAfter,
        protocolVersion: started.realtimeProtocolVersion,
      };
      revisionLastEventIdRef.current = started.initialAfter;
      savePendingRevision(pending);
      observeRevision(started.draftId, started.revisionId, started.eventsUrl, started.initialAfter);
      return false;
    } catch (nextError) {
      showAiOperationError(nextError, resources.learningPlans.revisionFailed);
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
    setOperationError(undefined);
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
    setOperationError(undefined);
    setFlowState('editing');
    setFormKey((current) => current + 1);
  }

  function handleBack() {
    if (draft) {
      retryCreateDraft();
      return;
    }
    onBackToPlans();
  }

  return (
    <>
      <section className="learning-shell learning-create-shell" aria-label={resources.learningPlans.createAriaLabel}>
      <div className={`learning-create-content${draft ? ' learning-create-content--preview' : ''}`}>
        <div className="learning-create-heading">
          <button
            aria-label={draft ? resources.learningPlans.backToCreate : resources.learningPlans.backToPlans}
            className="icon-button learning-create-back"
            disabled={flowState === 'generating' || flowState === 'confirming'}
            onClick={handleBack}
            type="button"
          >
            <ArrowLeft aria-hidden="true" />
          </button>
          <h1>{resources.learningPlans.newPlan}</h1>
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
              revisionCompletedVersion={revisionCompletedVersion}
              onSendFollowUp={sendFollowUp}
              capabilities={capabilities}
            />
          </>
        ) : (
          <article className="learning-panel create-plan-page-panel">
            <section className="question-block">
              <strong>{resources.learningPlans.createMode}</strong>
              <div className="segmented-grid create-mode-switch">
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
                  <LayoutTemplate aria-hidden="true" />
                  <span>{resources.learningPlans.createFromTemplate}</span>
                </button>
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
                  <Sparkles aria-hidden="true" />
                  <span>{resources.learningPlans.createWithAi}</span>
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
      </div>
      </section>
      <AiCapacityUnavailableDialog
        onClose={() => setCapacityUnavailable(false)}
        open={capacityUnavailable}
      />
      <AiOperationErrorDialog
        message={operationError?.message ?? ''}
        onClose={() => setOperationError(undefined)}
        open={Boolean(operationError)}
        reason={operationError?.reason}
      />
    </>
  );
}
