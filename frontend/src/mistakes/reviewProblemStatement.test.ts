import { describe, expect, it } from 'vitest';
import { reviewProblemStatementMarkdown } from './reviewProblemStatement';

describe('reviewProblemStatementMarkdown', () => {
  it('removes a duplicate leading level-one heading', () => {
    expect(reviewProblemStatementMarkdown({
      title: '两数之和',
      contentMarkdown: '# 两数之和\n\n给定一个整数数组。',
    })).toBe('给定一个整数数组。');
  });

  it('keeps a leading heading when it differs from the problem title', () => {
    const contentMarkdown = '# 解题说明\n\n给定一个整数数组。';

    expect(reviewProblemStatementMarkdown({
      title: '两数之和',
      contentMarkdown,
    })).toBe(contentMarkdown);
  });

  it('does not remove a matching heading from inside the statement', () => {
    const contentMarkdown = '先阅读下面的内容。\n\n# 两数之和\n\n给定一个整数数组。';

    expect(reviewProblemStatementMarkdown({
      title: '两数之和',
      contentMarkdown,
    })).toBe(contentMarkdown);
  });
});
