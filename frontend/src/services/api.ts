import type {
  AgentConversationStreamRequest,
  AgentToolPermissionDecisionRequest,
  AgentToolPermissionDecisionResponse,
  AbilityProfileResponse,
  AdminUserDetail,
  AdminUserListQuery,
  AdminUserPage,
  AdminUserStatusUpdateRequest,
  ApiResponse,
  CurrentUser,
  HealthStatus,
  LearningPlanConfirmResponse,
  LearningPlanCreateDraftRequest,
  LearningPlanDetailResponse,
  LearningPlanDraftResponse,
  LearningPlanExtensionApplyResponse,
  LearningPlanListQuery,
  LearningPlanMessageRequest,
  LearningPlanPageResponse,
  LearningPlanRevisionRequest,
  LearningPlanRhythmUpdateRequest,
  LearningPlanTemplateDetailResponse,
  LearningPlanTemplateDraftRequest,
  LearningPlanTemplateSummaryResponse,
  PracticeMessageRequest,
  PracticeMessage,
  PracticeActiveRun,
  PracticeCodeReviewDetail,
  PracticeCodeReviewHistoryResponse,
  PracticeProgressStatus,
  PracticeSessionResponse,
  MistakeNote,
  MistakeSource,
  PasswordLoginRequest,
  PasswordRegisterRequest,
  ProblemDetail,
  ProblemFilters,
  ProblemListItem,
  ProblemListQuery,
  ProblemPage,
  RecallConfirmResult,
  RecallEvaluationResult,
  RecallReviewResult,
  ReviewCard,
  ReviewIntervalPreview,
  ReviewPreference,
  ReviewPreferenceRequest,
  ReviewProblemStatementResponse,
  ReviewQueueResponse,
  ReviewSummaryResponse,
  SseEventName,
  SseStreamEvent,
  UserAiPreference,
  UserAiPreferenceRequest,
} from '../types/api';

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

export async function getHealth(): Promise<ApiResponse<HealthStatus>> {
  const response = await apiFetch('/api/health', {
    headers: jsonHeaders,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Health request failed');
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

export async function listMistakeNotes(
  query: {
    source?: MistakeSource | '';
    mistakeOnly?: boolean;
    keyword?: string;
    limit?: number;
    offset?: number;
  } = {},
  signal?: AbortSignal,
): Promise<ApiResponse<MistakeNote[]>> {
  const response = await apiFetch(`/api/mistake-notes${toQueryString(query)}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Mistake notes request failed');
  }

  return response.json();
}

export async function markMistake(problemSlug: string, signal?: AbortSignal): Promise<ApiResponse<MistakeNote>> {
  const response = await apiFetch('/api/mistake-notes', {
    method: 'POST',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ problemSlug }),
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Mark mistake request failed');
  }

  return response.json();
}

export async function archiveMistake(
  noteId: number,
  archived: boolean,
  signal?: AbortSignal,
): Promise<ApiResponse<MistakeNote>> {
  const response = await apiFetch(`/api/mistake-notes/${noteId}/archive`, {
    method: 'PATCH',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ archived }),
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Archive mistake request failed');
  }

  return response.json();
}

export async function updateMistakeNote(
  noteId: number,
  text: string,
  signal?: AbortSignal,
): Promise<ApiResponse<MistakeNote>> {
  const response = await apiFetch(`/api/mistake-notes/${noteId}/note`, {
    method: 'PATCH',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ text }),
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Update mistake note request failed');
  }

  return response.json();
}

export async function getReviewCard(noteId: number, signal?: AbortSignal): Promise<ApiResponse<ReviewCard>> {
  const response = await apiFetch(`/api/mistake-notes/${noteId}/card`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Review card request failed');
  }

  return response.json();
}

export async function getReviewProblemStatement(
  noteId: number,
  signal?: AbortSignal,
): Promise<ApiResponse<ReviewProblemStatementResponse>> {
  const response = await apiFetch(`/api/mistake-notes/${noteId}/problem-statement`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Review problem statement request failed');
  }

  return response.json();
}

export async function getReviewIntervals(
  noteId: number,
  signal?: AbortSignal,
): Promise<ApiResponse<ReviewIntervalPreview[]>> {
  const response = await apiFetch(`/api/mistake-notes/${noteId}/recall/intervals`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Review interval preview request failed');
  }

  return response.json();
}

export async function submitRecall(
  noteId: number,
  recallText: string,
  transientNote?: string,
  signal?: AbortSignal,
): Promise<ApiResponse<RecallReviewResult>> {
  const response = await apiFetch(`/api/mistake-notes/${noteId}/recall`, {
    method: 'POST',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ recallText, transientNote }),
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Submit recall request failed');
  }

  return response.json();
}

export async function evaluateRecall(
  noteId: number,
  recallText: string,
  transientNote?: string,
  signal?: AbortSignal,
): Promise<ApiResponse<RecallEvaluationResult>> {
  const response = await apiFetch(`/api/mistake-notes/${noteId}/recall/evaluation`, {
    method: 'POST',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ recallText, transientNote }),
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Recall evaluation request failed');
  }

  return response.json();
}

export async function confirmRecall(
  noteId: number,
  evaluationId: number,
  rating: string,
  signal?: AbortSignal,
): Promise<ApiResponse<RecallConfirmResult>> {
  const response = await apiFetch(`/api/mistake-notes/${noteId}/recall/confirm`, {
    method: 'POST',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ evaluationId, rating }),
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Recall confirm request failed');
  }

  return response.json();
}

export async function rateRecall(
  noteId: number,
  rating: string,
  signal?: AbortSignal,
): Promise<ApiResponse<RecallConfirmResult>> {
  const response = await apiFetch(`/api/mistake-notes/${noteId}/recall/rating`, {
    method: 'POST',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ rating }),
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Recall rating request failed');
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
  const response = await apiFetch('/api/review-sessions/summary', {
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

export interface StreamPracticeMessageOptions {
  idempotencyKey: string;
  signal?: AbortSignal;
  onOpen?: () => void;
  onEvent: (event: SseStreamEvent) => void;
}

export async function streamPracticeMessage(
  sessionId: number,
  request: PracticeMessageRequest,
  options: StreamPracticeMessageOptions,
): Promise<void> {
  const response = await apiFetch(`/api/practice-sessions/${sessionId}/messages/stream`, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream, application/json',
      'Content-Type': 'application/json',
      'Idempotency-Key': options.idempotencyKey,
    },
    body: JSON.stringify(request),
    signal: options.signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Practice message stream failed');
  }
  if (!response.body) {
    throw new Error('Practice message stream response does not include a readable body');
  }

  options.onOpen?.();
  await readEventStream(response.body, options.onEvent);
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

interface MistakeNoteListQuery {
  source?: MistakeSource | '';
  mistakeOnly?: boolean;
  keyword?: string;
  limit?: number;
  offset?: number;
}

type QueryParams =
  | ProblemListQuery
  | LearningPlanListQuery
  | PracticeSessionQuery
  | AdminUserListQuery
  | MistakeNoteListQuery;

function toQueryString(query: QueryParams): string {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value === undefined || value === '') {
      return;
    }
    params.set(key, String(value));
  });
  const serialized = params.toString();
  return serialized ? `?${serialized}` : '';
}

export interface StreamAgentConversationOptions {
  idempotencyKey: string;
  signal?: AbortSignal;
  onOpen?: () => void;
  onEvent: (event: SseStreamEvent) => void;
}

export async function streamAgentConversation(
  request: AgentConversationStreamRequest,
  options: StreamAgentConversationOptions,
): Promise<void> {
  const response = await apiFetch('/api/agent/conversations/stream', {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream, application/json',
      'Content-Type': 'application/json',
      'Idempotency-Key': options.idempotencyKey,
    },
    body: JSON.stringify(compactRequest(request)),
    signal: options.signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Conversation stream failed');
  }
  if (!response.body) {
    throw new Error('Conversation stream response does not include a readable body');
  }

  options.onOpen?.();
  await readEventStream(response.body, options.onEvent);
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

  headers.set(requestIdHeaderName, generateRequestId());

  if (csrfToken && method !== 'GET' && method !== 'HEAD' && method !== 'OPTIONS') {
    headers.set(xsrfHeaderName, csrfToken);
  }

  return fetch(input, {
    ...init,
    credentials: init.credentials ?? 'same-origin',
    headers,
  });
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

function compactRequest(request: AgentConversationStreamRequest): AgentConversationStreamRequest {
  return {
    ...(request.taskId === undefined ? {} : { taskId: request.taskId }),
    ...(request.userId === undefined ? {} : { userId: request.userId }),
    message: request.message,
    ...(request.practice === undefined ? {} : { practice: request.practice }),
  };
}

async function readEventStream(
  body: ReadableStream<Uint8Array>,
  onEvent: (event: SseStreamEvent) => void,
): Promise<void> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  try {
    for (;;) {
      const { done, value } = await reader.read();
      buffer += decoder.decode(value, { stream: !done }).replace(/\r\n/g, '\n');
      buffer = drainEventBuffer(buffer, onEvent);

      if (done) {
        if (buffer.trim()) {
          parseEventBlock(buffer, onEvent);
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
): string {
  let nextBuffer = buffer;
  let separatorIndex = nextBuffer.indexOf('\n\n');

  while (separatorIndex >= 0) {
    const block = nextBuffer.slice(0, separatorIndex);
    parseEventBlock(block, onEvent);
    nextBuffer = nextBuffer.slice(separatorIndex + 2);
    separatorIndex = nextBuffer.indexOf('\n\n');
  }

  return nextBuffer;
}

function parseEventBlock(block: string, onEvent: (event: SseStreamEvent) => void) {
  let eventName: SseEventName | undefined;
  const dataLines: string[] = [];

  block.split('\n').forEach((line) => {
    if (line.startsWith('event:')) {
      eventName = line.slice('event:'.length).trim() as SseEventName;
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trimStart());
    }
  });

  if (!eventName) {
    return;
  }

  onEvent({
    eventName,
    data: parseEventData(dataLines.join('\n')),
  });
}

function parseEventData(rawData: string): unknown {
  if (!rawData) {
    return {};
  }

  try {
    return JSON.parse(rawData);
  } catch {
    return rawData;
  }
}
