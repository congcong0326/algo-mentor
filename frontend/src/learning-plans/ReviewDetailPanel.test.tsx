import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { localeResources } from '../i18n/locales';
import type { PracticeCodeReviewDetail } from '../types/api';
import ReviewDetailPanel from './ReviewDetailPanel';

afterEach(cleanup);

describe('ReviewDetailPanel', () => {
  it('shows score explanations from the progress bars and removes internal detail sections', () => {
    render(
      <ReviewDetailPanel
        detail={reviewDetail()}
        passScore={6}
        resources={localeResources['zh-CN']}
      />,
    );

    expect(screen.getByRole('heading', { name: '评分明细' })).toBeInTheDocument();
    expect(screen.getByText('代码质量')).toBeInTheDocument();
    expect(screen.getByText('0.75 / 1')).toBeInTheDocument();
    expect(screen.getAllByText('表现良好').length).toBeGreaterThan(0);
    expect(screen.getByText('题目要求符合度')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '评分规则影响' })).not.toBeInTheDocument();
    expect(screen.queryByText('correctness <= 2 caps total score at 5.0')).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '分析详情' })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '上下文摘要' })).not.toBeInTheDocument();
    expect(screen.queryByText('用户此前正在讨论快慢指针。')).not.toBeInTheDocument();
    expect(screen.queryByText('ENTRY_FUNCTION')).not.toBeInTheDocument();
    expect(screen.queryByText('CORRECTNESS_BLOCKING_CAP')).not.toBeInTheDocument();

    const complexityTrigger = screen.getByRole('button', { name: '查看复杂度评分说明' });
    fireEvent.mouseEnter(complexityTrigger);

    expect(screen.getByRole('tooltip')).toHaveTextContent('复杂度 · 表现优秀 · 2 / 2');
    expect(screen.getByRole('tooltip')).toHaveTextContent('快慢指针只遍历数组一次，并且原地修改数组。');
    expect(screen.getByRole('tooltip')).toHaveTextContent('时间复杂度：O(n)');
    expect(screen.getByRole('tooltip')).toHaveTextContent('空间复杂度：O(1)');
    expect(screen.getByRole('tooltip')).toHaveTextContent('判断方式：静态分析，未实际运行代码');

    fireEvent.mouseLeave(complexityTrigger);
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();

    const requirementFitTrigger = screen.getByRole('button', { name: '查看题目要求符合度评分说明' });
    fireEvent.focus(requirementFitTrigger);
    expect(screen.getByRole('tooltip')).toHaveTextContent('入口函数和原地覆盖方式符合题目要求。');
    fireEvent.blur(requirementFitTrigger);
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '查看正确性评分说明' }));
    expect(screen.getByRole('tooltip')).toHaveTextContent('slow 是下标，非空数组应返回 slow + 1。');
  });
});

function reviewDetail(): PracticeCodeReviewDetail {
  return {
    id: 42,
    sessionId: 101,
    versionNo: 2,
    language: 'java',
    contentLocale: 'zh-CN',
    submittedCode: 'class Solution { public int removeDuplicates(int[] nums) { return 0; } }',
    reviewMarkdown: '返回值需要修正。',
    passed: false,
    scores: {
      correctness: 2,
      complexity: 2,
      edgeCases: 1.5,
      codeQuality: 0.75,
      problemFit: 1,
      total: 5,
    },
    evidence: [
      {
        type: 'ENTRY_FUNCTION',
        value: 'LeetCode 所需的 public int removeDuplicates(int[] nums)',
      },
      {
        type: 'CORRECTNESS_ISSUE',
        value: '旧版正确性说明不应覆盖稳定分项说明。',
      },
      {
        type: 'SCORE_CORRECTNESS',
        value: 'slow 是下标，非空数组应返回 slow + 1。',
      },
      {
        type: 'SCORE_COMPLEXITY',
        value: '快慢指针只遍历数组一次，并且原地修改数组。',
      },
      {
        type: 'SCORE_EDGE_CASES',
        value: '空数组处理正确，但非空数组的返回数量少 1。',
      },
      {
        type: 'SCORE_CODE_QUALITY',
        value: '双指针结构清晰，但返回值语义需要修正。',
      },
      {
        type: 'SCORE_PROBLEM_FIT',
        value: '入口函数和原地覆盖方式符合题目要求。',
      },
      {
        type: 'JUDGE_VERDICT',
        value: 'WRONG_ANSWER (STATIC_ANALYSIS)',
      },
      {
        type: 'TIME_COMPLEXITY',
        value: 'O(n)',
      },
      {
        type: 'SPACE_COMPLEXITY',
        value: 'O(1)',
      },
      {
        type: 'EXPECTED_TIME_COMPLEXITY',
        value: 'O(n)',
      },
      {
        type: 'CORRECTNESS_BLOCKING_CAP',
        value: 'correctness <= 2 caps total score at 5.0',
      },
    ],
    deductionReasons: ['非空数组返回的唯一元素个数少 1。'],
    improvementSuggestions: ['将返回值修改为 slow + 1。'],
    contextSummary: '用户此前正在讨论快慢指针。',
    createdAt: '2026-08-03T00:00:00Z',
  };
}
