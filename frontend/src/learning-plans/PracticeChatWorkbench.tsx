import { ArrowLeft, CheckCircle2, ClipboardList, ExternalLink, Info } from 'lucide-react';
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import MarkdownView from '../components/MarkdownView';
import { formatDifficulty, formatProblemTitle } from '../i18n/formatters';
import { useI18n } from '../i18n/I18nProvider';
import type { LocaleResources, SupportedLocale } from '../i18n/locales';
import {
  ApiRequestError,
  createOrReusePracticeSession,
  getPracticeSessionActiveRun,
  getPracticeSessionMessages,
  streamPracticeMessage,
  updatePracticeProgressStatus,
} from '../services/api';
import type {
  LearningPlanDetailResponse,
  LearningPlanProblemDraft,
  PracticeMessage,
  PracticeProgressStatus,
  PracticeSessionResponse,
} from '../types/api';
import { AGENT_RUN_IN_PROGRESS_CODE } from '../types/api';
import CompletionGateHint from './CompletionGateHint';
import ReviewHistoryDrawer from './ReviewHistoryDrawer';

const LEETCODE_HOST_BY_LOCALE: Record<SupportedLocale, string> = {
  'zh-CN': 'leetcode.cn',
  'en-US': 'leetcode.com',
};

const LEETCODE_HOSTS = new Set(['leetcode.cn', 'www.leetcode.cn', 'leetcode.com', 'www.leetcode.com']);
const AUTO_SCROLL_THRESHOLD_PX = 96;
const ACTIVE_RUN_POLL_INTERVAL_MS = 3000;

function problemLabel(problem: LearningPlanProblemDraft | undefined, locale: SupportedLocale, fallback: string): string {
  if (!problem) {
    return fallback;
  }
  const id = problem.frontendId ? `${problem.frontendId}. ` : '';
  return `${id}${formatProblemTitle(problem, locale)}`;
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

function nextIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }

  return `practice-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export default function PracticeChatWorkbench({
  onBack,
  phaseIndex,
  plan,
  problemSlug,
}: {
  onBack: () => void;
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
  const [reviewHistoryOpen, setReviewHistoryOpen] = useState(false);
  const localMessageIdRef = useRef(-1);
  const streamControllerRef = useRef<AbortController | null>(null);
  const activeSessionIdRef = useRef<number | undefined>(undefined);
  const messageListRef = useRef<HTMLElement | null>(null);
  const shouldAutoScrollRef = useRef(true);
  const submittingRef = useRef(false);
  const practiceLoadTokenRef = useRef(0);

  useEffect(() => {
    const controller = new AbortController();
    practiceLoadTokenRef.current += 1;
    streamControllerRef.current?.abort();
    streamControllerRef.current = null;
    submittingRef.current = false;
    setSessionResponse(undefined);
    setMessages([]);
    setError('');
    setCompletionUpdating(false);
    setStatus('loading');

    createOrReusePracticeSession(plan.id, phaseIndex, problemSlug, locale, controller.signal)
      .then((response) => {
        if (!response.success || !response.data) {
          throw new Error(response.error?.message ?? resources.learningPlans.practiceSessionLoadFailed);
        }
        setSessionResponse(response.data);
        setMessages(response.data.messages);
        if (!response.data.activeRun) {
          void getPracticeSessionActiveRun(response.data.session.id, controller.signal)
            .then((activeRunResponse) => {
              if (controller.signal.aborted || !activeRunResponse.success || !activeRunResponse.data) {
                return;
              }
              setSessionResponse((current) => current && current.session.id === response.data!.session.id
                ? { ...current, activeRun: activeRunResponse.data }
                : current);
            })
            .catch(() => undefined);
        }
        setStatus('idle');
      })
      .catch((error) => {
        if (!controller.signal.aborted) {
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

  const sessionId = sessionResponse?.session.id;
  activeSessionIdRef.current = sessionId;
  const hasActiveRun = Boolean(sessionResponse?.activeRun);
  const progressStatus = sessionResponse?.session.progressStatus;
  const completionGate = sessionResponse?.completionGate;
  const latestReview = sessionResponse?.latestReview;
  const leetcodeUrl = localizedLeetCodeUrl(sessionResponse?.problem.leetcodeUrl, locale);
  const difficulty = sessionResponse?.problem.difficulty ?? problem?.difficulty;
  const canMarkCompleted = Boolean(sessionId)
    && progressStatus !== 'COMPLETED'
    && status !== 'streaming'
    && !hasActiveRun
    && (completionGate?.canComplete ?? true);
  const composerInputDisabled = !sessionId || status === 'loading' || hasActiveRun;
  const sendDisabled = !sessionId || status === 'loading' || status === 'streaming' || hasActiveRun || !composerValue.trim();

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
        const messagesResponse = await getPracticeSessionMessages(sessionId!, 50, controller.signal);
        if (stopped || controller.signal.aborted) {
          return;
        }
        if (messagesResponse.success && messagesResponse.data) {
          setMessages(messagesResponse.data);
        }
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
  }, [messages, error, reviewHistoryOpen, status]);

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
        contentMarkdown: resources.learningPlans.organizingThoughts,
        createdAt: now,
      },
    ]);

    let agentRunEnded = false;

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

            setMessages((current) => current.map((message) => {
              if (message.id !== assistantMessageId) {
                return message;
              }

              return {
                ...message,
                contentMarkdown: message.contentMarkdown === resources.learningPlans.organizingThoughts
                  ? delta
                  : `${message.contentMarkdown}${delta}`,
              };
            }));
          }

          if (event.eventName === 'agent_run_end') {
            agentRunEnded = true;
            setSessionResponse((current) => current ? { ...current, activeRun: null } : current);
            setStatus('idle');
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
        await refreshMessages(sessionId, controller.signal);
        setStatus('idle');
      } else {
        setError(resources.learningPlans.practiceMessageFailed);
        markAssistantMessageFailed(assistantMessageId);
        setStatus('error');
      }
    } catch (error) {
      if (!controller.signal.aborted) {
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
      if (streamControllerRef.current === controller) {
        streamControllerRef.current = null;
        submittingRef.current = false;
      }
    }
  }

  async function handleMarkCompleted() {
    if (!sessionId || completionUpdating || progressStatus === 'COMPLETED' || status === 'streaming') {
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
      if (!response.success) {
        throw new Error(response.error?.message ?? resources.learningPlans.progressUpdateFailed);
      }
      if (response.data) {
        setSessionResponse(response.data);
        setMessages(response.data.messages);
      } else {
        setSessionResponse((current) => current
          ? {
              ...current,
              session: {
                ...current.session,
                progressStatus: 'COMPLETED',
              },
            }
          : current);
      }
      setStatus('idle');
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

  async function refreshMessages(activeSessionId: number, signal?: AbortSignal) {
    const response = await getPracticeSessionMessages(activeSessionId, 50, signal);
    if (response.success && response.data) {
      setMessages(response.data);
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
              {problemLabel(problem, locale, resources.learningPlans.problemTraining)}
            </h2>
          </div>
        </div>
        <div className="practice-toolbar-actions">
          <span className={`difficulty-badge ${String(difficulty ?? '').toLowerCase()}`}>
            {formatDifficulty(difficulty, resources)}
          </span>
          <span className="status-badge">{progressStatusLabel(progressStatus, resources)}</span>
          {canMarkCompleted && (
            <button
              className="secondary-button compact"
              disabled={completionUpdating || status === 'loading'}
              onClick={handleMarkCompleted}
              type="button"
            >
              <CheckCircle2 aria-hidden="true" />
              <span>{resources.learningPlans.markCompleted}</span>
            </button>
          )}
          <button
            className="secondary-button compact"
            onClick={() => setReviewHistoryOpen((current) => !current)}
            type="button"
          >
            <ClipboardList aria-hidden="true" />
            <span>{resources.learningPlans.reviewHistory}</span>
          </button>
          <span
            aria-label={resources.learningPlans.practiceLeetCodeGuidance}
            className="icon-button practice-guidance-icon"
            role="img"
            tabIndex={0}
            title={resources.learningPlans.practiceLeetCodeGuidance}
          >
            <Info aria-hidden="true" />
          </span>
          {leetcodeUrl ? (
            <a
              aria-label={resources.learningPlans.openLeetCode}
              className="icon-button practice-leetcode-link"
              href={leetcodeUrl}
              rel="noreferrer"
              target="_blank"
              title={resources.learningPlans.openLeetCode}
            >
              <ExternalLink aria-hidden="true" />
            </a>
          ) : (
            <button
              aria-label={resources.learningPlans.leetcodeUnavailable}
              className="icon-button practice-leetcode-link"
              disabled
              title={resources.learningPlans.leetcodeUnavailable}
              type="button"
            >
              <ExternalLink aria-hidden="true" />
            </button>
          )}
        </div>
      </header>

      <section
        className="practice-message-list"
        aria-label={resources.learningPlans.chatMessages}
        onScroll={updateAutoScrollState}
        ref={messageListRef}
      >
        {status !== 'loading' && completionGate && (
          <CompletionGateHint gate={completionGate} latestReview={latestReview} resources={resources} />
        )}
        <ReviewHistoryDrawer
          open={reviewHistoryOpen}
          resources={resources}
          sessionId={sessionId}
        />
        {error && <p className="error-text practice-error" role="alert">{error}</p>}
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
            {message.contentMarkdown === resources.learningPlans.replyFailed
            || message.contentMarkdown === resources.learningPlans.practiceMessageBlocked ? (
              <p className="practice-message-failed">{message.contentMarkdown}</p>
            ) : (
              <MarkdownView content={message.contentMarkdown || resources.learningPlans.organizingThoughts} />
            )}
          </article>
        ))}
        {hasActiveRun && (
          <article className="practice-message assistant-message">
            <span>{resources.learningPlans.coach}</span>
            <MarkdownView content={resources.learningPlans.organizingThoughts} />
          </article>
        )}
      </section>

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
        {completionGate && !completionGate.canComplete && (
          <p className="practice-composer-hint">{resources.learningPlans.practiceComposerReviewHint}</p>
        )}
      </form>
    </article>
  );
}
