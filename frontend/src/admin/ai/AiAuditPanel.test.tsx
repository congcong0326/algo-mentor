import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../../i18n/I18nProvider';
import AiAuditPanel from './AiAuditPanel';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('AiAuditPanel', () => {
  it('loads run summaries first and retrieves a request snapshot only after a step is selected', async () => {
    const fetchMock = vi.fn((url: string) => {
      if (url.startsWith('/api/admin/ai/audit/runs?')) {
        return Promise.resolve(response({ items: [run()], total: 1, page: 1, pageSize: 20 }));
      }
      if (url === '/api/admin/ai/audit/runs/21') {
        return Promise.resolve(response(detail()));
      }
      if (url === '/api/admin/ai/audit/runs/21/steps/1') {
        return Promise.resolve(response(stepDetail()));
      }
      if (url === '/api/admin/ai/audit/runs/21/steps/1?raw=true') {
        return Promise.resolve(response({ ...stepDetail(), requestSnapshot: { metadata: { finalRequestTokenEstimate: 7920 } } }));
      }
      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);

    render(<I18nProvider><AiAuditPanel /></I18nProvider>);

    await screen.findByText('PRACTICE_CHAT');
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      expect.stringMatching(/^\/api\/admin\/ai\/audit\/runs\?/),
    ]);

    fireEvent.click(screen.getByRole('button', { name: '查看 21' }));
    await screen.findByRole('heading', { name: 'Run #21' });
    expect(screen.getAllByText('7,920 / 8,000').length).toBeGreaterThan(0);
    expect(fetchMock).toHaveBeenCalledWith('/api/admin/ai/audit/runs/21', expect.anything());
    expect(fetchMock.mock.calls.map(([url]) => String(url))).not.toContain('/api/admin/ai/audit/runs/21/steps/1');

    fireEvent.click(screen.getByRole('button', { name: /Step 1/ }));
    await screen.findByText('Prompt Assembly 估算');
    expect(screen.getAllByText('8,762').length).toBeGreaterThan(0);
    expect(fetchMock).toHaveBeenCalledWith('/api/admin/ai/audit/runs/21/steps/1', expect.anything());

    fireEvent.click(screen.getByRole('tab', { name: 'Messages' }));
    expect(await screen.findByText('SERVER_VALIDATED')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('tab', { name: 'Raw JSON' }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/ai/audit/runs/21/steps/1?raw=true', expect.anything(),
    ));
  });

  it('sends selected identifiers, completion, cache, and exception filters to the list endpoint', async () => {
    const fetchMock = vi.fn((url: string) => {
      void url;
        return Promise.resolve(response({ items: [], total: 0, page: 1, pageSize: 20, statistics: statistics() }));
    });
    vi.stubGlobal('fetch', fetchMock);

    render(<I18nProvider><AiAuditPanel /></I18nProvider>);
    await screen.findByText('没有符合条件的审计 run。');
    fireEvent.change(screen.getByLabelText('Task ID'), { target: { value: '17' } });
    fireEvent.change(screen.getByLabelText('Turn ID'), { target: { value: '19' } });
    fireEvent.change(screen.getByLabelText('完成原因'), { target: { value: 'stop' } });
    fireEvent.change(screen.getByLabelText('最少缓存 Token'), { target: { value: '200' } });
    fireEvent.change(screen.getByLabelText('最多缓存 Token'), { target: { value: '800' } });
    fireEvent.change(screen.getByLabelText('用途'), { target: { value: 'LEARNING' } });
    fireEvent.change(screen.getByLabelText('业务场景'), { target: { value: 'practice' } });
    fireEvent.change(screen.getByLabelText('最小缓存比例'), { target: { value: '0.2' } });
    fireEvent.change(screen.getByLabelText('最大缓存比例'), { target: { value: '0.8' } });
    fireEvent.change(screen.getByLabelText('排序字段'), { target: { value: 'overBudget' } });
    fireEvent.change(screen.getByLabelText('排序方向'), { target: { value: 'asc' } });
    fireEvent.click(screen.getByLabelText('仅超预算'));
    fireEvent.click(screen.getByRole('button', { name: '筛选' }));

    await waitFor(() => expect(fetchMock.mock.calls.length).toBeGreaterThan(1));
    const url = new URL(String(fetchMock.mock.calls.at(-1)?.[0]), 'http://localhost');
    expect(url.pathname).toBe('/api/admin/ai/audit/runs');
    expect(url.searchParams.get('overBudget')).toBe('true');
    expect(url.searchParams.get('taskId')).toBe('17');
    expect(url.searchParams.get('turnId')).toBe('19');
    expect(url.searchParams.get('finishReason')).toBe('stop');
    expect(url.searchParams.get('minCachedTokens')).toBe('200');
    expect(url.searchParams.get('maxCachedTokens')).toBe('800');
    expect(url.searchParams.get('purpose')).toBe('LEARNING');
    expect(url.searchParams.get('source')).toBe('practice');
    expect(url.searchParams.get('minCacheRatio')).toBe('0.2');
    expect(url.searchParams.get('maxCacheRatio')).toBe('0.8');
    expect(url.searchParams.get('sort')).toBe('overBudget');
    expect(url.searchParams.get('direction')).toBe('asc');
    expect(url.searchParams.get('pageSize')).toBe('20');
    expect(screen.getByText('3 · 15.0%')).toBeInTheDocument();
    expect(screen.getByText('42,000 / 180,000 · 23.3%')).toBeInTheDocument();
  });

  it('moves through pages without retaining the previous page data', async () => {
    const fetchMock = vi.fn((url: string) => {
      const request = new URL(url, 'http://localhost');
      const page = request.searchParams.get('page');
      return Promise.resolve(response({
        items: page === '2' ? [run(22)] : [run()],
        total: 40,
        page: Number(page),
        pageSize: 20,
      }));
    });
    vi.stubGlobal('fetch', fetchMock);

    render(<I18nProvider><AiAuditPanel /></I18nProvider>);
    await screen.findByText('PRACTICE_CHAT');
    fireEvent.click(screen.getByRole('button', { name: '下一页' }));

    await screen.findByText('PAGE_TWO');
    expect(screen.queryByText('PRACTICE_CHAT')).not.toBeInTheDocument();
    const request = new URL(String(fetchMock.mock.calls.at(-1)?.[0]), 'http://localhost');
    expect(request.searchParams.get('page')).toBe('2');
  });

  it('shows a two-step tool timeline with provider over-budget and compaction details', async () => {
    const fetchMock = vi.fn((url: string) => {
      if (url.startsWith('/api/admin/ai/audit/runs?')) {
        return Promise.resolve(response({ items: [run()], total: 1, page: 1, pageSize: 20 }));
      }
      if (url === '/api/admin/ai/audit/runs/21') {
        return Promise.resolve(response(toolRunDetail()));
      }
      if (url === '/api/admin/ai/audit/runs/21/steps/1') {
        return Promise.resolve(response(toolStepDetail()));
      }
      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);

    render(<I18nProvider><AiAuditPanel /></I18nProvider>);
    await screen.findByText('PRACTICE_CHAT');
    fireEvent.click(screen.getByRole('button', { name: '查看 21' }));

    await screen.findByRole('heading', { name: 'Run #21' });
    expect(screen.getByText('PROVIDER_ACTUAL_OVER_BUDGET')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Step 1/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Step 2/ })).toBeInTheDocument();
    expect(screen.getByText(/角色分布 system: 1, user: 1/)).toBeInTheDocument();
    expect(screen.getByText(/错误: PROVIDER_TIMEOUT Provider timed out/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /Step 1/ }));
    await screen.findByText('Prompt Assembly 估算');
    expect(screen.getByText('当前请求使用的历史消息')).toBeInTheDocument();
    expect(screen.getByText('practice.history.00000000000000000001')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('tab', { name: '工具' }));
    expect(await screen.findAllByText('lookup')).toHaveLength(2);
    expect(screen.getByText('工具结果 Preview')).toBeInTheDocument();
    expect(screen.getByText(/"summary": "two matches"/)).toBeInTheDocument();
    expect(screen.getByText('结果存储方式: blob')).toBeInTheDocument();
    expect(screen.getByText('原始结果引用: tool-result:1')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('tab', { name: '压缩' }));
    expect(await screen.findByText('tool_result_preview, section_drop')).toBeInTheDocument();
    expect(screen.getByText(/legacy.history.1/)).toBeInTheDocument();
  });
});

function response(data: unknown): Response {
  return new Response(JSON.stringify({ success: true, data, timestamp: '2026-08-12T00:00:00Z' }), {
    headers: { 'Content-Type': 'application/json' },
  });
}

function run(runId = 21) {
  return {
    runId, runUuid: 'run-uuid', taskId: 7, turnId: 9, userId: 42, userDisplayName: 'Member',
    scenario: runId === 22 ? 'PAGE_TWO' : 'PRACTICE_CHAT', purpose: 'LEARNING', source: 'practice', provider: 'openai', model: 'gpt-test', status: 'SUCCEEDED', finishReason: 'stop',
    stepCount: 1, failedStepCount: 0, toolCallCount: 0, failedToolCallCount: 0, promptTokenBudget: 8000,
    assemblyTokenEstimate: 6800, finalRequestTokenEstimate: 7920, actualInputTokens: 8762, cachedTokens: 640,
    overBudgetTokens: 762, compactionApplied: false, compactionActionCount: 0, providerError: false,
    startedAt: '2026-08-12T00:00:00Z', endedAt: '2026-08-12T00:00:10Z',
  };
}

function statistics() {
  return {
    runCount: 20, overBudgetRunCount: 3, overBudgetRate: 0.15, compactionRunCount: 4, compactionRate: 0.2,
    usageReportedRunCount: 18, inputTokens: 180000, cachedTokens: 42000, cacheRatio: 42 / 180,
  };
}

function detail() {
  return {
    run: run(), attemptNo: 1, maxSteps: 4, taskTurns: [{
      turnId: 9, sequenceNo: 1, status: 'SUCCEEDED', userMessage: 'Explain this', runAttemptCount: 1,
      runAttempts: [{ runId: 21, attemptNo: 1, status: 'SUCCEEDED' }], hasTools: false,
      usage: { inputTokens: 8762, cachedTokens: 640, outputTokens: 400, totalTokens: 9162 }, overBudgetTokens: 762,
    }],
    steps: [{
      stepIndex: 1, status: 'SUCCEEDED', provider: 'openai', model: 'gpt-test', messageCount: 2,
      toolsCount: 0, finalRequestTokenEstimate: 7920, promptTokenBudget: 8000, remainingBudgetTokens: 80,
      usage: { inputTokens: 8762, cachedTokens: 640 }, compactionApplied: false, snapshotAvailable: true,
      toolCallCount: 0, failedToolCallCount: 0,
    }],
    totalUsage: { inputTokens: 8762, cachedTokens: 640, outputTokens: 400, totalTokens: 9162 },
  };
}

function stepDetail() {
  return {
    step: detail().steps[0], snapshotId: 77,
    messages: [{ role: 'SERVER_VALIDATED', content: 'Question' }], tools: [], toolCalls: [],
    requestSnapshot: undefined,
    metadata: { assemblyTokenEstimate: 6800, finalRequestTokenEstimate: 7920 },
  };
}

function toolRunDetail() {
  return {
    ...detail(),
    run: { ...run(), stepCount: 2, toolCallCount: 1, compactionApplied: true, compactionActionCount: 2 },
    steps: [
      {
        stepIndex: 1, status: 'SUCCEEDED', provider: 'openai', model: 'gpt-test', messageCount: 2,
        roleCounts: { user: 1, system: 1 },
        toolsCount: 1, messageTokenEstimate: 7000, toolsTokenEstimate: 500, finalRequestTokenEstimate: 7600,
        promptTokenBudget: 8000, remainingBudgetTokens: 400, usage: { inputTokens: 7800, cachedTokens: 0 },
        compactionApplied: true, compaction: { compactionActions: ['tool_result_preview', 'section_drop'] },
        snapshotAvailable: true, toolCallCount: 1, failedToolCallCount: 0,
      },
      {
        stepIndex: 2, status: 'FAILED', provider: 'openai', model: 'gpt-test', messageCount: 4, roleCounts: { assistant: 1, tool: 1, user: 2 },
        toolsCount: 1, messageTokenEstimate: 7300, toolsTokenEstimate: 500, finalRequestTokenEstimate: 7920,
        promptTokenBudget: 8000, remainingBudgetTokens: 80, usage: { inputTokens: 8762, cachedTokens: 640 },
        compactionApplied: false, snapshotAvailable: true, toolCallCount: 0, failedToolCallCount: 0,
        errorCode: 'PROVIDER_TIMEOUT', errorMessage: 'Provider timed out',
      },
    ],
  };
}

function toolStepDetail() {
  return {
    step: toolRunDetail().steps[0], snapshotId: 77,
    messages: [
      { role: 'USER', auditSource: 'USER_INPUT', auditSectionId: 'practice.history.00000000000000000001', content: '上一轮的算法问题' },
      { role: 'USER', auditSource: 'USER_INPUT', content: '请解释长中文消息' },
      { role: 'ASSISTANT', auditSource: 'MODEL_GENERATED', content: '正在调用工具' },
    ],
    tools: [{ name: 'lookup', description: 'Find examples', inputSchema: { type: 'object', properties: { query: { type: 'string' } } } }],
    toolCalls: [{
      toolCallId: 'call_lookup', toolName: 'lookup', status: 'SUCCEEDED', arguments: { query: 'edge case' },
      preview: { summary: 'two matches' }, resultStorageMode: 'blob', resultRef: 'tool-result:1', resultTokenEstimate: 5,
    }],
    metadata: {
      assemblyTokenEstimate: 6800, finalRequestTokenEstimate: 7600, budgetStatus: 'WITHIN_ESTIMATE',
      compactionApplied: true, compactionActions: ['tool_result_preview', 'section_drop'],
      truncatedSectionIds: ['legacy.history.1'], droppedSectionIds: ['legacy.history.0'],
    },
  };
}
