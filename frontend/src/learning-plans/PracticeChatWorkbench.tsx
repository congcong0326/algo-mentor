import { ArrowLeft } from 'lucide-react';
import { useEffect, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize from 'rehype-sanitize';
import remarkGfm from 'remark-gfm';
import { formatDifficulty, formatProblemTitle } from '../i18n/formatters';
import { useI18n } from '../i18n/I18nProvider';
import type { SupportedLocale } from '../i18n/locales';
import { getProblemDetail } from '../services/api';
import type { LearningPlanDetailResponse, LearningPlanProblemDraft, ProblemDetail } from '../types/api';

function problemLabel(problem: LearningPlanProblemDraft | undefined, locale: SupportedLocale, fallback: string): string {
  if (!problem) {
    return fallback;
  }
  const id = problem.frontendId ? `${problem.frontendId}. ` : '';
  return `${id}${formatProblemTitle(problem, locale)}`;
}

function MarkdownMessage({ content }: { content: string }) {
  return (
    <div className="markdown-view">
      <ReactMarkdown rehypePlugins={[rehypeRaw, rehypeSanitize]} remarkPlugins={[remarkGfm]}>
        {content}
      </ReactMarkdown>
    </div>
  );
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

  useEffect(() => {
    const controller = new AbortController();
    setProblemDetail(undefined);
    setProblemError('');

    getProblemDetail(problemSlug, controller.signal)
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
  }, [problemSlug, resources.learningPlans.detailLoadProblemFailed]);

  const statement = problemDetail?.contentMarkdown.trim() || resources.learningPlans.statementUnavailable;

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
        </div>
      </header>

      <section className="practice-message-list" aria-label={resources.learningPlans.chatMessages}>
        <article className="practice-message assistant-message">
          <span>{resources.learningPlans.coach}</span>
          <MarkdownMessage content={problemError || (problemDetail ? statement : resources.learningPlans.loadingStatement)} />
        </article>
      </section>

      <form className="practice-composer" aria-label={resources.learningPlans.sendMessage}>
        <input
          aria-label={resources.learningPlans.composerLabel}
          disabled
          placeholder={resources.learningPlans.composerPlaceholder}
        />
        <button className="primary-button compact" disabled type="button">{resources.learningPlans.send}</button>
      </form>
    </article>
  );
}
