import { describe, expect, it } from 'vitest';
import {
  buildLearningPlanGoal,
  difficultyDistributionOptions,
  topicOptions,
} from './options';

describe('learning plan options', () => {
  it('maps difficulty distribution to visible percentages and backend preference', () => {
    expect(difficultyDistributionOptions.find((option) => option.value === 'BALANCED')).toMatchObject({
      preference: 'MIXED',
      easyPercent: 25,
      mediumPercent: 55,
      hardPercent: 20,
    });
  });

  it('maps Chinese topic labels to backend tags', () => {
    expect(topicOptions.find((option) => option.label === '动态规划')).toMatchObject({
      value: 'Dynamic Programming',
    });
  });

  it('builds a goal from questionnaire fields and optional notes', () => {
    expect(buildLearningPlanGoal({
      intentLabel: '面试冲刺',
      durationWeeks: 4,
      weeklyHours: 6,
      levelLabel: '中级',
      programmingLanguage: 'Java',
      difficultyLabel: '均衡',
      easyPercent: 25,
      mediumPercent: 55,
      hardPercent: 20,
      topics: ['Array', 'Hash Table'],
      additionalThoughts: '希望每周留一天复盘。',
    })).toContain('补充想法：希望每周留一天复盘。');
  });
});
