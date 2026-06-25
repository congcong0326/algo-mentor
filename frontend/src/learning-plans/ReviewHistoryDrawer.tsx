import { useEffect, useState } from 'react';
import type { LocaleResources } from '../i18n/locales';
import { getPracticeSessionReviewDetail, getPracticeSessionReviews } from '../services/api';
import type {
  PracticeCodeReviewDetail,
  PracticeCodeReviewHistoryResponse,
  PracticeCodeReviewSummary,
} from '../types/api';
import ReviewDetailPanel from './ReviewDetailPanel';
import ReviewVersionList from './ReviewVersionList';

interface ReviewHistoryDrawerProps {
  open: boolean;
  resources: LocaleResources;
  sessionId?: number;
}

export default function ReviewHistoryDrawer({ open, resources, sessionId }: ReviewHistoryDrawerProps) {
  const [history, setHistory] = useState<PracticeCodeReviewHistoryResponse>();
  const [selectedReviewId, setSelectedReviewId] = useState<number>();
  const [detail, setDetail] = useState<PracticeCodeReviewDetail>();
  const [historyStatus, setHistoryStatus] = useState<'idle' | 'loading' | 'error'>('idle');
  const [detailStatus, setDetailStatus] = useState<'idle' | 'loading' | 'error'>('idle');

  useEffect(() => {
    setHistory(undefined);
    setSelectedReviewId(undefined);
    setDetail(undefined);
    setDetailStatus('idle');

    if (!open || !sessionId) {
      setHistoryStatus('idle');
      return undefined;
    }

    const controller = new AbortController();
    setHistoryStatus('loading');

    getPracticeSessionReviews(sessionId, controller.signal)
      .then((response) => {
        if (!response.success || !response.data) {
          throw new Error(response.error?.message ?? resources.learningPlans.reviewLoadFailed);
        }
        setHistory(response.data);
        const firstReview = response.data.latestReview ?? response.data.reviews[0];
        setSelectedReviewId(firstReview?.id);
        setHistoryStatus('idle');
      })
      .catch((error) => {
        if (!controller.signal.aborted) {
          setHistory(undefined);
          setHistoryStatus('error');
          setSelectedReviewId(undefined);
          setDetail(undefined);
          void error;
        }
      });

    return () => controller.abort();
  }, [open, resources.learningPlans.reviewLoadFailed, sessionId]);

  useEffect(() => {
    if (!open || !sessionId || !selectedReviewId) {
      setDetail(undefined);
      return undefined;
    }

    const controller = new AbortController();
    setDetailStatus('loading');
    getPracticeSessionReviewDetail(sessionId, selectedReviewId, controller.signal)
      .then((response) => {
        if (!response.success || !response.data) {
          throw new Error(response.error?.message ?? resources.learningPlans.reviewDetailLoadFailed);
        }
        setDetail(response.data);
        setDetailStatus('idle');
      })
      .catch((error) => {
        if (!controller.signal.aborted) {
          setDetail(undefined);
          setDetailStatus('error');
          void error;
        }
      });

    return () => controller.abort();
  }, [open, resources.learningPlans.reviewDetailLoadFailed, selectedReviewId, sessionId]);

  if (!open) {
    return null;
  }

  const reviews = history?.reviews ?? [];
  const passScore = history?.completionGate.passScore;

  return (
    <aside className="practice-review-panel" aria-label={resources.learningPlans.reviewHistory} role="complementary">
      <header className="review-drawer-heading">
        <h3>{resources.learningPlans.reviewHistory}</h3>
      </header>

      {historyStatus === 'loading' && <p>{resources.learningPlans.reviewLoading}</p>}
      {historyStatus === 'error' && <p className="error-text" role="alert">{resources.learningPlans.reviewLoadFailed}</p>}
      {historyStatus === 'idle' && reviews.length === 0 && (
        <div className="review-empty-state">
          <h4>{resources.learningPlans.reviewEmptyTitle}</h4>
          <p>{resources.learningPlans.reviewEmptyDescription}</p>
        </div>
      )}
      {reviews.length > 0 && (
        <div className="review-drawer-content">
          <ReviewVersionList
            onSelect={(review: PracticeCodeReviewSummary) => setSelectedReviewId(review.id)}
            passScore={passScore}
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
              <ReviewDetailPanel detail={detail} passScore={passScore} resources={resources} />
            )}
          </div>
        </div>
      )}
    </aside>
  );
}
