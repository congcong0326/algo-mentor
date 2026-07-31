import type {
  LearnerProfileEvidenceItem,
  LearnerProfileMessageRole,
  LearnerProfileReviewRole,
} from '../types/api';
import type { SupportedLocale } from '../i18n/locales';

export function formatLearnerProfileDateTime(value: string, locale: SupportedLocale): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat(locale, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

export function learnerProfileEvidenceKey(item: LearnerProfileEvidenceItem): string {
  return `${item.type}:${item.sourceId}`;
}

export function learnerProfileRoleKey(
  item: LearnerProfileEvidenceItem,
): LearnerProfileReviewRole | LearnerProfileMessageRole {
  return item.type === 'CODE_REVIEW' ? item.reviewRole : item.messageRole;
}
