import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ApiResponse, FeedbackThreadPage } from '../../types/api';
import { getAdminFeedbackThreads } from '../../services/api';
import FeedbackManagementPage from './FeedbackManagementPage';

vi.mock('../../services/api', () => ({
  ApiRequestError: class ApiRequestError extends Error {},
  getAdminFeedbackThread: vi.fn(),
  getAdminFeedbackThreads: vi.fn(),
  markAdminFeedbackRead: vi.fn(),
  replyAdminFeedback: vi.fn(),
  requireApiData: <T,>(response: ApiResponse<T>, fallbackMessage: string): T => {
    if (response.success && response.data !== undefined) return response.data;
    throw new Error(fallbackMessage);
  },
  updateAdminFeedbackStatus: vi.fn(),
}));

beforeEach(() => {
  vi.mocked(getAdminFeedbackThreads).mockResolvedValue(apiResponse(feedbackPage()));
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('FeedbackManagementPage', () => {
  it('keeps active filters and includes the selected feedback thread in navigation', async () => {
    const onNavigate = vi.fn();

    render(<FeedbackManagementPage onNavigate={onNavigate} search="?status=OPEN" />);

    const row = (await screen.findByText('管理员无法回复')).closest('tr');
    if (!row) throw new Error('Expected feedback row to be rendered.');
    fireEvent.click(row);

    expect(onNavigate).toHaveBeenCalledWith('/admin/feedback?status=OPEN&threadId=42');
  });
});

function apiResponse<T>(data: T): ApiResponse<T> {
  return { success: true, data, timestamp: '2026-07-17T00:00:00Z' };
}

function feedbackPage(): FeedbackThreadPage {
  return {
    items: [{
      id: 42,
      category: 'BUG',
      status: 'OPEN',
      subject: '管理员无法回复',
      lastSenderType: 'USER',
      unreadMessageCount: 1,
      createdAt: '2026-07-17T00:00:00Z',
      updatedAt: '2026-07-17T00:00:00Z',
      user: { id: 18, email: 'member@example.com' },
    }],
    total: 1,
    page: 1,
    pageSize: 50,
    unreadMessageCount: 1,
  };
}
