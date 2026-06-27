import { ArrowLeft, ExternalLink } from 'lucide-react';
import { useEffect, useState } from 'react';
import { formatDifficulty, formatProblemTitle } from '../i18n/formatters';
import { useI18n } from '../i18n/I18nProvider';
import type { SupportedLocale } from '../i18n/locales';
import {
  createOrReusePracticeSession,
  getPracticeSessionReviewDetail,
  getPracticeSessionReviews,
  requireApiData,
} from '../services/api';
import type {
  LearningPlanDetailResponse,
  PracticeCodeReviewDetail,
  PracticeCodeReviewHistoryResponse,
  PracticeCodeReviewSummary,
  PracticeProgressStatus,
  PracticeSessionResponse,
} from '../types/api';
import ReviewDetailPanel from './ReviewDetailPanel';
import ReviewVersionList from './ReviewVersionList';

const LEETCODE_HOST_BY_LOCALE: Record<SupportedLocale, string> = {
  'zh-CN': 'leetcode.cn',
  'en-US': 'leetcode.com',
};

const LEETCODE_HOSTS = new Set(['leetcode.cn', 'www.leetcode.cn', 'leetcode.com', 'www.leetcode.com']);

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

function progressStatusLabel(status: PracticeProgressStatus | undefined, resources: ReturnType<typeof useI18n>['resources']) {
  const labels: Record<PracticeProgressStatus, string> = {
    NOT_STARTED: resources.learningPlans.notStarted,
    IN_PROGRESS: resources.learningPlans.inProgress,
    COMPLETED: resources.learningPlans.completed,
    SKIPPED: resources.learningPlans.skipped,
  };

  return labels[status ?? 'NOT_STARTED'];
}

export default function PracticeSubmissionHistoryPage({
  onBackToChat,
  phaseIndex,
  plan,
  problemSlug,
}: {
  onBackToChat: () => void;
  phaseIndex: number;
  plan: LearningPlanDetailResponse;
  problemSlug: string;
}) {
  const { locale, resources } = useI18n();
  const phase = plan.phases.find((candidate) => candidate.phaseIndex === phaseIndex);
  const fallbackProblem = phase?.problems.find((candidate) => candidate.slug === problemSlug);
  const [sessionResponse, setSessionResponse] = useState<PracticeSessionResponse>();
  const [history, setHistory] = useState<PracticeCodeReviewHistoryResponse>();
  const [selectedReviewId, setSelectedReviewId] = useState<number>();
  const [manualSelection, setManualSelection] = useState(false);
  const [detail, setDetail] = useState<PracticeCodeReviewDetail>();
  const [pageStatus, setPageStatus] = useState<'loading' | 'idle' | 'error'>('loading');
  const [detailStatus, setDetailStatus] = useState<'idle' | 'loading' | 'error'>('idle');
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    setSessionResponse(undefined);
    setHistory(undefined);
    setSelectedReviewId(undefined);
    setManualSelection(false);
    setDetail(undefined);
    setDetailStatus('idle');
    setError('');
    setPageStatus('loading');

    createOrReusePracticeSession(plan.id, phaseIndex, problemSlug, locale, controller.signal)
      .then((response) => {
        const nextSession = requireApiData(response, resources.learningPlans.practiceSessionLoadFailed);
        if (controller.signal.aborted) {
          return undefined;
        }
        setSessionResponse(nextSession);
        return getPracticeSessionReviews(nextSession.session.id, controller.signal);
      })
      .then((response) => {
        if (!response || controller.signal.aborted) {
          return;
        }
        setHistory(requireApiData(response, resources.learningPlans.reviewLoadFailed));
        setPageStatus('idle');
      })
      .catch((nextError) => {
        if (!controller.signal.aborted) {
          setError(nextError instanceof Error ? nextError.message : resources.learningPlans.reviewLoadFailed);
          setPageStatus('error');
        }
      });

    return () => controller.abort();
  }, [
    locale,
    phaseIndex,
    plan.id,
    problemSlug,
    resources.learningPlans.practiceSessionLoadFailed,
    resources.learningPlans.reviewLoadFailed,
  ]);

  useEffect(() => {
    const firstReview = history?.latestReview ?? history?.reviews[0];
    setSelectedReviewId((current) => {
      if (!firstReview) {
        return undefined;
      }
      if (!manualSelection) {
        return firstReview.id;
      }
      return history?.reviews.some((review) => review.id === current) ? current : firstReview.id;
    });
  }, [history, manualSelection]);

  useEffect(() => {
    if (!sessionResponse?.session.id || !selectedReviewId) {
      setDetail(undefined);
      setDetailStatus('idle');
      return undefined;
    }

    const controller = new AbortController();
    setDetail(undefined);
    setDetailStatus('loading');

    getPracticeSessionReviewDetail(sessionResponse.session.id, selectedReviewId, controller.signal)
      .then((response) => {
        if (!controller.signal.aborted) {
          setDetail(requireApiData(response, resources.learningPlans.reviewDetailLoadFailed));
          setDetailStatus('idle');
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setDetailStatus('error');
        }
      });

    return () => controller.abort();
  }, [resources.learningPlans.reviewDetailLoadFailed, selectedReviewId, sessionResponse?.session.id]);

  const sessionProblem = sessionResponse?.problem;
  const problemTitle = sessionProblem
    ? `${sessionProblem.frontendId ? `${sessionProblem.frontendId}. ` : ''}${formatProblemTitle(sessionProblem, locale)}`
    : fallbackProblem
      ? `${fallbackProblem.frontendId ? `${fallbackProblem.frontendId}. ` : ''}${formatProblemTitle(fallbackProblem, locale)}`
      : resources.learningPlans.problemTraining;
  const difficulty = sessionProblem?.difficulty ?? fallbackProblem?.difficulty;
  const progressStatus = sessionResponse?.session.progressStatus;
  const leetcodeUrl = localizedLeetCodeUrl(sessionProblem?.leetcodeUrl, locale);
  const reviews = history?.reviews ?? [];

  return (
    <article className="practice-submissions-page" aria-labelledby="practice-submissions-title">
      <header className="practice-submissions-header">
        <div className="practice-toolbar-main">
          <button className="secondary-button compact detail-back-button" onClick={onBackToChat} type="button">
            <ArrowLeft aria-hidden="true" />
            <span>{resources.learningPlans.backToPracticeChat}</span>
          </button>
          <div>
            <p className="eyebrow">{phase?.title ?? resources.learningPlans.phaseFallback(phaseIndex)}</p>
            <h2 id="practice-submissions-title">{resources.learningPlans.reviewHistory}</h2>
            <p>{problemTitle}</p>
          </div>
        </div>
        <div className="practice-toolbar-actions">
          <span className={`difficulty-badge ${String(difficulty ?? '').toLowerCase()}`}>
            {formatDifficulty(difficulty, resources)}
          </span>
          <span className="status-badge">{progressStatusLabel(progressStatus, resources)}</span>
          {leetcodeUrl && (
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
          )}
        </div>
      </header>

      {pageStatus === 'loading' && <p className="practice-submissions-status">{resources.learningPlans.reviewLoading}</p>}
      {pageStatus === 'error' && <p className="error-text practice-submissions-status" role="alert">{error}</p>}
      {pageStatus === 'idle' && reviews.length === 0 && (
        <div className="review-empty-state">
          <h3>{resources.learningPlans.reviewEmptyTitle}</h3>
          <p>{resources.learningPlans.reviewEmptyDescription}</p>
        </div>
      )}
      {reviews.length > 0 && (
        <section className="practice-submissions-content" aria-label={resources.learningPlans.reviewHistory}>
          <ReviewVersionList
            onSelect={(review: PracticeCodeReviewSummary) => {
              setManualSelection(true);
              setSelectedReviewId(review.id);
            }}
            passScore={history?.completionGate.passScore}
            resources={resources}
            reviews={reviews}
            selectedReviewId={selectedReviewId}
          />
          <div className="review-detail-region">
            {detailStatus === 'loading' && <p>{resources.learningPlans.reviewDetailLoading}</p>}
            {detailStatus === 'error' && (
              <p className="error-text" role="alert">{resources.learningPlans.reviewDetailLoadFailed}</p>
            )}
            {detailStatus === 'idle' && detail && (
              <ReviewDetailPanel
                detail={detail}
                passScore={history?.completionGate.passScore}
                resources={resources}
              />
            )}
          </div>
        </section>
      )}
    </article>
  );
}
