import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../i18n/I18nProvider';
import { learnerProfileDocument } from './testFixtures';
import LearnerProfileSection from './LearnerProfileSection';
import {
  getLearnerProfile,
  getLearnerProfileStatementEvidence,
} from '../services/api';
import type { ApiResponse } from '../types/api';

vi.mock('../services/api', () => ({
  getLearnerProfile: vi.fn(),
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

describe('LearnerProfileSection', () => {
  it('loads the document but defers evidence until a supported sentence is opened', async () => {
    vi.mocked(getLearnerProfile).mockResolvedValue(apiResponse(learnerProfileDocument()));
    vi.mocked(getLearnerProfileStatementEvidence).mockResolvedValue(apiResponse({ items: [] }));

    renderSection();

    expect(await screen.findByText('准备 Java 后端面试。')).toBeInTheDocument();
    expect(getLearnerProfileStatementEvidence).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: '打开第 1 条判断的依据' }));

    expect(await screen.findByRole('dialog', { name: '第 1 条判断的依据' })).toBeInTheDocument();
    expect(getLearnerProfileStatementEvidence).toHaveBeenCalledWith(
      'statement-ref-1',
      { cursor: undefined, limit: 20 },
      expect.any(AbortSignal),
    );
  });
});

function renderSection() {
  render(
    <I18nProvider>
      <LearnerProfileSection />
    </I18nProvider>,
  );
}

function apiResponse<T>(data: T): ApiResponse<T> {
  return { success: true, data, timestamp: '2026-07-20T12:00:00Z' };
}
