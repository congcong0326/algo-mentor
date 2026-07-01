import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '../services/api';
import type {
  ApiResponse,
  LearningPlanDetailResponse,
  LearningPlanExtensionApplyResponse,
  LearningPlanExtensionReadyEvent,
} from '../types/api';
import LearningPlanDetail from './LearningPlanDetail';

vi.mock('../services/api', async () => {
  const actual = await vi.importActual<typeof import('../services/api')>('../services/api');
  return {
    ...actual,
    applyLearningPlanExtensionProposal: vi.fn(),
    discardLearningPlanExtensionProposal: vi.fn(),
    streamLearningPlanExtensionProposal: vi.fn(),
    streamLearningPlanExtensionProposalRevision: vi.fn(),
  };
});

const applyLearningPlanExtensionProposal = vi.mocked(api.applyLearningPlanExtensionProposal);
const discardLearningPlanExtensionProposal = vi.mocked(api.discardLearningPlanExtensionProposal);
const streamLearningPlanExtensionProposal = vi.mocked(api.streamLearningPlanExtensionProposal);
const streamLearningPlanExtensionProposalRevision = vi.mocked(api.streamLearningPlanExtensionProposalRevision);

describe('LearningPlanDetail extension orchestration', () => {
  beforeEach(() => {
    applyLearningPlanExtensionProposal.mockResolvedValue(apiResponse(applyResponseFixture()));
    discardLearningPlanExtensionProposal.mockResolvedValue(apiResponse(undefined));
    streamLearningPlanExtensionProposal.mockResolvedValue(undefined);
    streamLearningPlanExtensionProposalRevision.mockResolvedValue(undefined);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('renders a ready extension from the proposal stream', async () => {
    streamLearningPlanExtensionProposal.mockImplementation(async (_planId, _request, options) => {
      options.onEvent({
        eventName: 'plan_extension_ready',
        data: extensionReadyEventFixture(),
      });
    });

    renderDetail();

    fireEvent.change(screen.getByRole('textbox', { name: '想继续学习？描述接下来的目标' }), {
      target: { value: '  继续练动态规划  ' },
    });
    fireEvent.click(screen.getByRole('button', { name: '生成扩展建议' }));

    await screen.findByRole('heading', { name: '待追加内容' });
    expect(screen.getByText('动态规划强化')).toBeInTheDocument();
    expect(streamLearningPlanExtensionProposal).toHaveBeenCalledWith(88, {
      instruction: '继续练动态规划',
    }, expect.any(Object));
  });

  it('preserves a work_error message when the stream closes without a terminal event', async () => {
    streamLearningPlanExtensionProposal.mockImplementation(async (_planId, _request, options) => {
      options.onEvent({
        eventName: 'work_error',
        data: { message: '模型额度不足，请稍后再试。' },
      });
    });

    renderDetail();

    fireEvent.change(screen.getByRole('textbox', { name: '想继续学习？描述接下来的目标' }), {
      target: { value: '继续练图论' },
    });
    fireEvent.click(screen.getByRole('button', { name: '生成扩展建议' }));

    expect(await screen.findByText('模型额度不足，请稍后再试。')).toBeInTheDocument();
    expect(screen.queryByText('生成扩展建议失败，请稍后重试。')).not.toBeInTheDocument();
  });

  it('keeps the pending extension when apply returns success false', async () => {
    applyLearningPlanExtensionProposal.mockResolvedValue(apiFailure('APPLY_FAILED', '应用扩展失败。'));
    const onPlanUpdated = vi.fn(() => Promise.resolve());

    await renderReadyExtension({ onPlanUpdated });
    fireEvent.click(screen.getByRole('button', { name: '应用扩展' }));

    expect(await screen.findByText('应用扩展失败。')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '待追加内容' })).toBeInTheDocument();
    expect(onPlanUpdated).not.toHaveBeenCalled();
  });

  it('keeps the pending extension when discard returns success false', async () => {
    discardLearningPlanExtensionProposal.mockResolvedValue(apiFailure('DISCARD_FAILED', '放弃扩展失败。'));

    await renderReadyExtension();
    fireEvent.click(screen.getByRole('button', { name: '放弃' }));

    expect(await screen.findByText('放弃扩展失败。')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '待追加内容' })).toBeInTheDocument();
  });

  it('waits for the detail refresh before clearing a successfully applied extension', async () => {
    const refresh = deferred<void>();
    const onPlanUpdated = vi.fn(() => refresh.promise);

    await renderReadyExtension({ onPlanUpdated });
    fireEvent.click(screen.getByRole('button', { name: '应用扩展' }));

    await waitFor(() => expect(onPlanUpdated).toHaveBeenCalled());
    expect(screen.getByRole('button', { name: '应用扩展' })).toBeDisabled();
    expect(screen.getByRole('heading', { name: '待追加内容' })).toBeInTheDocument();

    refresh.resolve();

    await waitFor(() => {
      expect(screen.queryByRole('heading', { name: '待追加内容' })).not.toBeInTheDocument();
    });
  });
});

async function renderReadyExtension({
  onPlanUpdated = vi.fn(() => Promise.resolve()),
}: {
  onPlanUpdated?: () => Promise<void>;
} = {}) {
  streamLearningPlanExtensionProposal.mockImplementation(async (_planId, _request, options) => {
    options.onEvent({
      eventName: 'plan_extension_ready',
      data: extensionReadyEventFixture(),
    });
  });

  renderDetail({ onPlanUpdated });
  fireEvent.change(screen.getByRole('textbox', { name: '想继续学习？描述接下来的目标' }), {
    target: { value: '继续练动态规划' },
  });
  fireEvent.click(screen.getByRole('button', { name: '生成扩展建议' }));

  await screen.findByRole('heading', { name: '待追加内容' });
}

function renderDetail({
  onPlanUpdated = vi.fn(() => Promise.resolve()),
}: {
  onPlanUpdated?: () => Promise<void>;
} = {}) {
  render(
    <LearningPlanDetail
      onBack={vi.fn()}
      onPlanUpdated={onPlanUpdated}
      onProblemSelect={vi.fn()}
      plan={planFixture}
    />,
  );
}

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((nextResolve, nextReject) => {
    resolve = nextResolve;
    reject = nextReject;
  });
  return { promise, resolve, reject };
}

function apiResponse<T>(data: T): ApiResponse<T> {
  return {
    success: true,
    data,
    timestamp: '2026-07-01T00:00:00Z',
  };
}

function apiFailure<T>(code: string, message: string): ApiResponse<T> {
  return {
    success: false,
    error: {
      code,
      message,
    },
    timestamp: '2026-07-01T00:00:00Z',
  };
}

function applyResponseFixture(): LearningPlanExtensionApplyResponse {
  return {
    planId: 88,
    proposalGroupId: 30,
    proposalId: 31,
    status: 'APPLIED',
    appendedPhaseCount: 1,
  };
}

function extensionReadyEventFixture(): LearningPlanExtensionReadyEvent {
  return {
    proposalGroupId: 30,
    proposalId: 31,
    planId: 88,
    revisionNo: 1,
    status: 'READY',
    supersededProposalIds: [],
    summary: '增加动态规划强化',
    extensionDraft: {
      summary: '追加动态规划和背包专题。',
      metadata: { source: 'ai' },
      newPhases: [{
        phaseIndex: 3,
        title: '动态规划强化',
        durationWeeks: 2,
        focus: '线性 DP 和背包模型',
        objectives: ['掌握状态定义'],
        recommendedTags: ['Dynamic Programming'],
        acceptanceCriteria: ['能解释转移方程'],
        reviewAdvice: '复盘状态压缩。',
        problems: [{
          slug: 'climbing-stairs',
          frontendId: 70,
          title: 'Climbing Stairs',
          titleCn: '爬楼梯',
          difficulty: 'EASY',
          tags: ['Dynamic Programming'],
          reason: '用低门槛题目恢复状态转移。',
          sortOrder: 1,
        }],
      }],
    },
  };
}

const planFixture: LearningPlanDetailResponse = {
  id: 88,
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
