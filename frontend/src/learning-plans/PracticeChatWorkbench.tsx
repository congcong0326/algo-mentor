import { ArrowLeft, ExternalLink, Info } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import type { FormEvent, MutableRefObject } from 'react';
import MarkdownView from '../components/MarkdownView';
import { formatDifficulty, formatProblemTitle } from '../i18n/formatters';
import { useI18n } from '../i18n/I18nProvider';
import type { SupportedLocale } from '../i18n/locales';
import { getProblemDetail, streamAgentConversation } from '../services/api';
import type {
  ContentDeltaData,
  LearningPlanDetailResponse,
  LearningPlanProblemDraft,
  ProblemDetail,
} from '../types/api';
import { generateClientId } from '../utils/id';

const LEETCODE_HOST_BY_LOCALE: Record<SupportedLocale, string> = {
  'zh-CN': 'leetcode.cn',
  'en-US': 'leetcode.com',
};

const LEETCODE_HOSTS = new Set(['leetcode.cn', 'www.leetcode.cn', 'leetcode.com', 'www.leetcode.com']);

interface PracticeMessage {
  id: string;
  role: 'assistant' | 'user';
  content: string;
}

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
  const [problemDetail, setProblemDetail] = useState<ProblemDetail>();
  const [problemError, setProblemError] = useState('');
  const [composerValue, setComposerValue] = useState('');
  const [messages, setMessages] = useState<PracticeMessage[]>([]);
  const [streaming, setStreaming] = useState(false);
  const messageSequenceRef = useRef(0);
  const streamControllerRef = useRef<AbortController | undefined>(undefined);

  useEffect(() => {
    const controller = new AbortController();
    setProblemDetail(undefined);
    setProblemError('');

    getProblemDetail(problemSlug, locale, controller.signal)
      .then((response) => {
        if (!response.success || !response.data) {
          throw new Error(response.error?.message ?? resources.learningPlans.detailLoadProblemFailed);
        }
        setProblemDetail(response.data);
      })
      .catch((error) => {
        if (!controller.signal.aborted) {
          setProblemError(error instanceof Error ? error.message : resources.learningPlans.detailLoadProblemFailed);
        }
      });

    return () => controller.abort();
  }, [locale, problemSlug, resources.learningPlans.detailLoadProblemFailed]);

  useEffect(() => () => streamControllerRef.current?.abort(), []);

  useEffect(() => {
    streamControllerRef.current?.abort();
    streamControllerRef.current = undefined;
    setComposerValue('');
    setMessages([]);
    setStreaming(false);
  }, [locale, phaseIndex, plan.id, problemSlug]);

  const statement = problemDetail?.contentMarkdown.trim() || resources.learningPlans.statementUnavailable;
  const leetcodeUrl = localizedLeetCodeUrl(problemDetail?.leetcodeUrl, locale);

  async function sendMessage(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const message = composerValue.trim();
    if (!message || streaming) {
      return;
    }

    const controller = new AbortController();
    const assistantMessageId = nextMessageId(messageSequenceRef, 'assistant');
    streamControllerRef.current = controller;
    setStreaming(true);
    setComposerValue('');
    setMessages((current) => [
      ...current,
      { id: nextMessageId(messageSequenceRef, 'user'), role: 'user', content: message },
    ]);

    try {
      await streamAgentConversation({
        message,
        practice: {
          planId: plan.id,
          phaseIndex,
          problemSlug,
          locale,
        },
      }, {
        idempotencyKey: generateClientId(),
        signal: controller.signal,
        onEvent: (streamEvent) => {
          if (streamEvent.eventName !== 'content_delta') {
            return;
          }
          const data = streamEvent.data as ContentDeltaData;
          if (!data.content) {
            return;
          }
          setMessages((current) => appendAssistantDelta(current, assistantMessageId, data.content ?? ''));
        },
      });
    } catch (error) {
      if (!controller.signal.aborted) {
        const errorMessage = error instanceof Error ? error.message : resources.learningPlans.practiceChatFailed;
        setMessages((current) => appendAssistantDelta(current, assistantMessageId, errorMessage));
      }
    } finally {
      if (!controller.signal.aborted) {
        setStreaming(false);
      }
      if (streamControllerRef.current === controller) {
        streamControllerRef.current = undefined;
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
              {problemLabel(problem, locale, resources.learningPlans.problemTraining)}
            </h2>
          </div>
        </div>
        <div className="practice-toolbar-actions">
          <span className={`difficulty-badge ${String(problem?.difficulty ?? '').toLowerCase()}`}>
            {formatDifficulty(problem?.difficulty, resources)}
          </span>
          <span className="status-badge">{resources.learningPlans.notStarted}</span>
          {leetcodeUrl && (
            <>
              <span
                aria-label={resources.learningPlans.practiceLeetCodeGuidance}
                className="icon-button practice-guidance-icon"
                role="img"
                tabIndex={0}
                title={resources.learningPlans.practiceLeetCodeGuidance}
              >
                <Info aria-hidden="true" />
              </span>
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
            </>
          )}
        </div>
      </header>

      <section className="practice-message-list" aria-label={resources.learningPlans.chatMessages}>
        <article className="practice-message assistant-message">
          <span>{resources.learningPlans.coach}</span>
          <MarkdownView content={problemError || (problemDetail ? statement : resources.learningPlans.loadingStatement)} />
        </article>
        {messages.map((message) => (
          <article className={`practice-message ${message.role}-message`} key={message.id}>
            <span>{message.role === 'assistant' ? resources.learningPlans.coach : resources.learningPlans.learner}</span>
            <MarkdownView content={message.content} />
          </article>
        ))}
      </section>

      <form className="practice-composer" aria-label={resources.learningPlans.sendMessage} onSubmit={sendMessage}>
        <textarea
          aria-label={resources.learningPlans.composerLabel}
          disabled={streaming}
          onChange={(event) => setComposerValue(event.target.value)}
          placeholder={resources.learningPlans.composerPlaceholder}
          rows={2}
          value={composerValue}
        />
        <button className="primary-button compact" disabled={streaming || !composerValue.trim()} type="submit">
          {resources.learningPlans.send}
        </button>
      </form>
    </article>
  );
}

function appendAssistantDelta(messages: PracticeMessage[], assistantMessageId: string, delta: string): PracticeMessage[] {
  const lastMessage = messages[messages.length - 1];
  if (lastMessage?.id === assistantMessageId) {
    return [
      ...messages.slice(0, -1),
      { ...lastMessage, content: `${lastMessage.content}${delta}` },
    ];
  }

  return [
    ...messages,
    { id: assistantMessageId, role: 'assistant', content: delta },
  ];
}

function nextMessageId(sequenceRef: MutableRefObject<number>, prefix: string): string {
  sequenceRef.current += 1;
  return `${prefix}-${sequenceRef.current}`;
}
