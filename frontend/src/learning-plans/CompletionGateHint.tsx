import { LockKeyhole, ShieldCheck } from 'lucide-react';
import type { LocaleResources } from '../i18n/locales';
import type { PracticeCompletionGate, PracticeCodeReviewSummary } from '../types/api';
import ReviewScoreBadge from './ReviewScoreBadge';

interface CompletionGateHintProps {
  gate?: PracticeCompletionGate | null;
  latestReview?: PracticeCodeReviewSummary | null;
  resources: LocaleResources;
}

export default function CompletionGateHint({ gate, latestReview, resources }: CompletionGateHintProps) {
  const canComplete = Boolean(gate?.canComplete);
  const message = gate?.message || resources.learningPlans.completionGateFallback;

  return (
    <aside className={`completion-gate-hint ${canComplete ? 'ready' : 'blocked'}`}>
      <div className="completion-gate-icon" aria-hidden="true">
        {canComplete ? <ShieldCheck /> : <LockKeyhole />}
      </div>
      <div className="completion-gate-copy">
        <p>{message}</p>
        <div className="completion-gate-meta">
          {latestReview ? (
            <ReviewScoreBadge
              passed={latestReview.passed}
              passScore={gate?.passScore}
              resources={resources}
              score={latestReview.totalScore}
            />
          ) : (
            <span className="review-empty-inline">{resources.learningPlans.reviewNoReview}</span>
          )}
          {gate?.passScore !== undefined && (
            <span>{resources.learningPlans.reviewPassScoreLabel(gate.passScore)}</span>
          )}
        </div>
      </div>
    </aside>
  );
}
