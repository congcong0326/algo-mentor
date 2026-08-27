import { ArrowLeft, CheckCircle2, ClipboardList, ExternalLink, Info, Maximize2, Minimize2, MoreHorizontal, Save, SkipForward } from 'lucide-react';
import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import type { FormEvent, KeyboardEvent as ReactKeyboardEvent } from 'react';
import MarkdownView from '../components/MarkdownView';
import AiCapacityUnavailableDialog from '../components/AiCapacityUnavailableDialog';
import { utf8ByteLength, useUserInputLimits } from '../config/userInputLimits';
import { formatDifficulty, formatProblemTitle } from '../i18n/formatters';
import { useI18n } from '../i18n/I18nProvider';
import type { LocaleResources, SupportedLocale } from '../i18n/locales';
import {
  ApiRequestError,
  applyPracticeCoachSummaryProposal,
  createOrReusePracticeSession,
  getPracticeSession,
  getPracticeSessionActiveRun,
  getPracticeSessionMessages,
  getPracticeSessionReviews,
  requireApiData,
  startPracticeMessage,
  readPracticeRunEvents,
  updatePracticeProgressStatus,
} from '../services/api';
import { isAgentExecutorOverloaded } from '../services/agentCapacity';
import type {
  AgentToolEndEvent,
  AgentToolStartEvent,
  LearningPlanDetailResponse,
  LearningPlanProblemDraft,
  CoachSummaryProposalAction,
  PracticeCodeReviewHistoryResponse,
  PracticeMessage,
  PracticeProgressStatus,
  PracticeSessionResponse,
} from '../types/api';
import { AGENT_RUN_IN_PROGRESS_CODE } from '../types/api';
import {
  LEARNER_DECLARED_PROFILE_TOOL_NAME,
  learnerDeclaredProfileToolEventKey,
  parseLearnerDeclaredProfileToolResult,
  type LearnerDeclaredProfileToolDisplayStatus,
} from './profileToolContract';

const LEETCODE_HOST_BY_LOCALE: Record<SupportedLocale, string> = {
  'zh-CN': 'leetcode.cn',
  'en-US': 'leetcode.com',
};

const LEETCODE_HOSTS = new Set(['leetcode.cn', 'www.leetcode.cn', 'leetcode.com', 'www.leetcode.com']);
const AUTO_SCROLL_THRESHOLD_PX = 96;
const ACTIVE_RUN_POLL_INTERVAL_MS = 3000;
const RUN_STREAM_RECONNECT_ATTEMPTS = 2;
const LEARNER_PROFILE_TOOL_RUNNING_MIN_VISIBLE_MS = 700;
const COMPOSER_AUTO_RESIZE_MAX_HEIGHT_PX = 360;
const COMPLETION_SUCCESS_VISIBLE_MS = 900;
// 后端 SSE/tool result 公共契约，用于识别 Review tool 是否真实落库。
const REVIEW_TOOL_NAME = 'submit_practice_code_review';
const COACH_SUMMARY_PROPOSAL_TOOL_NAME = 'propose_current_problem_coach_summary';
const REVIEW_SUBMITTED_RESULT_TYPE = 'practice_code_review_submitted';
const COACH_SUMMARY_PROPOSAL_RESULT_TYPE = 'current_problem_coach_summary_proposed';

type CoachWorkStatus = 'ORGANIZING' | 'REVIEW_RUNNING' | LearnerDeclaredProfileToolDisplayStatus;
type CoachSummaryApplyStatus = 'applying' | 'error';

interface AssistantWorkState {
  status: CoachWorkStatus;
  startedAt?: number;
  terminalStatus?: Exclude<LearnerDeclaredProfileToolDisplayStatus, 'RUNNING'>;
  toolCallKey?: string;
}

interface PracticeRunStreamState {
  sessionId: number;
  runUuid: string;
  eventsUrl: string;
  lastEventId: string;
  assistantMessageId: number;
  realtimeProtocolVersion: 1 | 2;
  nextExpectedSequence: number;
  realtimeIncomplete: boolean;
  successfulRunEndReceived: boolean;
  terminalErrorReceived: boolean;
  terminalErrorMessage?: string;
  currentStepIndex?: number;
  toolExecutionStepIndex?: number;
  activeToolCallIds: Set<string>;
  nextExpectedStepIndex: number;
  stepBuffers: Map<number, string>;
  confirmedAssistantContent: string;
}

class PracticeRealtimeIncompleteError extends Error {
  constructor() {
    super('Practice realtime stream is incomplete');
    this.name = 'PracticeRealtimeIncompleteError';
  }
}

function isPracticeRealtimeV2(runState: PracticeRunStreamState): boolean {
  return runState.realtimeProtocolVersion === 2;
}

function readAgentErrorMessage(value: unknown): string | undefined {
  if (!isRecord(value) || typeof value.message !== 'string' || !value.message.trim()) {
    return undefined;
  }
  return value.message;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function hasOnlyFields(value: Record<string, unknown>, fields: readonly string[]): boolean {
  return Object.keys(value).every((key) => fields.includes(key));
}

function isPositiveInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value > 0;
}

function isNonNegativeInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value >= 0;
}

function isPublicPracticeToolName(value: unknown): value is string {
  return value === REVIEW_TOOL_NAME
    || value === COACH_SUMMARY_PROPOSAL_TOOL_NAME
    || value === LEARNER_DECLARED_PROFILE_TOOL_NAME;
}

function isPublicToolResult(toolName: string, value: unknown): boolean {
  if (!isRecord(value)) {
    return false;
  }
  const fieldsByTool: Record<string, readonly string[]> = {
    [REVIEW_TOOL_NAME]: ['type', 'status', 'totalScore', 'passed', 'failureCode'],
    [COACH_SUMMARY_PROPOSAL_TOOL_NAME]: ['type', 'status', 'proposalId', 'summaryMarkdown', 'operation'],
    [LEARNER_DECLARED_PROFILE_TOOL_NAME]: ['type', 'status'],
  };
  if (!hasOnlyFields(value, fieldsByTool[toolName] ?? [])
    || typeof value.type !== 'string'
    || typeof value.status !== 'string') {
    return false;
  }
  if (toolName === REVIEW_TOOL_NAME) {
    return (!('totalScore' in value) || (typeof value.totalScore === 'number' && Number.isFinite(value.totalScore)))
      && (!('passed' in value) || typeof value.passed === 'boolean')
      && (!('failureCode' in value) || typeof value.failureCode === 'string');
  }
  if (toolName === COACH_SUMMARY_PROPOSAL_TOOL_NAME) {
    return (!('proposalId' in value) || typeof value.proposalId === 'string')
      && (!('summaryMarkdown' in value) || typeof value.summaryMarkdown === 'string')
      && (!('operation' in value) || typeof value.operation === 'string');
  }
  return true;
}

function assertValidPracticeV2Event(runState: PracticeRunStreamState, event: import('../types/api').SseStreamEvent) {
  const expectedId = `${runState.nextExpectedSequence}-0`;
  if (event.id !== expectedId || !isRecord(event.data)) {
    throw new PracticeRealtimeIncompleteError();
  }

  const data = event.data;
  const validRunId = data.runId === runState.runUuid;
  let valid = false;
  if (event.eventName === 'content_delta') {
    valid = hasOnlyFields(data, ['content']) && typeof data.content === 'string' && runState.currentStepIndex !== undefined;
  } else if (event.eventName === 'agent_step_start') {
    valid = hasOnlyFields(data, ['runId', 'stepIndex']) && validRunId && isPositiveInteger(data.stepIndex);
  } else if (event.eventName === 'agent_step_end') {
    valid = hasOnlyFields(data, ['runId', 'stepIndex', 'finishReason', 'toolCallCount'])
      && validRunId
      && isPositiveInteger(data.stepIndex)
      && typeof data.finishReason === 'string'
      && isNonNegativeInteger(data.toolCallCount);
  } else if (event.eventName === 'agent_tool_start') {
    valid = hasOnlyFields(data, ['runId', 'stepIndex', 'toolCallId', 'toolName'])
      && validRunId
      && isPositiveInteger(data.stepIndex)
      && typeof data.toolCallId === 'string'
      && isPublicPracticeToolName(data.toolName);
  } else if (event.eventName === 'agent_tool_end') {
    valid = hasOnlyFields(data, ['runId', 'stepIndex', 'toolCallId', 'toolName', 'result'])
      && validRunId
      && isPositiveInteger(data.stepIndex)
      && typeof data.toolCallId === 'string'
      && isPublicPracticeToolName(data.toolName)
      && isPublicToolResult(data.toolName, data.result);
  } else if (event.eventName === 'agent_run_end') {
    valid = hasOnlyFields(data, ['runId', 'steps', 'finishReason'])
      && validRunId
      && isPositiveInteger(data.steps)
      && typeof data.finishReason === 'string';
  } else if (event.eventName === 'agent_error') {
    valid = hasOnlyFields(data, ['runId', 'code', 'message', 'retryable'])
      && validRunId
      && typeof data.code === 'string'
      && typeof data.message === 'string'
      && typeof data.retryable === 'boolean';
  }

  if (!valid) {
    throw new PracticeRealtimeIncompleteError();
  }
}

function problemLabel(problem: LearningPlanProblemDraft | undefined, locale: SupportedLocale, fallback: string): string {
  if (!problem) {
    return fallback;
  }
  const id = problem.frontendId ? `${problem.frontendId}. ` : '';
  return `${id}${formatProblemTitle(problem, locale)}`;
}

function practiceProblemLabel(
  sessionResponse: PracticeSessionResponse | undefined,
  problem: LearningPlanProblemDraft | undefined,
  locale: SupportedLocale,
  fallback: string,
): string {
  const sessionProblem = sessionResponse?.problem;
  if (sessionProblem) {
    const id = sessionProblem.frontendId ? `${sessionProblem.frontendId}. ` : '';
    return `${id}${formatProblemTitle(sessionProblem, locale)}`;
  }
  return problemLabel(problem, locale, fallback);
}

function localizedLeetCodeUrl(leetcodeUrl: string | undefined, locale: SupportedLocale): string | undefined {
  if (!leetcodeUrl) {
    return undefined;
  }

  try {
    const url = new URL(leetcodeUrl);
    if (!LEETCODE_HOSTS.has(url.hostname)) {
      return leetcodeUrl;
    }
    url.protocol = 'https:';
    url.hostname = LEETCODE_HOST_BY_LOCALE[locale];
    return url.toString();
  } catch {
    return leetcodeUrl;
  }
}

function progressStatusLabel(status: PracticeProgressStatus | undefined, resources: LocaleResources): string {
  const labels: Record<PracticeProgressStatus, string> = {
    NOT_STARTED: resources.learningPlans.notStarted,
    IN_PROGRESS: resources.learningPlans.inProgress,
    COMPLETED: resources.learningPlans.completed,
    SKIPPED: resources.learningPlans.skipped,
  };

  return labels[status ?? 'NOT_STARTED'];
}


function readContentDelta(data: unknown): string {
  if (typeof data !== 'object' || data === null || !('content' in data)) {
    return '';
  }

  const content = (data as { content?: unknown }).content;
  return typeof content === 'string' ? content : '';
}

function readStringField(source: Record<string, unknown>, key: string): string | undefined {
  const value = source[key];
  return typeof value === 'string' && value.trim() ? value : undefined;
}

function readNumberField(source: Record<string, unknown>, key: string): number | undefined {
  const value = source[key];
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
}

function readBooleanField(source: Record<string, unknown>, key: string): boolean | undefined {
  const value = source[key];
  return typeof value === 'boolean' ? value : undefined;
}


function readCoachSummaryProposalAction(result: unknown, createdAt: string): CoachSummaryProposalAction | undefined {
  if (typeof result !== 'object' || result === null) {
    return undefined;
  }
  const value = result as Record<string, unknown>;
  const proposalId = readStringField(value, 'proposalId');
  const summaryMarkdown = readStringField(value, 'summaryMarkdown');
  const operation = readStringField(value, 'operation');
  if (readStringField(value, 'type') !== COACH_SUMMARY_PROPOSAL_RESULT_TYPE
    || readStringField(value, 'status') !== 'PROPOSED'
    || !proposalId
    || !summaryMarkdown
    || (operation !== 'CREATE' && operation !== 'REPLACE')) {
    return undefined;
  }
  return {
    proposalId,
    status: 'PENDING',
    operation,
    createdAt,
  };
}

function readCoachSummaryMarkdown(result: unknown): string | undefined {
  return typeof result === 'object' && result !== null
    ? readStringField(result as Record<string, unknown>, 'summaryMarkdown')
    : undefined;
}


function readAgentToolEndEvent(data: unknown): AgentToolEndEvent | undefined {
  if (typeof data !== 'object' || data === null) {
    return undefined;
  }

  const event = data as Record<string, unknown>;
  const runId = readStringField(event, 'runId');
  const stepIndex = readNumberField(event, 'stepIndex');
  const toolCallId = readStringField(event, 'toolCallId');
  const toolName = readStringField(event, 'toolName');

  if (!runId || stepIndex === undefined || !toolCallId || !toolName || !('result' in event)) {
    return undefined;
  }

  return {
    runId,
    stepIndex,
    toolCallId,
    toolName,
    result: event.result,
  };
}

function readAgentToolStartEvent(data: unknown): AgentToolStartEvent | undefined {
  if (typeof data !== 'object' || data === null) {
    return undefined;
  }

  const event = data as Record<string, unknown>;
  const runId = readStringField(event, 'runId');
  const stepIndex = readNumberField(event, 'stepIndex');
  const toolCallId = readStringField(event, 'toolCallId');
  const toolName = readStringField(event, 'toolName');

  if (!runId || stepIndex === undefined || !toolCallId || !toolName) {
    return undefined;
  }

  return {
    runId,
    stepIndex,
    toolCallId,
    toolName,
  };
}

function readResultType(result: unknown): string | undefined {
  if (typeof result !== 'object' || result === null || !('type' in result)) {
    return undefined;
  }

  const type = (result as { type?: unknown }).type;
  return typeof type === 'string' && type.trim() ? type : undefined;
}

function isSavedReviewResult(result: unknown): boolean {
  return typeof result === 'object'
    && result !== null
    && 'status' in result
    && result.status === 'SAVED';
}

function readResultPassed(result: unknown): boolean | undefined {
  if (typeof result !== 'object' || result === null || !('passed' in result)) {
    return undefined;
  }

  const passed = (result as { passed?: unknown }).passed;
  if (typeof passed === 'boolean') {
    return passed;
  }
  if (typeof passed === 'string') {
    if (passed.toLowerCase() === 'true') {
      return true;
    }
    if (passed.toLowerCase() === 'false') {
      return false;
    }
  }
  return undefined;
}

function readResultScore(result: unknown): number | undefined {
  if (!isRecord(result) || !('totalScore' in result)) {
    return undefined;
  }
  const totalScore = result.totalScore;
  if (typeof totalScore === 'number' && Number.isFinite(totalScore)) {
    return totalScore;
  }
  if (typeof totalScore === 'string' && totalScore.trim()) {
    const parsed = Number(totalScore);
    return Number.isFinite(parsed) ? parsed : undefined;
  }
  return undefined;
}

function learnerProfileToolStatusLabel(
  status: LearnerDeclaredProfileToolDisplayStatus,
  resources: LocaleResources,
): string {
  const labels: Record<LearnerDeclaredProfileToolDisplayStatus, string> = {
    RUNNING: resources.learningPlans.learnerProfileToolRunning,
    UPDATED: resources.learningPlans.learnerProfileToolUpdated,
    NO_CHANGE: resources.learningPlans.learnerProfileToolNoChange,
    FAILED: resources.learningPlans.learnerProfileToolFailed,
  };
  return labels[status];
}

function coachWorkStatusLabel(status: CoachWorkStatus, resources: LocaleResources): string {
  if (status === 'ORGANIZING') {
    return resources.learningPlans.organizingThoughts;
  }
  if (status === 'REVIEW_RUNNING') {
    return resources.learningPlans.reviewToolRunning;
  }
  return learnerProfileToolStatusLabel(status, resources);
}

function isCoachWorkRunning(status: CoachWorkStatus): boolean {
  return status === 'ORGANIZING' || status === 'REVIEW_RUNNING' || status === 'RUNNING';
}

function isLearnerProfileTerminalStatus(status: CoachWorkStatus): boolean {
  return status === 'UPDATED' || status === 'NO_CHANGE' || status === 'FAILED';
}

function coachWorkStatusClassName(status: CoachWorkStatus): string {
  const classes = ['practice-coach-work-status'];
  if (isCoachWorkRunning(status)) {
    classes.push('is-running');
  } else if (status === 'UPDATED') {
    classes.push('is-updated');
  } else if (status === 'FAILED') {
    classes.push('is-failed');
  } else {
    classes.push('is-no-change');
  }
  return classes.join(' ');
}

function nextIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }

  return `practice-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export default function PracticeChatWorkbench({
  onBack,
  onOpenSubmissions,
  onProgressUpdated,
  phaseIndex,
  plan,
  problemSlug,
}: {
  onBack: () => void;
  onOpenSubmissions: () => void;
  onProgressUpdated?: () => Promise<void>;
  phaseIndex: number;
  plan: LearningPlanDetailResponse;
  problemSlug: string;
}) {
  const { locale, resources } = useI18n();
  const inputLimits = useUserInputLimits();
  const phase = plan.phases.find((candidate) => candidate.phaseIndex === phaseIndex);
  const problem = phase?.problems.find((candidate) => candidate.slug === problemSlug);
  const [sessionResponse, setSessionResponse] = useState<PracticeSessionResponse>();
  const [messages, setMessages] = useState<PracticeMessage[]>([]);
  const [composerValue, setComposerValue] = useState('');
  const [composerExpanded, setComposerExpanded] = useState(false);
  const [status, setStatus] = useState<'loading' | 'idle' | 'streaming' | 'blocked' | 'error'>('loading');
  const [error, setError] = useState('');
  const [capacityUnavailable, setCapacityUnavailable] = useState(false);
  const [completionUpdating, setCompletionUpdating] = useState(false);
  const [completionSuccessSessionId, setCompletionSuccessSessionId] = useState<number>();
  const [moreActionsOpen, setMoreActionsOpen] = useState(false);
  const [skipConfirmationOpen, setSkipConfirmationOpen] = useState(false);
  const [postRunRefreshing, setPostRunRefreshing] = useState(false);
  const [reviewHistory, setReviewHistory] = useState<PracticeCodeReviewHistoryResponse>();
  const [reviewHistoryLoading, setReviewHistoryLoading] = useState(false);
  const [reviewHistoryError, setReviewHistoryError] = useState('');
  const [assistantWorkStates, setAssistantWorkStates] = useState<Record<number, AssistantWorkState>>({});
  const [coachSummaryApplyStates, setCoachSummaryApplyStates] = useState<Record<string, CoachSummaryApplyStatus>>({});
  const composerCounterId = useId();
  const composerFocusModeTitleId = useId();
  const composerExpandTooltipId = useId();
  const localMessageIdRef = useRef(-1);
  const composerRef = useRef<HTMLTextAreaElement | null>(null);
  const streamControllerRef = useRef<AbortController | null>(null);
  const activeSessionIdRef = useRef<number | undefined>(undefined);
  const moreActionsRef = useRef<HTMLSpanElement | null>(null);
  const messageListRef = useRef<HTMLElement | null>(null);
  const shouldAutoScrollRef = useRef(true);
  const submittingRef = useRef(false);
  const practiceLoadTokenRef = useRef(0);
  const coachSummaryProposalMessageIdsRef = useRef(new Set<number>());
  const practiceRunStreamRef = useRef<PracticeRunStreamState | undefined>(undefined);

  useEffect(() => {
    const controller = new AbortController();
    practiceLoadTokenRef.current += 1;
    const activeLoadToken = practiceLoadTokenRef.current;
    streamControllerRef.current?.abort();
    streamControllerRef.current = null;
    practiceRunStreamRef.current = undefined;
    submittingRef.current = false;
    setSessionResponse(undefined);
    setMessages([]);
    setError('');
    setComposerExpanded(false);
    setCompletionUpdating(false);
    setCompletionSuccessSessionId(undefined);
    setMoreActionsOpen(false);
    setSkipConfirmationOpen(false);
    setReviewHistory(undefined);
    setReviewHistoryError('');
    setReviewHistoryLoading(false);
    setAssistantWorkStates({});
    setCoachSummaryApplyStates({});
    coachSummaryProposalMessageIdsRef.current.clear();
    setStatus('loading');

    createOrReusePracticeSession(plan.id, phaseIndex, problemSlug, locale, controller.signal)
      .then((response) => {
        if (controller.signal.aborted || practiceLoadTokenRef.current !== activeLoadToken) {
          return;
        }
        const nextSessionResponse = requireApiData(response, resources.learningPlans.practiceSessionLoadFailed);
        setSessionResponse(nextSessionResponse);
        setMessages(nextSessionResponse.messages);
        if (!nextSessionResponse.activeRun) {
          void getPracticeSessionActiveRun(nextSessionResponse.session.id, controller.signal)
            .then((activeRunResponse) => {
              if (controller.signal.aborted || !activeRunResponse.success || !activeRunResponse.data) {
                return;
              }
              setSessionResponse((current) => current && current.session.id === nextSessionResponse.session.id
                ? { ...current, activeRun: activeRunResponse.data }
                : current);
            })
            .catch(() => undefined);
        }
        setStatus('idle');
      })
      .catch((error) => {
        if (!controller.signal.aborted && practiceLoadTokenRef.current === activeLoadToken) {
          setError(error instanceof Error ? error.message : resources.learningPlans.practiceSessionLoadFailed);
          setStatus('error');
        }
      });

    return () => {
      controller.abort();
      streamControllerRef.current?.abort();
      streamControllerRef.current = null;
      practiceRunStreamRef.current = undefined;
      submittingRef.current = false;
    };
  }, [locale, phaseIndex, plan.id, problemSlug, resources.learningPlans.practiceSessionLoadFailed]);

  useEffect(() => {
    if (!moreActionsOpen) {
      return undefined;
    }

    function closeMoreActions(event: MouseEvent) {
      if (event.target instanceof Node && !moreActionsRef.current?.contains(event.target)) {
        setMoreActionsOpen(false);
      }
    }

    function closeMoreActionsOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setMoreActionsOpen(false);
      }
    }

    document.addEventListener('mousedown', closeMoreActions);
    document.addEventListener('keydown', closeMoreActionsOnEscape);
    return () => {
      document.removeEventListener('mousedown', closeMoreActions);
      document.removeEventListener('keydown', closeMoreActionsOnEscape);
    };
  }, [moreActionsOpen]);

  useEffect(() => {
    if (!skipConfirmationOpen || completionUpdating) {
      return undefined;
    }

    function closeSkipConfirmationOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setSkipConfirmationOpen(false);
      }
    }

    document.addEventListener('keydown', closeSkipConfirmationOnEscape);
    return () => document.removeEventListener('keydown', closeSkipConfirmationOnEscape);
  }, [completionUpdating, skipConfirmationOpen]);

  useEffect(() => {
    if (!composerExpanded) {
      return undefined;
    }

    function closeComposerOnEscape(event: globalThis.KeyboardEvent) {
      if (event.key === 'Escape') {
        setComposerExpanded(false);
      }
    }

    document.addEventListener('keydown', closeComposerOnEscape);
    return () => document.removeEventListener('keydown', closeComposerOnEscape);
  }, [composerExpanded]);

  const sessionId = sessionResponse?.session.id;
  activeSessionIdRef.current = sessionId;
  const hasActiveRun = Boolean(sessionResponse?.activeRun);
  const progressStatus = sessionResponse?.session.progressStatus;
  const completionGate = sessionResponse?.completionGate;
  const leetcodeUrl = localizedLeetCodeUrl(sessionResponse?.problem.leetcodeUrl, locale);
  const difficulty = sessionResponse?.problem.difficulty ?? problem?.difficulty;
  const shouldShowSkipButton = Boolean(sessionId) && progressStatus !== 'COMPLETED' && progressStatus !== 'SKIPPED';
  const canMarkCompleted = Boolean(sessionId)
    && completionGate?.canComplete === true
    && progressStatus !== 'COMPLETED';
  const completionSucceeded = sessionId !== undefined && completionSuccessSessionId === sessionId;
  const shouldShowCompletionAction = canMarkCompleted || completionSucceeded;
  const completionActionDisabled = completionUpdating
    || completionSucceeded
    || postRunRefreshing
    || status === 'loading'
    || status === 'streaming'
    || hasActiveRun;
  const composerInputDisabled = !sessionId || status === 'loading' || hasActiveRun;
  const composerText = composerValue.trim();
  const composerBytes = utf8ByteLength(composerText);
  const composerOverLimit = composerBytes > inputLimits.practiceMessage.messageMaxBytes;
  const sendDisabled = !sessionId
    || status === 'loading'
    || status === 'streaming'
    || hasActiveRun
    || !composerText
    || composerOverLimit;
  const skipDisabled = completionUpdating
    || postRunRefreshing
    || status === 'loading'
    || status === 'streaming'
    || hasActiveRun;
  const workbenchTitle = practiceProblemLabel(
    sessionResponse,
    problem,
    locale,
    resources.learningPlans.problemTraining,
  );

  useEffect(() => {
    if (!sessionId || !hasActiveRun) {
      return undefined;
    }

    let stopped = false;
    const controller = new AbortController();

    async function poll() {
      if (practiceRunStreamRef.current?.sessionId === sessionId
        && streamControllerRef.current && !streamControllerRef.current.signal.aborted) {
        return;
      }
      try {
        const response = await getPracticeSessionActiveRun(sessionId!, controller.signal);
        if (stopped || controller.signal.aborted) {
          return;
        }
        if (response.data) {
          return;
        }
        const activeLoadToken = practiceLoadTokenRef.current;
        await refreshMessages(sessionId!, activeLoadToken, controller.signal);
        await refreshSession(sessionId!, activeLoadToken, controller.signal);
        await refreshReviews(sessionId!, activeLoadToken, controller.signal);
        setSessionResponse((current) => current && current.session.id === sessionId
          ? { ...current, activeRun: null }
          : current);
        setStatus('idle');
        setError('');
      } catch (error) {
        if (!controller.signal.aborted) {
          setError(error instanceof Error ? error.message : resources.learningPlans.practiceSessionLoadFailed);
        }
      }
    }

    const intervalId = window.setInterval(poll, ACTIVE_RUN_POLL_INTERVAL_MS);
    void poll();

    return () => {
      stopped = true;
      controller.abort();
      window.clearInterval(intervalId);
    };
  }, [hasActiveRun, resources.learningPlans.practiceSessionLoadFailed, sessionId]);

  useEffect(() => {
    const activeRun = sessionResponse?.activeRun;
    if (!sessionId || !activeRun) {
      return undefined;
    }
    if (practiceRunStreamRef.current?.sessionId === sessionId
      && practiceRunStreamRef.current.runUuid === activeRun.runUuid) {
      return undefined;
    }

    const controller = new AbortController();
    const assistantMessageId = localMessageIdRef.current;
    localMessageIdRef.current -= 1;
    const createdAt = new Date().toISOString();
    setStatus('streaming');
    setAssistantWorkState(assistantMessageId, { status: 'ORGANIZING' });
    setMessages((current) => [
      ...current,
      {
        id: assistantMessageId,
        role: 'ASSISTANT',
        messageType: 'CHAT',
        contentMarkdown: '',
        createdAt,
      },
    ]);
    void consumePracticeRun({
      sessionId,
      runUuid: activeRun.runUuid,
      eventsUrl: `/api/practice-sessions/${sessionId}/runs/${activeRun.runUuid}/events`,
      lastEventId: '0-0',
      assistantMessageId,
      realtimeProtocolVersion: 1,
      nextExpectedSequence: 1,
      realtimeIncomplete: false,
      successfulRunEndReceived: false,
      terminalErrorReceived: false,
      activeToolCallIds: new Set(),
      nextExpectedStepIndex: 1,
      stepBuffers: new Map(),
      confirmedAssistantContent: '',
    }, controller, createdAt);

    return () => controller.abort();
  }, [sessionId, sessionResponse?.activeRun?.runUuid]);

  useLayoutEffect(() => {
    const messageList = messageListRef.current;
    if (messageList && shouldAutoScrollRef.current) {
      messageList.scrollTop = messageList.scrollHeight;
    }
  }, [assistantWorkStates, messages, error, status]);

  useLayoutEffect(() => {
    const composer = composerRef.current;
    if (!composer) {
      return;
    }

    if (composerExpanded) {
      composer.style.height = '';
      return;
    }

    composer.style.height = 'auto';
    composer.style.height = `${Math.min(composer.scrollHeight, COMPOSER_AUTO_RESIZE_MAX_HEIGHT_PX)}px`;
  }, [composerExpanded, composerValue]);

  useEffect(() => {
    if (composerExpanded) {
      composerRef.current?.focus();
    }
  }, [composerExpanded]);

  useEffect(() => {
    if (!completionSucceeded || sessionId === undefined) {
      return undefined;
    }

    const completedSessionId = sessionId;
    const timer = window.setTimeout(() => {
      setCompletionSuccessSessionId((current) => current === completedSessionId ? undefined : current);
    }, COMPLETION_SUCCESS_VISIBLE_MS);

    return () => window.clearTimeout(timer);
  }, [completionSucceeded, sessionId]);

  useEffect(() => {
    const timers = Object.entries(assistantWorkStates).flatMap(([messageId, value]) => {
      if (value.status !== 'RUNNING' || !value.terminalStatus) {
        return [];
      }
      const terminalStatus = value.terminalStatus;
      const remaining = Math.max(
        0,
        LEARNER_PROFILE_TOOL_RUNNING_MIN_VISIBLE_MS - (Date.now() - (value.startedAt ?? Date.now())),
      );
      return [window.setTimeout(() => {
        setAssistantWorkStates((current) => {
          const workState = current[Number(messageId)];
          if (workState?.status !== 'RUNNING' || workState.terminalStatus !== terminalStatus) {
            return current;
          }
          return {
            ...current,
            [Number(messageId)]: {
              status: terminalStatus,
              startedAt: workState.startedAt,
              toolCallKey: workState.toolCallKey,
            },
          };
        });
      }, remaining)];
    });

    return () => timers.forEach((timer) => window.clearTimeout(timer));
  }, [assistantWorkStates]);

  function updateAutoScrollState() {
    const messageList = messageListRef.current;
    if (!messageList) {
      return;
    }

    shouldAutoScrollRef.current = messageList.scrollHeight - messageList.scrollTop - messageList.clientHeight
      < AUTO_SCROLL_THRESHOLD_PX;
  }

  function setAssistantWorkState(assistantMessageId: number, workState: AssistantWorkState) {
    setAssistantWorkStates((current) => ({ ...current, [assistantMessageId]: workState }));
  }

  function clearTransientAssistantWorkState(assistantMessageId: number) {
    setAssistantWorkStates((current) => {
      const workState = current[assistantMessageId];
      if (!workState
        || (!isCoachWorkRunning(workState.status) || workState.terminalStatus)) {
        return current;
      }
      const { [assistantMessageId]: _removed, ...remaining } = current;
      return remaining;
    });
  }

  function appendAssistantContent(assistantMessageId: number, content: string) {
    if (coachSummaryProposalMessageIdsRef.current.has(assistantMessageId)) {
      return;
    }
    setAssistantWorkStates((current) => {
      if (current[assistantMessageId]?.status !== 'ORGANIZING') {
        return current;
      }
      const { [assistantMessageId]: _removed, ...remaining } = current;
      return remaining;
    });
    setMessages((current) => current.map((message) => {
      if (message.id !== assistantMessageId) {
        return message;
      }

      const currentContent = message.contentMarkdown;
      return {
        ...message,
        contentMarkdown: `${currentContent}${content}`,
      };
    }));
  }

  function replaceAssistantContent(assistantMessageId: number, content: string) {
    if (coachSummaryProposalMessageIdsRef.current.has(assistantMessageId)) {
      return;
    }
    setMessages((current) => current.map((message) => (
      message.id === assistantMessageId ? { ...message, contentMarkdown: content } : message
    )));
  }

  function showCoachSummaryProposal(
    assistantMessageId: number,
    action: CoachSummaryProposalAction,
    summaryMarkdown: string,
  ) {
    coachSummaryProposalMessageIdsRef.current.add(assistantMessageId);
    setAssistantWorkStates((current) => {
      if (!(assistantMessageId in current)) {
        return current;
      }
      const { [assistantMessageId]: _removed, ...remaining } = current;
      return remaining;
    });
    setMessages((current) => current.map((message) => {
      if (message.coachSummaryAction?.status === 'PENDING') {
        return {
          ...message,
          coachSummaryAction: { ...message.coachSummaryAction, status: 'SUPERSEDED' },
        };
      }
      if (message.id !== assistantMessageId) {
        return message;
      }
      return {
        ...message,
        contentMarkdown: summaryMarkdown,
        coachSummaryAction: action,
      };
    }));
  }

  function updateLearnerProfileToolStatus(
    assistantMessageId: number,
    key: string,
    status: LearnerDeclaredProfileToolDisplayStatus,
  ) {
    if (status === 'RUNNING') {
      setAssistantWorkStates((current) => {
        const workState = current[assistantMessageId];
        if (workState?.toolCallKey === key
          && (workState.status === 'RUNNING' || isLearnerProfileTerminalStatus(workState.status))) {
          return current;
        }
        return {
          ...current,
          [assistantMessageId]: {
            status,
            startedAt: Date.now(),
            toolCallKey: key,
          },
        };
      });
      return;
    }
    setAssistantWorkStates((current) => {
      const workState = current[assistantMessageId];
      if (workState?.toolCallKey && workState.toolCallKey !== key) {
        return current;
      }
      if (workState?.status !== 'RUNNING') {
        return workState?.status === status && workState.toolCallKey === key
          ? current
          : {
              ...current,
              [assistantMessageId]: {
                status,
                startedAt: workState?.startedAt ?? Date.now(),
                toolCallKey: key,
              },
            };
      }
      return workState.terminalStatus === status
        ? current
        : { ...current, [assistantMessageId]: { ...workState, terminalStatus: status } };
    });
  }

  function reviewToolScoreSummary(result: unknown): string | undefined {
    const totalScore = readResultScore(result);
    const passed = readResultPassed(result);
    if (totalScore === undefined || passed === undefined) {
      return undefined;
    }
    const statusLabel = passed ? resources.learningPlans.reviewPassed : resources.learningPlans.reviewFailed;
    return resources.learningPlans.reviewToolScoreSummary(
      statusLabel,
      resources.learningPlans.reviewScoreText(totalScore, completionGate?.passScore),
    );
  }

  async function consumePracticeRun(
    runState: PracticeRunStreamState,
    controller: AbortController,
    createdAt: string,
  ) {
    practiceRunStreamRef.current = runState;
    let terminalEventReceived = false;
    let reviewRefreshRequested = false;

    const processAgentError = (data: unknown) => {
      runState.terminalErrorReceived = true;
      runState.terminalErrorMessage = readAgentErrorMessage(data);
      if (isAgentExecutorOverloaded(data)) {
        setCapacityUnavailable(true);
        setError(resources.common.aiCapacityUnavailable);
        return;
      }
      setError(runState.terminalErrorMessage ?? resources.learningPlans.practiceMessageFailed);
    };

    const processProjectedToolEvent = (event: import('../types/api').SseStreamEvent) => {
      if (event.eventName === 'agent_tool_end') {
        const toolEnd = readAgentToolEndEvent(event.data);
        if (!toolEnd) {
          if (isPracticeRealtimeV2(runState)) {
            throw new PracticeRealtimeIncompleteError();
          }
          return;
        }

        if (toolEnd.toolName === LEARNER_DECLARED_PROFILE_TOOL_NAME) {
          const key = learnerDeclaredProfileToolEventKey(toolEnd);
          const result = parseLearnerDeclaredProfileToolResult(toolEnd.result);
          if (key && result) {
            updateLearnerProfileToolStatus(runState.assistantMessageId, key, result.status);
          }
          return;
        }

        if (toolEnd.toolName === COACH_SUMMARY_PROPOSAL_TOOL_NAME) {
          const action = readCoachSummaryProposalAction(toolEnd.result, createdAt);
          const summaryMarkdown = readCoachSummaryMarkdown(toolEnd.result);
          if (action && summaryMarkdown) {
            showCoachSummaryProposal(runState.assistantMessageId, action, summaryMarkdown);
          }
          return;
        }

        if (toolEnd.toolName === REVIEW_TOOL_NAME
          && readResultType(toolEnd.result) === REVIEW_SUBMITTED_RESULT_TYPE
          && isSavedReviewResult(toolEnd.result)) {
          reviewRefreshRequested = true;
          if (!isPracticeRealtimeV2(runState)) {
            const scoreSummary = reviewToolScoreSummary(toolEnd.result);
            if (scoreSummary) {
              appendAssistantContent(runState.assistantMessageId, `${scoreSummary}\n\n`);
            }
          }
        }
      }

      if (event.eventName === 'agent_tool_start') {
        const toolStart = readAgentToolStartEvent(event.data);
        if (!toolStart) {
          if (isPracticeRealtimeV2(runState)) {
            throw new PracticeRealtimeIncompleteError();
          }
          return;
        }
        if (toolStart.toolName === LEARNER_DECLARED_PROFILE_TOOL_NAME) {
          const key = learnerDeclaredProfileToolEventKey(toolStart);
          if (key) {
            updateLearnerProfileToolStatus(runState.assistantMessageId, key, 'RUNNING');
          }
        } else if (toolStart.toolName === REVIEW_TOOL_NAME) {
          setAssistantWorkState(runState.assistantMessageId, { status: 'REVIEW_RUNNING' });
        }
      }
    };

    const processV2Event = (event: import('../types/api').SseStreamEvent) => {
      assertValidPracticeV2Event(runState, event);
      const data = event.data as Record<string, unknown>;
      if (event.eventName === 'agent_step_start') {
        const stepIndex = data.stepIndex as number;
        if (runState.currentStepIndex !== undefined
          || runState.activeToolCallIds.size > 0
          || stepIndex !== runState.nextExpectedStepIndex) {
          throw new PracticeRealtimeIncompleteError();
        }
        runState.toolExecutionStepIndex = undefined;
        runState.currentStepIndex = stepIndex;
        runState.stepBuffers.set(runState.currentStepIndex, '');
        runState.nextExpectedStepIndex += 1;
      } else if (event.eventName === 'content_delta') {
        const content = data.content as string;
        const stepIndex = runState.currentStepIndex;
        if (stepIndex === undefined) {
          throw new PracticeRealtimeIncompleteError();
        }
        const nextContent = `${runState.stepBuffers.get(stepIndex) ?? ''}${content}`;
        runState.stepBuffers.set(stepIndex, nextContent);
        replaceAssistantContent(runState.assistantMessageId, `${runState.confirmedAssistantContent}${nextContent}`);
      } else if (event.eventName === 'agent_step_end') {
        const stepIndex = data.stepIndex as number;
        if (runState.currentStepIndex !== stepIndex) {
          throw new PracticeRealtimeIncompleteError();
        }
        if (data.toolCallCount === 0) {
          runState.confirmedAssistantContent = runState.stepBuffers.get(stepIndex) ?? '';
          replaceAssistantContent(runState.assistantMessageId, runState.confirmedAssistantContent);
        } else {
          replaceAssistantContent(runState.assistantMessageId, runState.confirmedAssistantContent);
          // Tool 在模型声明调用后才执行，此时所属 step 已结束。
          runState.toolExecutionStepIndex = stepIndex;
        }
        runState.currentStepIndex = undefined;
      } else if (event.eventName === 'agent_run_end') {
        if (runState.currentStepIndex !== undefined
          || runState.toolExecutionStepIndex !== undefined
          || runState.activeToolCallIds.size > 0
          || data.steps !== runState.nextExpectedStepIndex - 1) {
          throw new PracticeRealtimeIncompleteError();
        }
        runState.successfulRunEndReceived = true;
        terminalEventReceived = true;
      } else if (event.eventName === 'agent_error') {
        runState.realtimeIncomplete = true;
        terminalEventReceived = true;
        processAgentError(event.data);
      }

      if (event.eventName === 'agent_tool_start' || event.eventName === 'agent_tool_end') {
        const stepIndex = data.stepIndex as number;
        const toolCallId = data.toolCallId as string;
        if (runState.currentStepIndex !== undefined || runState.toolExecutionStepIndex !== stepIndex) {
          throw new PracticeRealtimeIncompleteError();
        }
        if (event.eventName === 'agent_tool_start') {
          if (runState.activeToolCallIds.has(toolCallId)) {
            throw new PracticeRealtimeIncompleteError();
          }
          runState.activeToolCallIds.add(toolCallId);
        } else if (!runState.activeToolCallIds.delete(toolCallId)) {
          throw new PracticeRealtimeIncompleteError();
        }
        processProjectedToolEvent(event);
      }
      runState.lastEventId = event.id!;
      runState.nextExpectedSequence += 1;
    };

    const processLegacyEvent = (event: import('../types/api').SseStreamEvent) => {
      if (event.eventName === 'content_delta') {
        const delta = readContentDelta(event.data);
        if (delta) {
          appendAssistantContent(runState.assistantMessageId, delta);
        }
      }

      if (event.eventName === 'agent_run_end' || event.eventName === 'agent_error') {
        terminalEventReceived = true;
      }

      if (event.eventName === 'agent_error') {
        processAgentError(event.data);
      }
      processProjectedToolEvent(event);
    };

    const processEvent = (event: import('../types/api').SseStreamEvent) => {
      if (isPracticeRealtimeV2(runState)) {
        processV2Event(event);
      } else {
        processLegacyEvent(event);
        if (event.id) {
          runState.lastEventId = event.id;
        }
      }
    };

    for (let attempt = 0; attempt <= RUN_STREAM_RECONNECT_ATTEMPTS && !controller.signal.aborted; attempt += 1) {
      try {
        await readPracticeRunEvents(runState.eventsUrl, {
          after: runState.lastEventId,
          signal: controller.signal,
          realtimeProtocolVersion: runState.realtimeProtocolVersion,
          onEvent: (event) => {
            processEvent(event);
          },
        });
        if (terminalEventReceived || attempt === RUN_STREAM_RECONNECT_ATTEMPTS) {
          break;
        }
      } catch (error) {
        if (controller.signal.aborted) {
          return;
        }
        if (error instanceof PracticeRealtimeIncompleteError) {
          runState.realtimeIncomplete = true;
          break;
        }
        if (attempt === RUN_STREAM_RECONNECT_ATTEMPTS) {
          break;
        }
      }
    }

    if (controller.signal.aborted || !isCurrentSession(runState.sessionId, practiceLoadTokenRef.current)) {
      return;
    }

    const realtimeComplete = isPracticeRealtimeV2(runState)
      && runState.successfulRunEndReceived
      && !runState.realtimeIncomplete;
    await reconcilePracticeRunAfterStream(
      runState,
      controller.signal,
      terminalEventReceived,
      reviewRefreshRequested,
      realtimeComplete,
    );
  }

  async function reconcilePracticeRunAfterStream(
    runState: PracticeRunStreamState,
    signal: AbortSignal,
    terminalEventReceived: boolean,
    reviewRefreshRequested: boolean,
    realtimeComplete: boolean,
  ) {
    try {
      if (realtimeComplete) {
        const activeLoadToken = practiceLoadTokenRef.current;
        setSessionResponse((current) => current && current.session.id === runState.sessionId
          ? { ...current, activeRun: null }
          : current);
        setStatus('idle');
        setError('');
        if (practiceRunStreamRef.current?.runUuid === runState.runUuid) {
          practiceRunStreamRef.current = undefined;
        }
        clearTransientAssistantWorkState(runState.assistantMessageId);
        if (reviewRefreshRequested) {
          await refreshReviews(runState.sessionId, activeLoadToken, signal);
        }
        return;
      }
      const activeRunResponse = await getPracticeSessionActiveRun(runState.sessionId, signal);
      if (signal.aborted || !isCurrentSession(runState.sessionId, practiceLoadTokenRef.current)) {
        return;
      }
      const activeRun = activeRunResponse.success ? activeRunResponse.data : undefined;
      if (activeRun?.runUuid === runState.runUuid) {
        // Redis 回放不可用或短连接结束时，run 仍由 PostgreSQL 表示为执行中。
        setStatus(runState.terminalErrorReceived ? 'error' : 'streaming');
        return;
      }

      setPostRunRefreshing(true);
      const activeLoadToken = practiceLoadTokenRef.current;
      try {
        await refreshMessages(runState.sessionId, activeLoadToken, signal, runState.assistantMessageId);
        await refreshSession(runState.sessionId, activeLoadToken, signal);
        if (terminalEventReceived || reviewRefreshRequested) {
          await refreshReviews(runState.sessionId, activeLoadToken, signal);
        }
        setSessionResponse((current) => current && current.session.id === runState.sessionId
          ? { ...current, activeRun: null }
          : current);
        setStatus(runState.terminalErrorReceived ? 'error' : 'idle');
        setError(runState.terminalErrorReceived
          ? runState.terminalErrorMessage ?? resources.learningPlans.practiceMessageFailed
          : '');
        if (practiceRunStreamRef.current?.runUuid === runState.runUuid) {
          practiceRunStreamRef.current = undefined;
        }
        clearTransientAssistantWorkState(runState.assistantMessageId);
      } finally {
        if (!signal.aborted && isCurrentSession(runState.sessionId, activeLoadToken)) {
          setPostRunRefreshing(false);
        }
      }
    } catch (error) {
      if (!signal.aborted) {
        // Redis/SSE 或 active-run 查询暂时不可用不表示 Agent 失败；保留生成中状态。
        setStatus('streaming');
      }
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const text = composerValue.trim();

    if (!text || !sessionId || status === 'loading' || status === 'streaming' || submittingRef.current) {
      return;
    }
    if (utf8ByteLength(text) > inputLimits.practiceMessage.messageMaxBytes) {
      setError(resources.learningPlans.practiceMessageTooLong(inputLimits.practiceMessage.messageMaxBytes));
      return;
    }

    submittingRef.current = true;
    streamControllerRef.current?.abort();
    const controller = new AbortController();
    streamControllerRef.current = controller;
    const userMessageId = localMessageIdRef.current;
    localMessageIdRef.current -= 1;
    const assistantMessageId = localMessageIdRef.current;
    localMessageIdRef.current -= 1;
    const now = new Date().toISOString();

    setError('');
    setAssistantWorkState(assistantMessageId, { status: 'ORGANIZING' });
    setStatus('streaming');
    setComposerValue('');
    setComposerExpanded(false);
    setMessages((current) => [
      ...current,
      {
        id: userMessageId,
        role: 'USER',
        messageType: 'CHAT',
        contentMarkdown: text,
        createdAt: now,
      },
      {
        id: assistantMessageId,
        role: 'ASSISTANT',
        messageType: 'CHAT',
        contentMarkdown: '',
        createdAt: now,
      },
    ]);

    try {
      const subscription = await startPracticeMessage(sessionId, { message: text }, {
        idempotencyKey: nextIdempotencyKey(),
        signal: controller.signal,
      });
      const runState: PracticeRunStreamState = {
        sessionId,
        runUuid: subscription.runUuid,
        eventsUrl: subscription.eventsUrl,
        lastEventId: subscription.initialAfter,
        assistantMessageId,
        realtimeProtocolVersion: subscription.realtimeProtocolVersion === 2 ? 2 : 1,
        nextExpectedSequence: 1,
        realtimeIncomplete: false,
        successfulRunEndReceived: false,
        terminalErrorReceived: false,
        activeToolCallIds: new Set(),
        nextExpectedStepIndex: 1,
        stepBuffers: new Map(),
        confirmedAssistantContent: '',
      };
      // 先登记订阅，再发布 activeRun 状态，避免 effect 与提交链路并发创建两个订阅。
      practiceRunStreamRef.current = runState;
      setSessionResponse((current) => current && current.session.id === sessionId
        ? {
            ...current,
            activeRun: {
              runId: 0,
              taskId: subscription.taskId,
              runUuid: subscription.runUuid,
              startedAt: now,
            },
          }
        : current);
      await consumePracticeRun(runState, controller, now);
    } catch (error) {
      if (!controller.signal.aborted) {
        setPostRunRefreshing(false);
        if (error instanceof ApiRequestError && error.code === AGENT_RUN_IN_PROGRESS_CODE) {
          setError('');
          setMessages((current) => current.filter((message) => (
            message.id !== assistantMessageId && message.id !== userMessageId
          )));
          try {
            const activeRunResponse = await getPracticeSessionActiveRun(sessionId, controller.signal);
            if (activeRunResponse.success && activeRunResponse.data) {
              setSessionResponse((current) => current && current.session.id === sessionId
                ? { ...current, activeRun: activeRunResponse.data }
                : current);
            }
          } catch (activeRunError) {
            if (!controller.signal.aborted) {
              setError(activeRunError instanceof Error
                ? activeRunError.message
                : resources.learningPlans.practiceMessageBlocked);
            }
          }
          setStatus('idle');
          return;
        }

        if (isAgentExecutorOverloaded(error)) {
          setCapacityUnavailable(true);
          setError(resources.common.aiCapacityUnavailable);
        } else {
          setError(error instanceof Error ? error.message : resources.learningPlans.practiceMessageFailed);
        }
        setMessages((current) => current.filter((message) => (
          message.id !== assistantMessageId && message.id !== userMessageId
        )));
        setStatus('error');
      }
    } finally {
      if (practiceRunStreamRef.current?.assistantMessageId !== assistantMessageId) {
        clearTransientAssistantWorkState(assistantMessageId);
      }
      if (streamControllerRef.current === controller) {
        streamControllerRef.current = null;
        submittingRef.current = false;
      }
    }
  }

  function handleComposerKeyDown(event: ReactKeyboardEvent<HTMLTextAreaElement>) {
    if (event.key !== 'Enter'
      || (!event.ctrlKey && !event.metaKey)
      || event.nativeEvent.isComposing
      || sendDisabled) {
      return;
    }

    event.preventDefault();
    event.currentTarget.form?.requestSubmit();
  }

  async function handleMarkCompleted() {
    if (!sessionId
      || completionUpdating
      || progressStatus === 'COMPLETED'
      || status === 'loading'
      || status === 'streaming'
      || hasActiveRun
      || completionSucceeded
      || !completionGate?.canComplete) {
      return;
    }

    const activeSessionId = sessionId;
    const activeLoadToken = practiceLoadTokenRef.current;
    setCompletionUpdating(true);
    setError('');
    try {
      const response = await updatePracticeProgressStatus(sessionId, 'COMPLETED');
      if (activeSessionIdRef.current !== activeSessionId || practiceLoadTokenRef.current !== activeLoadToken) {
        return;
      }
      const nextSessionResponse = requireApiData(response, resources.learningPlans.progressUpdateFailed);
      setCompletionSuccessSessionId(activeSessionId);
      setSessionResponse(nextSessionResponse);
      setMessages(nextSessionResponse.messages);
      setStatus('idle');
      await onProgressUpdated?.();
    } catch (error) {
      if (activeSessionIdRef.current !== activeSessionId || practiceLoadTokenRef.current !== activeLoadToken) {
        return;
      }
      setError(error instanceof Error ? error.message : resources.learningPlans.progressUpdateFailed);
      setStatus('error');
    } finally {
      if (activeSessionIdRef.current === activeSessionId && practiceLoadTokenRef.current === activeLoadToken) {
        setCompletionUpdating(false);
      }
    }
  }

  async function handleSkipProblem() {
    if (!sessionId
      || completionUpdating
      || progressStatus === 'COMPLETED'
      || progressStatus === 'SKIPPED'
      || skipDisabled) {
      setSkipConfirmationOpen(false);
      return;
    }

    const activeSessionId = sessionId;
    const activeLoadToken = practiceLoadTokenRef.current;
    setSkipConfirmationOpen(false);
    setCompletionUpdating(true);
    setError('');
    try {
      const response = await updatePracticeProgressStatus(sessionId, 'SKIPPED');
      if (activeSessionIdRef.current !== activeSessionId || practiceLoadTokenRef.current !== activeLoadToken) {
        return;
      }
      const nextSessionResponse = requireApiData(response, resources.learningPlans.progressUpdateFailed);
      setSessionResponse(nextSessionResponse);
      setMessages(nextSessionResponse.messages);
      setStatus('idle');
      await onProgressUpdated?.();
    } catch (error) {
      if (activeSessionIdRef.current !== activeSessionId || practiceLoadTokenRef.current !== activeLoadToken) {
        return;
      }
      setError(error instanceof Error ? error.message : resources.learningPlans.progressUpdateFailed);
      setStatus('error');
    } finally {
      if (activeSessionIdRef.current === activeSessionId && practiceLoadTokenRef.current === activeLoadToken) {
        setCompletionUpdating(false);
      }
    }
  }

  async function handleCoachSummaryApply(action: CoachSummaryProposalAction) {
    if (!sessionId || action.status !== 'PENDING' || coachSummaryApplyStates[action.proposalId] === 'applying') {
      return;
    }
    setCoachSummaryApplyStates((current) => ({ ...current, [action.proposalId]: 'applying' }));
    try {
      const response = await applyPracticeCoachSummaryProposal(sessionId, action.proposalId);
      const appliedAction = requireApiData(response, resources.learningPlans.coachSummaryApplyFailed);
      setMessages((current) => current.map((message) => (
        message.coachSummaryAction?.proposalId === action.proposalId
          ? { ...message, coachSummaryAction: appliedAction }
          : message
      )));
      setCoachSummaryApplyStates((current) => {
        const { [action.proposalId]: _removed, ...remaining } = current;
        return remaining;
      });
    } catch {
      setCoachSummaryApplyStates((current) => ({ ...current, [action.proposalId]: 'error' }));
    }
  }

  function isCurrentSession(activeSessionId: number, activeLoadToken: number) {
    return activeSessionIdRef.current === activeSessionId && practiceLoadTokenRef.current === activeLoadToken;
  }

  async function refreshMessages(
    activeSessionId: number,
    activeLoadToken: number,
    signal?: AbortSignal,
    assistantMessageId?: number,
  ) {
    const response = await getPracticeSessionMessages(activeSessionId, 50, signal);
    if (!signal?.aborted && isCurrentSession(activeSessionId, activeLoadToken)) {
      const nextMessages = requireApiData(response, resources.learningPlans.practiceSessionLoadFailed);
      setMessages(nextMessages);
      if (assistantMessageId !== undefined) {
        const finalAssistantMessage = [...nextMessages].reverse().find((message) => message.role === 'ASSISTANT');
        if (finalAssistantMessage) {
          setAssistantWorkStates((current) => {
            const workState = current[assistantMessageId];
            if (!workState || (!workState.terminalStatus && !isLearnerProfileTerminalStatus(workState.status))) {
              return current;
            }
            const { [assistantMessageId]: _removed, ...remaining } = current;
            return { ...remaining, [finalAssistantMessage.id]: workState };
          });
        }
      }
    }
  }

  async function refreshSession(activeSessionId: number, activeLoadToken: number, signal?: AbortSignal) {
    const response = await getPracticeSession(activeSessionId, signal);
    if (!signal?.aborted && isCurrentSession(activeSessionId, activeLoadToken)) {
      setSessionResponse(requireApiData(response, resources.learningPlans.practiceSessionLoadFailed));
    }
  }

  async function refreshReviews(activeSessionId: number, activeLoadToken: number, signal?: AbortSignal) {
    setReviewHistoryLoading(true);
    setReviewHistoryError('');
    try {
      const response = await getPracticeSessionReviews(activeSessionId, signal);
      if (signal?.aborted || !isCurrentSession(activeSessionId, activeLoadToken)) {
        return;
      }
      const nextReviewHistory = requireApiData(response, resources.learningPlans.reviewLoadFailed);
      setReviewHistory(nextReviewHistory);
      setSessionResponse((current) => current && current.session.id === activeSessionId
        ? {
            ...current,
            completionGate: nextReviewHistory.completionGate,
            latestReview: nextReviewHistory.latestReview ?? null,
          }
        : current);
    } catch (error) {
      if (signal?.aborted || !isCurrentSession(activeSessionId, activeLoadToken)) {
        return;
      }
      setReviewHistoryError(error instanceof Error ? error.message : resources.learningPlans.reviewLoadFailed);
    } finally {
      if (isCurrentSession(activeSessionId, activeLoadToken)) {
        setReviewHistoryLoading(false);
      }
    }
  }

  return (
    <article className="practice-workbench" aria-labelledby="practice-workbench-title">
      <header className="practice-toolbar">
        <div className="practice-toolbar-main">
          <button className="secondary-button compact detail-back-button" onClick={onBack} type="button">
            <ArrowLeft aria-hidden="true" />
            <span>{resources.learningPlans.backToPlanDetail}</span>
          </button>
          <div>
            <p className="eyebrow">{phase?.title ?? resources.learningPlans.phaseFallback(phaseIndex)}</p>
            <h2 id="practice-workbench-title">
              {workbenchTitle}
            </h2>
          </div>
        </div>
        <div className="practice-toolbar-actions">
          <span className={`difficulty-badge ${String(difficulty ?? '').toLowerCase()}`}>
            {formatDifficulty(difficulty, resources)}
          </span>
          <span className="status-badge">{progressStatusLabel(progressStatus, resources)}</span>
          <button
            className="secondary-button compact"
            onClick={onOpenSubmissions}
            type="button"
          >
            <ClipboardList aria-hidden="true" />
            <span>{resources.learningPlans.reviewHistory}</span>
          </button>
          {shouldShowSkipButton && (
            <span
              className={`toolbar-tooltip-wrap practice-more-actions ${moreActionsOpen ? 'is-open' : ''}`}
              ref={moreActionsRef}
            >
              <button
                aria-controls="practice-more-actions-menu"
                aria-expanded={moreActionsOpen}
                aria-haspopup="menu"
                aria-label={resources.learningPlans.practiceMoreActions}
                className="icon-button"
                disabled={skipDisabled}
                onClick={() => setMoreActionsOpen((current) => !current)}
                title={resources.learningPlans.practiceMoreActions}
                type="button"
              >
                <MoreHorizontal aria-hidden="true" />
              </button>
              <span className="toolbar-tooltip practice-more-actions-tooltip" role="tooltip">
                {resources.learningPlans.practiceMoreActions}
              </span>
              {moreActionsOpen && (
                <div className="practice-actions-menu" id="practice-more-actions-menu" role="menu">
                  <button
                    className="practice-actions-menu-item"
                    onClick={() => {
                      setMoreActionsOpen(false);
                      setSkipConfirmationOpen(true);
                    }}
                    role="menuitem"
                    type="button"
                  >
                    <SkipForward aria-hidden="true" />
                    <span>{resources.learningPlans.skipProblem}</span>
                  </button>
                </div>
              )}
            </span>
          )}
          <span className="toolbar-tooltip-wrap">
            <span
              aria-describedby="practice-guidance-tooltip"
              aria-label={resources.learningPlans.practiceLeetCodeGuidance}
              className="icon-button practice-guidance-icon"
              role="img"
              tabIndex={0}
            >
              <Info aria-hidden="true" />
            </span>
            <span className="toolbar-tooltip practice-guidance-tooltip" id="practice-guidance-tooltip" role="tooltip">
              {resources.learningPlans.practiceLeetCodeGuidance}
            </span>
          </span>
          <span className="toolbar-tooltip-wrap">
            {leetcodeUrl ? (
              <a
                aria-describedby="practice-leetcode-tooltip"
                aria-label={resources.learningPlans.openLeetCode}
                className="icon-button practice-leetcode-link"
                href={leetcodeUrl}
                rel="noreferrer"
                target="_blank"
              >
                <ExternalLink aria-hidden="true" />
              </a>
            ) : (
              <button
                aria-describedby="practice-leetcode-tooltip"
                aria-label={resources.learningPlans.leetcodeUnavailable}
                className="icon-button practice-leetcode-link"
                disabled
                title={resources.learningPlans.leetcodeUnavailable}
                type="button"
              >
                <ExternalLink aria-hidden="true" />
              </button>
            )}
            <span className="toolbar-tooltip practice-leetcode-tooltip" id="practice-leetcode-tooltip" role="tooltip">
              {leetcodeUrl ? resources.learningPlans.openLeetCode : resources.learningPlans.leetcodeUnavailable}
            </span>
          </span>
        </div>
      </header>

      <section
        className="practice-message-list"
        aria-label={resources.learningPlans.chatMessages}
        onScroll={updateAutoScrollState}
        ref={messageListRef}
      >
        {error && <p className="error-text practice-error" role="alert">{error}</p>}
        {status === 'loading' && (
          <article className="practice-message assistant-message">
            <span>{resources.learningPlans.coach}</span>
            <MarkdownView
              content={resources.learningPlans.loadingStatement}
              defaultCodeLanguage={plan.programmingLanguage}
            />
          </article>
        )}
        {status !== 'loading' && messages.length === 0 && (
          <article className="practice-message assistant-message">
            <span>{resources.learningPlans.coach}</span>
            <MarkdownView
              content={resources.learningPlans.statementUnavailable}
              defaultCodeLanguage={plan.programmingLanguage}
            />
          </article>
        )}
        {messages.map((message) => (
          <article
            className={`practice-message ${message.role === 'USER' ? 'user-message' : 'assistant-message'}`}
            key={message.id}
          >
            <span>{message.role === 'USER' ? resources.learningPlans.you : resources.learningPlans.coach}</span>
            {message.role === 'ASSISTANT' && assistantWorkStates[message.id] && (
              <p
                aria-label={coachWorkStatusLabel(assistantWorkStates[message.id].status, resources)}
                className={coachWorkStatusClassName(assistantWorkStates[message.id].status)}
                role="status"
              >
                <span className="practice-coach-work-status-text">
                  {coachWorkStatusLabel(assistantWorkStates[message.id].status, resources)}
                </span>
              </p>
            )}
            {message.contentMarkdown === resources.learningPlans.replyFailed
            || message.contentMarkdown === resources.learningPlans.practiceMessageBlocked ? (
              <p className="practice-message-failed">{message.contentMarkdown}</p>
            ) : message.role === 'USER' ? (
              <p className="practice-message-plain-text">
                {message.contentMarkdown || resources.learningPlans.organizingThoughts}
              </p>
            ) : message.contentMarkdown ? (
              <MarkdownView
                content={message.contentMarkdown}
                defaultCodeLanguage={plan.programmingLanguage}
              />
            ) : null}
            {message.role === 'ASSISTANT' && message.coachSummaryAction && (
              <div className={`practice-coach-summary-action is-${message.coachSummaryAction.status.toLowerCase()}`}>
                {message.coachSummaryAction.status === 'PENDING' ? (
                  <button
                    className="primary-button compact"
                    disabled={coachSummaryApplyStates[message.coachSummaryAction.proposalId] === 'applying'}
                    onClick={() => void handleCoachSummaryApply(message.coachSummaryAction!)}
                    type="button"
                  >
                    <Save aria-hidden="true" />
                    <span>{coachSummaryApplyStates[message.coachSummaryAction.proposalId] === 'applying'
                      ? resources.learningPlans.coachSummaryApplying
                      : coachSummaryApplyStates[message.coachSummaryAction.proposalId] === 'error'
                        ? resources.learningPlans.coachSummaryApplyRetry
                        : message.coachSummaryAction.operation === 'CREATE'
                          ? resources.learningPlans.coachSummarySave
                          : resources.learningPlans.coachSummaryReplace}</span>
                  </button>
                ) : (
                  <p role="status">
                    <CheckCircle2 aria-hidden="true" />
                    <span>{message.coachSummaryAction.status === 'APPLIED'
                      ? resources.learningPlans.coachSummaryApplied
                      : resources.learningPlans.coachSummarySuperseded}</span>
                  </p>
                )}
                {coachSummaryApplyStates[message.coachSummaryAction.proposalId] === 'error' && (
                  <span className="error-text" role="alert">{resources.learningPlans.coachSummaryApplyFailed}</span>
                )}
              </div>
            )}
          </article>
        ))}
      </section>

      {(reviewHistoryLoading || reviewHistoryError || reviewHistory) && (
        <span className="visually-hidden" aria-live="polite">
          {reviewHistoryLoading
            ? resources.learningPlans.reviewLoading
            : reviewHistoryError || resources.learningPlans.reviewHistory}
        </span>
      )}

      {composerExpanded && (
        <div
          aria-hidden="true"
          className="practice-composer-backdrop"
          onMouseDown={() => setComposerExpanded(false)}
        />
      )}
      <footer className="practice-footer">
        {shouldShowCompletionAction && (
          <div className={`practice-completion-action${completionSucceeded ? ' is-success' : ''}`}>
            <p>
              <CheckCircle2 aria-hidden="true" />
              <span>{completionSucceeded
                ? resources.learningPlans.practiceCompletionSuccess
                : resources.learningPlans.reviewPassed}</span>
            </p>
            {completionSucceeded && (
              <span className="visually-hidden" role="status">{resources.learningPlans.practiceCompletionSuccess}</span>
            )}
            <button
              className="primary-button compact practice-completion-action-button"
              disabled={completionActionDisabled}
              onClick={() => void handleMarkCompleted()}
              type="button"
            >
              <CheckCircle2 aria-hidden="true" />
              <span>{completionSucceeded
                ? resources.learningPlans.practiceCompletionSuccess
                : resources.learningPlans.markCompleted}</span>
            </button>
          </div>
        )}
        <form
          aria-label={composerExpanded ? undefined : resources.learningPlans.sendMessage}
          aria-labelledby={composerExpanded ? composerFocusModeTitleId : undefined}
          aria-modal={composerExpanded || undefined}
          className={`practice-composer${composerExpanded ? ' is-expanded' : ''}`}
          onSubmit={handleSubmit}
          role={composerExpanded ? 'dialog' : undefined}
        >
        {composerExpanded && (
          <div className="practice-composer-expanded-header">
            <span className="visually-hidden" id={composerFocusModeTitleId}>{resources.learningPlans.composerFocusMode}</span>
            <button
              aria-label={resources.learningPlans.collapseComposer}
              className="icon-button"
              onClick={() => setComposerExpanded(false)}
              type="button"
            >
              <Minimize2 aria-hidden="true" />
            </button>
          </div>
        )}
        <div className="practice-composer-input">
          <textarea
            aria-describedby={composerCounterId}
            aria-invalid={composerOverLimit}
            aria-label={resources.learningPlans.composerLabel}
            disabled={composerInputDisabled}
            onChange={(event) => setComposerValue(event.target.value)}
            onKeyDown={handleComposerKeyDown}
            placeholder={resources.learningPlans.practiceComposerPlaceholderReview}
            ref={composerRef}
            value={composerValue}
          />
          {!composerExpanded && (
            <span className="toolbar-tooltip-wrap practice-composer-expand-control">
              <button
                aria-describedby={composerExpandTooltipId}
                aria-expanded={false}
                aria-label={resources.learningPlans.expandComposer}
                className="icon-button"
                disabled={composerInputDisabled}
                onClick={() => setComposerExpanded(true)}
                type="button"
              >
                <Maximize2 aria-hidden="true" />
              </button>
              <span className="toolbar-tooltip" id={composerExpandTooltipId} role="tooltip">
                {resources.learningPlans.expandComposer}
              </span>
            </span>
          )}
          <small className={composerOverLimit
            ? 'input-limit-counter is-over-limit'
            : 'input-limit-counter'} id={composerCounterId}>
            {resources.common.byteCount(composerBytes, inputLimits.practiceMessage.messageMaxBytes)}
          </small>
        </div>
        <button className="primary-button compact" disabled={sendDisabled} type="submit">
          {resources.learningPlans.send}
        </button>
        </form>
      </footer>

      {skipConfirmationOpen && (
        <div className="modal-backdrop" role="presentation">
          <section
            aria-describedby="practice-skip-confirm-description"
            aria-labelledby="practice-skip-confirm-title"
            aria-modal="true"
            className="practice-skip-confirm-dialog"
            role="dialog"
          >
            <h3 id="practice-skip-confirm-title">{resources.learningPlans.skipProblemConfirmTitle}</h3>
            <p id="practice-skip-confirm-description">{resources.learningPlans.skipProblemConfirmDescription}</p>
            <div className="modal-actions practice-skip-confirm-actions">
              <button
                autoFocus
                className="secondary-button compact"
                disabled={completionUpdating}
                onClick={() => setSkipConfirmationOpen(false)}
                type="button"
              >
                {resources.common.cancel}
              </button>
              <button
                className="practice-skip-confirm-button compact"
                disabled={completionUpdating}
                onClick={() => void handleSkipProblem()}
                type="button"
              >
                <SkipForward aria-hidden="true" />
                <span>{resources.learningPlans.confirmSkipProblem}</span>
              </button>
            </div>
          </section>
        </div>
      )}

      <AiCapacityUnavailableDialog
        onClose={() => setCapacityUnavailable(false)}
        open={capacityUnavailable}
      />
    </article>
  );
}
