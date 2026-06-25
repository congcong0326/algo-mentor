import { CheckCircle2, CircleAlert } from 'lucide-react';
import type { LocaleResources } from '../i18n/locales';

interface ReviewScoreBadgeProps {
  passed: boolean;
  score?: number | null;
  passScore?: number;
  resources: LocaleResources;
}

export default function ReviewScoreBadge({
  passed,
  passScore,
  resources,
  score,
}: ReviewScoreBadgeProps) {
  const statusLabel = passed ? resources.learningPlans.reviewPassed : resources.learningPlans.reviewFailed;

  return (
    <span className={`review-score-badge ${passed ? 'passed' : 'failed'}`}>
      {passed ? <CheckCircle2 aria-hidden="true" /> : <CircleAlert aria-hidden="true" />}
      <span>{statusLabel}</span>
      {score !== undefined && score !== null && (
        <strong>{resources.learningPlans.reviewScoreText(score, passScore)}</strong>
      )}
    </span>
  );
}
