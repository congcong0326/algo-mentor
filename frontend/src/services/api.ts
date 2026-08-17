import type {
  AgentToolPermissionDecisionRequest,
  AgentToolPermissionDecisionResponse,
  AbilityProfileResponse,
  AdminUserDetail,
  AdminUserListQuery,
  AdminUserPage,
  AdminUserStatusUpdateRequest,
  UserGroupCreateRequest,
  UserGroupDetail,
  UserGroupDeletionResponse,
  UserGroupListQuery,
  UserGroupMemberAddRequest,
  UserGroupMemberBatchResponse,
  UserGroupMemberListQuery,
  UserGroupMemberPage,
  UserGroupMemberRemovalResponse,
  UserGroupPage,
  UserGroupUpdateRequest,
  AdminPasswordResetResponse,
  AdminAiModelPrice,
  AdminAiModelPricePage,
  AdminAiModelPriceWriteRequest,
  AdminAiAuditRunDetail,
  AdminAiAuditRunPage,
  AdminAiAuditRunQuery,
  AdminAiAuditStepDetail,
  AdminAiAuditToolResult,
  AdminAiSettings,
  AdminAiSettingsUpdateRequest,
  AdminAiUsageByModel,
  AdminAiUsageBySource,
  AdminAiUsageByUserPage,
  AdminAiUsageByUserQuery,
  AdminAiUsageQuery,
  AdminAiUsageSummary,
  AdminUserAiPolicy,
  AdminUserAiPolicyUpdateRequest,
  AdminAuthSessionListQuery,
  AdminAuthSessionPage,
  AdminAuthSessionRevocationResponse,
  AdminGenericPolicy,
  AdminGenericPolicyListQuery,
  AdminGenericPolicyOrderRequest,
  AdminGenericPolicyPage,
  AdminGenericPolicyUpdateRequest,
  AdminGenericPolicyWriteRequest,
  BetaAccessListQuery,
  BetaAccessPage,
  BetaAccessSettings,
  BetaAccessSettingsUpdateRequest,
  BetaAllowedEmailBatchResponse,
  BetaAllowedEmailRemovalResponse,
  CompletePasswordResetRequest,
  ApiResponse,
  AuthCapabilities,
  CurrentUser,
  DatabaseRestoreResponse,
  HealthStatus,
  UserInputLimits,
  LearningPlanConfirmResponse,
  LearningPlanActivationResponse,
  LearningPlanCreateDraftRequest,
  LearningPlanDetailResponse,
  LearningPlanDraftResponse,
  LearningPlanAiRevisionCapabilities,
  LearningPlanExtensionApplyResponse,
  LearningPlanListQuery,
  LearningPlanMessageRequest,
  LearningPlanPageResponse,
  LearningPlanRevisionRequest,
  LearningPlanRhythmUpdateRequest,
  LearningPlanTemplateDetailResponse,
  LearningPlanTemplateDraftRequest,
  LearningPlanTemplateSummaryResponse,
  LearnerProfileDocumentResponse,
  LearnerProfileEvidencePage,
  LearnerProfileEvidenceQuery,
  TodayPackResponse,
  PracticeMessageRequest,
  PracticeMessage,
  CoachSummaryProposalAction,
  PracticeActiveRun,
  PracticeChatRunSubscription,
  PracticeCodeReviewDetail,
  PracticeCodeReviewHistoryResponse,
  PracticeProgressStatus,
  PracticeSessionResponse,
  ReviewAttempt,
  ReviewCard,
  ReviewCardOverview,
  ReviewCardContext,
  ReviewCardSource,
  PasswordLoginRequest,
  PasswordRegisterRequest,
  ProblemDetail,
  ProblemFilters,
  ProblemListItem,
  ProblemListQuery,
  ProblemPage,
  ReviewPreference,
  ReviewPreferenceRequest,
  ReviewQueueResponse,
  ReviewSummaryResponse,
  UserProblemNote,
  UserProblemNoteRequest,
  SseEventName,
  SseStreamEvent,
  UserAiPreference,
  UserAiPreferenceRequest,
  UserPasswordUpdateRequest,
  UserPasswordUpdateResponse,
  UserSessionPolicyContent,
  FeedbackCreateRequest,
  FeedbackMessageRequest,
  FeedbackThreadDetail,
  FeedbackThreadPage,
  FeedbackListQuery,
  AdminFeedbackListQuery,
  FeedbackReadResult,
  AdminOverview,
  BetaAccessUserMembership,
  SystemPromptEffective,
  SystemPromptTypeDetail,
  SystemPromptTypeSummary,
} from '../types/api';
import { recordFeedbackRequestContext } from '../feedback/feedbackSourceContext';
import { browserTimezone } from '../utils/time';

const jsonHeaders: HeadersInit = {
  Accept: 'application/json',
};

const requestIdHeaderName = 'X-Request-Id';
const xsrfCookieName = 'XSRF-TOKEN';
const xsrfHeaderName = 'X-XSRF-TOKEN';
const defaultLocale = 'zh-CN';
const supportedLocales = new Set(['zh-CN', 'en-US']);
let apiLocale = defaultLocale;

export class ApiRequestError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly messageKey?: string;
  readonly metadata?: Record<string, unknown>;

  constructor(
    status: number,
    message: string,
    code?: string,
    messageKey?: string,
    metadata?: Record<string, unknown>,
  ) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
    this.code = code;
    this.messageKey = messageKey;
    this.metadata = metadata;
  }
}

export function requireApiData<T>(response: ApiResponse<T>, fallbackMessage: string): T {
  if (response.success && response.data !== undefined) {
    return response.data;
  }
  throw apiResponseToRequestError(response, fallbackMessage);
}

export function requireApiSuccess<T>(response: ApiResponse<T>, fallbackMessage: string): void {
  if (response.success) {
    return;
  }
  throw apiResponseToRequestError(response, fallbackMessage);
}

export function setApiLocale(locale: string): void {
  apiLocale = supportedLocales.has(locale) ? locale : defaultLocale;
}

export async function getHealth(signal?: AbortSignal): Promise<ApiResponse<HealthStatus>> {
  const response = await apiFetch('/api/health', {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Health request failed');
  }

  return response.json();
}

export async function getUserInputLimits(signal?: AbortSignal): Promise<ApiResponse<UserInputLimits>> {
  const response = await apiFetch('/api/user-input-limits', {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'User input limits request failed');
  }

  return response.json();
}

export interface DatabaseBackupDownload {
  blob: Blob;
  filename: string;
}

export async function downloadDatabaseBackup(): Promise<DatabaseBackupDownload> {
  const response = await apiFetch('/api/admin/database/backup');
  if (!response.ok) {
    throw await toApiRequestError(response, 'Database backup download failed');
  }
  return {
    blob: await response.blob(),
    filename: filenameFromContentDisposition(response.headers.get('Content-Disposition'))
      ?? 'algo-mentor-data.ambak',
  };
}

export async function restoreDatabaseBackup(file: File): Promise<ApiResponse<DatabaseRestoreResponse>> {
  const form = new FormData();
  form.append('file', file);
  form.append('confirmation', 'OVERWRITE_ALL_DATA');
  const response = await apiFetch('/api/admin/database/restore', {
    method: 'POST',
    body: form,
  });
  if (!response.ok) {
    throw await toApiRequestError(response, 'Database restore failed');
  }
  return response.json();
}

export async function getCurrentUser(): Promise<CurrentUser | undefined> {
  const response = await apiFetch('/api/auth/me', {
    headers: jsonHeaders,
  });

  if (response.status === 401) {
    return undefined;
  }
  if (!response.ok) {
    throw await toApiRequestError(response, 'Current user request failed');
  }

  const body = await response.json() as ApiResponse<CurrentUser>;
  return body.data;
}

export async function getAuthCapabilities(): Promise<AuthCapabilities> {
  const response = await apiFetch('/api/auth/capabilities', {
    headers: jsonHeaders,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Authentication capabilities request failed');
  }

  const body = await response.json() as ApiResponse<AuthCapabilities>;
  return requireApiData(body, 'Authentication capabilities request failed');
}

export async function loginWithPassword(request: PasswordLoginRequest): Promise<CurrentUser> {
  const response = await apiFetch('/api/auth/login', {
    method: 'POST',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Login request failed');
  }

  const body = await response.json() as ApiResponse<CurrentUser>;
  return requireApiData(body, 'Login request failed');
}

export async function registerWithPassword(request: PasswordRegisterRequest): Promise<CurrentUser> {
  const response = await apiFetch('/api/auth/register', {
    method: 'POST',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Registration request failed');
  }

  const body = await response.json() as ApiResponse<CurrentUser>;
  return requireApiData(body, 'Registration request failed');
}

export async function logout(): Promise<void> {
  const response = await apiFetch('/api/auth/logout', {
    method: 'POST',
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Logout request failed');
  }
}

export async function completePasswordReset(request: CompletePasswordResetRequest): Promise<CurrentUser> {
  const response = await apiFetch('/api/auth/password/complete-reset', {
    method: 'POST',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Password reset completion failed');
  }

  const body = await response.json() as ApiResponse<CurrentUser>;
  return requireApiData(body, 'Password reset completion failed');
}

export async function updateUserPassword(
  request: UserPasswordUpdateRequest,
): Promise<ApiResponse<UserPasswordUpdateResponse>> {
  const response = await apiFetch('/api/auth/password', {
    method: 'PUT',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Password update failed');
  }

  return response.json();
}

export async function listReviewCards(
  query: {
    source?: ReviewCardSource | '';
    mistakeOnly?: boolean;
    keyword?: string;
    limit?: number;
    offset?: number;
  } = {},
  signal?: AbortSignal,
): Promise<ApiResponse<ReviewCardOverview[]>> {
  const response = await apiFetch(`/api/review-cards${toQueryString(query)}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Review cards request failed');
  }

  return response.json();
}

export async function createReviewCard(
  problemSlug: string,
  signal?: AbortSignal,
): Promise<ApiResponse<ReviewCard>> {
  const response = await apiFetch('/api/review-cards', {
    method: 'POST',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ problemSlug }),
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Create review card request failed');
  }

  return response.json();
}

export async function archiveReviewCard(
  cardId: number,
  archived: boolean,
  signal?: AbortSignal,
): Promise<ApiResponse<ReviewCard>> {
  const response = await apiFetch(`/api/review-cards/${cardId}/archive`, {
    method: 'PATCH',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ archived }),
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Archive review card request failed');
  }

  return response.json();
}

export async function getReviewCardContext(
  cardId: number,
  signal?: AbortSignal,
): Promise<ApiResponse<ReviewCardContext>> {
  const query = new URLSearchParams({ timezone: browserTimezone() });
  const response = await apiFetch(`/api/review-cards/${cardId}/context?${query.toString()}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Review card context request failed');
  }

  return response.json();
}

export async function submitReviewAttempt(
  cardId: number,
  clientAttemptId: string,
  rating: string,
  signal?: AbortSignal,
): Promise<ApiResponse<ReviewAttempt>> {
  const query = new URLSearchParams({ timezone: browserTimezone() });
  const response = await apiFetch(`/api/review-cards/${cardId}/attempts?${query.toString()}`, {
    method: 'POST',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ clientAttemptId, rating }),
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Review attempt request failed');
  }

  return response.json();
}

export async function getReviewAttempts(
  cardId: number,
  limit = 20,
  signal?: AbortSignal,
): Promise<ApiResponse<ReviewAttempt[]>> {
  const response = await apiFetch(`/api/review-cards/${cardId}/attempts${toQueryString({ limit })}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Review attempt history request failed');
  }

  return response.json();
}

export async function getProblemNote(
  problemSlug: string,
  signal?: AbortSignal,
): Promise<ApiResponse<UserProblemNote>> {
  const response = await apiFetch(`/api/problems/${encodeURIComponent(problemSlug)}/note`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Problem note request failed');
  }

  return response.json();
}

export async function upsertProblemNote(
  problemSlug: string,
  request: UserProblemNoteRequest,
  signal?: AbortSignal,
): Promise<ApiResponse<UserProblemNote>> {
  const response = await apiFetch(`/api/problems/${encodeURIComponent(problemSlug)}/note`, {
    method: 'PUT',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Problem note update request failed');
  }

  return response.json();
}

export async function getReviewQueue(limit = 20, signal?: AbortSignal): Promise<ApiResponse<ReviewQueueResponse>> {
  const response = await apiFetch(`/api/review-sessions/queue${toQueryString({ limit })}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Review queue request failed');
  }

  return response.json();
}

export async function getReviewPreference(signal?: AbortSignal): Promise<ApiResponse<ReviewPreference>> {
  const response = await apiFetch('/api/me/review-preferences', {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Review preference request failed');
  }

  return response.json();
}

export async function updateReviewPreference(
  request: ReviewPreferenceRequest,
  signal?: AbortSignal,
): Promise<ApiResponse<ReviewPreference>> {
  const response = await apiFetch('/api/me/review-preferences', {
    method: 'PATCH',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Review preference update request failed');
  }

  return response.json();
}

export async function getReviewSummary(signal?: AbortSignal): Promise<ApiResponse<ReviewSummaryResponse>> {
  const query = new URLSearchParams({ timezone: browserTimezone() });
  const response = await apiFetch(`/api/review-sessions/summary?${query.toString()}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Review summary request failed');
  }

  return response.json();
}

export async function getAbilityProfile(signal?: AbortSignal): Promise<ApiResponse<AbilityProfileResponse>> {
  const response = await apiFetch('/api/abilities/profile', {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Ability profile request failed');
  }

  return response.json();
}

export async function getLearnerProfile(signal?: AbortSignal): Promise<ApiResponse<LearnerProfileDocumentResponse>> {
  const response = await apiFetch('/api/me/learner-profile', {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Learner profile request failed');
  }

  return response.json();
}

export async function getLearnerProfileStatementEvidence(
  statementRef: string,
  options: LearnerProfileEvidenceQuery = {},
  signal?: AbortSignal,
): Promise<ApiResponse<LearnerProfileEvidencePage>> {
  const limit = options.limit ?? 20;
  if (!Number.isInteger(limit) || limit < 1 || limit > 20) {
    throw new RangeError('Learner profile evidence limit must be between 1 and 20.');
  }

  const path = `/api/me/learner-profile/statements/${encodeURIComponent(statementRef)}/evidence`
    + toQueryString({ cursor: options.cursor, limit });
  const response = await apiFetch(path, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Learner profile evidence request failed');
  }

  return response.json();
}

export async function getUserAiPreference(signal?: AbortSignal): Promise<ApiResponse<UserAiPreference>> {
  const response = await apiFetch('/api/me/ai-preferences', {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'AI preference request failed');
  }

  return response.json();
}

export async function updateUserAiPreference(
  request: UserAiPreferenceRequest,
  signal?: AbortSignal,
): Promise<ApiResponse<UserAiPreference>> {
  const response = await apiFetch('/api/me/ai-preferences', {
    method: 'PATCH',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'AI preference update request failed');
  }

  return response.json();
}

export async function getAdminUsers(
  query: AdminUserListQuery = {},
  signal?: AbortSignal,
): Promise<ApiResponse<AdminUserPage>> {
  const response = await apiFetch(`/api/admin/users${toQueryString(query)}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Admin users request failed');
  }

  return response.json();
}

export async function getAdminUserDetail(
  userId: number,
  signal?: AbortSignal,
): Promise<ApiResponse<AdminUserDetail>> {
  const response = await apiFetch(`/api/admin/users/${userId}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Admin user detail request failed');
  }

  return response.json();
}

export async function updateAdminUserStatus(
  userId: number,
  request: AdminUserStatusUpdateRequest,
): Promise<ApiResponse<AdminUserDetail>> {
  const response = await apiFetch(`/api/admin/users/${userId}/status`, {
    method: 'PATCH',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Admin user status update request failed');
  }

  return response.json();
}

export async function deleteAdminUser(userId: number): Promise<ApiResponse<AdminUserDetail>> {
  const response = await apiFetch(`/api/admin/users/${userId}`, {
    method: 'DELETE',
    headers: jsonHeaders,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Admin user delete request failed');
  }

  return response.json();
}

export async function getUserGroups(
  query: UserGroupListQuery = {},
  signal?: AbortSignal,
): Promise<ApiResponse<UserGroupPage>> {
  const response = await apiFetch(`/api/admin/user-groups${toQueryString(query)}`, { headers: jsonHeaders, signal });
  if (!response.ok) throw await toApiRequestError(response, 'User groups request failed');
  return response.json();
}

export async function getUserGroup(groupId: number, signal?: AbortSignal): Promise<ApiResponse<UserGroupDetail>> {
  const response = await apiFetch(`/api/admin/user-groups/${groupId}`, { headers: jsonHeaders, signal });
  if (!response.ok) throw await toApiRequestError(response, 'User group detail request failed');
  return response.json();
}

export async function createUserGroup(request: UserGroupCreateRequest): Promise<ApiResponse<UserGroupDetail>> {
  const response = await apiFetch('/api/admin/user-groups', {
    method: 'POST',
    headers: { ...jsonHeaders, 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });
  if (!response.ok) throw await toApiRequestError(response, 'User group creation failed');
  return response.json();
}

export async function updateUserGroup(groupId: number, request: UserGroupUpdateRequest): Promise<ApiResponse<UserGroupDetail>> {
  const response = await apiFetch(`/api/admin/user-groups/${groupId}`, {
    method: 'PATCH',
    headers: { ...jsonHeaders, 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });
  if (!response.ok) throw await toApiRequestError(response, 'User group update failed');
  return response.json();
}

export async function deleteUserGroup(groupId: number): Promise<ApiResponse<UserGroupDeletionResponse>> {
  const response = await apiFetch(`/api/admin/user-groups/${groupId}`, {
    method: 'DELETE',
    headers: jsonHeaders,
  });
  if (!response.ok) throw await toApiRequestError(response, 'User group deletion failed');
  return response.json();
}

export async function getUserGroupMembers(
  groupId: number,
  query: UserGroupMemberListQuery = {},
  signal?: AbortSignal,
): Promise<ApiResponse<UserGroupMemberPage>> {
  const response = await apiFetch(`/api/admin/user-groups/${groupId}/members${toQueryString(query)}`, { headers: jsonHeaders, signal });
  if (!response.ok) throw await toApiRequestError(response, 'User group members request failed');
  return response.json();
}

export async function addUserGroupMembers(
  groupId: number,
  request: UserGroupMemberAddRequest,
): Promise<ApiResponse<UserGroupMemberBatchResponse>> {
  const response = await apiFetch(`/api/admin/user-groups/${groupId}/members`, {
    method: 'POST',
    headers: { ...jsonHeaders, 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });
  if (!response.ok) throw await toApiRequestError(response, 'User group member update failed');
  return response.json();
}

export async function removeUserGroupMember(
  groupId: number,
  userId: number,
): Promise<ApiResponse<UserGroupMemberRemovalResponse>> {
  const response = await apiFetch(`/api/admin/user-groups/${groupId}/members/${userId}`, {
    method: 'DELETE',
    headers: jsonHeaders,
  });
  if (!response.ok) throw await toApiRequestError(response, 'User group member removal failed');
  return response.json();
}

export async function getBetaAccess(
  query: BetaAccessListQuery = {},
  signal?: AbortSignal,
): Promise<ApiResponse<BetaAccessPage>> {
  const response = await apiFetch(`/api/admin/beta-access${toQueryString(query)}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Beta access request failed');
  }

  return response.json();
}

export async function getAdminAuthSessions(
  query: AdminAuthSessionListQuery = {},
  signal?: AbortSignal,
): Promise<ApiResponse<AdminAuthSessionPage>> {
  const response = await apiFetch(`/api/admin/auth-sessions${toQueryString(query)}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Auth session monitoring request failed');
  }

  return response.json();
}

export async function revokeAdminAuthSession(
  sessionRef: string,
  signal?: AbortSignal,
): Promise<ApiResponse<AdminAuthSessionRevocationResponse>> {
  const response = await apiFetch(`/api/admin/auth-sessions/${encodeURIComponent(sessionRef)}`, {
    method: 'DELETE',
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Auth session revocation failed');
  }

  return response.json();
}

export async function getAdminPolicies<TContent = UserSessionPolicyContent>(
  query: AdminGenericPolicyListQuery,
  signal?: AbortSignal,
): Promise<ApiResponse<AdminGenericPolicyPage<TContent>>> {
  const response = await apiFetch(`/api/admin/policies${toQueryString(query)}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Admin policy list request failed');
  }

  return response.json();
}

export async function createAdminPolicy<TContent = UserSessionPolicyContent>(
  request: AdminGenericPolicyWriteRequest<TContent>,
): Promise<ApiResponse<AdminGenericPolicy<TContent>>> {
  const response = await apiFetch('/api/admin/policies', {
    method: 'POST',
    headers: { ...jsonHeaders, 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Admin policy creation failed');
  }

  return response.json();
}

export async function updateAdminPolicy<TContent = UserSessionPolicyContent>(
  policyId: number,
  request: AdminGenericPolicyUpdateRequest<TContent>,
): Promise<ApiResponse<AdminGenericPolicy<TContent>>> {
  const response = await apiFetch(`/api/admin/policies/${policyId}`, {
    method: 'PATCH',
    headers: { ...jsonHeaders, 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Admin policy update failed');
  }

  return response.json();
}

export async function deleteAdminPolicy(
  policyId: number,
  version: number,
): Promise<ApiResponse<boolean>> {
  const response = await apiFetch(`/api/admin/policies/${policyId}?version=${encodeURIComponent(String(version))}`, {
    method: 'DELETE',
    headers: jsonHeaders,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Admin policy deletion failed');
  }

  return response.json();
}

export async function reorderAdminPolicies(
  typeCode: string,
  request: AdminGenericPolicyOrderRequest,
): Promise<ApiResponse<void>> {
  const response = await apiFetch(`/api/admin/policy-types/${encodeURIComponent(typeCode)}/order`, {
    method: 'PUT',
    headers: { ...jsonHeaders, 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Admin policy reorder request failed');
  }

  return response.json();
}

export async function getSystemPromptTypes(): Promise<ApiResponse<{ items: SystemPromptTypeSummary[] }>> {
  const response = await apiFetch('/api/admin/system-prompt-types', { headers: jsonHeaders });
  if (!response.ok) throw await toApiRequestError(response, 'System prompt type list request failed');
  return response.json();
}

export async function getSystemPromptType(typeCode: string): Promise<ApiResponse<SystemPromptTypeDetail>> {
  const response = await apiFetch(`/api/admin/system-prompt-types/${encodeURIComponent(typeCode)}`, { headers: jsonHeaders });
  if (!response.ok) throw await toApiRequestError(response, 'System prompt detail request failed');
  return response.json();
}

export async function getEffectiveSystemPrompt(typeCode: string, userId: number): Promise<ApiResponse<SystemPromptEffective>> {
  const response = await apiFetch(`/api/admin/system-prompt-types/${encodeURIComponent(typeCode)}/effective?userId=${encodeURIComponent(String(userId))}`, { headers: jsonHeaders });
  if (!response.ok) throw await toApiRequestError(response, 'System prompt simulation request failed');
  return response.json();
}

export async function updateBetaAccessSettings(
  request: BetaAccessSettingsUpdateRequest,
): Promise<ApiResponse<BetaAccessSettings>> {
  const response = await apiFetch('/api/admin/beta-access/settings', {
    method: 'PATCH',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Beta access settings update failed');
  }

  return response.json();
}

export async function addBetaAllowedEmails(
  emails: string[],
): Promise<ApiResponse<BetaAllowedEmailBatchResponse>> {
  const response = await apiFetch('/api/admin/beta-access/emails', {
    method: 'POST',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ emails }),
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Beta allowlist update failed');
  }

  return response.json();
}

export async function removeBetaAllowedEmail(
  allowedEmailId: number,
): Promise<ApiResponse<BetaAllowedEmailRemovalResponse>> {
  const response = await apiFetch(`/api/admin/beta-access/emails/${allowedEmailId}`, {
    method: 'DELETE',
    headers: jsonHeaders,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Beta allowlist removal failed');
  }

  return response.json();
}

export async function resetAdminUserPassword(
  userId: number,
): Promise<ApiResponse<AdminPasswordResetResponse>> {
  const response = await apiFetch(`/api/admin/users/${userId}/password-reset`, {
    method: 'POST',
    headers: jsonHeaders,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Admin password reset failed');
  }

  return response.json();
}

export async function getAdminAiSettings(
  signal?: AbortSignal,
): Promise<ApiResponse<AdminAiSettings>> {
  const response = await apiFetch('/api/admin/ai/settings', {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'AI governance settings request failed');
  }

  return response.json();
}

export async function updateAdminAiSettings(
  request: AdminAiSettingsUpdateRequest,
): Promise<ApiResponse<AdminAiSettings>> {
  const response = await apiFetch('/api/admin/ai/settings', {
    method: 'PATCH',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'AI governance settings update failed');
  }

  return response.json();
}

export async function getAdminUserAiPolicy(
  userId: number,
  signal?: AbortSignal,
): Promise<ApiResponse<AdminUserAiPolicy>> {
  const response = await apiFetch(`/api/admin/users/${userId}/ai-policy`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Admin user AI policy request failed');
  }

  return response.json();
}

export async function updateAdminUserAiPolicy(
  userId: number,
  request: AdminUserAiPolicyUpdateRequest,
): Promise<ApiResponse<AdminUserAiPolicy>> {
  const response = await apiFetch(`/api/admin/users/${userId}/ai-policy`, {
    method: 'PATCH',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Admin user AI policy update failed');
  }

  return response.json();
}

export async function getAdminAiModelPrices(
  query: Pick<AdminAiUsageQuery, 'from' | 'to'> = {},
  signal?: AbortSignal,
): Promise<ApiResponse<AdminAiModelPricePage>> {
  const response = await apiFetch(`/api/admin/ai/model-prices${toQueryString(query)}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'AI model prices request failed');
  }

  return response.json();
}

export async function createAdminAiModelPrice(
  request: AdminAiModelPriceWriteRequest,
): Promise<ApiResponse<AdminAiModelPrice>> {
  const response = await apiFetch('/api/admin/ai/model-prices', {
    method: 'POST',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'AI model price create failed');
  }

  return response.json();
}

export async function updateAdminAiModelPrice(
  priceId: number,
  request: AdminAiModelPriceWriteRequest,
): Promise<ApiResponse<AdminAiModelPrice>> {
  const response = await apiFetch(`/api/admin/ai/model-prices/${priceId}`, {
    method: 'PATCH',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'AI model price update failed');
  }

  return response.json();
}

export async function getAdminAiProviderTypes(): Promise<ApiResponse<{ items: import('../types/api').AdminAiProviderType[] }>> {
  const response = await apiFetch('/api/admin/ai/provider-types', { headers: jsonHeaders });
  if (!response.ok) throw await toApiRequestError(response, 'AI provider types request failed');
  return response.json();
}

export async function getAdminAiProviders(): Promise<ApiResponse<{ items: import('../types/api').AdminAiProvider[] }>> {
  const response = await apiFetch('/api/admin/ai/providers', { headers: jsonHeaders });
  if (!response.ok) throw await toApiRequestError(response, 'AI providers request failed');
  return response.json();
}

export async function getAdminAiProvider(id: number): Promise<ApiResponse<import('../types/api').AdminAiProvider>> {
  const response = await apiFetch(`/api/admin/ai/providers/${id}`, { headers: jsonHeaders });
  if (!response.ok) throw await toApiRequestError(response, 'AI provider request failed');
  return response.json();
}

export async function createAdminAiProvider(request: import('../types/api').AdminAiProviderWriteRequest): Promise<ApiResponse<import('../types/api').AdminAiProvider>> {
  const response = await apiFetch('/api/admin/ai/providers', { method: 'POST', headers: { ...jsonHeaders, 'Content-Type': 'application/json' }, body: JSON.stringify(request) });
  if (!response.ok) throw await toApiRequestError(response, 'AI provider create failed');
  return response.json();
}

export async function updateAdminAiProvider(id: number, request: import('../types/api').AdminAiProviderUpdateRequest): Promise<ApiResponse<import('../types/api').AdminAiProvider>> {
  const response = await apiFetch(`/api/admin/ai/providers/${id}`, { method: 'PUT', headers: { ...jsonHeaders, 'Content-Type': 'application/json' }, body: JSON.stringify(request) });
  if (!response.ok) throw await toApiRequestError(response, 'AI provider update failed');
  return response.json();
}

export async function getAdminAiProviderModels(id: number): Promise<ApiResponse<{ items: import('../types/api').AdminAiConfiguredModel[] }>> {
  const response = await apiFetch(`/api/admin/ai/providers/${id}/models`, { headers: jsonHeaders });
  if (!response.ok) throw await toApiRequestError(response, 'AI provider models request failed');
  return response.json();
}

export async function createAdminAiProviderModel(id: number, request: import('../types/api').AdminAiModelWriteRequest): Promise<ApiResponse<import('../types/api').AdminAiConfiguredModel>> {
  const response = await apiFetch(`/api/admin/ai/providers/${id}/models`, { method: 'POST', headers: { ...jsonHeaders, 'Content-Type': 'application/json' }, body: JSON.stringify(request) });
  if (!response.ok) throw await toApiRequestError(response, 'AI model create failed');
  return response.json();
}

export async function updateAdminAiModel(id: number, request: import('../types/api').AdminAiModelWriteRequest): Promise<ApiResponse<import('../types/api').AdminAiConfiguredModel>> {
  const response = await apiFetch(`/api/admin/ai/models/${id}`, { method: 'PUT', headers: { ...jsonHeaders, 'Content-Type': 'application/json' }, body: JSON.stringify(request) });
  if (!response.ok) throw await toApiRequestError(response, 'AI model update failed');
  return response.json();
}

export async function getAdminAiRoutingScenarios(): Promise<ApiResponse<{ items: import('../types/api').AdminAiRoutingScenario[] }>> {
  const response = await apiFetch('/api/admin/ai/model-routing/scenarios', { headers: jsonHeaders });
  if (!response.ok) throw await toApiRequestError(response, 'AI routing scenarios request failed');
  return response.json();
}

export async function getAdminAiEffectiveRoute(scenarioCode: string, userId: number): Promise<ApiResponse<import('../types/api').AdminAiEffectiveRoute>> {
  const response = await apiFetch(`/api/admin/ai/model-routing/scenarios/${encodeURIComponent(scenarioCode)}/effective?userId=${encodeURIComponent(String(userId))}`, { headers: jsonHeaders });
  if (!response.ok) throw await toApiRequestError(response, 'AI route simulation request failed');
  return response.json();
}

export async function getAdminAiUsageSummary(
  query: AdminAiUsageQuery = {},
  signal?: AbortSignal,
): Promise<ApiResponse<AdminAiUsageSummary>> {
  const response = await apiFetch(`/api/admin/ai/usage/summary${toQueryString(query)}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'AI usage summary request failed');
  }

  return response.json();
}

export async function getAdminAiUsageByUser(
  query: AdminAiUsageByUserQuery = {},
  signal?: AbortSignal,
): Promise<ApiResponse<AdminAiUsageByUserPage>> {
  const response = await apiFetch(`/api/admin/ai/usage/by-user${toQueryString(query)}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'AI usage by user request failed');
  }

  return response.json();
}

export async function getAdminAiUsageByModel(
  query: AdminAiUsageQuery = {},
  signal?: AbortSignal,
): Promise<ApiResponse<AdminAiUsageByModel[]>> {
  const response = await apiFetch(`/api/admin/ai/usage/by-model${toQueryString(query)}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'AI usage by model request failed');
  }

  return response.json();
}

export async function getAdminAiUsageBySource(
  query: AdminAiUsageQuery = {},
  signal?: AbortSignal,
): Promise<ApiResponse<AdminAiUsageBySource[]>> {
  const response = await apiFetch(`/api/admin/ai/usage/by-source${toQueryString(query)}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'AI usage by source request failed');
  }

  return response.json();
}

export async function getAdminAiAuditRuns(
  query: AdminAiAuditRunQuery = {},
  signal?: AbortSignal,
): Promise<ApiResponse<AdminAiAuditRunPage>> {
  const response = await apiFetch(`/api/admin/ai/audit/runs${toQueryString(query)}`, {
    headers: jsonHeaders,
    signal,
  });
  if (!response.ok) {
    throw await toApiRequestError(response, 'AI audit run list request failed');
  }
  return response.json();
}

export async function getAdminAiAuditRun(
  runId: number,
  signal?: AbortSignal,
): Promise<ApiResponse<AdminAiAuditRunDetail>> {
  const response = await apiFetch(`/api/admin/ai/audit/runs/${encodeURIComponent(String(runId))}`, {
    headers: jsonHeaders,
    signal,
  });
  if (!response.ok) {
    throw await toApiRequestError(response, 'AI audit run request failed');
  }
  return response.json();
}

export async function getAdminAiAuditStep(
  runId: number,
  stepIndex: number,
  query: { raw?: boolean } = {},
  signal?: AbortSignal,
): Promise<ApiResponse<AdminAiAuditStepDetail>> {
  const response = await apiFetch(
    `/api/admin/ai/audit/runs/${encodeURIComponent(String(runId))}/steps/${encodeURIComponent(String(stepIndex))}${toQueryString(query)}`,
    { headers: jsonHeaders, signal },
  );
  if (!response.ok) {
    throw await toApiRequestError(response, 'AI audit step request failed');
  }
  return response.json();
}

export async function getAdminAiAuditToolResult(
  runId: number,
  toolCallId: string,
  query: { includeContent?: boolean; offset?: number; limit?: number } = {},
  signal?: AbortSignal,
): Promise<ApiResponse<AdminAiAuditToolResult>> {
  const response = await apiFetch(
    `/api/admin/ai/audit/runs/${encodeURIComponent(String(runId))}/tool-results/${encodeURIComponent(toolCallId)}${toQueryString(query)}`,
    { headers: jsonHeaders, signal },
  );
  if (!response.ok) {
    throw await toApiRequestError(response, 'AI audit tool result request failed');
  }
  return response.json();
}

export async function createFeedback(request: FeedbackCreateRequest): Promise<ApiResponse<FeedbackThreadDetail>> {
  return jsonRequest('/api/feedback', 'POST', request, 'Feedback create request failed');
}

export async function getFeedbackThreads(query: FeedbackListQuery = {}, signal?: AbortSignal): Promise<ApiResponse<FeedbackThreadPage>> {
  return getJson(`/api/feedback${toQueryString(query)}`, 'Feedback list request failed', signal);
}

export async function getFeedbackThread(threadId: number, signal?: AbortSignal): Promise<ApiResponse<FeedbackThreadDetail>> {
  return getJson(`/api/feedback/${threadId}`, 'Feedback detail request failed', signal);
}

export async function replyFeedback(threadId: number, request: FeedbackMessageRequest): Promise<ApiResponse<FeedbackThreadDetail>> {
  return jsonRequest(`/api/feedback/${threadId}/messages`, 'POST', request, 'Feedback reply request failed');
}

export async function markFeedbackRead(threadId: number): Promise<ApiResponse<FeedbackReadResult>> {
  return jsonRequest(`/api/feedback/${threadId}/read`, 'POST', undefined, 'Feedback read request failed');
}

export async function getAdminFeedbackThreads(query: AdminFeedbackListQuery = {}, signal?: AbortSignal): Promise<ApiResponse<FeedbackThreadPage>> {
  return getJson(`/api/admin/feedback${toQueryString(query)}`, 'Admin feedback list request failed', signal);
}

export async function getAdminFeedbackThread(threadId: number, signal?: AbortSignal): Promise<ApiResponse<FeedbackThreadDetail>> {
  return getJson(`/api/admin/feedback/${threadId}`, 'Admin feedback detail request failed', signal);
}

export async function replyAdminFeedback(threadId: number, request: FeedbackMessageRequest): Promise<ApiResponse<FeedbackThreadDetail>> {
  return jsonRequest(`/api/admin/feedback/${threadId}/messages`, 'POST', request, 'Admin feedback reply request failed');
}

export async function updateAdminFeedbackStatus(threadId: number, status: 'OPEN' | 'CLOSED'): Promise<ApiResponse<FeedbackThreadDetail>> {
  return jsonRequest(`/api/admin/feedback/${threadId}/status`, 'PATCH', { status }, 'Admin feedback status request failed');
}

export async function markAdminFeedbackRead(threadId: number): Promise<ApiResponse<FeedbackReadResult>> {
  return jsonRequest(`/api/admin/feedback/${threadId}/read`, 'POST', undefined, 'Admin feedback read request failed');
}

export async function getAdminOverview(signal?: AbortSignal): Promise<ApiResponse<AdminOverview>> {
  return getJson('/api/admin/overview', 'Admin overview request failed', signal);
}

export async function getBetaAccessUserMembership(userId: number, signal?: AbortSignal): Promise<ApiResponse<BetaAccessUserMembership>> {
  return getJson(`/api/admin/beta-access/users/${userId}`, 'Beta access membership request failed', signal);
}

export async function getProblems(
  query: ProblemListQuery = {},
  signal?: AbortSignal,
): Promise<ApiResponse<ProblemPage<ProblemListItem>>> {
  const response = await apiFetch(`/api/admin/problems${toQueryString(query)}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Problems request failed');
  }

  return response.json();
}

export async function getProblemFilters(
  locale?: ProblemListQuery['locale'],
  signal?: AbortSignal,
): Promise<ApiResponse<ProblemFilters>> {
  const response = await apiFetch(`/api/admin/problems/filters${toQueryString({ locale })}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Problem filters request failed');
  }

  return response.json();
}

export async function getProblemDetail(
  slug: string,
  locale?: ProblemListQuery['locale'],
  signal?: AbortSignal,
): Promise<ApiResponse<ProblemDetail>> {
  const response = await apiFetch(`/api/admin/problems/${encodeURIComponent(slug)}${toQueryString({ locale })}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Problem detail request failed');
  }

  return response.json();
}

export async function createOrReusePracticeSession(
  planId: number,
  phaseIndex: number,
  problemSlug: string,
  locale?: ProblemListQuery['locale'],
  signal?: AbortSignal,
): Promise<ApiResponse<PracticeSessionResponse>> {
  const response = await apiFetch(
    `/api/learning-plans/${planId}/phases/${phaseIndex}/problems/${encodeURIComponent(problemSlug)}/practice-session${toQueryString({ locale })}`,
    {
      method: 'POST',
      headers: {
        ...jsonHeaders,
        'Content-Type': 'application/json',
      },
      signal,
    },
  );

  if (!response.ok) {
    throw await toApiRequestError(response, 'Practice session request failed');
  }

  return response.json();
}

export async function getPracticeSession(
  sessionId: number,
  signal?: AbortSignal,
): Promise<ApiResponse<PracticeSessionResponse>> {
  const response = await apiFetch(`/api/practice-sessions/${sessionId}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Practice session detail request failed');
  }

  return response.json();
}

export async function getPracticeSessionActiveRun(
  sessionId: number,
  signal?: AbortSignal,
): Promise<ApiResponse<PracticeActiveRun | null>> {
  const response = await apiFetch(`/api/practice-sessions/${sessionId}/active-run`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Practice session active run request failed');
  }

  return response.json();
}

export async function getPracticeSessionMessages(
  sessionId: number,
  limit = 50,
  signal?: AbortSignal,
): Promise<ApiResponse<PracticeMessage[]>> {
  const response = await apiFetch(`/api/practice-sessions/${sessionId}/messages${toQueryString({ limit })}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Practice session messages request failed');
  }

  return response.json();
}

export async function applyPracticeCoachSummaryProposal(
  sessionId: number,
  proposalId: string,
): Promise<ApiResponse<CoachSummaryProposalAction>> {
  const response = await apiFetch(
    `/api/practice-sessions/${sessionId}/coach-summary-proposals/${encodeURIComponent(proposalId)}/apply`,
    {
      method: 'POST',
      headers: jsonHeaders,
    },
  );

  if (!response.ok) {
    throw await toApiRequestError(response, 'Coach summary proposal apply request failed');
  }

  return response.json();
}

export async function getPracticeSessionReviews(
  sessionId: number,
  signal?: AbortSignal,
): Promise<ApiResponse<PracticeCodeReviewHistoryResponse>> {
  const response = await apiFetch(`/api/practice-sessions/${sessionId}/reviews`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Practice code review history request failed');
  }

  return response.json();
}

export async function getPracticeSessionReviewDetail(
  sessionId: number,
  reviewId: number,
  signal?: AbortSignal,
): Promise<ApiResponse<PracticeCodeReviewDetail>> {
  const response = await apiFetch(`/api/practice-sessions/${sessionId}/reviews/${reviewId}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Practice code review detail request failed');
  }

  return response.json();
}

export async function updatePracticeProgressStatus(
  sessionId: number,
  status: Extract<PracticeProgressStatus, 'COMPLETED' | 'SKIPPED'>,
): Promise<ApiResponse<PracticeSessionResponse>> {
  const response = await apiFetch(`/api/practice-sessions/${sessionId}/progress-status`, {
    method: 'PATCH',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ status }),
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Practice progress status request failed');
  }

  return response.json();
}

export interface StartPracticeMessageOptions {
  idempotencyKey: string;
  signal?: AbortSignal;
}

export async function startPracticeMessage(
  sessionId: number,
  request: PracticeMessageRequest,
  options: StartPracticeMessageOptions,
): Promise<PracticeChatRunSubscription> {
  const response = await apiFetch(`/api/practice-sessions/${sessionId}/messages`, {
    method: 'POST',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
      'Idempotency-Key': options.idempotencyKey,
    },
    body: JSON.stringify(request),
    signal: options.signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Practice message start failed');
  }
  return requireApiData(await response.json() as ApiResponse<PracticeChatRunSubscription>, 'Practice message start failed');
}

export interface ReadPracticeRunEventsOptions {
  after?: string;
  signal?: AbortSignal;
  /** v2 Practice Stream 必须把非法 JSON 暴露为错误，而不是降级成普通字符串。 */
  realtimeProtocolVersion?: 1 | 2;
  onEvent: (event: SseStreamEvent) => void;
}

export class SseEventDataParseError extends Error {
  constructor() {
    super('Practice realtime event data is not valid JSON');
    this.name = 'SseEventDataParseError';
  }
}

export async function readPracticeRunEvents(
  eventsUrl: string,
  options: ReadPracticeRunEventsOptions,
): Promise<void> {
  const after = options.after ? `?after=${encodeURIComponent(options.after)}` : '';
  const response = await apiFetch(`${eventsUrl}${after}`, {
    headers: { Accept: 'text/event-stream, application/json' },
    signal: options.signal,
  });
  if (!response.ok) {
    throw await toApiRequestError(response, 'Practice run event subscription failed');
  }
  if (!response.body) {
    throw new Error('Practice run event response does not include a readable body');
  }
  await readEventStream(response.body, options.onEvent, options.realtimeProtocolVersion === 2);
}

export async function getLearningPlans(
  query: LearningPlanListQuery = {},
  signal?: AbortSignal,
): Promise<ApiResponse<LearningPlanPageResponse>> {
  const response = await apiFetch(`/api/learning-plans${toQueryString(query)}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Learning plans request failed');
  }

  return response.json();
}

export async function deleteLearningPlan(planId: number): Promise<ApiResponse<void>> {
  const response = await apiFetch(`/api/learning-plans/${planId}`, {
    method: 'DELETE',
    headers: jsonHeaders,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Learning plan delete request failed');
  }

  return response.json();
}

export async function getLearningPlanDetail(
  planId: number,
  signal?: AbortSignal,
): Promise<ApiResponse<LearningPlanDetailResponse>> {
  const response = await apiFetch(`/api/learning-plans/${planId}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Learning plan detail request failed');
  }

  return response.json();
}

export async function pauseLearningPlanContract(planId: number): Promise<ApiResponse<LearningPlanDetailResponse>> {
  const response = await apiFetch(`/api/learning-plans/${planId}/contract/pause`, {
    method: 'POST',
    headers: jsonHeaders,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Learning plan contract pause request failed');
  }

  return response.json();
}

export async function resumeLearningPlanContract(planId: number): Promise<ApiResponse<LearningPlanDetailResponse>> {
  const response = await apiFetch(`/api/learning-plans/${planId}/contract/resume`, {
    method: 'POST',
    headers: jsonHeaders,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Learning plan contract resume request failed');
  }

  return response.json();
}

export async function closeOutLearningPlanContract(planId: number): Promise<ApiResponse<LearningPlanDetailResponse>> {
  const response = await apiFetch(`/api/learning-plans/${planId}/contract/close-out`, {
    method: 'POST',
    headers: jsonHeaders,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Learning plan contract close-out request failed');
  }

  return response.json();
}

export async function updateLearningPlanRhythm(
  planId: number,
  request: LearningPlanRhythmUpdateRequest,
): Promise<ApiResponse<LearningPlanDetailResponse>> {
  const response = await apiFetch(`/api/learning-plans/${planId}/rhythm`, {
    method: 'PATCH',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Learning plan rhythm update request failed');
  }

  return response.json();
}

export async function getTodayPack(
  timezone?: string,
  packOffset = 0,
  signal?: AbortSignal,
): Promise<ApiResponse<TodayPackResponse>> {
  const response = await apiFetch(`/api/today-pack${toQueryString({ timezone, packOffset })}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Today pack request failed');
  }

  return response.json();
}

export async function activateRecommendedTodayPack(
  timezone?: string,
): Promise<ApiResponse<TodayPackResponse>> {
  const response = await apiFetch('/api/today-pack/recommended-activation', {
    method: 'POST',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ timezone }),
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Recommended today pack activation request failed');
  }

  return response.json();
}

export async function activateLearningPlan(planId: number): Promise<ApiResponse<LearningPlanActivationResponse>> {
  const response = await apiFetch(`/api/learning-plans/${planId}/activation`, {
    method: 'POST',
    headers: jsonHeaders,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Learning plan activation request failed');
  }

  return response.json();
}

export async function restartTodayPack(
  planId: number,
  timezone?: string,
): Promise<ApiResponse<TodayPackResponse>> {
  const response = await apiFetch(`/api/learning-plans/${planId}/activation/restart`, {
    method: 'POST',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ timezone }),
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Today pack restart request failed');
  }

  return response.json();
}

export async function getLearningPlanTemplates(
  signal?: AbortSignal,
): Promise<ApiResponse<LearningPlanTemplateSummaryResponse[]>> {
  const response = await apiFetch('/api/learning-plan-templates', {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Learning plan templates request failed');
  }

  return response.json();
}

export async function getLearningPlanAiRevisionCapabilities(
  signal?: AbortSignal,
): Promise<ApiResponse<LearningPlanAiRevisionCapabilities>> {
  const response = await apiFetch('/api/learning-plans/ai-revision-capabilities', {
    headers: { ...jsonHeaders, 'Cache-Control': 'no-cache' },
    cache: 'no-store',
    signal,
  });
  if (!response.ok) {
    throw await toApiRequestError(response, 'Learning plan AI revision capabilities request failed');
  }
  return response.json();
}

export async function getLearningPlanTemplate(
  templateId: string,
  signal?: AbortSignal,
): Promise<ApiResponse<LearningPlanTemplateDetailResponse>> {
  const response = await apiFetch(`/api/learning-plan-templates/${encodeURIComponent(templateId)}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Learning plan template detail request failed');
  }

  return response.json();
}

export async function createLearningPlanDraftFromTemplate(
  request: LearningPlanTemplateDraftRequest,
  signal?: AbortSignal,
): Promise<ApiResponse<LearningPlanDraftResponse>> {
  const response = await apiFetch('/api/learning-plans/drafts/from-template', {
    method: 'POST',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Learning plan template draft request failed');
  }

  return response.json();
}

export interface StreamLearningPlanDraftOptions {
  signal?: AbortSignal;
  onOpen?: () => void;
  onEvent: (event: SseStreamEvent) => void;
}

export async function streamLearningPlanDraft(
  request: LearningPlanCreateDraftRequest,
  options: StreamLearningPlanDraftOptions,
): Promise<void> {
  const response = await apiFetch('/api/learning-plans/drafts/stream', {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream, application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
    signal: options.signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Learning plan draft stream failed');
  }
  if (!response.body) {
    throw new Error('Learning plan draft stream response does not include a readable body');
  }

  options.onOpen?.();
  await readEventStream(response.body, options.onEvent);
}

export async function streamLearningPlanDraftRevision(
  draftId: number,
  request: LearningPlanRevisionRequest,
  options: StreamLearningPlanDraftOptions,
): Promise<void> {
  const response = await apiFetch(`/api/learning-plans/drafts/${draftId}/revisions/stream`, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream, application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
    signal: options.signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Learning plan draft revision stream failed');
  }
  if (!response.body) {
    throw new Error('Learning plan draft revision stream response does not include a readable body');
  }

  options.onOpen?.();
  await readEventStream(response.body, options.onEvent);
}

export async function sendLearningPlanDraftMessage(
  draftId: number,
  request: LearningPlanMessageRequest,
): Promise<ApiResponse<LearningPlanDraftResponse>> {
  const response = await apiFetch(`/api/learning-plans/drafts/${draftId}/messages`, {
    method: 'POST',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Learning plan draft message request failed');
  }

  return response.json();
}

export async function confirmLearningPlanDraft(
  draftId: number,
): Promise<ApiResponse<LearningPlanConfirmResponse>> {
  const response = await apiFetch(`/api/learning-plans/drafts/${draftId}/confirm`, {
    method: 'POST',
    headers: jsonHeaders,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Learning plan confirm request failed');
  }

  return response.json();
}

export async function streamLearningPlanExtensionProposal(
  planId: number,
  request: LearningPlanRevisionRequest,
  options: StreamLearningPlanDraftOptions,
): Promise<void> {
  const response = await apiFetch(`/api/learning-plans/${planId}/extension-proposals/stream`, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream, application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
    signal: options.signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Learning plan extension proposal stream failed');
  }
  if (!response.body) {
    throw new Error('Learning plan extension proposal stream response does not include a readable body');
  }

  options.onOpen?.();
  await readEventStream(response.body, options.onEvent);
}

export async function streamLearningPlanExtensionProposalRevision(
  planId: number,
  proposalGroupId: number,
  request: LearningPlanRevisionRequest,
  options: StreamLearningPlanDraftOptions,
): Promise<void> {
  const response = await apiFetch(
    `/api/learning-plans/${planId}/extension-proposals/${proposalGroupId}/revisions/stream`,
    {
      method: 'POST',
      headers: {
        Accept: 'text/event-stream, application/json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(request),
      signal: options.signal,
    },
  );

  if (!response.ok) {
    throw await toApiRequestError(response, 'Learning plan extension proposal revision stream failed');
  }
  if (!response.body) {
    throw new Error('Learning plan extension proposal revision stream response does not include a readable body');
  }

  options.onOpen?.();
  await readEventStream(response.body, options.onEvent);
}

export async function applyLearningPlanExtensionProposal(
  planId: number,
  proposalGroupId: number,
  signal?: AbortSignal,
): Promise<ApiResponse<LearningPlanExtensionApplyResponse>> {
  const response = await apiFetch(`/api/learning-plans/${planId}/extension-proposals/${proposalGroupId}/apply`, {
    method: 'POST',
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Learning plan extension proposal apply request failed');
  }

  return response.json();
}

export async function discardLearningPlanExtensionProposal(
  planId: number,
  proposalGroupId: number,
  signal?: AbortSignal,
): Promise<ApiResponse<void>> {
  const response = await apiFetch(`/api/learning-plans/${planId}/extension-proposals/${proposalGroupId}/discard`, {
    method: 'POST',
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Learning plan extension proposal discard request failed');
  }

  return response.json();
}

interface PracticeSessionQuery {
  locale?: ProblemListQuery['locale'];
  limit?: number;
}

interface ReviewCardListQuery {
  source?: ReviewCardSource | '';
  mistakeOnly?: boolean;
  keyword?: string;
  limit?: number;
  offset?: number;
}

interface TodayPackQuery {
  timezone?: string;
  packOffset?: number;
}

interface AdminAiAuditStepQuery {
  raw?: boolean;
}

type QueryParams =
  | ProblemListQuery
  | LearningPlanListQuery
  | PracticeSessionQuery
  | AdminUserListQuery
  | AdminGenericPolicyListQuery
  | UserGroupListQuery
  | UserGroupMemberListQuery
  | AdminAiUsageQuery
  | AdminAiUsageByUserQuery
  | AdminAiAuditRunQuery
  | AdminAiAuditStepQuery
  | LearnerProfileEvidenceQuery
  | ReviewCardListQuery
  | TodayPackQuery
  | FeedbackListQuery
  | AdminFeedbackListQuery;

function toQueryString(query: QueryParams): string {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') {
      return;
    }
    params.set(key, String(value));
  });
  const serialized = params.toString();
  return serialized ? `?${serialized}` : '';
}

export async function decideAgentToolPermission(
  permissionRequestId: string,
  request: AgentToolPermissionDecisionRequest,
  signal?: AbortSignal,
): Promise<ApiResponse<AgentToolPermissionDecisionResponse>> {
  const response = await apiFetch(
    `/api/agent/tool-permissions/${encodeURIComponent(permissionRequestId)}/decision`,
    {
      method: 'POST',
      headers: {
        ...jsonHeaders,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(request),
      signal,
    },
  );

  if (!response.ok) {
    throw await toApiRequestError(response, 'Agent tool permission decision request failed');
  }

  return response.json();
}

function apiFetch(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
  const method = (init.method ?? 'GET').toUpperCase();
  const headers = apiHeaders(init.headers);
  const csrfToken = readCookie(xsrfCookieName);

  const requestId = generateRequestId();
  headers.set(requestIdHeaderName, requestId);

  if (csrfToken && method !== 'GET' && method !== 'HEAD' && method !== 'OPTIONS') {
    headers.set(xsrfHeaderName, csrfToken);
  }

  return fetch(input, {
    ...init,
    credentials: init.credentials ?? 'same-origin',
    headers,
  }).then((response) => {
    recordFeedbackRequestContext(requestPath(input), response.headers.get(requestIdHeaderName) ?? requestId);
    return response;
  });
}

async function getJson<T>(path: string, fallback: string, signal?: AbortSignal): Promise<ApiResponse<T>> {
  const response = await apiFetch(path, { headers: jsonHeaders, signal });
  if (!response.ok) throw await toApiRequestError(response, fallback);
  return response.json();
}

async function jsonRequest<T>(path: string, method: 'POST' | 'PATCH', body: unknown, fallback: string): Promise<ApiResponse<T>> {
  const response = await apiFetch(path, { method, headers: { ...jsonHeaders, 'Content-Type': 'application/json' }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
  if (!response.ok) throw await toApiRequestError(response, fallback);
  return response.json();
}

function requestPath(input: RequestInfo | URL): string {
  if (typeof input === 'string') return new URL(input, window.location.origin).pathname;
  if (input instanceof URL) return input.pathname;
  return new URL(input.url, window.location.origin).pathname;
}

function generateRequestId(): string {
  if (globalThis.crypto?.getRandomValues) {
    const bytes = new Uint8Array(6);
    globalThis.crypto.getRandomValues(bytes);
    return [...bytes].map((byte) => byte.toString(16).padStart(2, '0')).join('');
  }

  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.slice(-12);
}

function apiHeaders(headersInit?: HeadersInit): Headers {
  const headers = new Headers(headersInit);
  if (!headers.has('Accept-Language')) {
    headers.set('Accept-Language', currentApiLocale());
  }
  return headers;
}

function currentApiLocale(): string {
  return apiLocale;
}

function readCookie(name: string): string | undefined {
  const prefix = `${encodeURIComponent(name)}=`;
  return document.cookie
    .split(';')
    .map((part) => part.trim())
    .find((part) => part.startsWith(prefix))
    ?.slice(prefix.length);
}

function filenameFromContentDisposition(value: string | null): string | undefined {
  const match = /filename="?([^";]+)"?/i.exec(value ?? '');
  return match?.[1];
}

async function toApiRequestError(response: Response, fallbackMessage: string): Promise<ApiRequestError> {
  try {
    const body = await response.json() as ApiResponse<unknown>;
    return apiResponseToRequestError(body, `${fallbackMessage} with status ${response.status}`, response.status);
  } catch {
    return new ApiRequestError(response.status, `${fallbackMessage} with status ${response.status}`);
  }
}

function apiResponseToRequestError<T>(
  response: ApiResponse<T>,
  fallbackMessage: string,
  status = 0,
): ApiRequestError {
  return new ApiRequestError(
    status,
    response.error?.message ?? fallbackMessage,
    response.error?.code,
    response.error?.messageKey,
    response.error?.metadata,
  );
}

async function readEventStream(
  body: ReadableStream<Uint8Array>,
  onEvent: (event: SseStreamEvent) => void,
  strictJson = false,
): Promise<void> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  try {
    for (;;) {
      const { done, value } = await reader.read();
      buffer += decoder.decode(value, { stream: !done }).replace(/\r\n/g, '\n');
      buffer = drainEventBuffer(buffer, onEvent, strictJson);

      if (done) {
        if (buffer.trim()) {
          parseEventBlock(buffer, onEvent, strictJson);
        }
        return;
      }
    }
  } finally {
    reader.releaseLock();
  }
}

function drainEventBuffer(
  buffer: string,
  onEvent: (event: SseStreamEvent) => void,
  strictJson: boolean,
): string {
  let nextBuffer = buffer;
  let separatorIndex = nextBuffer.indexOf('\n\n');

  while (separatorIndex >= 0) {
    const block = nextBuffer.slice(0, separatorIndex);
    parseEventBlock(block, onEvent, strictJson);
    nextBuffer = nextBuffer.slice(separatorIndex + 2);
    separatorIndex = nextBuffer.indexOf('\n\n');
  }

  return nextBuffer;
}

function parseEventBlock(
  block: string,
  onEvent: (event: SseStreamEvent) => void,
  strictJson: boolean,
) {
  let eventName: SseEventName | undefined;
  let id: string | undefined;
  const dataLines: string[] = [];

  block.split('\n').forEach((line) => {
    if (line.startsWith('event:')) {
      eventName = line.slice('event:'.length).trim() as SseEventName;
    }
    if (line.startsWith('id:')) {
      id = line.slice('id:'.length).trim();
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trimStart());
    }
  });

  if (!eventName) {
    return;
  }

  onEvent({
    id,
    eventName,
    data: parseEventData(dataLines.join('\n'), strictJson),
  });
}

function parseEventData(rawData: string, strictJson: boolean): unknown {
  if (!rawData) {
    return {};
  }

  try {
    return JSON.parse(rawData);
  } catch {
    if (strictJson) {
      throw new SseEventDataParseError();
    }
    return rawData;
  }
}
