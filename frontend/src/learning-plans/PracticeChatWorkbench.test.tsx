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
const createOrReusePracticeSession = vi.mocked(api.createOrReusePracticeSession);
const getPracticeSessionActiveRun = vi.mocked(api.getPracticeSessionActiveRun);
const getPracticeSession = vi.mocked(api.getPracticeSession);
const getPracticeSessionMessages = vi.mocked(api.getPracticeSessionMessages);
const getPracticeSessionReviewDetail = vi.mocked(api.getPracticeSessionReviewDetail);
const getPracticeSessionReviews = vi.mocked(api.getPracticeSessionReviews);
const streamPracticeMessage = vi.mocked(api.streamPracticeMessage);
const updatePracticeProgressStatus = vi.mocked(api.updatePracticeProgressStatus);

describe('PracticeChatWorkbench review contracts', () => {
  beforeEach(() => {
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
    cleanup();
    vi.clearAllMocks();
  });

  it('renders no-review gate and disables completion', async () => {
    renderWorkbench();

    const completionButton = await screen.findByRole('button', { name: '标记完成' });
    expect(completionButton).toBeDisabled();
    expect(screen.getByRole('tooltip', {
      name: '完成前需要先粘贴完整代码完成一次 AI Review，并且 Review 通过后才能标记完成。',
    })).toHaveClass('completion-disabled-tooltip');
    expect(screen.queryByText('暂无 Review')).not.toBeInTheDocument();
    expect(screen.queryByText('通过分 80')).not.toBeInTheDocument();
    expect(updatePracticeProgressStatus).not.toHaveBeenCalled();
  });

  it('shows permission request details with preview and effects', async () => {
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

    const dialog = await screen.findByRole('dialog', { name: '代码 Review' });
    expect(within(dialog).getByText('需要运行代码 Review')).toBeInTheDocument();
    expect(within(dialog).getByText('两数之和 (two-sum)')).toBeInTheDocument();
    expect(within(dialog).getByText('Java')).toBeInTheDocument();
    expect(within(dialog).getByText('128 字符')).toBeInTheDocument();
    expect(within(dialog).getByText('class Solution { return; }')).toBeInTheDocument();
    expect(within(dialog).getByText('会保存一条 Review 记录')).toBeInTheDocument();
    expect(within(dialog).getByText('可能更新完成状态')).toBeInTheDocument();
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
    fireEvent.click(await screen.findByRole('button', { name: '允许' }));

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

  it('updates the pending assistant bubble when Review tool starts', async () => {
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
    expect(await screen.findByText('正在整理思路...')).toBeInTheDocument();

    act(() => {
      streamOptions?.onEvent({
        eventName: 'agent_tool_start',
        data: agentToolEvent({ toolName: 'submit_practice_code_review' }),
      });
    });

    expect(await screen.findByText('正在执行代码 Review...')).toBeInTheDocument();
    expect(screen.queryByText('正在整理思路...')).not.toBeInTheDocument();
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

    expect(await screen.findByText('代码 Review 已完成：未通过，4.5 / 80 分。')).toBeInTheDocument();

    act(() => {
      streamOptions?.onEvent({
        eventName: 'content_delta',
        data: { content: '主要问题是返回值固定，不能覆盖输入。' },
      });
    });

    expect(screen.getByText(/代码 Review 已完成：未通过，4.5 \/ 80 分。/)).toBeInTheDocument();
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
    fireEvent.click(await screen.findByRole('button', { name: '拒绝' }));

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

    const allowButton = await screen.findByRole('button', { name: '允许' });
    const denyButton = screen.getByRole('button', { name: '拒绝' });
    fireEvent.click(allowButton);

    expect(allowButton).toBeDisabled();
    expect(denyButton).toBeDisabled();

    await act(async () => {
      rejectDecision(new Error('授权提交失败'));
    });

    const dialog = await screen.findByRole('dialog', { name: '代码 Review' });
    expect(within(dialog).getByText('授权提交失败')).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: '允许' })).not.toBeDisabled();

    fireEvent.click(within(dialog).getByRole('button', { name: '允许' }));

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

    expect(await screen.findByRole('dialog', { name: '代码 Review' })).toBeInTheDocument();

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

    await waitFor(() => expect(screen.queryByRole('dialog', { name: '代码 Review' })).not.toBeInTheDocument());
    expect(screen.getByRole('status')).toHaveTextContent('本次未执行。');
  });

  it('renders rounded guidance tooltip for generated problem statements', async () => {
    renderWorkbench();

    expect(await screen.findByRole('img', { name: /题面内容为大模型生成/ })).toBeInTheDocument();
    expect(screen.getByRole('tooltip', { name: /本站不内置题库，最终以 LeetCode 为准/ }))
      .toHaveClass('toolbar-tooltip', 'practice-guidance-tooltip');
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
      element?.classList.contains('practice-message-plain-text') && element.textContent === pastedCode
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
      messageFixture({ id: 3, role: 'ASSISTANT', contentMarkdown: 'Review 已完成。' }),
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

    expect(await screen.findByText('Review 已完成。')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '标记完成' })).not.toBeDisabled();
    expect(getPracticeSessionMessages).toHaveBeenCalledWith(101, 50, expect.any(AbortSignal));
    expect(getPracticeSession).toHaveBeenCalledWith(101, expect.any(AbortSignal));
    expect(getPracticeSessionReviews).toHaveBeenCalledWith(101, expect.any(AbortSignal));
  });

  it('refreshes review drawer and completion gate once after successful Review tool end and run end', async () => {
    let streamOptions: Parameters<typeof api.streamPracticeMessage>[2] | undefined;
    let resolveStream: () => void = () => undefined;
    const passedGate = {
      canComplete: true,
      reasonCode: 'PASSED' as const,
      message: 'Review tool 刷新后已通过。',
      latestScore: 94,
      passScore: 80,
    };
    getPracticeSessionReviews
      .mockResolvedValueOnce(apiResponse(historyFixture({ reviews: [] })))
      .mockResolvedValueOnce(apiResponse(historyFixture({
        latestReview: reviewSummaryFixture({ id: 43, versionNo: 3, totalScore: 94, passed: true }),
        reviews: [reviewSummaryFixture({ id: 43, versionNo: 3, totalScore: 94, passed: true })],
        completionGate: passedGate,
      })));
    getPracticeSession.mockResolvedValue(apiResponse(sessionFixture({
      latestReview: reviewSummaryFixture({ id: 43, versionNo: 3, totalScore: 94, passed: true }),
      completionGate: passedGate,
    })));
    getPracticeSessionReviewDetail.mockResolvedValue(apiResponse(reviewDetailFixture({
      id: 43,
      versionNo: 3,
      reviewMarkdown: '## 整体评价\nReview tool 刷新后通过。',
      submittedCode: 'class Solution { version3(); }',
      scores: scoreFixture({ total: 94 }),
    })));
    streamPracticeMessage.mockImplementation(async (_sessionId, _request, options) => {
      streamOptions = options;
      await new Promise<void>((resolve) => {
        resolveStream = resolve;
      });
    });
    renderWorkbench();

    fireEvent.click(await screen.findByRole('button', { name: 'Review 记录' }));
    const drawer = await screen.findByRole('complementary', { name: 'Review 记录' });
    expect(await within(drawer).findByText('暂无代码 Review')).toBeInTheDocument();
    await waitFor(() => expect(getPracticeSessionReviews).toHaveBeenCalledTimes(1));

    fireEvent.change(screen.getByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' }), {
      target: { value: '这是第三版完整代码。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));
    await waitFor(() => expect(streamOptions).toBeDefined());

    act(() => {
      streamOptions?.onEvent({
        eventName: 'agent_tool_end',
        data: agentToolEndEvent({
          result: { type: 'practice_code_review_submitted' },
          toolName: 'submit_practice_code_review',
        }),
      });
    });

    expect(getPracticeSessionReviews).toHaveBeenCalledTimes(1);

    act(() => {
      streamOptions?.onEvent({ eventName: 'agent_run_end', data: { runId: 'run-1' } });
    });
    await act(async () => {
      resolveStream();
    });

    await waitFor(() => expect(getPracticeSessionReviews).toHaveBeenCalledTimes(2));
    expect(await within(drawer).findByRole('button', { name: /V3/ })).toBeInTheDocument();
    expect(await within(drawer).findByText('Review tool 刷新后通过。')).toBeInTheDocument();
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
    expect(screen.getByRole('tooltip', { name: '新版 Review 未通过。' })).toBeInTheDocument();
  });

  it('updates an open review drawer after agent_run_end refreshes reviews', async () => {
    getPracticeSessionReviews
      .mockResolvedValueOnce(apiResponse(historyFixture({ reviews: [] })))
      .mockResolvedValueOnce(apiResponse(historyFixture({
        latestReview: reviewSummaryFixture({ id: 43, versionNo: 3, totalScore: 94, passed: true }),
        reviews: [reviewSummaryFixture({ id: 43, versionNo: 3, totalScore: 94, passed: true })],
        completionGate: {
          canComplete: true,
          reasonCode: 'PASSED',
          message: '刷新后 Review 已通过。',
          latestScore: 94,
          passScore: 80,
        },
      })));
    getPracticeSessionReviewDetail.mockResolvedValue(apiResponse(reviewDetailFixture({
      id: 43,
      versionNo: 3,
      reviewMarkdown: '## 整体评价\n刷新后通过。',
      submittedCode: 'class Solution { version3(); }',
      scores: scoreFixture({ total: 94 }),
    })));
    getPracticeSession.mockResolvedValue(apiResponse(sessionFixture({
      completionGate: {
        canComplete: true,
        reasonCode: 'PASSED',
        message: '刷新后 Review 已通过。',
        latestScore: 94,
        passScore: 80,
      },
      latestReview: reviewSummaryFixture({ id: 43, versionNo: 3, totalScore: 94, passed: true }),
    })));
    streamPracticeMessage.mockImplementation(async (_sessionId, _request, options) => {
      options.onEvent?.({ eventName: 'agent_run_end', data: { runId: 'run_1' } });
    });
    renderWorkbench();

    fireEvent.click(await screen.findByRole('button', { name: 'Review 记录' }));
    const drawer = await screen.findByRole('complementary', { name: 'Review 记录' });
    expect(within(drawer).getByText('暂无代码 Review')).toBeInTheDocument();

    fireEvent.change(screen.getByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' }), {
      target: { value: '这是完整代码。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));

    expect(await within(drawer).findByRole('button', { name: /V3/ })).toBeInTheDocument();
    expect(await within(drawer).findByText('刷新后通过。')).toBeInTheDocument();
  });

  it('selects the latest review when an open drawer receives a newer version', async () => {
    getPracticeSessionReviews
      .mockResolvedValueOnce(apiResponse(historyFixture({
        latestReview: reviewSummaryFixture({ id: 42, versionNo: 2, totalScore: 92, passed: true }),
        reviews: [
          reviewSummaryFixture({ id: 42, versionNo: 2, totalScore: 92, passed: true }),
          reviewSummaryFixture({ id: 41, versionNo: 1, totalScore: 68, passed: false }),
        ],
      })))
      .mockResolvedValueOnce(apiResponse(historyFixture({
        latestReview: reviewSummaryFixture({ id: 43, versionNo: 3, totalScore: 94, passed: true }),
        reviews: [
          reviewSummaryFixture({ id: 43, versionNo: 3, totalScore: 94, passed: true }),
          reviewSummaryFixture({ id: 42, versionNo: 2, totalScore: 92, passed: true }),
          reviewSummaryFixture({ id: 41, versionNo: 1, totalScore: 68, passed: false }),
        ],
        completionGate: {
          canComplete: true,
          reasonCode: 'PASSED',
          message: '刷新后 Review 已通过。',
          latestScore: 94,
          passScore: 80,
        },
      })));
    getPracticeSessionReviewDetail
      .mockResolvedValueOnce(apiResponse(reviewDetailFixture({
        id: 42,
        versionNo: 2,
        reviewMarkdown: '## 整体评价\n第二版通过。',
        submittedCode: 'class Solution { version2(); }',
      })))
      .mockResolvedValueOnce(apiResponse(reviewDetailFixture({
        id: 43,
        versionNo: 3,
        reviewMarkdown: '## 整体评价\n第三版最新通过。',
        submittedCode: 'class Solution { version3(); }',
      })));
    getPracticeSession.mockResolvedValue(apiResponse(sessionFixture({
      completionGate: {
        canComplete: true,
        reasonCode: 'PASSED',
        message: '刷新后 Review 已通过。',
        latestScore: 94,
        passScore: 80,
      },
      latestReview: reviewSummaryFixture({ id: 43, versionNo: 3, totalScore: 94, passed: true }),
    })));
    streamPracticeMessage.mockImplementation(async (_sessionId, _request, options) => {
      options.onEvent?.({ eventName: 'agent_run_end', data: { runId: 'run_1' } });
    });
    renderWorkbench();

    fireEvent.click(await screen.findByRole('button', { name: 'Review 记录' }));
    const drawer = await screen.findByRole('complementary', { name: 'Review 记录' });
    expect(await within(drawer).findByText('第二版通过。')).toBeInTheDocument();

    fireEvent.change(screen.getByRole('textbox', { name: '输入你的思路、问题、代码或 LeetCode 反馈' }), {
      target: { value: '这是第三版完整代码。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送' }));

    expect(await within(drawer).findByRole('button', { name: /V3/ })).toBeInTheDocument();
    expect(await within(drawer).findByText('第三版最新通过。')).toBeInTheDocument();
    expect(getPracticeSessionReviewDetail).toHaveBeenCalledWith(101, 43, expect.any(AbortSignal));
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

  it('shows backend completion gate error', async () => {
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
    expect(screen.getByRole('tooltip', { name: '最新 Review 未通过：边界条件不足。' })).toBeInTheDocument();
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
    expect(screen.getByRole('tooltip', { name: '最新 Review 分数不足，请先修复代码。' })).toBeInTheDocument();
  });

  it('shows review drawer empty state', async () => {
    renderWorkbench();

    fireEvent.click(await screen.findByRole('button', { name: 'Review 记录' }));

    const drawer = await screen.findByRole('complementary', { name: 'Review 记录' });
    expect(within(drawer).getByText('暂无代码 Review')).toBeInTheDocument();
    expect(within(drawer).getByText('提交包含完整代码的练习消息后，系统会在这里展示 Review 版本。')).toBeInTheDocument();
    expect(getPracticeSessionReviews).toHaveBeenCalledWith(101, expect.any(AbortSignal));
    expect(getPracticeSessionReviews).toHaveBeenCalledTimes(1);
  });

  it('shows review versions and loads selected detail', async () => {
    createOrReusePracticeSession.mockResolvedValue(apiResponse(sessionFixture({
      completionGate: {
        canComplete: true,
        reasonCode: 'PASSED',
        message: '最新 Review 已通过。',
        latestScore: 92,
        passScore: 80,
      },
      latestReview: reviewSummaryFixture({ id: 42, versionNo: 2, totalScore: 92, passed: true }),
    })));
    getPracticeSessionReviews.mockResolvedValue(apiResponse(historyFixture({
      reviews: [
        reviewSummaryFixture({ id: 42, versionNo: 2, totalScore: 92, passed: true }),
        reviewSummaryFixture({ id: 41, versionNo: 1, totalScore: 68, passed: false }),
      ],
    })));
    getPracticeSessionReviewDetail.mockResolvedValueOnce(apiResponse(reviewDetailFixture({
      id: 42,
      versionNo: 2,
      reviewMarkdown: '## 整体评价\n通过了边界条件。',
      submittedCode: 'class Solution { version2(); }',
      passed: true,
      scores: scoreFixture({ total: 92 }),
    }))).mockResolvedValueOnce(apiResponse(reviewDetailFixture({
      id: 41,
      versionNo: 1,
      reviewMarkdown: '## 整体评价\n还需要修复空数组。',
      submittedCode: 'class Solution { version1(); }',
      passed: false,
      scores: scoreFixture({ total: 68 }),
    })));
    renderWorkbench();

    fireEvent.click(await screen.findByRole('button', { name: 'Review 记录' }));

    const drawer = await screen.findByRole('complementary', { name: 'Review 记录' });
    expect(await within(drawer).findByRole('button', { name: /V2/ })).toBeInTheDocument();
    expect(within(drawer).getByRole('button', { name: /V1/ })).toBeInTheDocument();
    expect(await within(drawer).findByText('通过了边界条件。')).toBeInTheDocument();
    expect(within(drawer).getByText('class Solution { version2(); }')).toBeInTheDocument();

    fireEvent.click(within(drawer).getByRole('button', { name: /V1/ }));

    expect(await within(drawer).findByText('还需要修复空数组。')).toBeInTheDocument();
    expect(within(drawer).getByText('class Solution { version1(); }')).toBeInTheDocument();
    expect(getPracticeSessionReviewDetail).toHaveBeenCalledWith(101, 42, expect.any(AbortSignal));
    expect(getPracticeSessionReviewDetail).toHaveBeenCalledWith(101, 41, expect.any(AbortSignal));
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
    getPracticeSessionReviews
      .mockResolvedValueOnce(apiResponse(historyFixture({
        reviews: [
          reviewSummaryFixture({ id: 42, versionNo: 2, totalScore: 92, passed: true }),
        ],
      })))
      .mockImplementationOnce(() => new Promise(() => undefined));

    const { rerender } = renderWorkbench();

    fireEvent.click(await screen.findByRole('button', { name: 'Review 记录' }));
    const drawer = await screen.findByRole('complementary', { name: 'Review 记录' });
    expect(await within(drawer).findByRole('button', { name: /V2/ })).toBeInTheDocument();

    rerender(
      <I18nProvider>
        <PracticeChatWorkbench
          onBack={vi.fn()}
          phaseIndex={1}
          plan={planFixtureWithSecondProblem}
          problemSlug="valid-palindrome"
        />
      </I18nProvider>,
    );

    const completionButton = await screen.findByRole('button', { name: '标记完成' });
    expect(completionButton).toBeDisabled();
    expect(screen.getByRole('tooltip', {
      name: '完成前需要先粘贴完整代码完成一次 AI Review，并且 Review 通过后才能标记完成。',
    })).toBeInTheDocument();
    expect(within(drawer).queryByRole('button', { name: /V2/ })).not.toBeInTheDocument();
  });
});

function renderWorkbench() {
  return render(
    <I18nProvider>
      <PracticeChatWorkbench
        onBack={vi.fn()}
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

function permissionRequestEvent() {
  return {
    runId: 'run-1',
    stepIndex: 1,
    toolCallId: 'call-1',
    toolName: 'practice_code_review',
    permissionRequestId: 'permission-1',
    displayName: '代码 Review',
    reason: '需要运行代码 Review',
    preview: {
      problemSlug: 'two-sum',
      problemTitle: '两数之和',
      languageHint: 'Java',
      codeLength: 128,
      codePreview: 'class Solution { return; }',
      effects: ['会保存一条 Review 记录', '可能更新完成状态'],
      contextAvailable: true,
    },
    expiresAt: '2026-06-26T00:01:00Z',
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

function messageFixture(overrides: Partial<PracticeMessage> = {}): PracticeMessage {
  return {
    id: 2,
    role: 'ASSISTANT',
    messageType: 'CHAT',
    contentMarkdown: 'Review 已完成。',
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
      message: '完成前需要先粘贴完整代码完成一次 AI Review，并且 Review 通过后才能标记完成。',
      latestScore: null,
      passScore: 80,
    },
    ...overrides,
  };
}

function reviewSummaryFixture(overrides: Partial<PracticeCodeReviewHistoryResponse['reviews'][number]> = {}) {
  return {
    id: 42,
    versionNo: 2,
    language: 'java',
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
      message: '完成前需要先粘贴完整代码完成一次 AI Review，并且 Review 通过后才能标记完成。',
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
  status: 'ACTIVE',
  createdAt: '2026-06-25T00:00:00Z',
  updatedAt: '2026-06-25T00:00:00Z',
  title: '数组训练',
  summary: '练习数组和哈希表。',
  intent: 'PRACTICE_GOAL',
  goal: '系统训练数组题',
  durationWeeks: 2,
  level: 'BEGINNER',
  weeklyHours: 5,
  programmingLanguage: 'Java',
  difficultyPreference: 'MIXED',
  interviewOriented: false,
  topicPreferences: ['Array'],
  profileSummary: '初学者',
  metadata: {},
  phases: [
    {
      phaseIndex: 1,
      title: '基础阶段',
      durationWeeks: 1,
      focus: '数组基础',
      objectives: ['理解哈希表'],
      recommendedTags: ['Array'],
      acceptanceCriteria: ['完成 Two Sum'],
      reviewAdvice: '复盘边界条件',
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
        },
      ],
    },
  ],
};
