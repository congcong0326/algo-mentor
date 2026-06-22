import type {
  AgentConversationStreamRequest,
  ApiResponse,
  CurrentUser,
  HealthStatus,
  LearningPlanConfirmResponse,
  LearningPlanCreateDraftRequest,
  LearningPlanDetailResponse,
  LearningPlanDraftResponse,
  LearningPlanMessageRequest,
  LearningPlanSummaryResponse,
  ProblemDetail,
  ProblemListItem,
  ProblemListQuery,
  ProblemPage,
  SseEventName,
  SseStreamEvent,
} from '../types/api';

const jsonHeaders = {
  Accept: 'application/json',
};

const xsrfCookieName = 'XSRF-TOKEN';
const xsrfHeaderName = 'X-XSRF-TOKEN';

export class ApiRequestError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly metadata?: Record<string, unknown>;

  constructor(status: number, message: string, code?: string, metadata?: Record<string, unknown>) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
    this.code = code;
    this.metadata = metadata;
  }
}

export async function getHealth(): Promise<ApiResponse<HealthStatus>> {
  const response = await fetch('/api/health', {
    headers: jsonHeaders,
    credentials: 'same-origin',
  });

  if (!response.ok) {
    throw new Error(`Health request failed with status ${response.status}`);
  }

  return response.json();
}

export async function getCurrentUser(): Promise<CurrentUser | undefined> {
  const response = await fetch('/api/auth/me', {
    headers: jsonHeaders,
    credentials: 'same-origin',
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

export async function logout(): Promise<void> {
  const response = await apiFetch('/api/auth/logout', {
    method: 'POST',
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Logout request failed');
  }
}

export async function getProblems(
  query: ProblemListQuery = {},
  signal?: AbortSignal,
): Promise<ApiResponse<ProblemPage<ProblemListItem>>> {
  const response = await fetch(`/api/problems${toQueryString(query)}`, {
    headers: jsonHeaders,
    credentials: 'same-origin',
    signal,
  });

  if (!response.ok) {
    throw new Error(`Problems request failed with status ${response.status}`);
  }

  return response.json();
}

export async function getProblemDetail(
  slug: string,
  signal?: AbortSignal,
): Promise<ApiResponse<ProblemDetail>> {
  const response = await fetch(`/api/problems/${encodeURIComponent(slug)}`, {
    headers: jsonHeaders,
    credentials: 'same-origin',
    signal,
  });

  if (!response.ok) {
    throw new Error(`Problem detail request failed with status ${response.status}`);
  }

  return response.json();
}

export async function getLearningPlans(
  signal?: AbortSignal,
): Promise<ApiResponse<LearningPlanSummaryResponse[]>> {
  const response = await fetch('/api/learning-plans', {
    headers: jsonHeaders,
    signal,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Learning plans request failed');
  }

  return response.json();
}

export async function getLearningPlanDetail(
  planId: number,
  signal?: AbortSignal,
): Promise<ApiResponse<LearningPlanDetailResponse>> {
  const response = await fetch(`/api/learning-plans/${planId}`, {
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
  const response = await fetch('/api/learning-plans/drafts', {
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

export async function sendLearningPlanDraftMessage(
  draftId: number,
  request: LearningPlanMessageRequest,
): Promise<ApiResponse<LearningPlanDraftResponse>> {
  const response = await fetch(`/api/learning-plans/drafts/${draftId}/messages`, {
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
  const response = await fetch(`/api/learning-plans/drafts/${draftId}/confirm`, {
    method: 'POST',
    headers: jsonHeaders,
  });

  if (!response.ok) {
    throw await toApiRequestError(response, 'Learning plan confirm request failed');
  }

  return response.json();
}

function toQueryString(query: ProblemListQuery): string {
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

function apiFetch(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
  const method = (init.method ?? 'GET').toUpperCase();
  const headers = new Headers(init.headers);
  const csrfToken = readCookie(xsrfCookieName);

  if (csrfToken && method !== 'GET' && method !== 'HEAD' && method !== 'OPTIONS') {
    headers.set(xsrfHeaderName, csrfToken);
  }

  return fetch(input, {
    ...init,
    credentials: init.credentials ?? 'same-origin',
    headers,
  });
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
    return new ApiRequestError(
      response.status,
      body.error?.message ?? `${fallbackMessage} with status ${response.status}`,
      body.error?.code,
      body.error?.metadata,
    );
  } catch {
    return new ApiRequestError(response.status, `${fallbackMessage} with status ${response.status}`);
  }
}

function compactRequest(request: AgentConversationStreamRequest): AgentConversationStreamRequest {
  return {
    ...(request.taskId === undefined ? {} : { taskId: request.taskId }),
    ...(request.userId === undefined ? {} : { userId: request.userId }),
    message: request.message,
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
