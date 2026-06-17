export interface ApiError {
  code: string;
  message: string;
}

export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: ApiError;
  timestamp: string;
}

export interface HealthStatus {
  status: 'UP' | 'DOWN';
}

export type SseEventName =
  | 'agent_run_start'
  | 'agent_step_start'
  | 'agent_tool_start'
  | 'agent_tool_end'
  | 'agent_step_end'
  | 'agent_run_end'
  | 'message_start'
  | 'content_delta'
  | 'tool_call_start'
  | 'tool_call_delta'
  | 'tool_call_end'
  | 'usage'
  | 'message_end'
  | 'heartbeat'
  | 'error'
  | 'agent_error';

export interface AgentConversationStreamRequest {
  taskId?: number;
  userId?: number;
  message: string;
}

export interface SseStreamEvent {
  eventName: SseEventName;
  data: unknown;
}

export interface AgentStreamStartData {
  runId?: string;
  topic?: string;
  maxSteps?: number;
}

export interface MessageStartData {
  provider?: string;
  model?: string;
}

export interface ContentDeltaData {
  content?: string;
}

export interface ToolCallDeltaData {
  id?: string;
  argumentsDelta?: string;
}

export interface UsageData {
  usage?: {
    inputTokens?: number;
    outputTokens?: number;
    reasoningTokens?: number;
    cachedInputTokens?: number;
    totalTokens?: number;
  };
}

export interface MessageEndData {
  finishReason?: string;
}
