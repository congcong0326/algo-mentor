import { describe, expect, it } from 'vitest';
import {
  buildLearningPlanGoal,
  difficultyDistributionOptions,
  getDifficultyDistribution,
  topicOptions,
} from './options';

describe('learning plan options', () => {
  it('maps difficulty distribution to visible percentages and backend preference', () => {
    expect(difficultyDistributionOptions.find((option) => option.value === 50)).toMatchObject({
      preference: 'MIXED',
      easyPercent: 25,
      mediumPercent: 55,
      hardPercent: 20,
    });
  });

  it('interpolates difficulty distribution with one-percent steps', () => {
    expect(getDifficultyDistribution(54)).toMatchObject({
      preference: 'MIXED',
      easyPercent: 24,
      mediumPercent: 55,
      hardPercent: 21,
    });
  });

  it('maps Chinese topic labels to backend tags', () => {
    expect(topicOptions.find((option) => option.label === '动态规划')).toMatchObject({
      value: 'Dynamic Programming',
    });
  });

  it('builds a goal from questionnaire fields and optional notes', () => {
    const goal = buildLearningPlanGoal({
      intentLabel: '面试冲刺',
      durationWeeks: 4,
      weeklyHours: 6,
      levelLabel: '中级',
      programmingLanguage: 'Java',
      difficultyLabel: '均衡',
      easyPercent: 25,
      mediumPercent: 55,
      hardPercent: 20,
      topics: [' Array ', '', 'Hash Table'],
      additionalThoughts: '希望每周留一天复盘。',
    });

    expect(goal).toContain('难度分布：均衡（简单 25%，中等 55%，困难 20%）');
    expect(goal).toContain('主题偏好：Array, Hash Table');
    expect(goal).toContain('补充想法：希望每周留一天复盘。');
  });
});
