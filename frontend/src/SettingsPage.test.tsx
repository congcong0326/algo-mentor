import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from './i18n/I18nProvider';
import SettingsPage from './SettingsPage';
import {
  getReviewPreference,
  getUserAiPreference,
  updateUserPassword,
} from './services/api';
import type {
  ApiResponse,
  CurrentUser,
  ReviewPreference,
  UserAiPreference,
} from './types/api';

vi.mock('./services/api', () => ({
  getReviewPreference: vi.fn(),
  getUserAiPreference: vi.fn(),
  requireApiData: <T,>(response: ApiResponse<T>, fallbackMessage: string): T => {
    if (response.success && response.data !== undefined) {
      return response.data;
    }
    throw new Error(fallbackMessage);
  },
  setApiLocale: vi.fn(),
  updateReviewPreference: vi.fn(),
  updateUserAiPreference: vi.fn(),
  updateUserPassword: vi.fn(),
}));

beforeEach(() => {
  vi.mocked(getUserAiPreference).mockResolvedValue(apiResponse(userAiPreference()));
  vi.mocked(getReviewPreference).mockResolvedValue(apiResponse(reviewPreference()));
  vi.mocked(updateUserPassword).mockResolvedValue(apiResponse({
    passwordConfigured: true,
    operation: 'CREATED',
    revokedSessionCount: 0,
  }));
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('SettingsPage', () => {
  it('associates every review setting help icon with its accessible tooltip', async () => {
    renderPage();

    expect(await screen.findByRole('heading', { name: '复习策略' })).toBeInTheDocument();
    expect(getUserAiPreference).toHaveBeenCalledTimes(1);
    expect(getReviewPreference).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByText('高级复习设置'));

    const helpItems = [
      {
        description: '数值越高，复习安排越频繁、遗忘风险越低。',
        label: '目标记忆率说明',
        tooltipId: 'review-desired-retention-tooltip',
      },
      {
        description: '当天首次进入队列的卡片数量，0 表示不安排新卡。',
        label: '每日新卡说明',
        tooltipId: 'review-daily-new-limit-tooltip',
      },
      {
        description: '当天处于学习或重新学习状态的到期卡数量。',
        label: '学习中上限说明',
        tooltipId: 'review-daily-learning-limit-tooltip',
      },
      {
        description: '当天处于复习状态的到期卡数量。',
        label: '复习卡上限说明',
        tooltipId: 'review-daily-review-limit-tooltip',
      },
      {
        description: '限制 FSRS 排出的最长天数，避免单次间隔无限增长。',
        label: '最长复习间隔说明',
        tooltipId: 'review-maximum-interval-tooltip',
      },
    ];

    expect(screen.getAllByRole('img', { name: /说明$/ })).toHaveLength(5);
    helpItems.forEach(({ description, label, tooltipId }) => {
      expect(screen.getByRole('img', { name: label })).toHaveAttribute('aria-describedby', tooltipId);
      expect(screen.getByRole('tooltip', { name: description })).toHaveAttribute('id', tooltipId);
    });
    expect(screen.getByRole('checkbox', { name: /启用间隔扰动/ })).toBeChecked();
    expect(screen.queryByText(/AI 评价建议/)).not.toBeInTheDocument();
  });

  it('hides password management and prevents its API call when password login is disabled', async () => {
    renderPage(user, vi.fn(), false);

    await screen.findByRole('heading', { name: '账户' });
    expect(screen.queryByText('登录密码')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '设置密码' })).not.toBeInTheDocument();
    expect(updateUserPassword).not.toHaveBeenCalled();
  });

  it('shows the set-password flow without a current-password field for an OIDC session', async () => {
    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: '设置密码' }));

    expect(screen.getByRole('dialog', { name: '设置登录密码' })).toBeInTheDocument();
    expect(screen.queryByLabelText('当前密码')).not.toBeInTheDocument();
    expect(screen.getByLabelText('新密码')).toHaveAttribute('autocomplete', 'new-password');
    expect(screen.getByLabelText('确认新密码')).toHaveAttribute('autocomplete', 'new-password');
  });

  it('shows the current-password field when the current session was created by password sign-in', async () => {
    renderPage({ passwordConfigured: true, sessionAuthenticationMethod: 'PASSWORD' });

    fireEvent.click(await screen.findByRole('button', { name: '修改密码' }));

    expect(screen.getByRole('dialog', { name: '修改登录密码' })).toBeInTheDocument();
    expect(screen.getByLabelText('当前密码')).toHaveAttribute('autocomplete', 'current-password');
  });

  it('does not send an update when confirmation does not match', async () => {
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: '设置密码' }));
    fireEvent.change(screen.getByLabelText('新密码'), { target: { value: 'new-password' } });
    fireEvent.change(screen.getByLabelText('确认新密码'), { target: { value: 'different-password' } });
    fireEvent.click(screen.getByRole('button', { name: '更新密码' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('两次输入的新密码不一致');
    expect(updateUserPassword).not.toHaveBeenCalled();
  });

  it('updates local password state and closes the dialog after a successful update', async () => {
    const onCurrentUserUpdated = vi.fn();
    renderPage(undefined, onCurrentUserUpdated);
    fireEvent.click(await screen.findByRole('button', { name: '设置密码' }));
    fireEvent.change(screen.getByLabelText('新密码'), { target: { value: 'new-password' } });
    fireEvent.change(screen.getByLabelText('确认新密码'), { target: { value: 'new-password' } });
    fireEvent.click(screen.getByRole('button', { name: '更新密码' }));

    await waitFor(() => expect(updateUserPassword).toHaveBeenCalledWith({
      newPassword: 'new-password',
      confirmPassword: 'new-password',
    }));
    expect(onCurrentUserUpdated).toHaveBeenCalledWith(expect.objectContaining({ passwordConfigured: true }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByText('已设置登录密码，以后可使用当前邮箱和此密码登录。')).toBeInTheDocument();
  });

  it('shows API failures inside the dialog', async () => {
    vi.mocked(updateUserPassword).mockRejectedValueOnce(new Error('当前密码不正确。'));
    renderPage({ passwordConfigured: true, sessionAuthenticationMethod: 'PASSWORD' });
    fireEvent.click(await screen.findByRole('button', { name: '修改密码' }));
    fireEvent.change(screen.getByLabelText('当前密码'), { target: { value: 'old-password' } });
    fireEvent.change(screen.getByLabelText('新密码'), { target: { value: 'new-password' } });
    fireEvent.change(screen.getByLabelText('确认新密码'), { target: { value: 'new-password' } });
    fireEvent.click(screen.getByRole('button', { name: '更新密码' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('当前密码不正确。');
    expect(updateUserPassword).toHaveBeenCalledWith({
      currentPassword: 'old-password',
      newPassword: 'new-password',
      confirmPassword: 'new-password',
    });
  });
});

function renderPage(
  currentUser: Partial<CurrentUser> = user,
  onCurrentUserUpdated = vi.fn(),
  passwordLoginEnabled = true,
) {
  render(
    <I18nProvider>
      <SettingsPage
        currentUser={{ ...user, ...currentUser }}
        onCurrentUserUpdated={onCurrentUserUpdated}
        onLogout={vi.fn()}
        passwordLoginEnabled={passwordLoginEnabled}
      />
    </I18nProvider>,
  );
}

function apiResponse<T>(data: T): ApiResponse<T> {
  return {
    success: true,
    data,
    timestamp: '2026-07-13T00:00:00Z',
  };
}

function userAiPreference(): UserAiPreference {
  return {
    coachStyle: 'GUIDED',
    coachStyleLabel: '引导型教练',
  };
}

const user: CurrentUser = {
  id: 42,
  email: 'user@example.com',
  displayName: 'User Name',
  avatarUrl: undefined,
  roles: ['USER'],
  permissions: [],
  status: 'ACTIVE',
  passwordChangeRequired: false,
  passwordConfigured: false,
  sessionAuthenticationMethod: 'OIDC',
};

function reviewPreference(): ReviewPreference {
  return {
    dailyLearningLimit: 50,
    dailyNewLimit: 10,
    dailyReviewLimit: 30,
    desiredRetention: 0.9,
    enableFuzzing: true,
    maximumIntervalDays: 36500,
  };
}
