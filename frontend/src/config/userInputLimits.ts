import { useEffect, useState } from 'react';
import { getUserInputLimits } from '../services/api';
import type { ProblemSolutionOutlineV1, UserInputLimits } from '../types/api';

export const DEFAULT_USER_INPUT_LIMITS: UserInputLimits = {
  reviewNote: {
    coreIdeaMaxChars: 2_000,
    dataStructureNotesMaxChars: 1_000,
    algorithmNotesMaxChars: 1_000,
    customItemMaxChars: 50,
    customItemMaxCount: 10,
    customComplexityMaxChars: 100,
    edgeCasesMaxChars: 1_000,
    requestMaxBytes: 32 * 1_024,
  },
  learningPlanCreate: {
    objectiveMaxChars: 300,
    additionalConstraintsMaxChars: 1_000,
    durationWeeksMax: 52,
    weeklyHoursMax: 80,
    requestMaxBytes: 8 * 1_024,
  },
  practiceMessage: {
    messageMaxBytes: 16 * 1_024,
    requestMaxBytes: 20 * 1_024,
  },
};

let cachedLimits: UserInputLimits | undefined;
let limitsRequest: Promise<UserInputLimits> | undefined;

export function useUserInputLimits(): UserInputLimits {
  const [limits, setLimits] = useState(cachedLimits ?? DEFAULT_USER_INPUT_LIMITS);

  useEffect(() => {
    let active = true;
    void loadUserInputLimits().then((loaded) => {
      if (active) {
        setLimits(loaded);
      }
    });
    return () => {
      active = false;
    };
  }, []);

  return limits;
}

export function unicodeCodePointLength(value: string): number {
  return Array.from(value).length;
}

export function utf8ByteLength(value: string): number {
  return new TextEncoder().encode(value).length;
}

export function reviewNoteExceedsLimits(
  outline: ProblemSolutionOutlineV1,
  limits: UserInputLimits['reviewNote'],
): boolean {
  return unicodeCodePointLength(outline.coreIdea) > limits.coreIdeaMaxChars
    || unicodeCodePointLength(outline.dataStructureNotes) > limits.dataStructureNotesMaxChars
    || unicodeCodePointLength(outline.algorithmNotes) > limits.algorithmNotesMaxChars
    || unicodeCodePointLength(outline.edgeCases) > limits.edgeCasesMaxChars
    || customItemsExceedLimits(outline.customDataStructures, limits)
    || customItemsExceedLimits(outline.customAlgorithms, limits)
    || unicodeCodePointLength(outline.timeComplexity.customText ?? '') > limits.customComplexityMaxChars
    || unicodeCodePointLength(outline.spaceComplexity.customText ?? '') > limits.customComplexityMaxChars;
}

async function loadUserInputLimits(): Promise<UserInputLimits> {
  if (cachedLimits) {
    return cachedLimits;
  }
  if (!limitsRequest) {
    limitsRequest = Promise.resolve()
      .then(() => getUserInputLimits())
      .then((response) => {
        if (!response.success || !response.data) {
          return DEFAULT_USER_INPUT_LIMITS;
        }
        cachedLimits = response.data;
        return cachedLimits;
      })
      .catch(() => DEFAULT_USER_INPUT_LIMITS)
      .finally(() => {
        limitsRequest = undefined;
      });
  }
  return limitsRequest;
}

function customItemsExceedLimits(
  values: string[],
  limits: UserInputLimits['reviewNote'],
): boolean {
  return values.length > limits.customItemMaxCount
    || values.some((value) => unicodeCodePointLength(value) > limits.customItemMaxChars);
}
