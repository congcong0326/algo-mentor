import { AlertCircle, ArrowLeft, CheckCircle2, ClipboardList, Clock, ExternalLink, Info, MoreHorizontal, Save, SkipForward } from 'lucide-react';
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import type { CSSProperties, FormEvent } from 'react';
import MarkdownView from '../components/MarkdownView';
import { formatDifficulty, formatProblemTitle } from '../i18n/formatters';
import { useI18n } from '../i18n/I18nProvider';
import type { LocaleResources, SupportedLocale } from '../i18n/locales';
import {
  ApiRequestError,
  applyPracticeCoachSummaryProposal,
  createOrReusePracticeSession,
  decideAgentToolPermission,
  getPracticeSession,
  getPracticeSessionActiveRun,
  getPracticeSessionMessages,
  getPracticeSessionReviews,
  requireApiData,
  streamPracticeMessage,
  updatePracticeProgressStatus,
} from '../services/api';
import type {
  AgentToolEndEvent,
  AgentToolStartEvent,
  LearningPlanDetailResponse,
  LearningPlanProblemDraft,
  AgentToolPermissionRequestEvent,
  AgentToolPermissionDecisionType,
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
const LEARNER_PROFILE_TOOL_RUNNING_MIN_VISIBLE_MS = 700;
// 后端 SSE/tool result 公共契约，用于识别 Review tool 是否真实落库。
const REVIEW_TOOL_NAME = 'submit_practice_code_review';
const COACH_SUMMARY_PROPOSAL_TOOL_NAME = 'propose_current_problem_coach_summary';
const REVIEW_PERMISSION_COPY_CODE = 'PRACTICE_CODE_REVIEW_REQUESTED';
const REVIEW_SUBMITTED_RESULT_TYPE = 'practice_code_review_submitted';
const COACH_SUMMARY_PROPOSAL_RESULT_TYPE = 'current_problem_coach_summary_proposed';
const TOOL_PERMISSION_DENIED_RESULT_TYPE = 'tool_permission_denied';
const TOOL_PERMISSION_TIMEOUT_RESULT_TYPE = 'tool_permission_timeout';
const TOOL_PERMISSION_COUNTDOWN_REFRESH_MS = 250;
const TOOL_PERMISSION_URGENT_SECONDS = 10;

interface PermissionPreview {
  problemSlug?: string;
  problemTitle?: string;
  codePreview?: string;
  contextAvailable?: boolean;
}

interface PendingPermissionState {
  request: AgentToolPermissionRequestEvent;
  preview: PermissionPreview;
  initialRemainingSeconds?: number;
  submitting: boolean;
  error: string;
}

type CoachWorkStatus = 'ORGANIZING' | 'REVIEW_RUNNING' | LearnerDeclaredProfileToolDisplayStatus;
type CoachSummaryApplyStatus = 'applying' | 'error';

interface AssistantWorkState {
  status: CoachWorkStatus;
  startedAt?: number;
  terminalStatus?: Exclude<LearnerDeclaredProfileToolDisplayStatus, 'RUNNING'>;
  toolCallKey?: string;
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

function permissionRequestCopy(
  request: AgentToolPermissionRequestEvent,
  resources: LocaleResources,
): { title: string; reason: string } {
  if (isReviewPermissionRequest(request)) {
    return {
      title: resources.learningPlans.toolPermissionReviewTitle,
      reason: resources.learningPlans.toolPermissionReviewReason,
    };
  }
  return { title: request.displayName, reason: request.reason };
}

function isReviewPermissionRequest(request: AgentToolPermissionRequestEvent): boolean {
  return request.copyCode === REVIEW_PERMISSION_COPY_CODE || request.toolName === REVIEW_TOOL_NAME;
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

function readPermissionPreview(data: unknown): PermissionPreview {
  if (typeof data !== 'object' || data === null) {
    return {};
  }

  const preview = data as Record<string, unknown>;
  return {
    problemSlug: readStringField(preview, 'problemSlug'),
    problemTitle: readStringField(preview, 'problemTitle'),
    codePreview: readStringField(preview, 'codePreview'),
    contextAvailable: readBooleanField(preview, 'contextAvailable'),
  };
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

function readPermissionRequestEvent(data: unknown): AgentToolPermissionRequestEvent | undefined {
  if (typeof data !== 'object' || data === null) {
    return undefined;
  }

  const event = data as Record<string, unknown>;
  const runId = readStringField(event, 'runId');
  const stepIndex = readNumberField(event, 'stepIndex');
  const toolCallId = readStringField(event, 'toolCallId');
  const toolName = readStringField(event, 'toolName');
  const permissionRequestId = readStringField(event, 'permissionRequestId');
  const displayName = readStringField(event, 'displayName');
  const reason = readStringField(event, 'reason');
  const copyCode = readStringField(event, 'copyCode');
  const expiresAt = readStringField(event, 'expiresAt');
  const preview = event.preview;

  if (!runId
    || stepIndex === undefined
    || !toolCallId
    || !toolName
    || !permissionRequestId
    || !displayName
    || !reason
    || typeof preview !== 'object'
    || preview === null
    || !expiresAt) {
    return undefined;
  }

  return {
    runId,
    stepIndex,
    toolCallId,
    toolName,
    permissionRequestId,
    displayName,
    reason,
    copyCode,
    preview: preview as Record<string, unknown>,
    expiresAt,
  };
}

function readPermissionRequestId(data: unknown): string | undefined {
  if (typeof data !== 'object' || data === null || !('permissionRequestId' in data)) {
    return undefined;
  }

  const permissionRequestId = (data as { permissionRequestId?: unknown }).permissionRequestId;
  return typeof permissionRequestId === 'string' && permissionRequestId.trim() ? permissionRequestId : undefined;
}

function permissionRemainingSeconds(expiresAt: string, now = Date.now()): number | undefined {
  const expiresAtMillis = Date.parse(expiresAt);
  if (!Number.isFinite(expiresAtMillis)) {
    return undefined;
  }
  return Math.max(0, Math.ceil((expiresAtMillis - now) / 1000));
}

function formatPermissionCountdown(seconds: number): string {
  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = seconds % 60;
  return `${String(minutes).padStart(2, '0')}:${String(remainingSeconds).padStart(2, '0')}`;
}

function permissionProblemLabel(
  preview: PermissionPreview,
  sessionResponse: PracticeSessionResponse | undefined,
  problem: LearningPlanProblemDraft | undefined,
  locale: SupportedLocale,
): string | undefined {
  if (!preview.problemSlug && !preview.problemTitle) {
    return undefined;
  }
  const localizedTitle = localizedProblemTitleForSlug(preview.problemSlug, sessionResponse, problem, locale);
  const title = localizedTitle ?? preview.problemTitle;
  if (title && preview.problemSlug && title !== preview.problemSlug) {
    return `${title} (${preview.problemSlug})`;
  }
  return title ?? preview.problemSlug;
}

function localizedProblemTitleForSlug(
  problemSlug: string | undefined,
  sessionResponse: PracticeSessionResponse | undefined,
  problem: LearningPlanProblemDraft | undefined,
  locale: SupportedLocale,
): string | undefined {
  if (sessionResponse?.problem && sessionResponse.problem.slug === problemSlug) {
    return formatProblemTitle(sessionResponse.problem, locale);
  }
  if (problem && problem.slug === problemSlug) {
    return formatProblemTitle(problem, locale);
  }
  return undefined;
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

function readResultScore(result: unknown): number | undefined {
  if (typeof result !== 'object' || result === null || !('totalScore' in result)) {
    return undefined;
  }

  const totalScore = (result as { totalScore?: unknown }).totalScore;
  if (typeof totalScore === 'number' && Number.isFinite(totalScore)) {
    return totalScore;
  }
  if (typeof totalScore === 'string' && totalScore.trim()) {
    const parsed = Number(totalScore);
    return Number.isFinite(parsed) ? parsed : undefined;
  }
  return undefined;
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
  const phase = plan.phases.find((candidate) => candidate.phaseIndex === phaseIndex);
  const problem = phase?.problems.find((candidate) => candidate.slug === problemSlug);
  const [sessionResponse, setSessionResponse] = useState<PracticeSessionResponse>();
  const [messages, setMessages] = useState<PracticeMessage[]>([]);
  const [composerValue, setComposerValue] = useState('');
  const [status, setStatus] = useState<'loading' | 'idle' | 'streaming' | 'blocked' | 'error'>('loading');
  const [error, setError] = useState('');
  const [completionUpdating, setCompletionUpdating] = useState(false);
  const [moreActionsOpen, setMoreActionsOpen] = useState(false);
  const [skipConfirmationOpen, setSkipConfirmationOpen] = useState(false);
  const [postRunRefreshing, setPostRunRefreshing] = useState(false);
  const [reviewHistory, setReviewHistory] = useState<PracticeCodeReviewHistoryResponse>();
  const [reviewHistoryLoading, setReviewHistoryLoading] = useState(false);
  const [reviewHistoryError, setReviewHistoryError] = useState('');
  const [pendingPermission, setPendingPermission] = useState<PendingPermissionState>();
  const [permissionNotice, setPermissionNotice] = useState('');
  const [, setPermissionCountdownTick] = useState(0);
  const [assistantWorkStates, setAssistantWorkStates] = useState<Record<number, AssistantWorkState>>({});
  const [coachSummaryApplyStates, setCoachSummaryApplyStates] = useState<Record<string, CoachSummaryApplyStatus>>({});
  const localMessageIdRef = useRef(-1);
  const streamControllerRef = useRef<AbortController | null>(null);
  const activeSessionIdRef = useRef<number | undefined>(undefined);
  const pendingPermissionIdRef = useRef<string | undefined>(undefined);
  const moreActionsRef = useRef<HTMLSpanElement | null>(null);
  const messageListRef = useRef<HTMLElement | null>(null);
  const shouldAutoScrollRef = useRef(true);
  const submittingRef = useRef(false);
  const practiceLoadTokenRef = useRef(0);
  const coachSummaryProposalMessageIdsRef = useRef(new Set<number>());

  useEffect(() => {
    const controller = new AbortController();
    practiceLoadTokenRef.current += 1;
    const activeLoadToken = practiceLoadTokenRef.current;
    streamControllerRef.current?.abort();
    streamControllerRef.current = null;
    submittingRef.current = false;
    setSessionResponse(undefined);
    setMessages([]);
    setError('');
    setCompletionUpdating(false);
    setMoreActionsOpen(false);
    setSkipConfirmationOpen(false);
    setReviewHistory(undefined);
    setReviewHistoryError('');
    setReviewHistoryLoading(false);
    setPendingPermission(undefined);
    pendingPermissionIdRef.current = undefined;
    setPermissionNotice('');
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

  const sessionId = sessionResponse?.session.id;
  activeSessionIdRef.current = sessionId;
  const hasActiveRun = Boolean(sessionResponse?.activeRun);
  const progressStatus = sessionResponse?.session.progressStatus;
  const completionGate = sessionResponse?.completionGate;
  const leetcodeUrl = localizedLeetCodeUrl(sessionResponse?.problem.leetcodeUrl, locale);
  const difficulty = sessionResponse?.problem.difficulty ?? problem?.difficulty;
  const shouldShowCompletionButton = Boolean(sessionId) && progressStatus !== 'COMPLETED';
  const shouldShowSkipButton = Boolean(sessionId) && progressStatus !== 'COMPLETED' && progressStatus !== 'SKIPPED';
  const completionDisabled = !completionGate?.canComplete
    || completionUpdating
    || postRunRefreshing
    || status === 'loading'
    || status === 'streaming'
    || hasActiveRun;
  const completionDisabledReason = completionGate && !completionGate.canComplete
    ? resources.learningPlans.completionGateMessages[completionGate.reasonCode]
      || completionGate.message
      || resources.learningPlans.completionGateFallback
    : undefined;
  const composerInputDisabled = !sessionId || status === 'loading' || hasActiveRun;
  const sendDisabled = !sessionId || status === 'loading' || status === 'streaming' || hasActiveRun || !composerValue.trim();
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
  const pendingPermissionProblem = pendingPermission
    ? permissionProblemLabel(pendingPermission.preview, sessionResponse, problem, locale)
    : undefined;
  const permissionCopy = pendingPermission
    ? permissionRequestCopy(pendingPermission.request, resources)
    : undefined;
  const permissionSeconds = pendingPermission
    ? permissionRemainingSeconds(pendingPermission.request.expiresAt)
    : undefined;
  const permissionExpired = permissionSeconds === 0;
  const permissionUrgent = permissionSeconds !== undefined && permissionSeconds <= TOOL_PERMISSION_URGENT_SECONDS;
  const permissionCountdownAngle = permissionSeconds !== undefined
    && pendingPermission?.initialRemainingSeconds
    && pendingPermission.initialRemainingSeconds > 0
    ? Math.min(360, Math.max(0, (permissionSeconds / pendingPermission.initialRemainingSeconds) * 360))
    : 0;
  const permissionCountdownStyle = {
    '--permission-countdown-angle': `${permissionCountdownAngle}deg`,
  } as CSSProperties;

  useEffect(() => {
    if (!pendingPermission || permissionRemainingSeconds(pendingPermission.request.expiresAt) === undefined) {
      return undefined;
    }

    const intervalId = window.setInterval(() => {
      setPermissionCountdownTick((current) => current + 1);
    }, TOOL_PERMISSION_COUNTDOWN_REFRESH_MS);
    return () => window.clearInterval(intervalId);
  }, [pendingPermission?.request.expiresAt, pendingPermission?.request.permissionRequestId]);

  useEffect(() => {
    if (!sessionId || !hasActiveRun) {
      return undefined;
    }

    let stopped = false;
    const controller = new AbortController();

    async function poll() {
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

  useLayoutEffect(() => {
    const messageList = messageListRef.current;
    if (messageList && shouldAutoScrollRef.current) {
      messageList.scrollTop = messageList.scrollHeight;
    }
  }, [assistantWorkStates, messages, error, status]);

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

  function markAssistantMessageFailed(
    assistantMessageId: number,
    contentMarkdown = resources.learningPlans.replyFailed,
  ) {
    setAssistantWorkStates((current) => {
      if (!(assistantMessageId in current)) {
        return current;
      }
      const { [assistantMessageId]: _removed, ...remaining } = current;
      return remaining;
    });
    setMessages((current) => current.map((message) => (
      message.id === assistantMessageId
        ? {
            ...message,
            contentMarkdown,
            messageType: 'CHAT',
          }
        : message
    )));
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

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const text = composerValue.trim();

    if (!text || !sessionId || status === 'loading' || status === 'streaming' || submittingRef.current) {
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
    setPermissionNotice('');
    setAssistantWorkState(assistantMessageId, { status: 'ORGANIZING' });
    setStatus('streaming');
    setComposerValue('');
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

    let agentRunEnded = false;
    let reviewRefreshRequested = false;

    try {
      await streamPracticeMessage(sessionId, { message: text }, {
        idempotencyKey: nextIdempotencyKey(),
        signal: controller.signal,
        onEvent: (event) => {
          if (event.eventName === 'content_delta') {
            const delta = readContentDelta(event.data);
            if (!delta) {
              return;
            }

            appendAssistantContent(assistantMessageId, delta);
          }

          if (event.eventName === 'agent_run_end') {
            agentRunEnded = true;
            setPostRunRefreshing(true);
            setSessionResponse((current) => current ? { ...current, activeRun: null } : current);
          }

          if (event.eventName === 'agent_tool_end') {
            const toolEnd = readAgentToolEndEvent(event.data);
            if (!toolEnd) {
              return;
            }

            if (toolEnd.toolName === LEARNER_DECLARED_PROFILE_TOOL_NAME) {
              const key = learnerDeclaredProfileToolEventKey(toolEnd);
              const result = parseLearnerDeclaredProfileToolResult(toolEnd.result);
              if (key && result) {
                updateLearnerProfileToolStatus(assistantMessageId, key, result.status);
              }
              return;
            }

            if (toolEnd.toolName === COACH_SUMMARY_PROPOSAL_TOOL_NAME) {
              const action = readCoachSummaryProposalAction(toolEnd.result, now);
              const summaryMarkdown = readCoachSummaryMarkdown(toolEnd.result);
              if (action && summaryMarkdown) {
                showCoachSummaryProposal(assistantMessageId, action, summaryMarkdown);
              }
              return;
            }

            if (toolEnd.toolName !== REVIEW_TOOL_NAME) {
              return;
            }

            const resultType = readResultType(toolEnd.result);
            if (resultType === REVIEW_SUBMITTED_RESULT_TYPE && isSavedReviewResult(toolEnd.result)) {
              reviewRefreshRequested = true;
              setPostRunRefreshing(true);
              const scoreSummary = reviewToolScoreSummary(toolEnd.result);
              if (scoreSummary) {
                appendAssistantContent(assistantMessageId, `${scoreSummary}\n\n`);
              }
            }
            if (resultType === TOOL_PERMISSION_DENIED_RESULT_TYPE
              || resultType === TOOL_PERMISSION_TIMEOUT_RESULT_TYPE) {
              return;
            }
          }

          if (event.eventName === 'agent_tool_start') {
            const toolStart = readAgentToolStartEvent(event.data);
            if (!toolStart) {
              return;
            }

            if (toolStart.toolName === LEARNER_DECLARED_PROFILE_TOOL_NAME) {
              const key = learnerDeclaredProfileToolEventKey(toolStart);
              if (key) {
                updateLearnerProfileToolStatus(assistantMessageId, key, 'RUNNING');
              }
              return;
            }

            if (toolStart.toolName !== REVIEW_TOOL_NAME) {
              return;
            }

            setAssistantWorkState(assistantMessageId, { status: 'REVIEW_RUNNING' });
          }

          if (event.eventName === 'tool_permission_request') {
            const permissionRequest = readPermissionRequestEvent(event.data);
            if (!permissionRequest) {
              return;
            }

            setPermissionNotice('');
            pendingPermissionIdRef.current = permissionRequest.permissionRequestId;
            setPendingPermission({
              request: permissionRequest,
              preview: readPermissionPreview(permissionRequest.preview),
              initialRemainingSeconds: permissionRemainingSeconds(permissionRequest.expiresAt),
              submitting: false,
              error: '',
            });
          }

          if (event.eventName === 'tool_permission_decision') {
            const permissionRequestId = readPermissionRequestId(event.data);
            if (!permissionRequestId) {
              return;
            }

            if (pendingPermissionIdRef.current === permissionRequestId) {
              pendingPermissionIdRef.current = undefined;
              setPendingPermission(undefined);
            }
          }

          if (event.eventName === 'tool_permission_timeout') {
            const permissionRequestId = readPermissionRequestId(event.data);
            if (!permissionRequestId) {
              return;
            }

            if (pendingPermissionIdRef.current === permissionRequestId) {
              pendingPermissionIdRef.current = undefined;
              setPermissionNotice(resources.learningPlans.toolPermissionTimeoutNotice);
              setPendingPermission(undefined);
            }
          }

          if (event.eventName === 'error' || event.eventName === 'agent_error') {
            setError(resources.learningPlans.practiceMessageFailed);
            markAssistantMessageFailed(assistantMessageId);
            setStatus('error');
          }
        },
      });

      if (controller.signal.aborted) {
        return;
      }

      if (agentRunEnded) {
        const activeLoadToken = practiceLoadTokenRef.current;
        try {
          await refreshMessages(sessionId, activeLoadToken, controller.signal, assistantMessageId);
          await refreshSession(sessionId, activeLoadToken, controller.signal);
          await refreshReviews(sessionId, activeLoadToken, controller.signal);
          setStatus('idle');
        } finally {
          if (!controller.signal.aborted && isCurrentSession(sessionId, activeLoadToken)) {
            setPostRunRefreshing(false);
          }
        }
      } else {
        if (reviewRefreshRequested) {
          setPostRunRefreshing(false);
        }
        setError(resources.learningPlans.practiceMessageFailed);
        markAssistantMessageFailed(assistantMessageId);
        setStatus('error');
      }
    } catch (error) {
      if (!controller.signal.aborted) {
        setPostRunRefreshing(false);
        if (error instanceof ApiRequestError && error.code === AGENT_RUN_IN_PROGRESS_CODE) {
          setError('');
          setMessages((current) => current.filter((message) => message.id !== assistantMessageId));
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

        setError(error instanceof Error ? error.message : resources.learningPlans.practiceMessageFailed);
        markAssistantMessageFailed(assistantMessageId);
        setStatus('error');
      }
    } finally {
      clearTransientAssistantWorkState(assistantMessageId);
      if (streamControllerRef.current === controller) {
        streamControllerRef.current = null;
        submittingRef.current = false;
      }
    }
  }

  async function handleMarkCompleted() {
    if (!sessionId
      || completionUpdating
      || progressStatus === 'COMPLETED'
      || status === 'loading'
      || status === 'streaming'
      || hasActiveRun
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

  async function handlePermissionDecision(decision: AgentToolPermissionDecisionType) {
    if (!pendingPermission || pendingPermission.submitting || permissionExpired) {
      return;
    }

    const permissionRequestId = pendingPermission.request.permissionRequestId;
    setPendingPermission((current) => current?.request.permissionRequestId === permissionRequestId
      ? { ...current, submitting: true, error: '' }
      : current);

    try {
      const response = await decideAgentToolPermission(permissionRequestId, {
        decision,
        reason: decision === 'ALLOW' ? 'user_confirmed' : 'user_rejected',
      });
      if (!response.success) {
        throw new Error(response.error?.message ?? resources.learningPlans.toolPermissionDecisionFailed);
      }
    } catch (error) {
      setPendingPermission((current) => current?.request.permissionRequestId === permissionRequestId
        ? {
            ...current,
            submitting: false,
            error: error instanceof Error ? error.message : resources.learningPlans.toolPermissionDecisionFailed,
          }
        : current);
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
          {shouldShowCompletionButton && (
            <span
              className={`completion-button-wrap ${completionDisabledReason ? 'has-tooltip' : ''}`}
            >
              <button
                className="secondary-button compact"
                aria-describedby={completionDisabledReason ? 'completion-disabled-tooltip' : undefined}
                disabled={completionDisabled}
                onClick={handleMarkCompleted}
                type="button"
              >
                <CheckCircle2 aria-hidden="true" />
                <span>{resources.learningPlans.markCompleted}</span>
              </button>
              {completionDisabledReason && (
                <span
                  className="toolbar-tooltip completion-disabled-tooltip"
                  id="completion-disabled-tooltip"
                  role="tooltip"
                >
                  {completionDisabledReason}
                </span>
              )}
            </span>
          )}
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
        {permissionNotice && <p className="status-note practice-status-note" role="status">{permissionNotice}</p>}
        {status === 'loading' && (
          <article className="practice-message assistant-message">
            <span>{resources.learningPlans.coach}</span>
            <MarkdownView content={resources.learningPlans.loadingStatement} />
          </article>
        )}
        {status !== 'loading' && messages.length === 0 && (
          <article className="practice-message assistant-message">
            <span>{resources.learningPlans.coach}</span>
            <MarkdownView content={resources.learningPlans.statementUnavailable} />
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
            ) : message.contentMarkdown ? <MarkdownView content={message.contentMarkdown} /> : null}
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
        {hasActiveRun && (
          <article className="practice-message assistant-message">
            <span>{resources.learningPlans.coach}</span>
            <p
              aria-label={coachWorkStatusLabel('ORGANIZING', resources)}
              className={coachWorkStatusClassName('ORGANIZING')}
              role="status"
            >
              <span className="practice-coach-work-status-text">
                {coachWorkStatusLabel('ORGANIZING', resources)}
              </span>
            </p>
          </article>
        )}
      </section>

      {(reviewHistoryLoading || reviewHistoryError || reviewHistory) && (
        <span className="visually-hidden" aria-live="polite">
          {reviewHistoryLoading
            ? resources.learningPlans.reviewLoading
            : reviewHistoryError || resources.learningPlans.reviewHistory}
        </span>
      )}

      <form className="practice-composer" aria-label={resources.learningPlans.sendMessage} onSubmit={handleSubmit}>
        <textarea
          aria-label={resources.learningPlans.composerLabel}
          disabled={composerInputDisabled}
          onChange={(event) => setComposerValue(event.target.value)}
          placeholder={resources.learningPlans.practiceComposerPlaceholderReview}
          value={composerValue}
        />
        <button className="primary-button compact" disabled={sendDisabled} type="submit">
          {resources.learningPlans.send}
        </button>
      </form>

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

      {pendingPermission && (
        <div className="modal-backdrop practice-permission-backdrop">
          <section
            aria-labelledby="practice-permission-title"
            aria-modal="true"
            className="practice-permission-modal"
            role="dialog"
          >
            <div className="practice-permission-heading">
              <p className="eyebrow">{resources.learningPlans.toolPermissionEyebrow}</p>
              <h3 id="practice-permission-title">{permissionCopy?.title}</h3>
              <p>{permissionCopy?.reason}</p>
            </div>

            {permissionSeconds !== undefined && (
              <div
                aria-label={permissionExpired
                  ? `${resources.learningPlans.toolPermissionExpired}. ${resources.learningPlans.toolPermissionExpiredHint}`
                  : `${resources.learningPlans.toolPermissionCountdownLabel} ${formatPermissionCountdown(permissionSeconds)}. ${resources.learningPlans.toolPermissionCountdownHint}`}
                className={`practice-permission-countdown ${permissionUrgent ? 'is-urgent' : ''} ${permissionExpired ? 'is-expired' : ''}`}
                role="timer"
              >
                <div className="practice-permission-clock" style={permissionCountdownStyle}>
                  <div className="practice-permission-clock-face">
                    <Clock aria-hidden="true" />
                    <time dateTime={`PT${permissionSeconds}S`}>{formatPermissionCountdown(permissionSeconds)}</time>
                  </div>
                </div>
                <div className="practice-permission-countdown-copy">
                  <strong>{permissionExpired
                    ? resources.learningPlans.toolPermissionExpired
                    : resources.learningPlans.toolPermissionCountdownLabel}</strong>
                  <span>{permissionExpired
                    ? resources.learningPlans.toolPermissionExpiredHint
                    : resources.learningPlans.toolPermissionCountdownHint}</span>
                </div>
              </div>
            )}

            {pendingPermissionProblem && (
              <div className="practice-permission-problem">
                <span>{resources.learningPlans.toolPermissionProblem}</span>
                <strong>{pendingPermissionProblem}</strong>
              </div>
            )}

            {pendingPermission.preview.contextAvailable === false && (
              <div className="practice-permission-context-warning" role="note">
                <AlertCircle aria-hidden="true" />
                <span>{resources.learningPlans.toolPermissionContextWarning}</span>
              </div>
            )}

            {pendingPermission.preview.codePreview && (
              <div className="practice-permission-code">
                <strong>{resources.learningPlans.toolPermissionCodePreview}</strong>
                <pre><code>{pendingPermission.preview.codePreview}</code></pre>
              </div>
            )}

            <p className="practice-permission-effect">
              <CheckCircle2 aria-hidden="true" />
              <span>{resources.learningPlans.toolPermissionEffectSummary}</span>
            </p>

            {pendingPermission.error && (
              <p className="error-text practice-permission-error" role="alert">{pendingPermission.error}</p>
            )}

            <div className="modal-actions practice-permission-actions">
              <button
                className="secondary-button compact"
                disabled={pendingPermission.submitting || permissionExpired}
                onClick={() => void handlePermissionDecision('DENY')}
                type="button"
              >
                {resources.learningPlans.toolPermissionDeny}
              </button>
              <button
                className="primary-button compact"
                disabled={pendingPermission.submitting || permissionExpired}
                onClick={() => void handlePermissionDecision('ALLOW')}
                type="button"
              >
                {resources.learningPlans.toolPermissionAllow}
              </button>
            </div>
          </section>
        </div>
      )}
    </article>
  );
}
