import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../i18n/I18nProvider';
import * as api from '../services/api';
import type {
  ApiResponse,
  LearningPlanDetailResponse,
  PracticeChatRunSubscription,
  PracticeCodeReviewDetail,
  PracticeCodeReviewHistoryResponse,
  PracticeMessage,
  PracticeSessionResponse,
  SseStreamEvent,
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
    readPracticeRunEvents: vi.fn(),
    startPracticeMessage: vi.fn(),
    updatePracticeProgressStatus: vi.fn(),
  };
});

const applyPracticeCoachSummaryProposal = vi.mocked(api.applyPracticeCoachSummaryProposal);
const createOrReusePracticeSession = vi.mocked(api.createOrReusePracticeSession);
const getPracticeSessionActiveRun = vi.mocked(api.getPracticeSessionActiveRun);
const getPracticeSession = vi.mocked(api.getPracticeSession);
const getPracticeSessionMessages = vi.mocked(api.getPracticeSessionMessages);
const getPracticeSessionReviewDetail = vi.mocked(api.getPracticeSessionReviewDetail);
const getPracticeSessionReviews = vi.mocked(api.getPracticeSessionReviews);
const readPracticeRunEvents = vi.mocked(api.readPracticeRunEvents);
const startPracticeMessage = vi.mocked(api.startPracticeMessage);
const updatePracticeProgressStatus = vi.mocked(api.updatePracticeProgressStatus);

function setBrowserLocale(locale: string) {
  Object.defineProperty(window.navigator, 'language', { configurable: true, value: locale });
  Object.defineProperty(window.navigator, 'languages', { configurable: true, value: [locale] });
}

describe('PracticeChatWorkbench run subscription contracts', () => {
  beforeEach(() => {
    setBrowserLocale('zh-CN');
    createOrReusePracticeSession.mockResolvedValue(apiResponse(sessionFixture()));
    getPracticeSessionActiveRun.mockResolvedValue(apiResponse(null));
    getPracticeSession.mockResolvedValue(apiResponse(sessionFixture()));
    getPracticeSessionMessages.mockResolvedValue(apiResponse(sessionFixture().messages));
    getPracticeSessionReviews.mockResolvedValue(apiResponse(historyFixture()));
    getPracticeSessionReviewDetail.mockResolvedValue(apiResponse(reviewDetailFixture()));
    startPracticeMessage.mockResolvedValue(runSubscription());
    readPracticeRunEvents.mockImplementation(() => new Promise<void>(() => undefined));
    applyPracticeCoachSummaryProposal.mockResolvedValue(apiResponse({
      proposalId: 'proposal-1',
      status: 'APPLIED',
      operation: 'REPLACE',
      appliedCoachSummaryRevision: 3,
      createdAt: '2026-06-25T00:12:00Z',
      appliedAt: '2026-06-25T00:13:00Z',
    }));
    updatePracticeProgressStatus.mockResolvedValue(apiResponse(sessionFixture({
      completionGate: completionGate({ canComplete: true, reasonCode: 'PASSED', latestScore: 92 }),
      latestReview: reviewSummaryFixture(),
    })));
  });

  afterEach(() => {
    vi.useRealTimers();
    cleanup();
    vi.clearAllMocks();
  });

  it('hides the completion action without a passed Review and enforces the input byte limit', async () => {
    renderWorkbench();

    expect(screen.queryByRole('button', { name: '标记完成' })).not.toBeInTheDocument();

    const composer = screen.getByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' });
    fireEvent.change(composer, { target: { value: '你'.repeat(2_731) } });
    expect(screen.getByText('8193 / 8192 字节')).toHaveClass('is-over-limit');
    expect(screen.getByRole('button', { name: '发送' })).toBeDisabled();
    expect(startPracticeMessage).not.toHaveBeenCalled();
  });

  it('grows the composer to its cap and supports focus editing with keyboard send', async () => {
    renderWorkbench();
    await screen.findByText('给定整数数组 nums 和目标值 target。');

    const composer = screen.getByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' });
    Object.defineProperty(composer, 'scrollHeight', { configurable: true, value: 480 });
    fireEvent.change(composer, { target: { value: '第一行\n第二行\n第三行' } });
    expect(composer).toHaveStyle({ height: '360px' });

    fireEvent.click(screen.getByRole('button', { name: '展开输入框' }));
    expect(screen.getByRole('dialog', { name: '展开输入框' })).toBeInTheDocument();
    expect(composer).toHaveFocus();

    fireEvent.keyDown(composer, { key: 'Enter', ctrlKey: true });
    await waitFor(() => expect(startPracticeMessage).toHaveBeenCalledWith(
      101,
      { message: '第一行\n第二行\n第三行' },
      expect.objectContaining({ idempotencyKey: expect.any(String), signal: expect.any(AbortSignal) }),
    ));
  });

  it('closes the expanded composer with Escape without discarding input', async () => {
    renderWorkbench();
    await screen.findByText('给定整数数组 nums 和目标值 target。');

    const composer = screen.getByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' });
    fireEvent.change(composer, { target: { value: '保留这段代码' } });
    fireEvent.click(screen.getByRole('button', { name: '展开输入框' }));
    fireEvent.keyDown(document, { key: 'Escape' });

    await waitFor(() => expect(screen.queryByRole('dialog', { name: '展开输入框' })).not.toBeInTheDocument());
    expect(composer).toHaveValue('保留这段代码');
  });

  it('hides the completion action when the latest Review did not pass', async () => {
    createOrReusePracticeSession.mockResolvedValue(apiResponse(sessionFixture({
      latestReview: reviewSummaryFixture({ passed: false, totalScore: 55 }),
      completionGate: completionGate({
        reasonCode: 'LATEST_REVIEW_FAILED',
        latestScore: 55,
      }),
    })));
    renderWorkbench();

    expect(await screen.findByText('给定整数数组 nums 和目标值 target。')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '标记完成' })).not.toBeInTheDocument();
  });

  it('starts a run through POST and reads the separately issued event URL', async () => {
    const subscription = captureSubscription();
    renderWorkbench();

    await sendMessage('我想用哈希表。');

    await waitFor(() => expect(startPracticeMessage).toHaveBeenCalledWith(
      101,
      { message: '我想用哈希表。' },
      expect.objectContaining({ idempotencyKey: expect.any(String), signal: expect.any(AbortSignal) }),
    ));
    expect(readPracticeRunEvents).toHaveBeenCalledWith(
      '/api/practice-sessions/101/runs/run-80/events',
      expect.objectContaining({ after: '0-0', signal: expect.any(AbortSignal) }),
    );
    await waitFor(() => expect(subscription.emit).toBeDefined());
    act(() => subscription.emit(sseEvent('content_delta', { content: '先用哈希表记录已经见过的数字。' }, '1710000000000-0')));
    expect(await screen.findByText('先用哈希表记录已经见过的数字。')).toBeInTheDocument();
  });

  it('reconnects from the latest Redis cursor after a normal short EOF', async () => {
    let subscriptionCount = 0;
    readPracticeRunEvents.mockImplementation(async (_url, options) => {
      subscriptionCount += 1;
      if (subscriptionCount === 1) {
        options.onEvent(sseEvent('content_delta', { content: '第一段。' }, '1710000000000-0'));
      } else if (subscriptionCount === 2) {
        options.onEvent(sseEvent('content_delta', { content: '第二段。' }, '1710000000001-0'));
      } else {
        options.onEvent(sseEvent('content_delta', { content: '第三段。' }, '1710000000002-0'));
      }
    });
    getPracticeSessionActiveRun.mockResolvedValue(apiResponse(activeRun()));
    renderWorkbench();

    await sendMessage('请继续。');

    await waitFor(() => expect(readPracticeRunEvents).toHaveBeenCalledTimes(3));
    expect(readPracticeRunEvents.mock.calls.map(([, options]) => options.after)).toEqual([
      '0-0',
      '1710000000000-0',
      '1710000000001-0',
    ]);
    expect(await screen.findByText('第一段。第二段。第三段。')).toBeInTheDocument();
  });

  it('restores a pre-existing active run from cursor zero after a page load', async () => {
    createOrReusePracticeSession.mockResolvedValue(apiResponse(sessionFixture({
      activeRun: activeRun(),
    })));
    getPracticeSessionActiveRun.mockResolvedValue(apiResponse(activeRun()));
    const subscription = captureSubscription();
    renderWorkbench();

    await waitFor(() => expect(readPracticeRunEvents).toHaveBeenCalled());
    act(() => subscription.emit(sseEvent('content_delta', { content: '刷新后补回的回复。' }, '1710000000000-0')));
    expect(await screen.findByText('刷新后补回的回复。')).toBeInTheDocument();
    expect(readPracticeRunEvents).toHaveBeenCalledWith(
      '/api/practice-sessions/101/runs/run-80/events',
      expect.objectContaining({ after: '0-0' }),
    );
  });

  it('shows capacity feedback when starting the run is rejected', async () => {
    startPracticeMessage.mockRejectedValue(new api.ApiRequestError(
      429,
      'Agent executor rejected task',
      'AGENT_EXECUTOR_OVERLOADED',
    ));
    renderWorkbench();

    await sendMessage('帮我分析一下这个解法。');

    const dialog = await screen.findByRole('dialog', { name: '提示' });
    expect(within(dialog).getByText('当前算力不够，请稍后重试。')).toBeInTheDocument();
  });

  it('shows capacity feedback when the subscribed run reports overload', async () => {
    const subscription = captureSubscription();
    renderWorkbench();

    await sendMessage('请给我一点提示。');
    await waitFor(() => expect(readPracticeRunEvents).toHaveBeenCalled());
    act(() => subscription.emit(sseEvent('agent_error', { code: 'AGENT_EXECUTOR_OVERLOADED', retryable: true }, '1710000000000-0')));

    expect(await screen.findByRole('dialog', { name: '提示' })).toHaveTextContent('当前算力不够，请稍后重试。');
  });

  it('does not render a permission dialog when an automatic Review runs', async () => {
    const subscription = captureSubscription();
    renderWorkbench();

    await sendMessage('请 Review 这段完整代码。');
    await waitFor(() => expect(readPracticeRunEvents).toHaveBeenCalled());
    act(() => {
      subscription.emit(sseEvent('agent_tool_start', agentToolEvent(), '1710000000000-0'));
      subscription.emit(sseEvent('agent_tool_end', agentToolEndEvent({
        result: { type: 'practice_code_review_submitted', status: 'SAVED', totalScore: 92, passed: true },
      }), '1710000000001-0'));
    });

    expect(await screen.findByText('代码提交记录已生成：已通过，92 / 80 分。')).toBeInTheDocument();
    expect(screen.queryByRole('dialog', { name: /提交代码记录|Submit code for review/ })).not.toBeInTheDocument();
  });

  it('renders Review and learner-profile work events in the assistant bubble', async () => {
    const subscription = captureSubscription();
    renderWorkbench();

    await sendMessage('记录我的学习目标。');
    await waitFor(() => expect(readPracticeRunEvents).toHaveBeenCalled());
    expect(screen.getByRole('status', { name: '正在整理思路...' })).toBeInTheDocument();

    act(() => {
      subscription.emit(sseEvent('agent_tool_start', learnerProfileToolEvent(), '1710000000000-0'));
    });
    expect(await screen.findByRole('status', { name: '正在更新学习记忆...' })).toBeInTheDocument();

    vi.useFakeTimers();
    await act(async () => {
      subscription.emit(sseEvent(
        'agent_tool_end',
        learnerProfileToolEndEvent({ status: 'UPDATED' }),
        '1710000000001-0',
      ));
    });
    expect(screen.getByRole('status', { name: '正在更新学习记忆...' })).toBeInTheDocument();
    act(() => vi.advanceTimersByTime(700));
    expect(screen.getByRole('status', { name: '已更新学习记忆' })).toHaveClass('is-updated');
  });

  it('renders an inline coach summary proposal and applies it', async () => {
    const subscription = captureSubscription();
    renderWorkbench();

    await sendMessage('生成并更新教练总结。');
    await waitFor(() => expect(readPracticeRunEvents).toHaveBeenCalled());
    act(() => {
      subscription.emit(sseEvent('content_delta', { content: '这段前置文案会被候选正文替换。' }, '1710000000000-0'));
      subscription.emit(sseEvent('agent_tool_end', agentToolEndEvent({
        toolName: 'propose_current_problem_coach_summary',
        result: {
          type: 'current_problem_coach_summary_proposed',
          status: 'PROPOSED',
          proposalId: 'proposal-1',
          summaryMarkdown: '# 教练总结\n\n- 先查补数，再写入当前元素。',
          operation: 'REPLACE',
        },
      }), '1710000000001-0'));
    });

    expect(await screen.findByRole('heading', { name: '教练总结' })).toBeInTheDocument();
    expect(screen.getByText('先查补数，再写入当前元素。')).toBeInTheDocument();
    expect(screen.queryByText('这段前置文案会被候选正文替换。')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '替换教练总结' }));
    await waitFor(() => expect(applyPracticeCoachSummaryProposal).toHaveBeenCalledWith(101, 'proposal-1'));
    expect(await screen.findByText('已保存到教练总结')).toBeInTheDocument();
  });

  it('refreshes persisted messages, session, and reviews after run completion', async () => {
    const refreshedMessages = [
      ...sessionFixture().messages,
      messageFixture({ id: 2, role: 'USER', contentMarkdown: '这是完整代码。' }),
      messageFixture({ id: 3, role: 'ASSISTANT', contentMarkdown: '代码提交记录已生成。' }),
      messageFixture({ id: 4, role: 'ASSISTANT', contentMarkdown: '第二次代码还存在问题，但本轮没有生成正式 Review。' }),
    ];
    getPracticeSessionMessages.mockResolvedValue(apiResponse(refreshedMessages));
    getPracticeSession.mockResolvedValue(apiResponse(sessionFixture({
      messages: refreshedMessages,
      completionGate: completionGate({ canComplete: true, reasonCode: 'PASSED', latestScore: 92 }),
      latestReview: reviewSummaryFixture(),
    })));
    getPracticeSessionReviews.mockResolvedValue(apiResponse(historyFixture({
      latestReview: reviewSummaryFixture(),
      reviews: [reviewSummaryFixture()],
      completionGate: completionGate({ canComplete: true, reasonCode: 'PASSED', latestScore: 92 }),
    })));
    readPracticeRunEvents.mockImplementation(async (_url, options) => {
      options.onEvent(sseEvent('agent_run_end', { runId: 'run-80' }, '1710000000000-0'));
    });
    renderWorkbench();

    await sendMessage('这是完整代码。');

    expect(await screen.findByText('代码提交记录已生成。')).toBeInTheDocument();
    expect(getPracticeSessionMessages).toHaveBeenCalledWith(101, 50, expect.any(AbortSignal));
    expect(getPracticeSession).toHaveBeenCalledWith(101, expect.any(AbortSignal));
    expect(getPracticeSessionReviews).toHaveBeenCalledWith(101, expect.any(AbortSignal));
    const reviewMessage = await screen.findByText('代码提交记录已生成。');
    const reviewBubble = reviewMessage.closest('article');
    expect(reviewBubble).not.toBeNull();
    expect(screen.getByRole('button', { name: '标记完成' })).not.toBeDisabled();
    expect(within(reviewBubble!).queryByRole('button', { name: '标记完成' })).not.toBeInTheDocument();
    const unreviewedBubble = screen.getByText('第二次代码还存在问题，但本轮没有生成正式 Review。').closest('article');
    expect(unreviewedBubble).not.toBeNull();
    expect(within(unreviewedBubble!).queryByRole('button', { name: '标记完成' })).not.toBeInTheDocument();
  });

  it('confirms a complete v2 run in memory without messages or active-run reads', async () => {
    const subscription = captureSubscription();
    startPracticeMessage.mockResolvedValue({ ...runSubscription(), realtimeProtocolVersion: 2 });
    readPracticeRunEvents.mockImplementation(async (_url, options) => {
      options.onEvent(sseEvent('agent_step_start', { runId: 'run-80', stepIndex: 1 }, '1-0'));
      options.onEvent(sseEvent('content_delta', { content: '直接确认的回复。' }, '2-0'));
      options.onEvent(sseEvent(
        'agent_step_end',
        { runId: 'run-80', stepIndex: 1, finishReason: 'STOP', toolCallCount: 0 },
        '3-0',
      ));
      options.onEvent(sseEvent(
        'agent_run_end',
        { runId: 'run-80', steps: 1, finishReason: 'STOP' },
        '4-0',
      ));
    });
    renderWorkbench();
    await screen.findByText('给定整数数组 nums 和目标值 target。');
    const messagesBefore = getPracticeSessionMessages.mock.calls.length;
    const activeBefore = getPracticeSessionActiveRun.mock.calls.length;

    await sendMessage('v2 直接确认');

    expect(await screen.findByText('直接确认的回复。')).toBeInTheDocument();
    expect(getPracticeSessionMessages).toHaveBeenCalledTimes(messagesBefore);
    expect(getPracticeSessionActiveRun).toHaveBeenCalledTimes(activeBefore);
    expect(getPracticeSession).not.toHaveBeenCalled();
    expect(getPracticeSessionReviews).not.toHaveBeenCalled();
    expect(subscription.emit).not.toHaveBeenCalled();
  });

  it('falls back after a v2 sequence gap and never treats the stream as final', async () => {
    startPracticeMessage.mockResolvedValue({ ...runSubscription(), realtimeProtocolVersion: 2 });
    getPracticeSessionActiveRun.mockResolvedValue(apiResponse(null));
    readPracticeRunEvents.mockImplementation(async (_url, options) => {
      options.onEvent(sseEvent('agent_step_start', { runId: 'run-80', stepIndex: 1 }, '1-0'));
      options.onEvent(sseEvent('agent_run_end', { runId: 'run-80', steps: 1, finishReason: 'STOP' }, '3-0'));
    });
    renderWorkbench();

    await sendMessage('模拟缺口');

    await waitFor(() => expect(getPracticeSessionMessages).toHaveBeenCalledWith(
      101,
      50,
      expect.any(AbortSignal),
    ));
    expect(getPracticeSessionActiveRun).toHaveBeenCalled();
  });

  it('keeps only the final assistant step text after an unprojected v2 Tool', async () => {
    startPracticeMessage.mockResolvedValue({ ...runSubscription(), realtimeProtocolVersion: 2 });
    readPracticeRunEvents.mockImplementation(async (_url, options) => {
      options.onEvent(sseEvent('agent_step_start', { runId: 'run-80', stepIndex: 1 }, '1-0'));
      options.onEvent(sseEvent('content_delta', { content: '第一轮草稿。' }, '2-0'));
      options.onEvent(sseEvent(
        'agent_step_end',
        { runId: 'run-80', stepIndex: 1, finishReason: 'TOOL_CALLS', toolCallCount: 1 },
        '3-0',
      ));
      options.onEvent(sseEvent('agent_step_start', { runId: 'run-80', stepIndex: 2 }, '4-0'));
      options.onEvent(sseEvent('content_delta', { content: '最后一轮文本。' }, '5-0'));
      options.onEvent(sseEvent(
        'agent_step_end',
        { runId: 'run-80', stepIndex: 2, finishReason: 'STOP', toolCallCount: 0 },
        '6-0',
      ));
      options.onEvent(sseEvent(
        'agent_run_end',
        { runId: 'run-80', steps: 2, finishReason: 'STOP' },
        '7-0',
      ));
    });
    renderWorkbench();

    await sendMessage('请多轮思考后作答');

    const finalAnswer = await screen.findByText('最后一轮文本。');
    expect(finalAnswer.closest('article')).not.toHaveTextContent('第一轮草稿。');
    expect(screen.queryByText('第一轮草稿。')).not.toBeInTheDocument();
  });

  it('refreshes reviews once after a complete v2 Review run without reading messages or active-run', async () => {
    startPracticeMessage.mockResolvedValue({ ...runSubscription(), realtimeProtocolVersion: 2 });
    getPracticeSessionReviews.mockResolvedValue(apiResponse(historyFixture({
      latestReview: reviewSummaryFixture(),
      reviews: [reviewSummaryFixture()],
      completionGate: completionGate({ canComplete: true, reasonCode: 'PASSED', latestScore: 92 }),
    })));
    readPracticeRunEvents.mockImplementation(async (_url, options) => {
      options.onEvent(sseEvent('agent_step_start', { runId: 'run-80', stepIndex: 1 }, '1-0'));
      options.onEvent(sseEvent(
        'agent_step_end',
        { runId: 'run-80', stepIndex: 1, finishReason: 'TOOL_CALLS', toolCallCount: 1 },
        '2-0',
      ));
      options.onEvent(sseEvent('agent_tool_start', agentToolEvent(), '3-0'));
      options.onEvent(sseEvent('agent_tool_end', agentToolEndEvent({
        result: {
          type: 'practice_code_review_submitted',
          status: 'SAVED',
          totalScore: 92,
          passed: true,
        },
      }), '4-0'));
      options.onEvent(sseEvent('agent_step_start', { runId: 'run-80', stepIndex: 2 }, '5-0'));
      options.onEvent(sseEvent('content_delta', { content: 'Review 已完成。' }, '6-0'));
      options.onEvent(sseEvent(
        'agent_step_end',
        { runId: 'run-80', stepIndex: 2, finishReason: 'STOP', toolCallCount: 0 },
        '7-0',
      ));
      options.onEvent(sseEvent(
        'agent_run_end',
        { runId: 'run-80', steps: 2, finishReason: 'STOP' },
        '8-0',
      ));
    });
    renderWorkbench();
    await screen.findByText('给定整数数组 nums 和目标值 target。');
    const messagesBefore = getPracticeSessionMessages.mock.calls.length;
    const activeBefore = getPracticeSessionActiveRun.mock.calls.length;

    await sendMessage('请 Review 这段完整代码。');

    expect(await screen.findByText('Review 已完成。')).toBeInTheDocument();
    await waitFor(() => expect(getPracticeSessionReviews).toHaveBeenCalledTimes(1));
    expect(getPracticeSessionReviews).toHaveBeenCalledWith(101, expect.any(AbortSignal));
    expect(getPracticeSessionMessages).toHaveBeenCalledTimes(messagesBefore);
    expect(getPracticeSessionActiveRun).toHaveBeenCalledTimes(activeBefore);
    expect(getPracticeSession).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: '标记完成' })).not.toBeDisabled();
  });

  it('confirms a v2 run after multiple projected Tools without fallback reads', async () => {
    startPracticeMessage.mockResolvedValue({ ...runSubscription(), realtimeProtocolVersion: 2 });
    const profileToolStart = agentToolEvent({
      toolName: 'update_learner_declared_profile',
      toolCallId: 'profile-call-1',
    });
    const proposalToolStart = agentToolEvent({
      toolName: 'propose_current_problem_coach_summary',
      toolCallId: 'proposal-call-1',
    });
    readPracticeRunEvents.mockImplementation(async (_url, options) => {
      options.onEvent(sseEvent('agent_step_start', { runId: 'run-80', stepIndex: 1 }, '1-0'));
      options.onEvent(sseEvent(
        'agent_step_end',
        { runId: 'run-80', stepIndex: 1, finishReason: 'TOOL_CALLS', toolCallCount: 2 },
        '2-0',
      ));
      options.onEvent(sseEvent('agent_tool_start', profileToolStart, '3-0'));
      options.onEvent(sseEvent('agent_tool_end', {
        ...profileToolStart,
        result: { type: 'learner_declared_profile_update', status: 'UPDATED' },
      }, '4-0'));
      options.onEvent(sseEvent('agent_tool_start', proposalToolStart, '5-0'));
      options.onEvent(sseEvent('agent_tool_end', {
        ...proposalToolStart,
        result: { type: 'current_problem_coach_summary_proposed', status: 'PROPOSED' },
      }, '6-0'));
      options.onEvent(sseEvent('agent_step_start', { runId: 'run-80', stepIndex: 2 }, '7-0'));
      options.onEvent(sseEvent('content_delta', { content: '多个 Tool 已完成。' }, '8-0'));
      options.onEvent(sseEvent(
        'agent_step_end',
        { runId: 'run-80', stepIndex: 2, finishReason: 'STOP', toolCallCount: 0 },
        '9-0',
      ));
      options.onEvent(sseEvent(
        'agent_run_end',
        { runId: 'run-80', steps: 2, finishReason: 'STOP' },
        '10-0',
      ));
    });
    renderWorkbench();
    await screen.findByText('给定整数数组 nums 和目标值 target。');
    const messagesBefore = getPracticeSessionMessages.mock.calls.length;
    const activeBefore = getPracticeSessionActiveRun.mock.calls.length;

    await sendMessage('测试多个 Tool');

    expect(await screen.findByText('多个 Tool 已完成。')).toBeInTheDocument();
    expect(getPracticeSessionMessages).toHaveBeenCalledTimes(messagesBefore);
    expect(getPracticeSessionActiveRun).toHaveBeenCalledTimes(activeBefore);
  });

  it('keeps the persisted active run visible when event replay is temporarily unavailable', async () => {
    getPracticeSessionActiveRun.mockResolvedValue(apiResponse(activeRun()));
    readPracticeRunEvents.mockRejectedValue(new Error('Redis stream unavailable'));
    renderWorkbench();

    await sendMessage('请继续。');

    await waitFor(() => expect(readPracticeRunEvents).toHaveBeenCalledTimes(3));
    expect(screen.getByRole('button', { name: '发送' })).toBeDisabled();
  });

  it('keeps the established toolbar, Markdown safety, and skip confirmation behavior', async () => {
    const onOpenSubmissions = vi.fn();
    renderWorkbench({ onOpenSubmissions });

    expect(await screen.findByRole('img', { name: /题面内容为大模型生成/ })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '代码提交记录' }));
    expect(onOpenSubmissions).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole('button', { name: '更多操作' }));
    fireEvent.click(screen.getByRole('menuitem', { name: '跳过本题' }));
    const dialog = await screen.findByRole('dialog', { name: '跳过本题？' });
    expect(within(dialog).getByText('跳过后仍可查看此题的对话，并可稍后标记完成。')).toBeInTheDocument();
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByRole('dialog', { name: '跳过本题？' })).not.toBeInTheDocument();
  });
});

async function sendMessage(message: string) {
  const composer = await screen.findByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' });
  fireEvent.change(composer, { target: { value: message } });
  fireEvent.click(screen.getByRole('button', { name: '发送' }));
}

function captureSubscription() {
  let onEvent: ((event: SseStreamEvent) => void) | undefined;
  const emit = vi.fn((event: SseStreamEvent) => onEvent?.(event));
  readPracticeRunEvents.mockImplementation(async (_url, options) => {
    onEvent = options.onEvent;
    await new Promise<void>(() => undefined);
  });
  return { emit };
}

function sseEvent(eventName: SseStreamEvent['eventName'], data: unknown, id: string): SseStreamEvent {
  return { eventName, data, id };
}

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
  return { success: true, data, timestamp: '2026-06-25T00:00:00Z' };
}

function runSubscription(): PracticeChatRunSubscription {
  return {
    type: 'accepted',
    taskId: 80,
    runUuid: 'run-80',
    status: 'ACCEPTED',
    eventsUrl: '/api/practice-sessions/101/runs/run-80/events',
    initialAfter: '0-0',
  };
}

function activeRun() {
  return {
    runId: 80,
    taskId: 80,
    runUuid: 'run-80',
    idempotencyKey: 'idem-80',
    startedAt: '2026-06-25T00:05:00Z',
  };
}

function agentToolEvent(overrides: { toolName?: string; toolCallId?: string } = {}) {
  return {
    runId: 'run-80',
    stepIndex: 1,
    toolCallId: overrides.toolCallId ?? 'call-1',
    toolName: overrides.toolName ?? 'submit_practice_code_review',
  };
}

function agentToolEndEvent(overrides: { result: Record<string, unknown>; toolName?: string; toolCallId?: string }) {
  return {
    ...agentToolEvent({ toolName: overrides.toolName, toolCallId: overrides.toolCallId }),
    result: overrides.result,
  };
}

function learnerProfileToolEvent() {
  return {
    runId: 'run-80',
    stepIndex: 2,
    toolCallId: 'profile-call-1',
    toolName: 'update_learner_declared_profile',
  };
}

function learnerProfileToolEndEvent(overrides: { status: 'UPDATED' | 'NO_CHANGE' | 'FAILED' }) {
  return {
    ...learnerProfileToolEvent(),
    result: { type: 'learner_declared_profile_update', status: overrides.status },
  };
}

function completionGate(overrides: Partial<PracticeSessionResponse['completionGate']> = {}) {
  return {
    canComplete: false,
    reasonCode: 'NO_REVIEW' as const,
    message: '完成前需要先粘贴完整代码生成一次代码提交记录，并且通过后才能标记完成。',
    latestScore: null,
    passScore: 80,
    ...overrides,
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
    messages: [{
      id: 1,
      role: 'ASSISTANT',
      messageType: 'PROBLEM_STATEMENT',
      contentMarkdown: '给定整数数组 nums 和目标值 target。',
      createdAt: '2026-06-25T00:00:00Z',
    }],
    activeRun: null,
    latestReview: null,
    completionGate: completionGate(),
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
  return { latestReview: null, reviews: [], completionGate: completionGate(), ...overrides };
}

function reviewDetailFixture(): PracticeCodeReviewDetail {
  return {
    id: 42,
    sessionId: 101,
    versionNo: 2,
    language: 'java',
    contentLocale: 'zh-CN',
    submittedCode: 'class Solution { version2(); }',
    reviewMarkdown: '## 整体评价\n通过了边界条件。',
    passed: true,
    scores: { correctness: 4, complexity: 2, edgeCases: 2, codeQuality: 1, problemFit: 1, total: 10 },
    evidence: [{ type: '边界条件', value: '覆盖空数组和重复值。' }],
    deductionReasons: ['变量命名还可以更明确。'],
    improvementSuggestions: ['补充 target 不存在时的说明。'],
    contextSummary: '用户提交了 Java 解法并说明已在 LeetCode 通过。',
    createdAt: '2026-06-25T00:10:00Z',
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
  phases: [{
    phaseIndex: 1,
    title: '基础阶段',
    durationWeeks: 1,
    focus: '数组基础',
    problems: [{
      slug: 'two-sum',
      frontendId: 1,
      title: 'Two Sum',
      titleCn: '两数之和',
      difficulty: 'EASY',
      tags: ['Array', 'Hash Table'],
      reason: '基础题',
      sortOrder: 1,
      progressStatus: 'IN_PROGRESS',
    }],
  }],
};
