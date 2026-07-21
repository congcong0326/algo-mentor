import type { AbilityProfileResponse, AbilityTagScore } from '../types/api';

export const DEFAULT_RADAR_TAG_COUNT = 8;

export interface AbilitySummary {
  averageScore: number;
  reviewedProblems: number;
  reviewedTags: number;
  strongestTag?: AbilityTagScore;
  totalTags: number;
}

export function summarizeAbilityProfile(profile?: AbilityProfileResponse): AbilitySummary {
  const tags = profile?.tags ?? [];
  const scoredTags = [...tags].sort((left, right) => {
    if (right.abilityScore !== left.abilityScore) {
      return right.abilityScore - left.abilityScore;
    }
    return right.reviewedProblemCount - left.reviewedProblemCount;
  });
  const reviewedProblems = tags.reduce((total, tag) => total + tag.reviewedProblemCount, 0);

  return {
    averageScore: tags.length === 0
      ? 0
      : tags.reduce((total, tag) => total + tag.abilityScore, 0) / tags.length,
    reviewedProblems,
    reviewedTags: tags.filter((tag) => tag.reviewedProblemCount > 0).length,
    strongestTag: scoredTags[0],
    totalTags: tags.length,
  };
}

export function formatAbilityScore(score: number, locale: string): string {
  return new Intl.NumberFormat(locale, {
    maximumFractionDigits: 1,
    minimumFractionDigits: 1,
  }).format(score);
}

export function defaultAbilityTagKeys(profile: AbilityProfileResponse): string[] {
  return profile.tags.slice(0, DEFAULT_RADAR_TAG_COUNT).map((tag) => tag.tag);
}

export function findBreakthroughTag(
  profile?: AbilityProfileResponse,
  strongestTag?: AbilityTagScore,
): AbilityTagScore | undefined {
  const tags = profile?.tags ?? [];
  return tags.find((tag) => tag.reviewedProblemCount === 0)
    ?? tags.find((tag) => tag.tag !== strongestTag?.tag)
    ?? strongestTag;
}
