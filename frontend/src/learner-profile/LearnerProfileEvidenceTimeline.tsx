import { ArrowUpRight } from 'lucide-react';
import {
  LEARNER_PROFILE_REVIEW_ORIGIN,
  learningPlanPracticeSubmissionsPath,
} from '../app/navigation';
import { useI18n } from '../i18n/I18nProvider';
import type { LearnerProfileEvidenceItem } from '../types/api';
import {
  formatLearnerProfileDateTime,
  learnerProfileEvidenceKey,
  learnerProfileRoleKey,
} from './profilePresentation';

interface LearnerProfileEvidenceTimelineProps {
  compact?: boolean;
  items: LearnerProfileEvidenceItem[];
  profileAnchor?: string;
}

export default function LearnerProfileEvidenceTimeline({
  compact = false,
  items,
  profileAnchor,
}: LearnerProfileEvidenceTimelineProps) {
  const { locale, resources } = useI18n();

  return (
    <ol className={`learner-profile-evidence-timeline${compact ? ' compact' : ''}`}>
      {items.map((item) => (
        <li className={`learner-profile-evidence-item ${item.type.toLowerCase()}`} key={learnerProfileEvidenceKey(item)}>
          <div className="learner-profile-evidence-meta">
            <span>{resources.myPage.evidenceRoles[learnerProfileRoleKey(item)]}</span>
            <time dateTime={item.occurredAt}>{formatLearnerProfileDateTime(item.occurredAt, locale)}</time>
          </div>
          {item.type === 'CODE_REVIEW' ? (
            <>
              <strong>{item.codeReview.problemSlug}</strong>
              <p>
                {resources.myPage.reviewEvidenceSummary(
                  item.codeReview.versionNo,
                  item.codeReview.totalScore,
                  item.codeReview.passed,
                )}
              </p>
              {!compact ? (
                <a
                  className="learner-profile-review-link"
                  href={learningPlanPracticeSubmissionsPath(
                  item.codeReview.planId,
                  item.codeReview.phaseIndex,
                  item.codeReview.problemSlug,
                  {
                    reviewId: item.codeReview.reviewId,
                    from: LEARNER_PROFILE_REVIEW_ORIGIN,
                    profileAnchor,
                  },
                )}
                >
                  <span>{resources.myPage.viewReviewSubmission}</span>
                  <ArrowUpRight aria-hidden="true" />
                </a>
              ) : null}
            </>
          ) : (
            <>
              <strong>{resources.myPage.messageEvidenceTitle(item.messageRole)}</strong>
              <p>{item.userMessage.excerpt}</p>
            </>
          )}
        </li>
      ))}
    </ol>
  );
}
