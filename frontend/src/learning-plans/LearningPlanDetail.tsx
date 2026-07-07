import { ArrowLeft } from 'lucide-react';
import { useState } from 'react';
import { formatPlanIntent } from '../i18n/formatters';
import { useI18n } from '../i18n/I18nProvider';
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
  LearningPlanPaceStatus,
  SseStreamEvent,
} from '../types/api';
import AgentWorkIndicator from './AgentWorkIndicator';
import LearningPlanExtensionPanel from './LearningPlanExtensionPanel';
import PlanPreview from './PlanPreview';

export default function LearningPlanDetail({
  contractFeedback,
  onBack,
  onPlanUpdated,
  onProblemSelect,
  plan,
}: {
  contractFeedback?: string;
  onBack: () => void;
  onPlanUpdated: () => Promise<void>;
  onProblemSelect: (phaseIndex: number, problemSlug: string) => void;
  plan: LearningPlanDetailResponse;
}) {
  const { resources } = useI18n();
  const [extension, setExtension] = useState<LearningPlanExtensionReadyEvent>();
  const [extensionWorkEvent, setExtensionWorkEvent] = useState<AgentWorkStatusEvent>();
  const [extensionLoading, setExtensionLoading] = useState(false);
  const [extensionError, setExtensionError] = useState('');
  const pace = plan.paceSummary;
  const contract = plan.livingContractSummary;
  const nextPackage = contract?.nextTrainingPackage ?? plan.nextTrainingPackage;
  const nextProblem = nextPackage?.priorityProblemSlugs[0]
    ? findProblemPhase(plan, nextPackage.priorityProblemSlugs[0])
    : undefined;

  function handleExtensionStreamEvent(event: SseStreamEvent) {
    if (event.eventName.startsWith('work_')) {
      const nextWorkEvent = event.data as AgentWorkStatusEvent;
      setExtensionWorkEvent(nextWorkEvent);
      if (event.eventName === 'work_error') {
        setExtensionError(nextWorkEvent.message || resources.learningPlans.extensionFailed);
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
      setExtensionError(extensionStreamError.message || resources.learningPlans.extensionFailed);
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
    setExtensionWorkEvent({ message: resources.learningPlans.generateExtension });
    try {
      let extensionReady = false;
      let extensionFailed = false;
      let terminalErrorMessage = '';
      await streamLearningPlanExtensionProposal(plan.id, { instruction: instruction.trim() }, {
        onEvent: (event) => {
          if (event.eventName === 'plan_extension_ready') {
            extensionReady = true;
          }
          if (event.eventName === 'plan_extension_error') {
            extensionFailed = true;
            terminalErrorMessage = (event.data as LearningPlanDraftErrorEvent).message
              || resources.learningPlans.extensionFailed;
          }
          if (event.eventName === 'work_error') {
            extensionFailed = true;
            terminalErrorMessage = (event.data as AgentWorkStatusEvent).message
              || resources.learningPlans.extensionFailed;
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
      if (!extensionReady && terminalErrorMessage) {
        setExtensionError(terminalErrorMessage);
        setExtensionWorkEvent(undefined);
        setExtensionLoading(false);
      }
      return extensionReady && !extensionFailed;
    } catch (nextError) {
      setExtensionError(nextError instanceof Error ? nextError.message : resources.learningPlans.extensionFailed);
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
    setExtensionWorkEvent({ message: resources.learningPlans.reviseExtension });
    try {
      let extensionReady = false;
      let extensionFailed = false;
      let terminalErrorMessage = '';
      await streamLearningPlanExtensionProposalRevision(plan.id, proposalGroupId, { instruction: instruction.trim() }, {
        onEvent: (event) => {
          if (event.eventName === 'plan_extension_ready') {
            extensionReady = true;
          }
          if (event.eventName === 'plan_extension_error') {
            extensionFailed = true;
            terminalErrorMessage = (event.data as LearningPlanDraftErrorEvent).message
              || resources.learningPlans.extensionFailed;
          }
          if (event.eventName === 'work_error') {
            extensionFailed = true;
            terminalErrorMessage = (event.data as AgentWorkStatusEvent).message
              || resources.learningPlans.extensionFailed;
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
      if (!extensionReady && terminalErrorMessage) {
        setExtensionError(terminalErrorMessage);
        setExtensionWorkEvent(undefined);
        setExtensionLoading(false);
      }
      return extensionReady && !extensionFailed;
    } catch (nextError) {
      setExtensionError(nextError instanceof Error ? nextError.message : resources.learningPlans.extensionFailed);
      setExtensionWorkEvent(undefined);
      setExtensionLoading(false);
      return false;
    }
  }

  async function applyExtension(proposalGroupId: number) {
    setExtensionLoading(true);
    setExtensionError('');
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
      </div>
      {contract && (
        <section className={`living-contract-panel ${contract.visibleStatus.toLowerCase().replace('_', '-')}`}>
          <div className="living-contract-main">
            <div>
              <span>{resources.learningPlans.routeProgressTitle}</span>
              <strong>
                {resources.learningPlans.routeProgressLine(
                  contract.completedProblemCount,
                  contract.totalProblemCount,
                  contract.progressPercent,
                )}
              </strong>
            </div>
            <div>
              <span>{resources.learningPlans.visibleStatusLabels[contract.visibleStatus]}</span>
              <strong>
                {contract.estimatedCompletionDate
                  ? resources.learningPlans.estimatedCompletionDate(contract.estimatedCompletionDate)
                  : resources.learningPlans.unspecified}
              </strong>
            </div>
            <div>
              <span>{resources.learningPlans.openProblemsLine(contract.openProblemCount, contract.skippedProblemCount)}</span>
              <strong>{nextPackage ? resources.learningPlans.nextTrainingPackageLine(nextPackage.newProblemCount, nextPackage.estimatedMinutes) : '-'}</strong>
            </div>
          </div>
          {(contract.notice || contractFeedback) && (
            <p className="living-contract-notice">{contractFeedback || contract.notice}</p>
          )}
          {nextProblem && contract.visibleStatus !== 'COMPLETED' && contract.visibleStatus !== 'CLOSED_OUT' && (
            <button
              className="primary-button compact"
              onClick={() => onProblemSelect(nextProblem.phaseIndex, nextProblem.problemSlug)}
              type="button"
            >
              {resources.learningPlans.startNextTrainingPackage}
            </button>
          )}
          {contract.completionSummary && (
            <div className="completion-summary-panel">
              <strong>{resources.learningPlans.completionSummaryTitle}</strong>
              <span>
                {resources.learningPlans.completionSummaryLine(
                  contract.completionSummary.completionRate,
                  contract.completionSummary.totalDurationDays,
                  contract.completionSummary.completedProblemCount,
                  contract.completionSummary.skippedProblemCount,
                  contract.completionSummary.openProblemCount,
                )}
              </span>
              {contract.completionSummary.weakTags.length > 0 && (
                <span>
                  {resources.learningPlans.weakTagsLabel}：{contract.completionSummary.weakTags.join(' / ')}
                </span>
              )}
            </div>
          )}
        </section>
      )}
      {pace && (
        <section className={`pace-summary-panel ${pace.status.toLowerCase().replace('_', '-')}`}>
          <div>
            <span>{resources.learningPlans.paceTitle}</span>
            <strong>{resources.learningPlans.paceCurrentWeek(pace.currentWeek, pace.totalWeeks)}</strong>
          </div>
          <div>
            <span>{resources.learningPlans.paceStatusLabels[pace.status as LearningPlanPaceStatus]}</span>
            <strong>
              {resources.learningPlans.paceCurrentTarget(
                pace.currentBucket?.plannedProblemCount ?? 0,
                pace.currentBucket?.plannedLoadPoints ?? 0,
              )}
            </strong>
          </div>
          <div>
            <span>{resources.learningPlans.paceCurrentCompleted(pace.currentWeekCompletedProblemCount)}</span>
            <strong>{resources.learningPlans.paceLoadGap(pace.loadGapPoints)}</strong>
          </div>
          {pace.recommendation && <p>{pace.recommendation}</p>}
        </section>
      )}
      <PlanPreview onProblemSelect={onProblemSelect} plan={plan} />
      {(extensionLoading || extensionWorkEvent || extensionError) && (
        <AgentWorkIndicator active={extensionLoading} event={extensionWorkEvent} error={extensionError} />
      )}
      <LearningPlanExtensionPanel
        extension={extension}
        loading={extensionLoading}
        onApply={applyExtension}
        onDiscard={discardExtension}
        onGenerate={generateExtension}
        onRevise={reviseExtension}
      />
    </article>
  );
}

function findProblemPhase(plan: LearningPlanDetailResponse, problemSlug: string) {
  for (const phase of plan.phases) {
    if (phase.problems.some((problem) => problem.slug === problemSlug)) {
      return { phaseIndex: phase.phaseIndex, problemSlug };
    }
  }
  return undefined;
}
