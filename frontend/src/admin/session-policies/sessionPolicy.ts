import type { AdminGenericPolicy, PolicySubjectRange, UserSessionPolicyContent } from '../../types/api';

export const AUTH_USER_SESSION_POLICY_TYPE = 'auth.user-session.v1';
export const DEFAULT_MAX_SESSIONS = 2;
export const DEFAULT_ABSOLUTE_TIMEOUT_SECONDS = 86_400;
export const MAX_SESSION_POLICY_VALUE = 2_147_483_647;

export function defaultUserSessionPolicyContent(): UserSessionPolicyContent {
  return {
    maxSessions: DEFAULT_MAX_SESSIONS,
    absoluteTimeoutSeconds: DEFAULT_ABSOLUTE_TIMEOUT_SECONDS,
  };
}

export function userSessionPolicyContent(policy: AdminGenericPolicy): UserSessionPolicyContent {
  return policy.content;
}

export function policySubjectRange(policy: AdminGenericPolicy): PolicySubjectRange {
  return policy.subjectRange;
}

export type DurationUnit = 'days' | 'hours' | 'minutes' | 'seconds';

export function durationParts(seconds: number): { value: number; unit: DurationUnit } {
  if (!Number.isFinite(seconds) || seconds < 0) {
    return { value: seconds, unit: 'seconds' };
  }
  if (seconds > 0 && seconds % 86_400 === 0) {
    return { value: seconds / 86_400, unit: 'days' };
  }
  if (seconds > 0 && seconds % 3_600 === 0) {
    return { value: seconds / 3_600, unit: 'hours' };
  }
  if (seconds > 0 && seconds % 60 === 0) {
    return { value: seconds / 60, unit: 'minutes' };
  }
  return { value: seconds, unit: 'seconds' };
}

export function isPositivePolicyInteger(value: string): boolean {
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed >= 1 && parsed <= MAX_SESSION_POLICY_VALUE;
}
