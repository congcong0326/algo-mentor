import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ApiResponse, FeedbackThreadDetail, FeedbackThreadPage } from '../types/api';
import {
  createFeedback,
  getFeedbackThread,
  getFeedbackThreads,
  markFeedbackRead,
  replyFeedback,
} from '../services/api';
import { clearFeedbackSourceContext, recordFeedbackRequestContext } from './feedbackSourceContext';
import UserFeedbackDialog from './UserFeedbackDialog';

vi.mock('../services/api', () => ({
  ApiRequestError: class ApiRequestError extends Error {},
  createFeedback: vi.fn(),
  getFeedbackThread: vi.fn(),
  getFeedbackThreads: vi.fn(),
  markFeedbackRead: vi.fn(),
  replyFeedback: vi.fn(),
  requireApiData: <T,>(response: ApiResponse<T>, fallbackMessage: string): T => {
    if (response.success && response.data !== undefined) return response.data;
    throw new Error(fallbackMessage);
  },
}));

describe('UserFeedbackDialog', () => {
  beforeEach(() => {
    clearFeedbackSourceContext();
    vi.mocked(getFeedbackThreads).mockResolvedValue(response(threadPage()));
    vi.mocked(getFeedbackThread).mockResolvedValue(response(threadDetail()));
    vi.mocked(markFeedbackRead).mockResolvedValue(response({ threadId: 42, markedReadCount: 1, unreadMessageCount: 0 }));
    vi.mocked(replyFeedback).mockResolvedValue(response(threadDetail()));
    vi.mocked(createFeedback).mockResolvedValue(response(threadDetail()));
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('loads conversations without marking them read, then marks an opened unread conversation', async () => {
    const onUnreadCountChanged = vi.fn();

    render(<UserFeedbackDialog onClose={vi.fn()} onUnreadCountChanged={onUnreadCountChanged} />);

    expect(await screen.findByText('管理员回复待查看')).toBeInTheDocument();
    expect(markFeedbackRead).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: /管理员回复待查看/ }));

    await waitFor(() => expect(markFeedbackRead).toHaveBeenCalledWith(42));
    expect(onUnreadCountChanged).toHaveBeenCalledWith(0);
    expect(screen.getByText('我们已经定位到问题。')).toBeInTheDocument();
  });

  it('filters the list without changing the URL', async () => {
    render(<UserFeedbackDialog onClose={vi.fn()} />);

    await screen.findByText('管理员回复待查看');
    fireEvent.click(screen.getByRole('button', { name: '处理中' }));

    await waitFor(() => expect(getFeedbackThreads).toHaveBeenLastCalledWith({ status: 'OPEN' }));
    expect(window.location.pathname).not.toBe('/feedback');
  });

  it('creates feedback in the same dialog and selects the new conversation', async () => {
    const created = { ...threadDetail(), id: 99, subject: '新建会话', messages: [{ ...threadDetail().messages[0], id: 99, senderType: 'USER' as const, content: '新反馈正文' }] };
    vi.mocked(createFeedback).mockResolvedValue(response(created));
    recordFeedbackRequestContext('/api/learning-plans', '8ce1bf019955');

    render(<UserFeedbackDialog onClose={vi.fn()} />);

    await screen.findByText('管理员回复待查看');
    fireEvent.click(screen.getByRole('button', { name: '新建反馈' }));
    fireEvent.change(screen.getByRole('textbox', { name: '主题（可选）' }), { target: { value: '新建会话' } });
    fireEvent.change(screen.getByRole('textbox', { name: '正文' }), { target: { value: '新反馈正文' } });
    fireEvent.click(screen.getByRole('button', { name: '提交反馈' }));

    await waitFor(() => expect(createFeedback).toHaveBeenCalledWith(expect.objectContaining({
      category: 'BUG',
      subject: '新建会话',
      content: '新反馈正文',
      sourcePath: '/',
      sourceRequestId: '8ce1bf019955',
    })));
    expect(screen.queryByText('将附带页面来源：/')).not.toBeInTheDocument();
    expect(screen.queryByText('请求 ID：8ce1bf019955')).not.toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: '新建会话' })).toBeInTheDocument();
    expect(screen.queryByText('/learning-plans/1')).not.toBeInTheDocument();
  });

  it('replies to a closed conversation and exposes the reopened state', async () => {
    const closed = { ...threadDetail(), status: 'CLOSED' as const, unreadMessageCount: 0 };
    const reopened = { ...closed, status: 'OPEN' as const };
    vi.mocked(getFeedbackThread).mockResolvedValue(response(closed));
    vi.mocked(replyFeedback).mockResolvedValue(response(reopened));

    render(<UserFeedbackDialog onClose={vi.fn()} />);

    await screen.findByText('管理员回复待查看');
    fireEvent.click(screen.getByRole('button', { name: /管理员回复待查看/ }));
    await screen.findByRole('button', { name: '回复并重新打开' });
    fireEvent.change(screen.getByRole('textbox', { name: '回复内容' }), { target: { value: '补充信息' } });
    fireEvent.click(screen.getByRole('button', { name: '回复并重新打开' }));

    await waitFor(() => expect(replyFeedback).toHaveBeenCalledWith(42, { content: '补充信息' }));
    expect(await screen.findByRole('button', { name: '发送回复' })).toBeInTheDocument();
  });

  it('closes on Escape and restores control to the trigger', async () => {
    const onClose = vi.fn();
    const trigger = document.createElement('button');
    document.body.append(trigger);
    trigger.focus();

    render(<UserFeedbackDialog onClose={onClose} />);

    await screen.findByText('管理员回复待查看');
    fireEvent.keyDown(document, { key: 'Escape' });

    expect(onClose).toHaveBeenCalledOnce();
    trigger.remove();
  });
});

function response<T>(data: T): ApiResponse<T> {
  return { success: true, data, timestamp: '2026-07-17T00:00:00Z' };
}

function threadPage(): FeedbackThreadPage {
  return {
    items: [{
      id: 42,
      category: 'BUG',
      status: 'OPEN',
      subject: '管理员回复待查看',
      lastSenderType: 'ADMIN',
      unreadMessageCount: 1,
      createdAt: '2026-07-17T00:00:00Z',
      updatedAt: '2026-07-17T00:01:00Z',
    }],
    total: 1,
    page: 1,
    pageSize: 20,
    unreadMessageCount: 1,
  };
}

function threadDetail(): FeedbackThreadDetail {
  return {
    id: 42,
    category: 'BUG',
    status: 'OPEN',
    subject: '管理员回复待查看',
    sourcePath: '/learning-plans/1',
    createdAt: '2026-07-17T00:00:00Z',
    updatedAt: '2026-07-17T00:01:00Z',
    unreadMessageCount: 1,
    messages: [{
      id: 1,
      senderType: 'ADMIN',
      senderUserId: 7,
      content: '我们已经定位到问题。',
      createdAt: '2026-07-17T00:01:00Z',
    }],
  };
}
