import { describe, expect, it } from 'vitest';
import { emptyProblemSolutionOutline } from '../problem-notes/problemNoteOptions';
import {
  DEFAULT_USER_INPUT_LIMITS,
  reviewNoteExceedsLimits,
  unicodeCodePointLength,
  utf8ByteLength,
} from './userInputLimits';

describe('user input limit helpers', () => {
  it('counts Unicode code points instead of UTF-16 code units', () => {
    expect('😀'.length).toBe(2);
    expect(unicodeCodePointLength('A😀中')).toBe(3);
  });

  it('counts actual UTF-8 bytes for practice messages', () => {
    expect(utf8ByteLength('abc')).toBe(3);
    expect(utf8ByteLength('中文')).toBe(6);
    expect(DEFAULT_USER_INPUT_LIMITS.practiceMessage.messageMaxBytes).toBe(8_192);
  });

  it('detects review note text, item count and custom item violations', () => {
    const limits = {
      ...DEFAULT_USER_INPUT_LIMITS.reviewNote,
      coreIdeaMaxChars: 2,
      customItemMaxChars: 3,
      customItemMaxCount: 2,
    };

    expect(reviewNoteExceedsLimits({
      ...emptyProblemSolutionOutline(),
      coreIdea: '😀😀',
    }, limits)).toBe(false);
    expect(reviewNoteExceedsLimits({
      ...emptyProblemSolutionOutline(),
      coreIdea: '😀😀😀',
    }, limits)).toBe(true);
    expect(reviewNoteExceedsLimits({
      ...emptyProblemSolutionOutline(),
      customAlgorithms: ['one', 'two', 'three'],
    }, limits)).toBe(true);
    expect(reviewNoteExceedsLimits({
      ...emptyProblemSolutionOutline(),
      customDataStructures: ['four'],
    }, limits)).toBe(true);
  });
});
