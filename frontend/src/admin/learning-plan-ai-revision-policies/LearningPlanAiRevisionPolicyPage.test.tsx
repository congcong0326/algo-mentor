import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../../i18n/I18nProvider';
import LearningPlanAiRevisionPolicyPage from './LearningPlanAiRevisionPolicyPage';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('LearningPlanAiRevisionPolicyPage', () => {
  it('loads the registered type and submits all capability switches', async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url.startsWith('/api/admin/policies?')) {
        return Promise.resolve(apiResponse(policyPage()));
      }
      if (url === '/api/admin/policies' && init?.method === 'POST') {
        return Promise.resolve(apiResponse(policy(23)));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);

    renderPage();
    expect(await screen.findByRole('heading', { name: '学习计划 AI 修订策略' })).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/policies?typeCode=learning-plan.ai-revision-access.v1&page=1&pageSize=100',
      expect.objectContaining({ headers: expect.any(Headers) }),
    );

    fireEvent.click(screen.getByRole('button', { name: '新建策略' }));
    fireEvent.change(screen.getByLabelText('策略名称'), { target: { value: '内测 AI 修订' } });
    fireEvent.click(screen.getByLabelText('模板草案 AI 修订'));
    fireEvent.click(screen.getByLabelText('已保存计划 AI 修订（扩展提案）'));
    expect(screen.getByLabelText('AI 个性化草案 AI 修订')).toBeChecked();
    fireEvent.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/policies',
      expect.objectContaining({ method: 'POST', headers: expect.any(Headers) }),
    ));
    const postCall = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST');
    expect(JSON.parse(String(postCall?.[1]?.body))).toMatchObject({
      typeCode: 'learning-plan.ai-revision-access.v1',
      content: {
        templateDraftRevisionEnabled: true,
        savedPlanRevisionEnabled: true,
        personalizedDraftRevisionEnabled: true,
      },
    });
  });

  it('submits a selected user-group scope', async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url.startsWith('/api/admin/policies?')) {
        return Promise.resolve(apiResponse(policyPage()));
      }
      if (url === '/api/admin/user-groups?page=1&pageSize=20&status=ACTIVE') {
        return Promise.resolve(apiResponse({
          items: [{
            id: 7,
            code: 'INTERNAL_TESTERS',
            name: '内测用户组',
            status: 'ACTIVE',
            activeMemberCount: 2,
            createdAt: '2026-08-11T08:00:00Z',
            updatedAt: '2026-08-11T08:00:00Z',
          }],
          total: 1,
          page: 1,
          pageSize: 20,
        }));
      }
      if (url === '/api/admin/policies' && init?.method === 'POST') {
        return Promise.resolve(apiResponse(policy(23)));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);

    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: '新建策略' }));
    fireEvent.change(screen.getByLabelText('策略名称'), { target: { value: '内测用户组策略' } });
    fireEvent.click(screen.getByText('指定主体'));
    fireEvent.click(await screen.findByRole('button', { name: /内测用户组/ }));
    fireEvent.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() => expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(true));
    const postCall = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST');
    expect(JSON.parse(String(postCall?.[1]?.body))).toMatchObject({
      subjectRange: { allSubject: false, subjects: [{ type: 'GROUP', id: 7 }] },
    });
  });
});

function renderPage() {
  render(
    <I18nProvider>
      <LearningPlanAiRevisionPolicyPage />
    </I18nProvider>,
  );
}

function policyPage() {
  return { items: [policy(21)], total: 1, page: 1, pageSize: 100 };
}

function policy(id: number) {
  return {
    id,
    typeCode: 'learning-plan.ai-revision-access.v1',
    name: '基线策略',
    description: '',
    status: 'ENABLED',
    priority: 1,
    subjectRange: { allSubject: true, subjects: [] },
    content: {
      templateDraftRevisionEnabled: false,
      savedPlanRevisionEnabled: false,
      personalizedDraftRevisionEnabled: false,
    },
    version: 1,
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
