import { ArrowLeft } from 'lucide-react';
import { useState } from 'react';
import { formatPlanIntent } from '../i18n/formatters';
import { useI18n } from '../i18n/I18nProvider';
import AiCapacityUnavailableDialog from '../components/AiCapacityUnavailableDialog';
import AiOperationErrorDialog from '../components/AiOperationErrorDialog';
import { isAgentExecutorOverloaded } from '../services/agentCapacity';
import {
  applyLearningPlanExtensionProposal,
  discardLearningPlanExtensionProposal,
  requireApiData,
  requireApiSuccess,
  streamLearningPlanExtensionProposal,
  streamLearningPlanExtensionProposalRevision,
} from '../services/api';
import type {
  AgentWorkStatusEvent,
  LearningPlanDetailResponse,
  LearningPlanDraftErrorEvent,
  LearningPlanExtensionReadyEvent,
  LearningPlanAiRevisionCapabilities,
  SseStreamEvent,
} from '../types/api';
import AgentWorkIndicator from './AgentWorkIndicator';
import LearningPlanExtensionPanel from './LearningPlanExtensionPanel';
import { PlanPhaseDetails } from './PlanPreview';

interface AiOperationError {
  message: string;
  reason?: string;
}

export default function LearningPlanDetail({
  onBack,
  onPlanUpdated,
  onProblemSelect,
  plan,
  capabilities,
}: {
  onBack: () => void;
  onPlanUpdated: () => Promise<void>;
  onProblemSelect: (phaseIndex: number, problemSlug: string) => void;
  plan: LearningPlanDetailResponse;
  capabilities?: LearningPlanAiRevisionCapabilities;
}) {
  const { resources } = useI18n();
  const [extension, setExtension] = useState<LearningPlanExtensionReadyEvent>();
  const [extensionWorkEvent, setExtensionWorkEvent] = useState<AgentWorkStatusEvent>();
  const [extensionLoading, setExtensionLoading] = useState(false);
  const [extensionError, setExtensionError] = useState('');
  const [capacityUnavailable, setCapacityUnavailable] = useState(false);
  const [operationError, setOperationError] = useState<AiOperationError>();

  function capacityUnavailableMessage() {
    setCapacityUnavailable(true);
  }

  function showAiOperationError(nextError: unknown, fallbackMessage: string) {
    if (isAgentExecutorOverloaded(nextError)) {
      setExtensionError('');
      capacityUnavailableMessage();
      return '';
    }
    const errorEvent = nextError as LearningPlanDraftErrorEvent | undefined;
    const message = errorEvent?.message || (nextError instanceof Error ? nextError.message : fallbackMessage);
    setExtensionError('');
    setOperationError({ message, reason: errorEvent?.reason });
    return message;
  }

  function handleExtensionStreamEvent(event: SseStreamEvent) {
    if (event.eventName.startsWith('work_')) {
      const nextWorkEvent = event.data as AgentWorkStatusEvent;
      setExtensionWorkEvent(nextWorkEvent);
      if (event.eventName === 'work_error') {
        showAiOperationError(nextWorkEvent, resources.learningPlans.extensionFailed);
      }
      return;
    }
    if (event.eventName === 'plan_extension_ready') {
      setExtension(event.data as LearningPlanExtensionReadyEvent);
      setExtensionError('');
      setExtensionWorkEvent(undefined);
      setExtensionLoading(false);
      return;
    }
    if (event.eventName === 'plan_extension_error') {
      const extensionStreamError = event.data as LearningPlanDraftErrorEvent;
      showAiOperationError(extensionStreamError, resources.learningPlans.extensionFailed);
      setExtensionWorkEvent(undefined);
      setExtensionLoading(false);
    }
  }

  async function generateExtension(instruction: string) {
    if (!instruction.trim()) {
      return false;
    }
    setExtensionLoading(true);
    setExtensionError('');
    setOperationError(undefined);
    setExtensionWorkEvent({ message: resources.learningPlans.generateExtension });
    try {
      let extensionReady = false;
      let extensionFailed = false;
      await streamLearningPlanExtensionProposal(plan.id, { instruction: instruction.trim() }, {
        onEvent: (event) => {
          if (event.eventName === 'plan_extension_ready') {
            extensionReady = true;
          }
          if (event.eventName === 'plan_extension_error') {
            extensionFailed = true;
          }
          if (event.eventName === 'work_error') {
            extensionFailed = true;
          }
          handleExtensionStreamEvent(event);
        },
      });
      if (!extensionReady && !extensionFailed) {
        setExtensionError(resources.learningPlans.extensionFailed);
        setExtensionWorkEvent(undefined);
        setExtensionLoading(false);
        return false;
      }
      if (!extensionReady) {
        setExtensionWorkEvent(undefined);
        setExtensionLoading(false);
      }
      return extensionReady && !extensionFailed;
    } catch (nextError) {
      showAiOperationError(nextError, resources.learningPlans.extensionFailed);
      setExtensionWorkEvent(undefined);
      setExtensionLoading(false);
      return false;
    }
  }

  async function reviseExtension(proposalGroupId: number, instruction: string) {
    if (!instruction.trim()) {
      return false;
    }
    setExtensionLoading(true);
    setExtensionError('');
    setOperationError(undefined);
    setExtensionWorkEvent({ message: resources.learningPlans.reviseExtension });
    try {
      let extensionReady = false;
      let extensionFailed = false;
      await streamLearningPlanExtensionProposalRevision(plan.id, proposalGroupId, { instruction: instruction.trim() }, {
        onEvent: (event) => {
          if (event.eventName === 'plan_extension_ready') {
            extensionReady = true;
          }
          if (event.eventName === 'plan_extension_error') {
            extensionFailed = true;
          }
          if (event.eventName === 'work_error') {
            extensionFailed = true;
          }
          handleExtensionStreamEvent(event);
        },
      });
      if (!extensionReady && !extensionFailed) {
        setExtensionError(resources.learningPlans.extensionFailed);
        setExtensionWorkEvent(undefined);
        setExtensionLoading(false);
        return false;
      }
      if (!extensionReady) {
        setExtensionWorkEvent(undefined);
        setExtensionLoading(false);
      }
      return extensionReady && !extensionFailed;
    } catch (nextError) {
      showAiOperationError(nextError, resources.learningPlans.extensionFailed);
      setExtensionWorkEvent(undefined);
      setExtensionLoading(false);
      return false;
    }
  }

  async function applyExtension(proposalGroupId: number) {
    setExtensionLoading(true);
    setExtensionError('');
    setOperationError(undefined);
    try {
      requireApiData(
        await applyLearningPlanExtensionProposal(plan.id, proposalGroupId),
        resources.learningPlans.extensionApplyFailed,
      );
      await onPlanUpdated();
      setExtension(undefined);
      setExtensionError('');
    } catch (nextError) {
      setExtensionError(nextError instanceof Error ? nextError.message : resources.learningPlans.extensionApplyFailed);
    } finally {
      setExtensionWorkEvent(undefined);
      setExtensionLoading(false);
    }
  }

  async function discardExtension(proposalGroupId: number) {
    setExtensionLoading(true);
    setExtensionError('');
    setOperationError(undefined);
    try {
      requireApiSuccess(
        await discardLearningPlanExtensionProposal(plan.id, proposalGroupId),
        resources.learningPlans.extensionFailed,
      );
      setExtension(undefined);
      setExtensionError('');
    } catch (nextError) {
      setExtensionError(nextError instanceof Error ? nextError.message : resources.learningPlans.extensionFailed);
    } finally {
      setExtensionWorkEvent(undefined);
      setExtensionLoading(false);
    }
  }

  return (
    <>
      <article className="learning-panel">
      <button className="secondary-button compact detail-back-button" onClick={onBack} type="button">
        <ArrowLeft aria-hidden="true" />
        <span>{resources.learningPlans.backToList}</span>
      </button>
      <div className="detail-heading">
        <div>
          <p className="eyebrow">{formatPlanIntent(plan.intent, resources)}</p>
          <h2>{plan.title}</h2>
          <p>{plan.summary}</p>
        </div>
        {plan.active ? (
          <span className="status-badge current-plan-badge">{resources.learningPlans.currentActive}</span>
        ) : null}
      </div>
      <PlanPhaseDetails
        onProblemSelect={onProblemSelect}
        plan={plan}
      />
      {(extensionLoading || extensionWorkEvent || extensionError) && (
        <AgentWorkIndicator active={extensionLoading} event={extensionWorkEvent} error={extensionError} />
      )}
      <LearningPlanExtensionPanel
        contentLocale={plan.contentLocale}
        extension={extension}
        loading={extensionLoading}
        onApply={applyExtension}
        onDiscard={discardExtension}
        onGenerate={generateExtension}
        onRevise={reviseExtension}
        capabilities={capabilities}
      />
      </article>
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
