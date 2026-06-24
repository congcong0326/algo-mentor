import type {
  LearningPlanDifficultyPreference,
  LearningPlanIntent,
  LearningPlanLevel,
  LearningPlanStatus,
  ProblemDifficulty,
  ProblemListItem,
} from '../types/api';
import type { LocaleResources, SupportedLocale } from './locales';

type ProblemLike = Pick<ProblemListItem, 'title' | 'titleCn'>;

export function formatDate(value: string, locale: SupportedLocale, options?: Intl.DateTimeFormatOptions): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '-';
  }

  return new Intl.DateTimeFormat(locale, options ?? {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  }).format(date);
}

export function formatShortDate(value: string, locale: SupportedLocale): string {
  return formatDate(value, locale, {
    month: '2-digit',
    day: '2-digit',
  });
}

export function formatTime(value: Date, locale: SupportedLocale): string {
  return new Intl.DateTimeFormat(locale, {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    fractionalSecondDigits: 3,
  }).format(value);
}

export function formatProblemTitle(problem: ProblemLike, locale: SupportedLocale): string {
  return locale === 'zh-CN' ? (problem.titleCn || problem.title) : problem.title;
}

export function formatTopicTag(tag: string, resources: LocaleResources): string {
  return resources.labels.topics[tag] ?? tag;
}

export function formatDifficulty(
  difficulty: ProblemDifficulty | LearningPlanDifficultyPreference | string | undefined,
  resources: LocaleResources,
): string {
  if (!difficulty) {
    return '-';
  }

  return resources.labels.difficulties[difficulty as ProblemDifficulty | LearningPlanDifficultyPreference] ?? difficulty;
}

export function formatPlanStatus(status: LearningPlanStatus, resources: LocaleResources): string {
  return resources.labels.planStatus[status] ?? status;
}

export function formatPlanLevel(level: LearningPlanLevel | string, resources: LocaleResources): string {
  return resources.labels.levels[level as LearningPlanLevel] ?? level;
}

export function formatPlanIntent(intent: LearningPlanIntent | string, resources: LocaleResources): string {
  return resources.labels.intents[intent as LearningPlanIntent] ?? intent;
}
