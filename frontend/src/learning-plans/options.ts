import type {
  LearningPlanDifficultyPreference,
  LearningPlanIntent,
  LearningPlanLevel,
} from '../types/api';

export const intentOptions: Array<{ label: string; value: LearningPlanIntent }> = [
  { label: '面试冲刺', value: 'INTERVIEW_SPRINT' },
  { label: '刷题目标', value: 'PRACTICE_GOAL' },
  { label: '专题突破', value: 'TOPIC_BREAKTHROUGH' },
  { label: '长期学习', value: 'LONG_TERM_LEARNING' },
  { label: '能力诊断', value: 'ABILITY_DIAGNOSIS' },
  { label: '错题复盘', value: 'MISTAKE_REVIEW' },
];

export const levelOptions: Array<{ label: string; value: LearningPlanLevel }> = [
  { label: '入门', value: 'BEGINNER' },
  { label: '中级', value: 'INTERMEDIATE' },
  { label: '高级', value: 'ADVANCED' },
];

export const difficultyOptions: Array<{ label: string; value: LearningPlanDifficultyPreference }> = [
  { label: 'Easy', value: 'EASY' },
  { label: 'Medium', value: 'MEDIUM' },
  { label: 'Hard', value: 'HARD' },
  { label: 'Mixed', value: 'MIXED' },
];
