import { describe, expect, it } from 'vitest';
import { getPhaseDisplayTags } from './phaseTags';

describe('getPhaseDisplayTags', () => {
  it('sorts by problem order, preserves tag order, deduplicates, and limits output', () => {
    expect(getPhaseDisplayTags({
      problems: [
        { tags: ['Tree', 'Graph'], sortOrder: 2 },
        { tags: ['Array', 'Graph', 'Hash Table', 'Dynamic Programming'], sortOrder: 1 },
        { tags: ['Stack'], sortOrder: 3 },
      ],
    })).toEqual(['Array', 'Graph', 'Hash Table', 'Dynamic Programming']);
  });

  it('returns no tags when phase problems have no tags', () => {
    expect(getPhaseDisplayTags({
      problems: [{ tags: [], sortOrder: 1 }],
    })).toEqual([]);
  });
});
