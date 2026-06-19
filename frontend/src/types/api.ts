export interface ApiError {
  code: string;
  message: string;
  metadata?: Record<string, unknown>;
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

export type ProblemDifficulty = 'EASY' | 'MEDIUM' | 'HARD';

export interface ProblemListQuery {
  keyword?: string;
  difficulty?: ProblemDifficulty | '';
  tag?: string;
  category?: string;
  sort?: 'frontend_id_asc' | 'frontend_id_desc' | 'title_asc' | 'updated_desc';
  page?: number;
  pageSize?: number;
}

export interface ProblemListItem {
  slug: string;
  frontendId?: number;
  title: string;
  titleCn?: string;
  difficulty?: ProblemDifficulty;
  tags: string[];
}

export interface ProblemDetail extends ProblemListItem {
  contentMarkdown: string;
  leetcodeUrl?: string;
  sampleTestCase?: string;
  python3Template?: string;
  sourceCommit?: string;
}

export interface ProblemPage<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
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

export const AGENT_RUN_IN_PROGRESS_CODE = 'AGENT_RUN_IN_PROGRESS';

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
