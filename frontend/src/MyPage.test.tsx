import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from './i18n/I18nProvider';
import MyPage from './MyPage';
import { learnerProfileDocument } from './learner-profile/testFixtures';
import {
  getAbilityProfile,
  getLearnerProfile,
  getLearnerProfileStatementEvidence,
} from './services/api';
import type { AbilityProfileResponse, ApiResponse } from './types/api';

vi.mock('./services/api', () => ({
  getAbilityProfile: vi.fn(),
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

beforeEach(() => {
  vi.mocked(getAbilityProfile).mockResolvedValue(apiResponse(abilityProfile()));
  vi.mocked(getLearnerProfile).mockResolvedValue(apiResponse(learnerProfileDocument()));
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('MyPage learning memory', () => {
  it('composes a continuous profile document without legacy category controls', async () => {
    render(
      <I18nProvider>
        <MyPage />
      </I18nProvider>,
    );

    expect(await screen.findByRole('heading', { name: '学习画像' })).toBeInTheDocument();
    expect(getAbilityProfile).toHaveBeenCalledTimes(1);
    expect(getLearnerProfile).toHaveBeenCalledTimes(1);
    expect(screen.getByText('准备 Java 后端面试。')).toBeInTheDocument();
    expect(screen.getByText('编码前会先拆解状态。')).toBeInTheDocument();
    expect(screen.getByText('[1]')).toBeInTheDocument();
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
    expect(screen.queryByText('第 1 版')).not.toBeInTheDocument();
    expect(getLearnerProfileStatementEvidence).not.toHaveBeenCalled();
  });

  it('restores a valid profile sentence anchor after the document renders and clears the route state', async () => {
    const scrollIntoView = vi.fn();
    Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
      configurable: true,
      value: scrollIntoView,
    });
    const onProfileAnchorHandled = vi.fn();

    render(
      <I18nProvider>
        <MyPage
          onProfileAnchorHandled={onProfileAnchorHandled}
          profileAnchor="learner-profile-statement-101"
        />
      </I18nProvider>,
    );

    const statement = await screen.findByRole('group', { name: /第 1 条判断/ });
    await waitFor(() => expect(onProfileAnchorHandled).toHaveBeenCalledTimes(1));
    expect(scrollIntoView).toHaveBeenCalledWith({ block: 'center' });
    expect(statement).toHaveFocus();
  });

  it('clears a missing valid anchor without throwing', async () => {
    const scrollTo = vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined);
    const onProfileAnchorHandled = vi.fn();

    render(
      <I18nProvider>
        <MyPage
          onProfileAnchorHandled={onProfileAnchorHandled}
          profileAnchor="learner-profile-statement-999"
        />
      </I18nProvider>,
    );

    await waitFor(() => expect(onProfileAnchorHandled).toHaveBeenCalledTimes(1));
    expect(scrollTo).toHaveBeenCalledWith({ top: 0, behavior: 'auto' });
  });
});

function apiResponse<T>(data: T): ApiResponse<T> {
  return { success: true, data, timestamp: '2026-07-20T12:00:00Z' };
}

function abilityProfile(): AbilityProfileResponse {
  return {
    tags: [],
    scope: {
      minProblemCount: 20,
      scorePrecision: 1,
      latestReviewOnly: true,
      conservativeWeight: 4,
    },
  };
}
