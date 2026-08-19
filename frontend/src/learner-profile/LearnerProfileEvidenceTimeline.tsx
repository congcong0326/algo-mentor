import { ArrowUpRight } from 'lucide-react';
import type { MouseEvent } from 'react';
import {
  LEARNER_PROFILE_REVIEW_ORIGIN,
  learningPlanPracticeSubmissionsPath,
} from '../app/navigation';
import { useI18n } from '../i18n/I18nProvider';
import { preloadPracticeSubmissionHistoryPage } from '../learning-plans/practiceSubmissionHistoryLazy';
import type { LearnerProfileEvidenceItem } from '../types/api';
import {
  formatLearnerProfileDateTime,
  learnerProfileEvidenceKey,
  learnerProfileRoleKey,
} from './profilePresentation';

interface LearnerProfileEvidenceTimelineProps {
  compact?: boolean;
  items: LearnerProfileEvidenceItem[];
  onNavigate?: (pathname: string) => void;
  profileAnchor?: string;
}

export default function LearnerProfileEvidenceTimeline({
  compact = false,
  items,
  onNavigate,
  profileAnchor,
}: LearnerProfileEvidenceTimelineProps) {
  const { locale, resources } = useI18n();

  function handleReviewClick(event: MouseEvent<HTMLAnchorElement>, pathname: string) {
    if (
      !onNavigate
      || event.button !== 0
      || event.metaKey
      || event.ctrlKey
      || event.shiftKey
      || event.altKey
    ) {
      return;
    }
    event.preventDefault();
    onNavigate(pathname);
  }

  return (
    <ol className={`learner-profile-evidence-timeline${compact ? ' compact' : ''}`}>
      {items.map((item) => {
        const reviewPath = item.type === 'CODE_REVIEW' && !compact
          ? learningPlanPracticeSubmissionsPath(
            item.codeReview.planId,
            item.codeReview.phaseIndex,
            item.codeReview.problemSlug,
            {
              reviewId: item.codeReview.reviewId,
              from: LEARNER_PROFILE_REVIEW_ORIGIN,
              profileAnchor,
            },
          )
          : undefined;

        return (
          <li className={`learner-profile-evidence-item ${item.type.toLowerCase()}`} key={learnerProfileEvidenceKey(item)}>
            <div className="learner-profile-evidence-meta">
              <span>{resources.myPage.evidenceRoles[learnerProfileRoleKey(item)]}</span>
              <time dateTime={item.occurredAt}>{formatLearnerProfileDateTime(item.occurredAt, locale)}</time>
            </div>
            {item.type === 'CODE_REVIEW' ? (
              <>
                <strong>{item.codeReview.problemTitle}</strong>
                <p>
                  {resources.myPage.reviewEvidenceSummary(
                    item.codeReview.versionNo,
                    item.codeReview.totalScore,
                    item.codeReview.passed,
                  )}
                </p>
                {reviewPath ? (
                  <a
                    className="learner-profile-review-link"
                    href={reviewPath}
                    onClick={(event) => handleReviewClick(event, reviewPath)}
                    onFocus={preloadPracticeSubmissionHistoryPage}
                    onMouseEnter={preloadPracticeSubmissionHistoryPage}
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
        );
      })}
    </ol>
  );
}
