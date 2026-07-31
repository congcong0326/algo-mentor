import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useState } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../i18n/I18nProvider';
import { getLearnerProfileStatementEvidence } from '../services/api';
import type { ApiResponse, LearnerProfileCitation } from '../types/api';
import LearnerProfileEvidenceDrawer from './LearnerProfileEvidenceDrawer';
import { citation, codeReviewEvidence, userMessageEvidence } from './testFixtures';

vi.mock('../services/api', () => ({
  getLearnerProfileStatementEvidence: vi.fn(),
  requireApiData: <T,>(response: ApiResponse<T>, fallbackMessage: string): T => {
    if (response.success && response.data !== undefined) {
      return response.data;
    }
    throw new Error(fallbackMessage);
  },
  setApiLocale: vi.fn(),
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('LearnerProfileEvidenceDrawer', () => {
  it('loads evidence on open and appends later pages without duplicate items', async () => {
    vi.mocked(getLearnerProfileStatementEvidence)
      .mockResolvedValueOnce(apiResponse({
        items: [userMessageEvidence(11), codeReviewEvidence(12)],
        nextCursor: 'next-page',
      }))
      .mockResolvedValueOnce(apiResponse({
        items: [codeReviewEvidence(12), userMessageEvidence(13)],
        nextCursor: null,
      }));

    renderDrawer(citation(1));

    expect(await screen.findByText('two-sum')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '加载更多' }));

    await waitFor(() => expect(getLearnerProfileStatementEvidence).toHaveBeenCalledTimes(2));
    expect(screen.getAllByText('two-sum')).toHaveLength(1);
    expect(screen.getAllByText('来自你在题目聊天中的陈述')).toHaveLength(2);
    expect(screen.getByText('已显示全部依据。')).toBeInTheDocument();
  });

  it('aborts a pending request and clears old evidence when the citation changes', async () => {
    let firstSignal: AbortSignal | undefined;
    vi.mocked(getLearnerProfileStatementEvidence)
      .mockImplementationOnce((_statementRef, _options, signal) => {
        firstSignal = signal;
        return new Promise(() => {});
      })
      .mockResolvedValueOnce(apiResponse({ items: [userMessageEvidence(21)] }));
    const { rerender } = renderDrawer(citation(1));

    await waitFor(() => expect(getLearnerProfileStatementEvidence).toHaveBeenCalledTimes(1));
    rerender(
      <I18nProvider>
        <LearnerProfileEvidenceDrawer citation={citation(2)} onClose={vi.fn()} open />
      </I18nProvider>,
    );

    expect(firstSignal?.aborted).toBe(true);
    expect(await screen.findByText('我希望在三个月内完成后端面试准备。')).toBeInTheDocument();
    expect(screen.queryByText('two-sum')).not.toBeInTheDocument();
  });

  it('shows a retry state when evidence loading fails', async () => {
    vi.mocked(getLearnerProfileStatementEvidence)
      .mockRejectedValueOnce(new Error('network error'))
      .mockResolvedValueOnce(apiResponse({ items: [userMessageEvidence(31)] }));
    renderDrawer(citation(1));

    expect(await screen.findByRole('alert')).toHaveTextContent('依据加载失败');
    fireEvent.click(screen.getByRole('button', { name: '重试' }));

    expect(await screen.findByText('我希望在三个月内完成后端面试准备。')).toBeInTheDocument();
    expect(getLearnerProfileStatementEvidence).toHaveBeenCalledTimes(2);
  });

  it('traps focus and returns it to the opening statement when Escape closes the drawer', async () => {
    vi.mocked(getLearnerProfileStatementEvidence).mockResolvedValue(apiResponse({ items: [] }));
    render(
      <I18nProvider>
        <DrawerHarness citation={citation(1)} />
      </I18nProvider>,
    );
    const opener = screen.getByRole('button', { name: '打开依据' });
    opener.focus();
    fireEvent.click(opener);

    const drawer = await screen.findByRole('dialog', { name: '第 1 条判断的依据' });
    fireEvent.keyDown(drawer, { key: 'Escape' });

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(opener).toHaveFocus();
  });

  it('builds a precise review deep link from the cited profile sentence', async () => {
    vi.mocked(getLearnerProfileStatementEvidence).mockResolvedValue(apiResponse({ items: [codeReviewEvidence(12)] }));
    renderDrawer(citation(1));

    expect(await screen.findByRole('link', { name: '查看本次提交' })).toHaveAttribute(
      'href',
      '/learning-plans/31/phases/2/problems/two-sum/submissions?review=12&from=learner-profile&profileAnchor=learner-profile-statement-101',
    );
  });
});

function renderDrawer(value: LearnerProfileCitation) {
  return render(
    <I18nProvider>
      <LearnerProfileEvidenceDrawer citation={value} onClose={vi.fn()} open />
    </I18nProvider>,
  );
}

function DrawerHarness({ citation: value }: { citation: LearnerProfileCitation }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button onClick={() => setOpen(true)} type="button">打开依据</button>
      <LearnerProfileEvidenceDrawer citation={value} onClose={() => setOpen(false)} open={open} />
    </>
  );
}

function apiResponse<T>(data: T): ApiResponse<T> {
  return { success: true, data, timestamp: '2026-07-20T12:00:00Z' };
}
