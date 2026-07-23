import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../../i18n/I18nProvider';
import SystemMonitoringPage from './SystemMonitoringPage';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('SystemMonitoringPage', () => {
  it('shows the API health check and refreshes it on demand', async () => {
    const fetchMock = vi.fn(() => Promise.resolve(apiResponse({ status: 'UP' })));
    vi.stubGlobal('fetch', fetchMock);

    renderPage();

    expect(await screen.findByRole('status')).toHaveTextContent('运行正常');
    expect(screen.getByText('API 服务')).toBeInTheDocument();
    expect(screen.getByText('UP')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/health', expect.objectContaining({
      headers: expect.any(Headers),
      signal: expect.any(AbortSignal),
    }));

    fireEvent.click(screen.getByRole('button', { name: '刷新运行状态' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
  });

  it('reports an unavailable service when the health check fails', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new Error('Network unavailable'))));

    renderPage();

    expect(await screen.findByRole('status')).toHaveTextContent('不可用');
    expect(screen.getByRole('alert')).toHaveTextContent('Network unavailable');
  });
});

function renderPage() {
  render(
    <I18nProvider>
      <SystemMonitoringPage />
    </I18nProvider>,
  );
}

function apiResponse(data: unknown): Response {
  return new Response(JSON.stringify({
    success: true,
    data,
    timestamp: '2026-07-23T00:00:00Z',
  }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}
