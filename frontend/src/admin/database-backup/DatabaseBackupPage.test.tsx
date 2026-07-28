import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../../i18n/I18nProvider';
import DatabaseBackupPage from './DatabaseBackupPage';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('DatabaseBackupPage', () => {
  it('downloads the archive through the administrator backup endpoint', async () => {
    const fetchMock = vi.fn(() => Promise.resolve(new Response(new Blob(['archive']), {
      status: 200,
      headers: { 'Content-Disposition': 'attachment; filename="daily.ambak"' },
    })));
    const createObjectUrl = vi.fn(() => 'blob:test');
    const revokeObjectUrl = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: createObjectUrl });
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: revokeObjectUrl });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

    renderPage();
    fireEvent.click(screen.getByRole('button', { name: '下载全表备份' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/admin/database/backup', expect.objectContaining({
      credentials: 'same-origin',
      headers: expect.any(Headers),
    })));
    expect(createObjectUrl).toHaveBeenCalled();
    expect(click).toHaveBeenCalled();
    expect(revokeObjectUrl).toHaveBeenCalledWith('blob:test');
  });

  it('requires confirmation before restoring and returns to login after success', async () => {
    const fetchMock = vi.fn(() => Promise.resolve(jsonResponse({
      restoredAt: '2026-07-28T12:10:00Z',
      tableCount: 58,
      dumpSizeBytes: 128,
      loginRequired: true,
    })));
    const completed = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    renderPage(completed);
    const input = screen.getByLabelText('选择备份文件');
    fireEvent.change(input, { target: { files: [new File(['backup'], 'daily.ambak')] } });
    fireEvent.click(screen.getByRole('button', { name: '覆盖全部数据' }));

    expect(screen.getByRole('dialog')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '确认覆盖' }));

    await waitFor(() => expect(completed).toHaveBeenCalledTimes(1));
    const [path, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(path).toBe('/api/admin/database/restore');
    expect(init.method).toBe('POST');
    expect(init.body).toBeInstanceOf(FormData);
    expect((init.body as FormData).get('confirmation')).toBe('OVERWRITE_ALL_DATA');
    expect((init.body as FormData).get('file')).toBeInstanceOf(File);
  });

  it('keeps the page open and displays the API failure message', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response(JSON.stringify({
      success: false,
      error: { code: 'DATABASE_RESTORE_VERSION_MISMATCH', message: '版本不匹配' },
      timestamp: '2026-07-28T12:10:00Z',
    }), {
      status: 400,
      headers: { 'Content-Type': 'application/json' },
    }))));

    renderPage();
    fireEvent.change(screen.getByLabelText('选择备份文件'), {
      target: { files: [new File(['backup'], 'daily.ambak')] },
    });
    fireEvent.click(screen.getByRole('button', { name: '覆盖全部数据' }));
    fireEvent.click(screen.getByRole('button', { name: '确认覆盖' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('版本不匹配');
    expect(screen.getByRole('button', { name: '覆盖全部数据' })).toBeInTheDocument();
  });
});

function renderPage(onRestoreCompleted = vi.fn()) {
  render(
    <I18nProvider>
      <DatabaseBackupPage onRestoreCompleted={onRestoreCompleted} />
    </I18nProvider>,
  );
}

function jsonResponse(data: unknown): Response {
  return new Response(JSON.stringify({
    success: true,
    data,
    timestamp: '2026-07-28T12:10:00Z',
  }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}
