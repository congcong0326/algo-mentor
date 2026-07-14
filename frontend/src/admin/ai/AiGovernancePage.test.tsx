import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import AiGovernancePage from './AiGovernancePage';
import { I18nProvider } from '../../i18n/I18nProvider';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('AiGovernancePage', () => {
  it('waits for confirmation before changing the global AI switch', async () => {
    const fetchMock = governanceFetch();
    vi.stubGlobal('fetch', fetchMock);

    renderPage();

    const toggle = await screen.findByRole('switch', { name: '全局 AI 状态' });
    expect(screen.getByText('已准入入口请求')).toBeInTheDocument();
    expect(screen.getAllByText('实际模型调用').length).toBeGreaterThan(0);

    fireEvent.click(toggle);
    expect(screen.getByRole('dialog')).toHaveTextContent('关闭全局 AI');
    expect(fetchMock.mock.calls.some(([url, init]) => url === '/api/admin/ai/settings' && init?.method === 'PATCH')).toBe(false);

    fireEvent.click(screen.getByRole('button', { name: '确认' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/ai/settings',
      expect.objectContaining({
        method: 'PATCH',
        body: JSON.stringify({ aiEnabled: false, defaultDailyRequestLimit: 50 }),
      }),
    ));
    await waitFor(() => expect(toggle).toHaveAttribute('aria-checked', 'false'));
  });

  it('uses URL navigation for a selected user and keeps unpriced calls separate from estimated cost', async () => {
    const onNavigate = vi.fn();
    vi.stubGlobal('fetch', governanceFetch());

    renderPage(onNavigate);

    expect((await screen.findAllByText('未定价调用')).length).toBeGreaterThan(0);
    fireEvent.click(await screen.findByRole('button', { name: '仅看该用户' }));

    expect(onNavigate).toHaveBeenCalledWith(expect.stringContaining('userId=42'));
    expect(onNavigate).toHaveBeenCalledWith(expect.stringContaining('dimension=user'));
    expect(screen.getAllByText('按当前价格估算').length).toBeGreaterThan(0);
  });

  it('shows input, cached input, output, and total tokens for each user', async () => {
    vi.stubGlobal('fetch', governanceFetch());

    renderPage();

    const userRow = (await screen.findByText('Member')).closest('tr');
    if (!userRow) {
      throw new Error('Expected the user usage row to be rendered.');
    }
    const table = userRow.closest('table');
    if (!table) {
      throw new Error('Expected the user usage table to be rendered.');
    }

    expect(table).toHaveTextContent('输入 Token');
    expect(table).toHaveTextContent('缓存输入 Token');
    expect(table).toHaveTextContent('输出 Token');
    expect(userRow).toHaveTextContent('1,200');
    expect(userRow).toHaveTextContent('200');
    expect(userRow).toHaveTextContent('300');
    expect(userRow).toHaveTextContent('1,500');
  });

  it('submits model price decimals as the original strings', async () => {
    const fetchMock = governanceFetch();
    vi.stubGlobal('fetch', fetchMock);

    renderPage(vi.fn(), '?tab=pricing');

    fireEvent.click(await screen.findByRole('button', { name: '新增价格' }));

    fireEvent.change(screen.getByLabelText('Provider'), { target: { value: 'OPENAI' } });
    fireEvent.change(screen.getByLabelText('模型'), { target: { value: 'gpt-5.2' } });
    fireEvent.change(screen.getByLabelText('非缓存输入价格（USD / 1M Token）'), { target: { value: '1.25000000' } });
    fireEvent.change(screen.getByLabelText('缓存输入价格（USD / 1M Token）'), { target: { value: '0.12500000' } });
    fireEvent.change(screen.getByLabelText('输出价格（USD / 1M Token）'), { target: { value: '10.00000000' } });
    fireEvent.change(screen.getByLabelText('成本倍率'), { target: { value: '1.000000' } });
    fireEvent.click(screen.getByRole('button', { name: '创建价格' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/ai/model-prices',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          provider: 'openai',
          model: 'gpt-5.2',
          inputPricePerMillion: '1.25000000',
          cachedInputPricePerMillion: '0.12500000',
          outputPricePerMillion: '10.00000000',
          costMultiplier: '1.000000',
          enabled: true,
        }),
      }),
    ));
  });
});

function renderPage(onNavigate = vi.fn(), search = '') {
  render(
    <I18nProvider>
      <AiGovernancePage onNavigate={onNavigate} search={search} />
    </I18nProvider>,
  );
}

function governanceFetch() {
  return vi.fn((url: string, init?: RequestInit) => {
    if (url === '/api/admin/ai/settings' && !init?.method) {
      return Promise.resolve(apiResponse(settings(true)));
    }
    if (url === '/api/admin/ai/settings' && init?.method === 'PATCH') {
      return Promise.resolve(apiResponse(settings(false)));
    }
    if (url.startsWith('/api/admin/ai/usage/summary')) {
      return Promise.resolve(apiResponse(summary()));
    }
    if (url.startsWith('/api/admin/ai/usage/by-user')) {
      return Promise.resolve(apiResponse({
        items: [{
          userId: 42,
          email: 'member@example.com',
          displayName: 'Member',
          accountStatus: 'ACTIVE',
          metrics: metrics(),
          todayEntryRequestCount: 3,
          effectiveDailyRequestLimit: 50,
          effectiveAiEnabled: true,
        }],
        total: 1,
        page: 1,
        pageSize: 100,
      }));
    }
    if (url.startsWith('/api/admin/ai/model-prices') && !init?.method) {
      return Promise.resolve(apiResponse({ items: [], unpricedModels: [] }));
    }
    if (url === '/api/admin/ai/model-prices' && init?.method === 'POST') {
      return Promise.resolve(apiResponse({
        id: 1,
        provider: 'openai',
        model: 'gpt-5.2',
        currency: 'USD',
        inputPricePerMillion: '1.25000000',
        cachedInputPricePerMillion: '0.12500000',
        outputPricePerMillion: '10.00000000',
        costMultiplier: '1.000000',
        enabled: true,
        createdAt: '2026-07-14T00:00:00Z',
        updatedAt: '2026-07-14T00:00:00Z',
      }));
    }
    return Promise.reject(new Error(`Unexpected URL: ${url}`));
  });
}

function settings(aiEnabled: boolean) {
  return {
    aiEnabled,
    defaultDailyRequestLimit: 50,
    updatedBy: 1,
    updatedByDisplayName: 'Admin',
    updatedAt: '2026-07-14T00:00:00Z',
  };
}

function summary() {
  return {
    from: '2026-07-14',
    to: '2026-07-14',
    quotaZone: 'UTC',
    admittedEntryRequestCount: 3,
    metrics: metrics(),
  };
}

function metrics() {
  return {
    modelCallCount: 5,
    inputTokens: 1200,
    cachedTokens: 200,
    outputTokens: 300,
    reasoningTokens: 50,
    totalTokens: 1500,
    pricedCallCount: 4,
    pricedTokenCount: 1300,
    estimatedCostUsd: '0.01250000',
    unpricedCallCount: 1,
    unpricedTokenCount: 200,
  };
}

function apiResponse(data: unknown, status = 200): Response {
  return new Response(JSON.stringify({
    success: status < 400,
    data,
    timestamp: '2026-07-14T00:00:00Z',
  }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
