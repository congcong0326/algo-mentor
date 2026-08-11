import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../../i18n/I18nProvider';
import LearningPlanPolicyPage from './LearningPlanPolicyPage';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('LearningPlanPolicyPage', () => {
  it('queries and displays only the learning plan creation policy type', async () => {
    const fetchMock = vi.fn((_url: string, _init?: RequestInit) => Promise.resolve(apiResponse(policyPage())));
    vi.stubGlobal('fetch', fetchMock);

    renderPage();

    expect(await screen.findByRole('heading', { name: '学习计划策略' })).toBeInTheDocument();
    expect(screen.getByText('全局基线')).toBeInTheDocument();
    expect(screen.getByText('30 天')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/policies?typeCode=learning-plan.creation.v1&page=1&pageSize=100',
      expect.objectContaining({ headers: expect.any(Headers), signal: expect.any(AbortSignal) }),
    );
  });

  it('creates typed content and accepts zero creation limits', async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url.startsWith('/api/admin/policies?')) {
        return Promise.resolve(apiResponse(policyPage()));
      }
      if (url === '/api/admin/policies' && init?.method === 'POST') {
        return Promise.resolve(apiResponse(policy(23, 3, '暂停创建')));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);

    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: '新建策略' }));
    fireEvent.change(screen.getByLabelText('策略名称'), { target: { value: '暂停创建' } });
    fireEvent.change(screen.getByLabelText('正式计划上限'), { target: { value: '0' } });
    fireEvent.change(screen.getByLabelText('每日草案额度'), { target: { value: '0' } });
    fireEvent.change(screen.getByLabelText('草案保留天数'), { target: { value: '21' } });
    fireEvent.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/policies',
      expect.objectContaining({ method: 'POST', headers: expect.any(Headers) }),
    ));
    const postCall = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST');
    expect(JSON.parse(String(postCall?.[1]?.body))).toEqual({
      typeCode: 'learning-plan.creation.v1',
      name: '暂停创建',
      description: '',
      status: 'ENABLED',
      subjectRange: { allSubject: true, subjects: [] },
      content: {
        maxSavedPlans: 0,
        dailyDraftCreationLimit: 0,
        draftRetentionDays: 21,
      },
    });
  });

  it('rejects values outside the backend policy contract', async () => {
    const fetchMock = vi.fn((_url: string, _init?: RequestInit) => Promise.resolve(apiResponse(policyPage())));
    vi.stubGlobal('fetch', fetchMock);

    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: '新建策略' }));
    fireEvent.change(screen.getByLabelText('策略名称'), { target: { value: '无效策略' } });
    fireEvent.change(screen.getByLabelText('草案保留天数'), { target: { value: '0' } });
    fireEvent.click(screen.getByRole('button', { name: '保存' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('草案保留天数必须为 1–365 的整数');
    expect(fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST')).toHaveLength(0);
  });

  it('reorders the complete policy set with current versions', async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url.startsWith('/api/admin/policies?')) {
        return Promise.resolve(apiResponse(policyPage()));
      }
      if (url === '/api/admin/policy-types/learning-plan.creation.v1/order' && init?.method === 'PUT') {
        return Promise.resolve(apiResponse(null));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);

    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: '下移策略 全局基线' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/policy-types/learning-plan.creation.v1/order',
      expect.objectContaining({ method: 'PUT', headers: expect.any(Headers) }),
    ));
    const putCall = fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT');
    expect(JSON.parse(String(putCall?.[1]?.body))).toEqual({
      policyIds: [22, 21],
      versions: { 21: 4, 22: 2 },
    });
  });
});

function renderPage() {
  render(
    <I18nProvider>
      <LearningPlanPolicyPage />
    </I18nProvider>,
  );
}

function policyPage() {
  return {
    items: [
      policy(21, 1, '全局基线'),
      {
        ...policy(22, 2, '高级用户'),
        subjectRange: { allSubject: false, subjects: [{ type: 'GROUP', id: 7 }] },
        content: { maxSavedPlans: 100, dailyDraftCreationLimit: 20, draftRetentionDays: 30 },
        version: 2,
      },
    ],
    total: 2,
    page: 1,
    pageSize: 100,
  };
}

function policy(id: number, priority: number, name: string) {
  return {
    id,
    typeCode: 'learning-plan.creation.v1',
    name,
    description: '学习计划创建治理。',
    status: 'ENABLED',
    priority,
    subjectRange: { allSubject: true, subjects: [] },
    content: { maxSavedPlans: 30, dailyDraftCreationLimit: 5, draftRetentionDays: 14 },
    version: 4,
    createdBy: 1,
    createdAt: '2026-08-11T08:00:00Z',
    updatedBy: 1,
    updatedAt: '2026-08-11T09:00:00Z',
  };
}

function apiResponse(data: unknown): Response {
  return new Response(JSON.stringify({
    success: true,
    data,
    timestamp: '2026-08-11T09:00:00Z',
  }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}
