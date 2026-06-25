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
  history?: PracticeCodeReviewHistoryResponse;
  historyError?: string;
  historyLoading?: boolean;
  open: boolean;
  onHistoryLoaded?: (history: PracticeCodeReviewHistoryResponse) => void;
  resources: LocaleResources;
  sessionId?: number;
}

export default function ReviewHistoryDrawer({
  history: controlledHistory,
  historyError,
  historyLoading,
  onHistoryLoaded,
  open,
  resources,
  sessionId,
}: ReviewHistoryDrawerProps) {
  const [localHistory, setLocalHistory] = useState<PracticeCodeReviewHistoryResponse>();
  const [selectedReviewId, setSelectedReviewId] = useState<number>();
  const [manualSelection, setManualSelection] = useState(false);
  const [detail, setDetail] = useState<PracticeCodeReviewDetail>();
  const [localHistoryStatus, setLocalHistoryStatus] = useState<'idle' | 'loading' | 'error'>('idle');
  const [detailStatus, setDetailStatus] = useState<'idle' | 'loading' | 'error'>('idle');
  const history = controlledHistory ?? localHistory;
  const effectiveHistoryStatus = historyLoading
    ? 'loading'
    : historyError
      ? 'error'
      : controlledHistory !== undefined
        ? 'idle'
        : localHistoryStatus;

  useEffect(() => {
    setLocalHistory(undefined);
    setSelectedReviewId(undefined);
    setManualSelection(false);
    setDetail(undefined);
    setDetailStatus('idle');

    if (!open || !sessionId) {
      setLocalHistoryStatus('idle');
      return undefined;
    }
    if (onHistoryLoaded) {
      setLocalHistoryStatus('idle');
      return undefined;
    }

    const controller = new AbortController();
    setLocalHistoryStatus('loading');

    getPracticeSessionReviews(sessionId, controller.signal)
      .then((response) => {
        if (!response.success || !response.data) {
          throw new Error(response.error?.message ?? resources.learningPlans.reviewLoadFailed);
        }
        setLocalHistory(response.data);
        setLocalHistoryStatus('idle');
      })
      .catch((error) => {
        if (!controller.signal.aborted) {
          setLocalHistory(undefined);
          setLocalHistoryStatus('error');
          setSelectedReviewId(undefined);
          setDetail(undefined);
          void error;
        }
      });

    return () => controller.abort();
  }, [onHistoryLoaded, open, resources.learningPlans.reviewLoadFailed, sessionId]);

  useEffect(() => {
    if (!open) {
      return;
    }
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
  }, [history, manualSelection, open]);

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

      {effectiveHistoryStatus === 'loading' && <p>{resources.learningPlans.reviewLoading}</p>}
      {effectiveHistoryStatus === 'error' && (
        <p className="error-text" role="alert">{historyError || resources.learningPlans.reviewLoadFailed}</p>
      )}
      {effectiveHistoryStatus === 'idle' && reviews.length === 0 && (
        <div className="review-empty-state">
          <h4>{resources.learningPlans.reviewEmptyTitle}</h4>
          <p>{resources.learningPlans.reviewEmptyDescription}</p>
        </div>
      )}
      {reviews.length > 0 && (
        <div className="review-drawer-content">
          <ReviewVersionList
            onSelect={(review: PracticeCodeReviewSummary) => {
              setManualSelection(true);
              setSelectedReviewId(review.id);
            }}
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
