import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../i18n/I18nProvider';
import * as api from '../services/api';
import type {
  ApiResponse,
  LearningPlanDetailResponse,
  PracticeCodeReviewDetail,
  PracticeCodeReviewHistoryResponse,
  PracticeMessage,
  PracticeSessionResponse,
} from '../types/api';
import PracticeChatWorkbench from './PracticeChatWorkbench';

vi.mock('../services/api', async () => {
  const actual = await vi.importActual<typeof import('../services/api')>('../services/api');
  return {
    ...actual,
    applyPracticeCoachSummaryProposal: vi.fn(),
    createOrReusePracticeSession: vi.fn(),
    getPracticeSessionActiveRun: vi.fn(),
    getPracticeSessionMessages: vi.fn(),
    getPracticeSessionReviewDetail: vi.fn(),
    getPracticeSessionReviews: vi.fn(),
    getPracticeSession: vi.fn(),
    decideAgentToolPermission: vi.fn(),
    streamPracticeMessage: vi.fn(),
    updatePracticeProgressStatus: vi.fn(),
  };
});

const decideAgentToolPermission = vi.mocked(api.decideAgentToolPermission);
const applyPracticeCoachSummaryProposal = vi.mocked(api.applyPracticeCoachSummaryProposal);
const createOrReusePracticeSession = vi.mocked(api.createOrReusePracticeSession);
const getPracticeSessionActiveRun = vi.mocked(api.getPracticeSessionActiveRun);
const getPracticeSession = vi.mocked(api.getPracticeSession);
const getPracticeSessionMessages = vi.mocked(api.getPracticeSessionMessages);
const getPracticeSessionReviewDetail = vi.mocked(api.getPracticeSessionReviewDetail);
const getPracticeSessionReviews = vi.mocked(api.getPracticeSessionReviews);
const streamPracticeMessage = vi.mocked(api.streamPracticeMessage);
const updatePracticeProgressStatus = vi.mocked(api.updatePracticeProgressStatus);

function setBrowserLocale(locale: string) {
  Object.defineProperty(window.navigator, 'language', { configurable: true, value: locale });
  Object.defineProperty(window.navigator, 'languages', { configurable: true, value: [locale] });
}

describe('PracticeChatWorkbench review contracts', () => {
  beforeEach(() => {
    setBrowserLocale('zh-CN');
    createOrReusePracticeSession.mockResolvedValue(apiResponse(sessionFixture({
      completionGate: {
        canComplete: false,
        reasonCode: 'NO_REVIEW',
        message: '旧接口文案。',
        latestScore: null,
        passScore: 80,
      },
      latestReview: null,
    })));
    getPracticeSessionActiveRun.mockResolvedValue(apiResponse(null));
    getPracticeSession.mockResolvedValue(apiResponse(sessionFixture()));
    getPracticeSessionMessages.mockResolvedValue(apiResponse(sessionFixture().messages));
    getPracticeSessionReviews.mockResolvedValue(apiResponse(historyFixture({ reviews: [] })));
    getPracticeSessionReviewDetail.mockResolvedValue(apiResponse(reviewDetailFixture()));
    decideAgentToolPermission.mockResolvedValue(apiResponse({
      permissionRequestId: 'permission-1',
      decision: 'ALLOW',
      accepted: true,
    }));
    applyPracticeCoachSummaryProposal.mockResolvedValue(apiResponse({
      proposalId: 'proposal-1',
      status: 'APPLIED',
      operation: 'REPLACE',
      appliedCoachSummaryRevision: 3,
      createdAt: '2026-06-25T00:12:00Z',
      appliedAt: '2026-06-25T00:13:00Z',
    }));
    streamPracticeMessage.mockResolvedValue(undefined);
    updatePracticeProgressStatus.mockResolvedValue(apiResponse(sessionFixture({
      completionGate: {
        canComplete: true,
        reasonCode: 'PASSED',
        message: '已通过 Review，可以标记完成。',
        latestScore: 92,
        passScore: 80,
      },
      latestReview: reviewSummaryFixture({ id: 42, versionNo: 2, totalScore: 92, passed: true }),
    })));
  });

  afterEach(() => {
    vi.useRealTimers();
    cleanup();
    vi.clearAllMocks();
  });

  it('renders no-review gate and disables completion', async () => {
    renderWorkbench();

    const completionButton = await screen.findByRole('button', { name: '标记完成' });
    expect(completionButton).toBeDisabled();
    expect(screen.getByRole('tooltip', {
      name: '完成前需要先粘贴完整代码生成一次代码提交记录，并且通过后才能标记完成。',
    })).toHaveClass('completion-disabled-tooltip');
    expect(screen.queryByText('暂无 Review')).not.toBeInTheDocument();
    expect(screen.queryByText('通过分 80')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /我的题目笔记/ })).not.toBeInTheDocument();
    expect(screen.queryByText('粘贴完整代码生成代码提交记录，并通过后才能标记完成。')).not.toBeInTheDocument();
    expect(updatePracticeProgressStatus).not.toHaveBeenCalled();
  });

  it('blocks practice messages that exceed the UTF-8 byte limit', async () => {
    renderWorkbench();

    const composer = await screen.findByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' });
    await waitFor(() => expect(composer).not.toBeDisabled());
    fireEvent.change(composer, { target: { value: '你'.repeat(5_462) } });

    expect(screen.getByText('16386 / 16384 字节')).toHaveClass('is-over-limit');
    expect(screen.getByRole('button', { name: '发送' })).toBeDisabled();
    expect(streamPracticeMessage).not.toHaveBeenCalled();
  });

  it('shows a dialog when practice agent capacity is exhausted before the stream opens', async () => {
    streamPracticeMessage.mockRejectedValue(new api.ApiRequestError(
      429,
      'Agent executor rejected task',
      'AGENT_EXECUTOR_OVERLOADED',
    ));
    renderWorkbench();

    fireEvent.change(await screen.findByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' }), {
      target: { value: '帮我分析一下这个解法。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));

    const dialog = await screen.findByRole('dialog', { name: '提示' });
    expect(within(dialog).getByText('当前算力不够，请稍后重试。')).toBeInTheDocument();
    fireEvent.click(within(dialog).getByRole('button', { name: '关闭' }));
    expect(screen.queryByRole('dialog', { name: '提示' })).not.toBeInTheDocument();
  });

  it('shows a dialog when practice agent capacity is exhausted during the stream', async () => {
    streamPracticeMessage.mockImplementation(async (_sessionId, _request, options) => {
      options.onEvent({
        eventName: 'agent_error',
        data: { code: 'AGENT_EXECUTOR_OVERLOADED', retryable: true },
      });
    });
    renderWorkbench();

    fireEvent.change(await screen.findByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' }), {
      target: { value: '请给我一点提示。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));

    expect(await screen.findByRole('dialog', { name: '提示' })).toHaveTextContent('当前算力不够，请稍后重试。');
  });

  it('shows only decision-relevant permission details', async () => {
    streamPracticeMessage.mockImplementation(async (_sessionId, _request, options) => {
      options.onEvent?.({
        eventName: 'tool_permission_request',
        data: permissionRequestEvent({
          toolName: 'renamed_review_tool',
          copyCode: 'PRACTICE_CODE_REVIEW_REQUESTED',
          reason: '模型请求生成一次代码提交记录。',
        }),
      });
      await new Promise<void>(() => undefined);
    });
    renderWorkbench();

    fireEvent.change(await screen.findByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' }), {
      target: { value: '请 Review 这段代码。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));

    const dialog = await screen.findByRole('dialog', { name: '提交代码记录' });
    expect(within(dialog).getByText('模型请求生成一次代码提交记录。')).toBeInTheDocument();
    expect(within(dialog).getByText('两数之和 (two-sum)')).toBeInTheDocument();
    expect(within(dialog).getByText('class Solution { return; }')).toBeInTheDocument();
    expect(within(dialog).getByText('确认后将生成代码提交记录，并可能影响题目完成状态。')).toBeInTheDocument();
    expect(within(dialog).queryByText('语言')).not.toBeInTheDocument();
    expect(within(dialog).queryByText('Java')).not.toBeInTheDocument();
    expect(within(dialog).queryByText('代码长度')).not.toBeInTheDocument();
    expect(within(dialog).queryByText('128 字符')).not.toBeInTheDocument();
    expect(within(dialog).queryByText('上下文')).not.toBeInTheDocument();
  });

  it('localizes review permission copy from the current UI locale', async () => {
    setBrowserLocale('en-US');
    streamPracticeMessage.mockImplementation(async (_sessionId, _request, options) => {
      options.onEvent?.({
        eventName: 'tool_permission_request',
        data: permissionRequestEvent({ reason: '模型请求生成一次代码提交记录。' }),
      });
      await new Promise<void>(() => undefined);
    });
    renderWorkbench();

    fireEvent.change(await screen.findByRole('textbox', {
      name: 'Enter your approach, question, code, or LeetCode feedback',
    }), {
      target: { value: 'Please review this code.' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Send' }));

    const dialog = await screen.findByRole('dialog', { name: 'Submit code for review' });
    expect(within(dialog).getByText(
      'The model is requesting permission to create a code submission record.',
    )).toBeInTheDocument();
    expect(within(dialog).queryByText('提交代码记录')).not.toBeInTheDocument();
    expect(within(dialog).queryByText('模型请求生成一次代码提交记录。')).not.toBeInTheDocument();
  });

  it('renders an inline coach summary proposal and applies it without a permission dialog', async () => {
    streamPracticeMessage.mockImplementation(async (_sessionId, _request, options) => {
      options.onEvent?.({
        eventName: 'content_delta',
        data: { content: '这段前置文案会被候选正文替换。' },
      });
      options.onEvent?.({
        eventName: 'agent_tool_end',
        data: agentToolEndEvent({
          toolName: 'propose_current_problem_coach_summary',
          result: {
            type: 'current_problem_coach_summary_proposed',
            status: 'PROPOSED',
            proposalId: 'proposal-1',
            summaryMarkdown: '# 教练总结\n\n- 先查补数，再写入当前元素。',
            operation: 'REPLACE',
            baseCoachSummaryRevision: 2,
          },
        }),
      });
      options.onEvent?.({
        eventName: 'content_delta',
        data: { content: '这段工具后的重复文案不应展示。' },
      });
      await new Promise<void>(() => undefined);
    });
    renderWorkbench();

    fireEvent.change(await screen.findByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' }), {
      target: { value: '生成并更新教练总结。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));

    expect(await screen.findByRole('heading', { name: '教练总结' })).toBeInTheDocument();
    expect(screen.getByText('先查补数，再写入当前元素。')).toBeInTheDocument();
    expect(screen.queryByText('这段前置文案会被候选正文替换。')).not.toBeInTheDocument();
    expect(screen.queryByText('这段工具后的重复文案不应展示。')).not.toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '替换教练总结' }));

    await waitFor(() => expect(applyPracticeCoachSummaryProposal).toHaveBeenCalledWith(101, 'proposal-1'));
    expect(await screen.findByText('已保存到教练总结')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '替换教练总结' })).not.toBeInTheDocument();
  });

  it('shows the remaining confirmation time and disables decisions after expiry', async () => {
    let streamOptions: Parameters<typeof api.streamPracticeMessage>[2] | undefined;
    streamPracticeMessage.mockImplementation(async (_sessionId, _request, options) => {
      streamOptions = options;
      await new Promise<void>(() => undefined);
    });
    renderWorkbench();

    fireEvent.change(await screen.findByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' }), {
      target: { value: '请 Review 这段代码。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));
    await waitFor(() => expect(streamOptions).toBeDefined());

    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-06-26T00:00:00Z'));
    act(() => {
      streamOptions?.onEvent({
        eventName: 'tool_permission_request',
        data: permissionRequestEvent({ expiresAt: '2026-06-26T00:00:12Z' }),
      });
    });

    const timer = screen.getByRole('timer');
    expect(timer).toHaveTextContent('超时后自动取消');
    expect(timer).toHaveTextContent('请在倒计时结束前确认，本次对话不会因取消而中断。');
    expect(timer).toHaveTextContent('00:12');
    expect(timer).not.toHaveClass('is-urgent');
    expect(timer.querySelector('.practice-permission-clock')).toHaveStyle({
      '--permission-countdown-angle': '360deg',
    });

    act(() => {
      vi.advanceTimersByTime(2_000);
    });
    expect(timer).toHaveTextContent('00:10');
    expect(timer).toHaveClass('is-urgent');

    act(() => {
      vi.advanceTimersByTime(10_000);
    });
    expect(timer).toHaveTextContent('确认时间已结束');
    expect(timer).toHaveTextContent('正在取消本次代码 Review…');
    expect(timer).toHaveTextContent('00:00');
    expect(timer).toHaveClass('is-expired');
    expect(screen.getByRole('button', { name: '确认生成' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '暂不生成' })).toBeDisabled();
    expect(decideAgentToolPermission).not.toHaveBeenCalled();
  });

  it('shows a warning only when the trusted practice context is unavailable', async () => {
    streamPracticeMessage.mockImplementation(async (_sessionId, _request, options) => {
      options.onEvent?.({
        eventName: 'tool_permission_request',
        data: permissionRequestEvent({ preview: { contextAvailable: false } }),
      });
      await new Promise<void>(() => undefined);
    });
    renderWorkbench();

    fireEvent.change(await screen.findByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' }), {
      target: { value: '请 Review 这段代码。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));

    const dialog = await screen.findByRole('dialog', { name: '提交代码记录' });
    expect(within(dialog).getByText('暂时无法读取完整练习上下文，请确认代码和题目是否匹配。'))
      .toBeInTheDocument();
  });

  it('uses the current locale for the workbench title and permission problem title', async () => {
    streamPracticeMessage.mockImplementation(async (_sessionId, _request, options) => {
      options.onEvent?.({
        eventName: 'tool_permission_request',
        data: {
          ...permissionRequestEvent(),
          preview: {
            ...permissionRequestEvent().preview,
            problemTitle: 'two-sum',
          },
        },
      });
      await new Promise<void>(() => undefined);
    });
    renderWorkbench();

    expect(await screen.findByRole('heading', { name: '1. 两数之和' })).toBeInTheDocument();

    fireEvent.change(await screen.findByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' }), {
      target: { value: '提交完整代码。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));

    const dialog = await screen.findByRole('dialog', { name: '提交代码记录' });
    expect(within(dialog).getByText('两数之和 (two-sum)')).toBeInTheDocument();
    expect(within(dialog).queryByText('two-sum (two-sum)')).not.toBeInTheDocument();
  });

  it('allows permission without aborting the stream and keeps appending content deltas', async () => {
    let streamOptions: Parameters<typeof api.streamPracticeMessage>[2] | undefined;
    streamPracticeMessage.mockImplementation(async (_sessionId, _request, options) => {
      streamOptions = options;
      options.onEvent?.({
        eventName: 'tool_permission_request',
        data: permissionRequestEvent(),
      });
      await new Promise<void>(() => undefined);
    });
    renderWorkbench();

    fireEvent.change(await screen.findByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' }), {
      target: { value: '请 Review 这段代码。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));
    fireEvent.click(await screen.findByRole('button', { name: '确认生成' }));

    await waitFor(() => expect(decideAgentToolPermission).toHaveBeenCalledWith(
      'permission-1',
      { decision: 'ALLOW', reason: 'user_confirmed' },
    ));
    expect(streamOptions?.signal?.aborted).toBe(false);

    act(() => {
      streamOptions?.onEvent({
        eventName: 'content_delta',
        data: { content: 'Review 正在执行。' },
      });
    });

    expect(await screen.findByText('Review 正在执行。')).toBeInTheDocument();
  });

  it('uses the assistant bubble work area for organizing and Review progress', async () => {
    let streamOptions: Parameters<typeof api.streamPracticeMessage>[2] | undefined;
    streamPracticeMessage.mockImplementation(async (_sessionId, _request, options) => {
      streamOptions = options;
      await new Promise<void>(() => undefined);
    });
    renderWorkbench();

    fireEvent.change(await screen.findByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' }), {
      target: { value: 'class Solution { int[] twoSum() { return null; } }' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));
    const organizingStatus = await screen.findByRole('status', { name: '正在整理思路...' });
    expect(organizingStatus).toHaveClass('practice-coach-work-status', 'is-running');
    expect(organizingStatus.querySelector('.practice-coach-work-status-text')).toBeInTheDocument();
    expect(organizingStatus.closest('.practice-message')).toHaveClass('assistant-message');

    act(() => {
      streamOptions?.onEvent({
        eventName: 'agent_tool_start',
        data: agentToolEvent({ toolName: 'submit_practice_code_review' }),
      });
    });

    expect(await screen.findByRole('status', { name: '正在生成代码提交记录...' })).toHaveClass(
      'practice-coach-work-status',
      'is-running',
    );
    expect(screen.queryByText('正在整理思路...')).not.toBeInTheDocument();
  });

  it.each([
    ['UPDATED', '已更新学习记忆', 'is-updated'],
    ['NO_CHANGE', '学习记忆无需更新', 'is-no-change'],
    ['FAILED', '学习记忆暂未更新', 'is-failed'],
  ] as const)('keeps learner profile RUNNING visible before %s', async (status, terminalLabel, terminalClassName) => {
    let streamOptions: Parameters<typeof api.streamPracticeMessage>[2] | undefined;
    streamPracticeMessage.mockImplementation(async (_sessionId, _request, options) => {
      streamOptions = options;
      await new Promise<void>(() => undefined);
    });
    renderWorkbench();

    fireEvent.change(await screen.findByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' }), {
      target: { value: '记录我的学习目标。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));
    await waitFor(() => expect(streamOptions).toBeDefined());

    vi.useFakeTimers();
    act(() => {
      streamOptions?.onEvent({ eventName: 'agent_tool_start', data: learnerProfileToolEvent() });
      streamOptions?.onEvent({
        eventName: 'agent_tool_end',
        data: learnerProfileToolEndEvent({ status }),
      });
    });

    expect(screen.getByRole('status', { name: '正在更新学习记忆...' })).toHaveClass('is-running');
    act(() => {
      vi.advanceTimersByTime(699);
    });
    expect(screen.getByRole('status', { name: '正在更新学习记忆...' })).toBeInTheDocument();
    act(() => {
      vi.advanceTimersByTime(1);
    });
    expect(screen.getByRole('status', { name: terminalLabel })).toHaveClass(
      'practice-coach-work-status',
      terminalClassName,
    );
  });

  it('keeps streamed content separate from the deduplicated learner profile work status', async () => {
    let streamOptions: Parameters<typeof api.streamPracticeMessage>[2] | undefined;
    streamPracticeMessage.mockImplementation(async (_sessionId, _request, options) => {
      streamOptions = options;
      await new Promise<void>(() => undefined);
    });
    renderWorkbench();

    fireEvent.change(await screen.findByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' }), {
      target: { value: '记录我的学习目标。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));
    await waitFor(() => expect(streamOptions).toBeDefined());

    vi.useFakeTimers();
    act(() => {
      streamOptions?.onEvent({ eventName: 'agent_tool_start', data: learnerProfileToolEvent() });
      streamOptions?.onEvent({ eventName: 'agent_tool_start', data: learnerProfileToolEvent() });
      streamOptions?.onEvent({ eventName: 'content_delta', data: { content: '继续说明下一步练习。' } });
      streamOptions?.onEvent({
        eventName: 'agent_tool_end',
        data: learnerProfileToolEndEvent({ status: 'UPDATED' }),
      });
      streamOptions?.onEvent({
        eventName: 'agent_tool_end',
        data: learnerProfileToolEndEvent({ status: 'UPDATED' }),
      });
    });

    const runningStatus = screen.getByRole('status', { name: '正在更新学习记忆...' });
    expect(screen.getAllByRole('status', { name: '正在更新学习记忆...' })).toHaveLength(1);
    const assistantMessage = screen.getByText('继续说明下一步练习。').closest('.practice-message');
    expect(assistantMessage).toContainElement(runningStatus);
    expect(assistantMessage?.querySelector('.markdown-view')).toHaveTextContent('继续说明下一步练习。');
    expect(assistantMessage?.querySelector('.markdown-view')).not.toHaveTextContent('正在更新学习记忆...');

    act(() => {
      vi.advanceTimersByTime(700);
    });
    expect(screen.getAllByRole('status', { name: '已更新学习记忆' })).toHaveLength(1);

    act(() => {
      streamOptions?.onEvent({ eventName: 'agent_tool_start', data: learnerProfileToolEvent() });
      streamOptions?.onEvent({
        eventName: 'agent_tool_end',
        data: learnerProfileToolEndEvent({ status: 'UPDATED' }),
      });
    });
    expect(screen.getAllByRole('status', { name: '已更新学习记忆' })).toHaveLength(1);
  });

  it('shows failed Review score from tool result before the final assistant text', async () => {
    let streamOptions: Parameters<typeof api.streamPracticeMessage>[2] | undefined;
    streamPracticeMessage.mockImplementation(async (_sessionId, _request, options) => {
      streamOptions = options;
      await new Promise<void>(() => undefined);
    });
    renderWorkbench();

    fireEvent.change(await screen.findByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' }), {
      target: { value: 'class Solution { int[] twoSum() { return new int[]{0,0}; } }' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));
    await waitFor(() => expect(streamOptions).toBeDefined());

    act(() => {
      streamOptions?.onEvent({
        eventName: 'agent_tool_end',
        data: agentToolEndEvent({
          result: {
            type: 'practice_code_review_submitted',
            status: 'SAVED',
            totalScore: 4.5,
            passed: false,
          },
          toolName: 'submit_practice_code_review',
        }),
      });
    });

    expect(await screen.findByText('代码提交记录已生成：未通过，4.5 / 80 分。')).toBeInTheDocument();

    act(() => {
      streamOptions?.onEvent({
        eventName: 'content_delta',
        data: { content: '主要问题是返回值固定，不能覆盖输入。' },
      });
    });

    expect(screen.getByText(/代码提交记录已生成：未通过，4.5 \/ 80 分。/)).toBeInTheDocument();
    expect(screen.getByText(/主要问题是返回值固定，不能覆盖输入。/)).toBeInTheDocument();
  });

  it('denies permission with user rejection reason', async () => {
    streamPracticeMessage.mockImplementation(async (_sessionId, _request, options) => {
      options.onEvent?.({
        eventName: 'tool_permission_request',
        data: permissionRequestEvent(),
      });
      await new Promise<void>(() => undefined);
    });
    renderWorkbench();

    fireEvent.change(await screen.findByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' }), {
      target: { value: '请 Review 这段代码。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));
    fireEvent.click(await screen.findByRole('button', { name: '暂不生成' }));

    await waitFor(() => expect(decideAgentToolPermission).toHaveBeenCalledWith(
      'permission-1',
      { decision: 'DENY', reason: 'user_rejected' },
    ));
  });

  it('keeps permission dialog open on decision failure and allows retry', async () => {
    let rejectDecision: (error: Error) => void = () => undefined;
    decideAgentToolPermission
      .mockImplementationOnce(() => new Promise((resolve, reject) => {
        rejectDecision = reject;
      }))
      .mockResolvedValueOnce(apiResponse({
        permissionRequestId: 'permission-1',
        decision: 'ALLOW',
        accepted: true,
      }));
    streamPracticeMessage.mockImplementation(async (_sessionId, _request, options) => {
      options.onEvent?.({
        eventName: 'tool_permission_request',
        data: permissionRequestEvent(),
      });
      await new Promise<void>(() => undefined);
    });
    renderWorkbench();

    fireEvent.change(await screen.findByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' }), {
      target: { value: '请 Review 这段代码。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));

    const allowButton = await screen.findByRole('button', { name: '确认生成' });
    const denyButton = screen.getByRole('button', { name: '暂不生成' });
    fireEvent.click(allowButton);

    expect(allowButton).toBeDisabled();
    expect(denyButton).toBeDisabled();

    await act(async () => {
      rejectDecision(new Error('授权提交失败'));
    });

    const dialog = await screen.findByRole('dialog', { name: '提交代码记录' });
    expect(within(dialog).getByText('授权提交失败')).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: '确认生成' })).not.toBeDisabled();

    fireEvent.click(within(dialog).getByRole('button', { name: '确认生成' }));

    await waitFor(() => expect(decideAgentToolPermission).toHaveBeenCalledTimes(2));
    expect(decideAgentToolPermission).toHaveBeenLastCalledWith(
      'permission-1',
      { decision: 'ALLOW', reason: 'user_confirmed' },
    );
  });

  it('closes permission dialog on timeout and shows not-run status', async () => {
    let streamOptions: Parameters<typeof api.streamPracticeMessage>[2] | undefined;
    streamPracticeMessage.mockImplementation(async (_sessionId, _request, options) => {
      streamOptions = options;
      options.onEvent?.({
        eventName: 'tool_permission_request',
        data: permissionRequestEvent(),
      });
      await new Promise<void>(() => undefined);
    });
    renderWorkbench();

    fireEvent.change(await screen.findByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' }), {
      target: { value: '请 Review 这段代码。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));

    expect(await screen.findByRole('dialog', { name: '提交代码记录' })).toBeInTheDocument();

    act(() => {
      streamOptions?.onEvent({
        eventName: 'tool_permission_timeout',
        data: {
          permissionRequestId: 'permission-1',
          reason: 'expired',
          expiredAt: '2026-06-26T00:01:00Z',
        },
      });
    });

    await waitFor(() => expect(screen.queryByRole('dialog', { name: '提交代码记录' })).not.toBeInTheDocument());
    expect(screen.getByText('确认已超时，本次未生成代码提交记录。')).toHaveClass('practice-status-note');
  });

  it('renders rounded guidance tooltip for generated problem statements', async () => {
    renderWorkbench();

    expect(await screen.findByRole('img', { name: /题面内容为大模型生成/ })).toBeInTheDocument();
    expect(screen.getByRole('tooltip', { name: /本站不内置题库，最终以 LeetCode 为准/ }))
      .toHaveClass('toolbar-tooltip', 'practice-guidance-tooltip');
  });

  it('renders a rounded tooltip for the LeetCode action', async () => {
    renderWorkbench();

    const link = await screen.findByRole('link', { name: '打开 LeetCode 题目' });
    expect(link).toHaveAttribute('aria-describedby', 'practice-leetcode-tooltip');
    expect(link).not.toHaveAttribute('title');
    expect(screen.getByRole('tooltip', { name: '打开 LeetCode 题目' }))
      .toHaveClass('toolbar-tooltip', 'practice-leetcode-tooltip');
  });

  it('renders user messages as plain text without markdown parsing', async () => {
    const pastedCode = '# class Solution\n\n**bold**\n<script>alert("xss")</script>';
    createOrReusePracticeSession.mockResolvedValue(apiResponse(sessionFixture({
      messages: [
        messageFixture({
          id: 2,
          role: 'USER',
          contentMarkdown: pastedCode,
        }),
        messageFixture({
          id: 3,
          role: 'ASSISTANT',
          contentMarkdown: '# Review\n\n**重点：**继续保持 Markdown。',
        }),
      ],
    })));

    renderWorkbench();

    const userText = await screen.findByText((_, element) => (
      Boolean(element?.classList.contains('practice-message-plain-text') && element.textContent === pastedCode)
    ));
    expect(userText).toBeInTheDocument();
    expect(userText.querySelector('strong')).toBeNull();
    expect(userText.querySelector('script')).toBeNull();
    expect(screen.queryByRole('heading', { level: 1, name: 'class Solution' })).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: 'Review' })).toBeInTheDocument();
    expect(screen.getByText('重点：').closest('strong')).toBeInTheDocument();
    expect(document.querySelector('script')).not.toBeInTheDocument();
  });

  it('refreshes messages session and reviews after agent_run_end', async () => {
    const refreshedMessages = [
      ...sessionFixture().messages,
      messageFixture({ id: 2, role: 'USER', contentMarkdown: '这是完整代码。' }),
      messageFixture({ id: 3, role: 'ASSISTANT', contentMarkdown: '代码提交记录已生成。' }),
    ];
    getPracticeSessionMessages.mockResolvedValue(apiResponse(refreshedMessages));
    getPracticeSession.mockResolvedValue(apiResponse(sessionFixture({
      messages: refreshedMessages,
      completionGate: {
        canComplete: true,
        reasonCode: 'PASSED',
        message: '最新 Review 已通过，可以标记完成。',
        latestScore: 92,
        passScore: 80,
      },
      latestReview: reviewSummaryFixture({ id: 42, totalScore: 92, passed: true }),
    })));
    getPracticeSessionReviews.mockResolvedValue(apiResponse(historyFixture({
      latestReview: reviewSummaryFixture({ id: 42, totalScore: 92, passed: true }),
      reviews: [reviewSummaryFixture({ id: 42, totalScore: 92, passed: true })],
      completionGate: {
        canComplete: true,
        reasonCode: 'PASSED',
        message: '最新 Review 已通过，可以标记完成。',
        latestScore: 92,
        passScore: 80,
      },
    })));
    streamPracticeMessage.mockImplementation(async (_sessionId, _request, options) => {
      options.onEvent?.({ eventName: 'agent_run_end', data: { runId: 'run_1' } });
    });
    renderWorkbench();

    fireEvent.change(await screen.findByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' }), {
      target: { value: '这是完整代码。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));

    expect(await screen.findByText('代码提交记录已生成。')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '标记完成' })).not.toBeDisabled();
    expect(getPracticeSessionMessages).toHaveBeenCalledWith(101, 50, expect.any(AbortSignal));
    expect(getPracticeSession).toHaveBeenCalledWith(101, expect.any(AbortSignal));
    expect(getPracticeSessionReviews).toHaveBeenCalledWith(101, expect.any(AbortSignal));
  });

  it('refreshes completion gate once after successful Review tool end and run end', async () => {
    let streamOptions: Parameters<typeof api.streamPracticeMessage>[2] | undefined;
    let resolveStream: () => void = () => undefined;
    const passedGate = {
      canComplete: true,
      reasonCode: 'PASSED' as const,
      message: 'Review tool 刷新后已通过。',
      latestScore: 94,
      passScore: 80,
    };
    getPracticeSessionReviews.mockResolvedValueOnce(apiResponse(historyFixture({
      latestReview: reviewSummaryFixture({ id: 43, versionNo: 3, totalScore: 94, passed: true }),
      reviews: [reviewSummaryFixture({ id: 43, versionNo: 3, totalScore: 94, passed: true })],
      completionGate: passedGate,
    })));
    getPracticeSession.mockResolvedValue(apiResponse(sessionFixture({
      latestReview: reviewSummaryFixture({ id: 43, versionNo: 3, totalScore: 94, passed: true }),
      completionGate: passedGate,
    })));
    streamPracticeMessage.mockImplementation(async (_sessionId, _request, options) => {
      streamOptions = options;
      await new Promise<void>((resolve) => {
        resolveStream = resolve;
      });
    });
    renderWorkbench();

    fireEvent.change(await screen.findByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' }), {
      target: { value: '这是第三版完整代码。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));
    await waitFor(() => expect(streamOptions).toBeDefined());

    act(() => {
      streamOptions?.onEvent({
        eventName: 'agent_tool_end',
        data: agentToolEndEvent({
          result: { type: 'practice_code_review_submitted', status: 'NOT_COMPLETE_SUBMISSION' },
          toolName: 'submit_practice_code_review',
        }),
      });
    });

    expect(getPracticeSessionReviews).not.toHaveBeenCalled();

    act(() => {
      streamOptions?.onEvent({ eventName: 'agent_run_end', data: { runId: 'run-1' } });
    });
    await act(async () => {
      resolveStream();
    });

    await waitFor(() => expect(getPracticeSessionReviews).toHaveBeenCalledTimes(1));
    expect(screen.getByRole('button', { name: '标记完成' })).not.toBeDisabled();
    expect(getPracticeSessionMessages).toHaveBeenCalledTimes(1);
    expect(getPracticeSession).toHaveBeenCalledTimes(1);
  });

  it.each([
    'tool_permission_denied',
    'tool_permission_timeout',
  ])('does not refresh reviews immediately for synthetic %s tool end', async (resultType) => {
    let streamOptions: Parameters<typeof api.streamPracticeMessage>[2] | undefined;
    let resolveStream: () => void = () => undefined;
    streamPracticeMessage.mockImplementation(async (_sessionId, _request, options) => {
      streamOptions = options;
      await new Promise<void>((resolve) => {
        resolveStream = resolve;
      });
    });
    renderWorkbench();

    fireEvent.change(await screen.findByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' }), {
      target: { value: '请 Review 这段代码。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));
    await waitFor(() => expect(streamOptions).toBeDefined());

    act(() => {
      streamOptions?.onEvent({
        eventName: 'agent_tool_end',
        data: agentToolEndEvent({
          result: { type: resultType },
          toolName: 'submit_practice_code_review',
        }),
      });
    });

    expect(getPracticeSessionReviews).not.toHaveBeenCalled();

    act(() => {
      streamOptions?.onEvent({ eventName: 'agent_run_end', data: { runId: 'run-1' } });
    });
    await act(async () => {
      resolveStream();
    });

    await waitFor(() => expect(getPracticeSessionReviews).toHaveBeenCalledTimes(1));
    expect(getPracticeSessionMessages).toHaveBeenCalledTimes(1);
    expect(getPracticeSession).toHaveBeenCalledTimes(1);
  });

  it('does not trigger special review refresh for non Review tool end', async () => {
    let streamOptions: Parameters<typeof api.streamPracticeMessage>[2] | undefined;
    let resolveStream: () => void = () => undefined;
    streamPracticeMessage.mockImplementation(async (_sessionId, _request, options) => {
      streamOptions = options;
      await new Promise<void>((resolve) => {
        resolveStream = resolve;
      });
    });
    renderWorkbench();

    fireEvent.change(await screen.findByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' }), {
      target: { value: '普通聊天。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));
    await waitFor(() => expect(streamOptions).toBeDefined());

    act(() => {
      streamOptions?.onEvent({
        eventName: 'agent_tool_end',
        data: agentToolEndEvent({
          result: { type: 'practice_code_review_submitted' },
          toolName: 'search_learning_notes',
        }),
      });
    });

    expect(getPracticeSessionReviews).not.toHaveBeenCalled();

    act(() => {
      streamOptions?.onEvent({ eventName: 'agent_run_end', data: { runId: 'run-1' } });
    });
    await act(async () => {
      resolveStream();
    });

    await waitFor(() => expect(getPracticeSessionReviews).toHaveBeenCalledTimes(1));
    expect(getPracticeSessionMessages).toHaveBeenCalledTimes(1);
    expect(getPracticeSession).toHaveBeenCalledTimes(1);
  });

  it('keeps completion disabled while post-run review refresh is pending', async () => {
    createOrReusePracticeSession.mockResolvedValue(apiResponse(sessionFixture({
      completionGate: {
        canComplete: true,
        reasonCode: 'PASSED',
        message: '上一版 Review 已通过。',
        latestScore: 92,
        passScore: 80,
      },
      latestReview: reviewSummaryFixture({ id: 42, versionNo: 2, totalScore: 92, passed: true }),
    })));
    getPracticeSessionMessages.mockResolvedValue(apiResponse(sessionFixture().messages));
    let resolveSession: (response: ApiResponse<PracticeSessionResponse>) => void = () => undefined;
    getPracticeSession.mockImplementation(() => new Promise((resolve) => {
      resolveSession = resolve;
    }));
    let resolveReviews: (response: ApiResponse<PracticeCodeReviewHistoryResponse>) => void = () => undefined;
    getPracticeSessionReviews.mockImplementation(() => new Promise((resolve) => {
      resolveReviews = resolve;
    }));
    streamPracticeMessage.mockImplementation(async (_sessionId, _request, options) => {
      options.onEvent?.({ eventName: 'agent_run_end', data: { runId: 'run_1' } });
      await new Promise<void>((resolve) => setTimeout(resolve, 20));
    });
    renderWorkbench();

    expect(await screen.findByRole('button', { name: '标记完成' })).not.toBeDisabled();

    fireEvent.change(screen.getByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' }), {
      target: { value: '这是新版本完整代码。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));

    await waitFor(() => expect(getPracticeSession).toHaveBeenCalled());
    expect(screen.getByRole('button', { name: '标记完成' })).toBeDisabled();

    resolveSession(apiResponse(sessionFixture({
      completionGate: {
        canComplete: false,
        reasonCode: 'LATEST_REVIEW_FAILED',
        message: '新版 Review 未通过。',
        latestScore: 70,
        passScore: 80,
      },
      latestReview: reviewSummaryFixture({ id: 43, versionNo: 3, totalScore: 70, passed: false }),
    })));
    await waitFor(() => expect(getPracticeSessionReviews).toHaveBeenCalled());
    resolveReviews(apiResponse(historyFixture({
      latestReview: reviewSummaryFixture({ id: 43, versionNo: 3, totalScore: 70, passed: false }),
      reviews: [reviewSummaryFixture({ id: 43, versionNo: 3, totalScore: 70, passed: false })],
      completionGate: {
        canComplete: false,
        reasonCode: 'LATEST_REVIEW_FAILED',
        message: '新版 Review 未通过。',
        latestScore: 70,
        passScore: 80,
      },
    })));

    const failedCompletionButton = await screen.findByRole('button', { name: '标记完成' });
    expect(failedCompletionButton).toBeDisabled();
    expect(screen.getByRole('tooltip', { name: '最近一次代码提交记录未通过，请修改后重新提交。' })).toBeInTheDocument();
  });

  it('refreshes session and reviews when active run polling clears', async () => {
    createOrReusePracticeSession.mockResolvedValue(apiResponse(sessionFixture({
      activeRun: {
        runId: 88,
        taskId: 501,
        runUuid: 'run-active',
        idempotencyKey: 'idem-active',
        startedAt: '2026-06-25T00:05:00Z',
      },
    })));
    getPracticeSessionActiveRun.mockResolvedValue(apiResponse(null));
    getPracticeSessionMessages.mockResolvedValue(apiResponse([
      ...sessionFixture().messages,
      messageFixture({ id: 4, contentMarkdown: '后台 Review 已落库。' }),
    ]));
    getPracticeSession.mockResolvedValue(apiResponse(sessionFixture({
      completionGate: {
        canComplete: true,
        reasonCode: 'PASSED',
        message: '轮询刷新后已通过 Review。',
        latestScore: 90,
        passScore: 80,
      },
      latestReview: reviewSummaryFixture({ id: 43, versionNo: 3, totalScore: 90, passed: true }),
    })));
    getPracticeSessionReviews.mockResolvedValue(apiResponse(historyFixture({
      latestReview: reviewSummaryFixture({ id: 43, versionNo: 3, totalScore: 90, passed: true }),
      reviews: [reviewSummaryFixture({ id: 43, versionNo: 3, totalScore: 90, passed: true })],
      completionGate: {
        canComplete: true,
        reasonCode: 'PASSED',
        message: '轮询刷新后已通过 Review。',
        latestScore: 90,
        passScore: 80,
      },
    })));
    renderWorkbench();

    expect(await screen.findByText('后台 Review 已落库。')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '标记完成' })).not.toBeDisabled();
    expect(getPracticeSessionMessages).toHaveBeenCalledWith(101, 50, expect.any(AbortSignal));
    expect(getPracticeSession).toHaveBeenCalledWith(101, expect.any(AbortSignal));
    expect(getPracticeSessionReviews).toHaveBeenCalledWith(101, expect.any(AbortSignal));
  });

  it('localizes backend completion gate errors by reason code', async () => {
    createOrReusePracticeSession.mockResolvedValue(apiResponse(sessionFixture({
      completionGate: {
        canComplete: false,
        reasonCode: 'LATEST_REVIEW_FAILED',
        message: '最新 Review 未通过：边界条件不足。',
        latestScore: 72,
        passScore: 80,
      },
      latestReview: reviewSummaryFixture({ id: 41, versionNo: 1, totalScore: 72, passed: false }),
    })));
    renderWorkbench();

    const completionButton = await screen.findByRole('button', { name: '标记完成' });
    expect(completionButton).toBeDisabled();
    expect(screen.getByRole('tooltip', { name: '最近一次代码提交记录未通过，请修改后重新提交。' })).toBeInTheDocument();
  });

  it('enables completion when latest review passes', async () => {
    createOrReusePracticeSession.mockResolvedValue(apiResponse(sessionFixture({
      completionGate: {
        canComplete: true,
        reasonCode: 'PASSED',
        message: '最新 Review 已通过。',
        latestScore: 92,
        passScore: 80,
      },
      latestReview: reviewSummaryFixture({ id: 42, totalScore: 92, passed: true }),
    })));
    renderWorkbench();

    expect(await screen.findByRole('button', { name: '标记完成' })).not.toBeDisabled();
  });

  it('keeps completion disabled for latest failed review', async () => {
    createOrReusePracticeSession.mockResolvedValue(apiResponse(sessionFixture({
      completionGate: {
        canComplete: false,
        reasonCode: 'LATEST_REVIEW_FAILED',
        message: '最新 Review 分数不足，请先修复代码。',
        latestScore: 68,
        passScore: 80,
      },
      latestReview: reviewSummaryFixture({ id: 41, versionNo: 1, totalScore: 68, passed: false }),
    })));
    renderWorkbench();

    const completionButton = await screen.findByRole('button', { name: '标记完成' });
    expect(completionButton).toBeDisabled();
    expect(screen.getByRole('tooltip', { name: '最近一次代码提交记录未通过，请修改后重新提交。' })).toBeInTheDocument();
  });

  it('opens the standalone submission history route from the toolbar', async () => {
    const onOpenSubmissions = vi.fn();
    renderWorkbench({ onOpenSubmissions });

    fireEvent.click(await screen.findByRole('button', { name: '代码提交记录' }));

    expect(onOpenSubmissions).toHaveBeenCalledTimes(1);
    expect(getPracticeSessionReviews).not.toHaveBeenCalled();
  });

  it('requires confirmation before skipping a problem from more actions', async () => {
    updatePracticeProgressStatus.mockResolvedValue(apiResponse(sessionFixture({
      session: {
        ...sessionFixture().session,
        progressStatus: 'SKIPPED',
      },
    })));
    renderWorkbench();

    fireEvent.click(await screen.findByRole('button', { name: '更多操作' }));
    fireEvent.click(screen.getByRole('menuitem', { name: '跳过本题' }));

    const dialog = await screen.findByRole('dialog', { name: '跳过本题？' });
    expect(within(dialog).getByText('跳过后仍可查看此题的对话，并可稍后标记完成。')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '取消' })).toHaveFocus();
    expect(updatePracticeProgressStatus).not.toHaveBeenCalled();

    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByRole('dialog', { name: '跳过本题？' })).not.toBeInTheDocument();
    expect(updatePracticeProgressStatus).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: '更多操作' }));
    fireEvent.click(screen.getByRole('menuitem', { name: '跳过本题' }));
    fireEvent.click(await screen.findByRole('button', { name: '确认跳过' }));

    await waitFor(() => expect(updatePracticeProgressStatus).toHaveBeenCalledWith(101, 'SKIPPED'));
    expect(await screen.findByText('已跳过')).toBeInTheDocument();
  });

  it('clears stale review versions when session changes', async () => {
    createOrReusePracticeSession.mockResolvedValueOnce(apiResponse(sessionFixture({
      completionGate: {
        canComplete: true,
        reasonCode: 'PASSED',
        message: '最新 Review 已通过。',
        latestScore: 92,
        passScore: 80,
      },
      latestReview: reviewSummaryFixture({ id: 42, versionNo: 2, totalScore: 92, passed: true }),
    }))).mockResolvedValueOnce(apiResponse(sessionFixture({
      session: {
        id: 202,
        planId: 7,
        phaseIndex: 1,
        problemSlug: 'valid-palindrome',
        progressStatus: 'IN_PROGRESS',
        agentTaskId: 502,
        createdAt: '2026-06-25T00:00:00Z',
        updatedAt: '2026-06-25T00:00:00Z',
      },
      problem: {
        slug: 'valid-palindrome',
        frontendId: 125,
        title: 'Valid Palindrome',
        titleCn: '验证回文串',
        difficulty: 'EASY',
        tags: ['Two Pointers'],
        leetcodeUrl: 'https://leetcode.cn/problems/valid-palindrome/',
      },
      messages: [],
      latestReview: null,
      completionGate: {
        canComplete: false,
        reasonCode: 'NO_REVIEW',
        message: '新题还没有 Review。',
        latestScore: null,
        passScore: 80,
      },
    })));
    const { rerender } = renderWorkbench();

    rerender(
      <I18nProvider>
        <PracticeChatWorkbench
          onBack={vi.fn()}
          onOpenSubmissions={vi.fn()}
          phaseIndex={1}
          plan={planFixtureWithSecondProblem}
          problemSlug="valid-palindrome"
        />
      </I18nProvider>,
    );

    const completionButton = await screen.findByRole('button', { name: '标记完成' });
    expect(completionButton).toBeDisabled();
    expect(screen.getByRole('tooltip', {
      name: '完成前需要先粘贴完整代码生成一次代码提交记录，并且通过后才能标记完成。',
    })).toBeInTheDocument();
  });
});

function renderWorkbench(overrides: { onOpenSubmissions?: () => void } = {}) {
  return render(
    <I18nProvider>
      <PracticeChatWorkbench
        onBack={vi.fn()}
        onOpenSubmissions={overrides.onOpenSubmissions ?? vi.fn()}
        phaseIndex={1}
        plan={planFixture}
        problemSlug="two-sum"
      />
    </I18nProvider>,
  );
}

function apiResponse<T>(data: T): ApiResponse<T> {
  return {
    success: true,
    data,
    timestamp: '2026-06-25T00:00:00Z',
  };
}

function permissionRequestEvent(overrides: {
  expiresAt?: string;
  toolName?: string;
  copyCode?: string;
  displayName?: string;
  reason?: string;
  preview?: Partial<{
    problemSlug: string;
    problemTitle: string;
    languageHint: string;
    codeLength: number;
    codePreview: string;
    effects: string[];
    contextAvailable: boolean;
  }>;
} = {}) {
  const toolName = overrides.toolName ?? 'submit_practice_code_review';
  return {
    runId: 'run-1',
    stepIndex: 1,
    toolCallId: 'call-1',
    toolName,
    permissionRequestId: 'permission-1',
    displayName: overrides.displayName ?? '提交代码记录',
    reason: overrides.reason ?? '需要生成一次代码提交记录',
    copyCode: overrides.copyCode ?? 'PRACTICE_CODE_REVIEW_REQUESTED',
    preview: {
      problemSlug: 'two-sum',
      problemTitle: '两数之和',
      languageHint: 'Java',
      codeLength: 128,
      codePreview: 'class Solution { return; }',
      effects: ['会保存一条代码提交记录', '可能更新完成状态'],
      contextAvailable: true,
      ...overrides.preview,
    },
    expiresAt: overrides.expiresAt ?? new Date(Date.now() + 60_000).toISOString(),
  };
}

function agentToolEndEvent(overrides: {
  result: Record<string, unknown>;
  toolName?: string;
}) {
  return {
    ...agentToolEvent({ toolName: overrides.toolName }),
    result: overrides.result,
  };
}

function agentToolEvent(overrides: {
  toolName?: string;
} = {}) {
  return {
    runId: 'run-1',
    stepIndex: 1,
    toolCallId: 'call-1',
    toolName: overrides.toolName ?? 'submit_practice_code_review',
  };
}

function learnerProfileToolEvent(overrides: {
  toolCallId?: string;
} = {}) {
  return {
    runId: 'run-1',
    stepIndex: 2,
    toolCallId: overrides.toolCallId ?? 'profile-call-1',
    toolName: 'update_learner_declared_profile',
  };
}

function learnerProfileToolEndEvent(overrides: {
  toolCallId?: string;
  status: 'UPDATED' | 'NO_CHANGE' | 'FAILED';
}) {
  return {
    ...learnerProfileToolEvent({ toolCallId: overrides.toolCallId }),
    result: {
      type: 'learner_declared_profile_update',
      status: overrides.status,
    },
  };
}

function messageFixture(overrides: Partial<PracticeMessage> = {}): PracticeMessage {
  return {
    id: 2,
    role: 'ASSISTANT',
    messageType: 'CHAT',
    contentMarkdown: '代码提交记录已生成。',
    createdAt: '2026-06-25T00:11:00Z',
    ...overrides,
  };
}

function sessionFixture(overrides: Partial<PracticeSessionResponse> = {}): PracticeSessionResponse {
  return {
    session: {
      id: 101,
      planId: 7,
      phaseIndex: 1,
      problemSlug: 'two-sum',
      progressStatus: 'IN_PROGRESS',
      agentTaskId: 501,
      createdAt: '2026-06-25T00:00:00Z',
      updatedAt: '2026-06-25T00:00:00Z',
    },
    problem: {
      slug: 'two-sum',
      frontendId: 1,
      title: 'Two Sum',
      titleCn: '两数之和',
      difficulty: 'EASY',
      tags: ['Array', 'Hash Table'],
      leetcodeUrl: 'https://leetcode.cn/problems/two-sum/',
    },
    messages: [
      {
        id: 1,
        role: 'ASSISTANT',
        messageType: 'PROBLEM_STATEMENT',
        contentMarkdown: '给定整数数组 nums 和目标值 target。',
        createdAt: '2026-06-25T00:00:00Z',
      },
    ],
    activeRun: null,
    latestReview: null,
    completionGate: {
      canComplete: false,
      reasonCode: 'NO_REVIEW',
      message: '完成前需要先粘贴完整代码生成一次代码提交记录，并且通过后才能标记完成。',
      latestScore: null,
      passScore: 80,
    },
    ...overrides,
  };
}

function reviewSummaryFixture(
  overrides: Partial<PracticeCodeReviewHistoryResponse['reviews'][number]> = {},
): PracticeCodeReviewHistoryResponse['reviews'][number] {
  return {
    id: 42,
    versionNo: 2,
    language: 'java',
    contentLocale: 'zh-CN',
    totalScore: 92,
    passed: true,
    createdAt: '2026-06-25T00:10:00Z',
    ...overrides,
  };
}

function historyFixture(overrides: Partial<PracticeCodeReviewHistoryResponse> = {}): PracticeCodeReviewHistoryResponse {
  return {
    latestReview: null,
    reviews: [],
    completionGate: {
      canComplete: false,
      reasonCode: 'NO_REVIEW',
      message: '完成前需要先粘贴完整代码生成一次代码提交记录，并且通过后才能标记完成。',
      latestScore: null,
      passScore: 80,
    },
    ...overrides,
  };
}

function reviewDetailFixture(overrides: Partial<PracticeCodeReviewDetail> = {}): PracticeCodeReviewDetail {
  return {
    id: 42,
    sessionId: 101,
    versionNo: 2,
    language: 'java',
    contentLocale: 'zh-CN',
    submittedCode: 'class Solution { version2(); }',
    reviewMarkdown: '## 整体评价\n通过了边界条件。',
    passed: true,
    scores: scoreFixture({ total: 92 }),
    evidence: [
      { type: '边界条件', value: '覆盖空数组和重复值。' },
    ],
    deductionReasons: ['变量命名还可以更明确。'],
    improvementSuggestions: ['补充 target 不存在时的说明。'],
    contextSummary: '用户提交了 Java 解法并说明已在 LeetCode 通过。',
    createdAt: '2026-06-25T00:10:00Z',
    ...overrides,
  };
}

function scoreFixture(overrides: Partial<PracticeCodeReviewDetail['scores']> = {}): PracticeCodeReviewDetail['scores'] {
  return {
    correctness: 4,
    complexity: 2,
    edgeCases: 2,
    codeQuality: 1,
    problemFit: 1,
    total: 10,
    ...overrides,
  };
}

const planFixture: LearningPlanDetailResponse = {
  id: 7,
  contentLocale: 'zh-CN',
  status: 'ACTIVE',
  active: true,
  createdAt: '2026-06-25T00:00:00Z',
  updatedAt: '2026-06-25T00:00:00Z',
  title: '数组训练',
  summary: '练习数组和哈希表。',
  intent: 'PRACTICE_GOAL',
  objective: '系统训练数组题',
  durationWeeks: 2,
  level: 'BEGINNER',
  weeklyHours: 5,
  programmingLanguage: 'Java',
  difficultyDistribution: { easyPercent: 25, mediumPercent: 55, hardPercent: 20 },
  topicPreferences: ['Array'],
  additionalConstraints: '每周留一天复盘。',
  metadata: {},
  phases: [
    {
      phaseIndex: 1,
      title: '基础阶段',
      durationWeeks: 1,
      focus: '数组基础',
      problems: [
        {
          slug: 'two-sum',
          frontendId: 1,
          title: 'Two Sum',
          titleCn: '两数之和',
          difficulty: 'EASY',
          tags: ['Array', 'Hash Table'],
          reason: '基础题',
          sortOrder: 1,
          progressStatus: 'IN_PROGRESS',
        },
      ],
    },
  ],
};

const planFixtureWithSecondProblem: LearningPlanDetailResponse = {
  ...planFixture,
  phases: [
    {
      ...planFixture.phases[0],
      problems: [
        ...planFixture.phases[0].problems,
        {
          slug: 'valid-palindrome',
          frontendId: 125,
          title: 'Valid Palindrome',
          titleCn: '验证回文串',
          difficulty: 'EASY',
          tags: ['Two Pointers'],
          reason: '双指针基础题',
          sortOrder: 2,
          progressStatus: 'NOT_STARTED',
        },
      ],
    },
  ],
};
