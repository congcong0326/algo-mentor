import type {
  AgentConversationStreamRequest,
  ApiResponse,
  HealthStatus,
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

export async function getHealth(): Promise<ApiResponse<HealthStatus>> {
  const response = await fetch('/api/health', {
    headers: jsonHeaders,
  });

  if (!response.ok) {
    throw new Error(`Health request failed with status ${response.status}`);
  }

  return response.json();
}

export async function getProblems(
  query: ProblemListQuery = {},
  signal?: AbortSignal,
): Promise<ApiResponse<ProblemPage<ProblemListItem>>> {
  const response = await fetch(`/api/problems${toQueryString(query)}`, {
    headers: jsonHeaders,
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
    signal,
  });

  if (!response.ok) {
    throw new Error(`Problem detail request failed with status ${response.status}`);
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
  const response = await fetch('/api/agent/conversations/stream', {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
      'Idempotency-Key': options.idempotencyKey,
    },
    body: JSON.stringify(compactRequest(request)),
    signal: options.signal,
  });

  if (!response.ok) {
    throw new Error(`Conversation stream failed with status ${response.status}`);
  }
  if (!response.body) {
    throw new Error('Conversation stream response does not include a readable body');
  }

  options.onOpen?.();
  await readEventStream(response.body, options.onEvent);
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
