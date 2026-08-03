import { describe, expect, it } from 'vitest';
import {
  difficultyDistributionOptions,
  getDifficultyDistribution,
  topicOptions,
} from './options';
import { formatTopicTag } from '../i18n/formatters';
import { localeResources } from '../i18n/locales';

const zhResources = localeResources['zh-CN'];

describe('learning plan options', () => {
  it('maps difficulty distribution to visible percentages', () => {
    expect(difficultyDistributionOptions.find((option) => option.value === 50)).toMatchObject({
      easyPercent: 25,
      mediumPercent: 55,
      hardPercent: 20,
    });
  });

  it('interpolates difficulty distribution with one-percent steps', () => {
    expect(getDifficultyDistribution(54)).toMatchObject({
      easyPercent: 24,
      mediumPercent: 55,
      hardPercent: 21,
    });
  });

  it('maps Chinese topic labels to backend tags', () => {
    expect(topicOptions.find((option) => formatTopicTag(option.value, zhResources) === '动态规划')).toMatchObject({
      value: 'Dynamic Programming',
    });
  });

});
