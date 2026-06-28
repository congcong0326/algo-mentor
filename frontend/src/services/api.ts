import type {
  AgentConversationStreamRequest,
  AgentToolPermissionDecisionRequest,
  AgentToolPermissionDecisionResponse,
  AbilityProfileResponse,
  ApiResponse,
  CurrentUser,
  HealthStatus,
  LearningPlanConfirmResponse,
  LearningPlanCreateDraftRequest,
  LearningPlanDetailResponse,
  LearningPlanDraftResponse,
  LearningPlanListQuery,
  LearningPlanMessageRequest,
  LearningPlanPageResponse,
  PracticeMessageRequest,
  PracticeMessage,
  PracticeActiveRun,
  PracticeCodeReviewDetail,
  PracticeCodeReviewHistoryResponse,
  PracticeProgressStatus,
  PracticeSessionResponse,
  PasswordLoginRequest,
  PasswordRegisterRequest,
  ProblemDetail,
  ProblemListItem,
  ProblemListQuery,
  ProblemPage,
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
const localeStorageKey = 'algo-mentor-locale';
const defaultLocale = 'zh-CN';
const supportedLocales = new Set(['zh-CN', 'en-US']);

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

export async function getProblems(
  query: ProblemListQuery = {},
  signal?: AbortSignal,
): Promise<ApiResponse<ProblemPage<ProblemListItem>>> {
  const response = await apiFetch(`/api/problems${toQueryString(query)}`, {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Problems request failed');
  }

  return response.json();
}

export async function getProblemDetail(
  slug: string,
  locale?: ProblemListQuery['locale'],
  signal?: AbortSignal,
): Promise<ApiResponse<ProblemDetail>> {
  const response = await apiFetch(`/api/problems/${encodeURIComponent(slug)}${toQueryString({ locale })}`, {
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
  status: Extract<PracticeProgressStatus, 'COMPLETED'>,
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

export async function createLearningPlanDraft(
  request: LearningPlanCreateDraftRequest,
): Promise<ApiResponse<LearningPlanDraftResponse>> {
  const response = await apiFetch('/api/learning-plans/drafts', {
    method: 'POST',
    headers: {
      ...jsonHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Learning plan draft request failed');
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

interface PracticeSessionQuery {
  locale?: ProblemListQuery['locale'];
  limit?: number;
}

type QueryParams = ProblemListQuery | LearningPlanListQuery | PracticeSessionQuery;

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
  if (typeof window === 'undefined' || typeof window.localStorage?.getItem !== 'function') {
    return defaultLocale;
  }
  try {
    const storedLocale = window.localStorage.getItem(localeStorageKey);
    return storedLocale && supportedLocales.has(storedLocale) ? storedLocale : defaultLocale;
  } catch {
    return defaultLocale;
  }
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
