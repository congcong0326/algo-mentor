export interface ApiError {
  code: string;
  messageKey?: string;
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

export type AuthRole = 'USER' | 'ADMIN';
export type AuthUserStatus = 'ACTIVE' | 'DISABLED' | 'DELETED';
export type AuthPermission =
  | 'learning-plan:read:own'
  | 'learning-plan:write:own'
  | 'practice-session:write:own'
  | 'problem:read'
  | 'problem:write'
  | 'user:manage'
  | 'debug:access';

export interface CurrentUser {
  id: number;
  email?: string;
  displayName?: string;
  avatarUrl?: string;
  roles: AuthRole[];
  permissions: AuthPermission[];
  status: AuthUserStatus;
}

export interface AdminUserSummary {
  id: number;
  email?: string;
  displayName?: string;
  avatarUrl?: string;
  status: AuthUserStatus;
  roles: AuthRole[];
  createdAt: string;
  updatedAt: string;
  lastLoginAt?: string | null;
}

export interface AdminUserDetail extends AdminUserSummary {
  emailNormalized?: string;
  deletedAt?: string | null;
  deletedBy?: number | null;
}

export interface AdminUserPage {
  items: AdminUserSummary[];
  total: number;
  page: number;
  pageSize: number;
}

export interface AdminUserListQuery {
  page?: number;
  pageSize?: number;
  keyword?: string;
  status?: AuthUserStatus | '';
}

export interface AdminUserStatusUpdateRequest {
  status: Extract<AuthUserStatus, 'ACTIVE' | 'DISABLED'>;
}

export interface AbilityTagScore {
  tag: string;
  label: string;
  problemCount: number;
  reviewedProblemCount: number;
  rawAverageScore: number;
  abilityScore: number;
}

export interface AbilityProfileScope {
  minProblemCount: number;
  scorePrecision: number;
  latestReviewOnly: boolean;
  conservativeWeight: number;
}

export interface AbilityProfileResponse {
  tags: AbilityTagScore[];
  scope: AbilityProfileScope;
}

export type PracticeCoachStyle =
  | 'SOCRATIC_GUIDE'
  | 'DIRECT_EXPLAINER'
  | 'INTERVIEWER'
  | 'STRICT_REVIEWER'
  | 'SUPPORTIVE_MENTOR';

export type PracticeResponseLanguage = 'ZH_CN' | 'EN_US';

export interface UserAiPreference {
  coachStyle: PracticeCoachStyle;
  coachStyleLabel: string;
  updatedAt?: string;
}

export interface UserAiPreferenceRequest {
  coachStyle?: PracticeCoachStyle;
}

export interface PasswordLoginRequest {
  email: string;
  password: string;
}

export interface PasswordRegisterRequest extends PasswordLoginRequest {
  displayName: string;
}

export type ProblemDifficulty = 'EASY' | 'MEDIUM' | 'HARD';

export interface ProblemListQuery {
  keyword?: string;
  difficulty?: ProblemDifficulty | '';
  tag?: string;
  category?: string;
  sort?: 'frontend_id_asc' | 'frontend_id_desc' | 'title_asc' | 'updated_desc';
  locale?: 'zh-CN' | 'en-US';
  page?: number;
  pageSize?: number;
}

export interface ProblemTag {
  value: string;
  label: string;
}

export interface ProblemListItem {
  slug: string;
  frontendId?: number;
  title: string;
  difficulty?: ProblemDifficulty;
  tags: ProblemTag[];
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

export type PracticeProgressStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED';
export type PracticeMessageRole = 'USER' | 'ASSISTANT';
export type PracticeMessageType = 'PROBLEM_STATEMENT' | 'CHAT';

export interface PracticeSessionSummary {
  id: number;
  planId: number;
  phaseIndex: number;
  problemSlug: string;
  progressStatus: PracticeProgressStatus;
  agentTaskId: number;
  createdAt: string;
  updatedAt: string;
}

export interface PracticeProblemSummary {
  slug: string;
  frontendId?: number;
  title: string;
  titleCn?: string | null;
  difficulty?: ProblemDifficulty | string;
  tags: string[];
  leetcodeUrl?: string;
}

export interface PracticeMessage {
  id: number;
  role: PracticeMessageRole;
  messageType: PracticeMessageType;
  contentMarkdown: string;
  createdAt: string;
}

export interface PracticeActiveRun {
  runId: number;
  taskId: number;
  runUuid: string;
  idempotencyKey?: string;
  startedAt: string;
}

export type PracticeCompletionGateReasonCode =
  | 'NO_REVIEW'
  | 'LATEST_REVIEW_FAILED'
  | 'PASSED'
  | 'ALREADY_COMPLETED';

export interface PracticeCompletionGate {
  canComplete: boolean;
  reasonCode: PracticeCompletionGateReasonCode;
  message: string;
  latestScore?: number | null;
  passScore: number;
}

export interface PracticeCodeReviewSummary {
  id: number;
  versionNo: number;
  language: string;
  totalScore: number;
  passed: boolean;
  createdAt: string;
}

export interface PracticeCodeReviewEvidence {
  type: string;
  value: string;
}

export interface PracticeCodeReviewScore {
  correctness: number;
  complexity: number;
  edgeCases: number;
  codeQuality: number;
  problemFit: number;
  total: number;
}

export interface PracticeCodeReviewDetail {
  id: number;
  planId?: number;
  phaseIndex?: number;
  problemSlug?: string;
  sessionId: number;
  versionNo: number;
  userMessageId?: number | null;
  assistantMessageId?: number | null;
  agentRunDbId?: number | null;
  rawCode?: string;
  normalizedCode?: string;
  submittedCode?: string;
  language: string;
  evidence: PracticeCodeReviewEvidence[];
  contextSummary: string;
  scores: PracticeCodeReviewScore;
  passed: boolean;
  deductionReasons: string[];
  improvementSuggestions: string[];
  reviewMarkdown: string;
  createdAt: string;
}

export interface PracticeCodeReviewHistoryResponse {
  latestReview?: PracticeCodeReviewSummary | null;
  reviews: PracticeCodeReviewSummary[];
  completionGate: PracticeCompletionGate;
}

export interface PracticeSessionResponse {
  session: PracticeSessionSummary;
  problem: PracticeProblemSummary;
  messages: PracticeMessage[];
  activeRun?: PracticeActiveRun | null;
  latestReview?: PracticeCodeReviewSummary | null;
  completionGate?: PracticeCompletionGate | null;
}

export interface PracticeMessageRequest {
  message: string;
}

export type LearningPlanIntent =
  | 'PRACTICE_GOAL'
  | 'ABILITY_DIAGNOSIS'
  | 'INTERVIEW_SPRINT'
  | 'TOPIC_BREAKTHROUGH'
  | 'MISTAKE_REVIEW'
  | 'LONG_TERM_LEARNING';

export type LearningPlanLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';
export type LearningPlanDifficultyPreference = 'EASY' | 'MEDIUM' | 'HARD' | 'MIXED';
export type LearningPlanDraftStatus = 'COLLECTING' | 'GENERATED' | 'CONFIRMED' | 'GENERATION_FAILED' | 'EXPIRED';
export type LearningPlanStatus = 'ACTIVE' | 'ARCHIVED';

export interface LearningPlanCreateDraftRequest {
  intent?: LearningPlanIntent;
  goal: string;
  durationWeeks?: number;
  level?: LearningPlanLevel;
  weeklyHours?: number;
  programmingLanguage?: string;
  difficultyPreference?: LearningPlanDifficultyPreference;
  interviewOriented?: boolean;
  topicPreferences: string[];
}

export interface LearningPlanMessageRequest {
  message: string;
}

export interface LearningPlanProblemDraft {
  slug: string;
  frontendId?: number;
  title: string;
  titleCn?: string;
  difficulty?: ProblemDifficulty | string;
  tags: string[];
  reason: string;
  sortOrder: number;
}

export interface LearningPlanPhaseDraft {
  phaseIndex: number;
  title: string;
  durationWeeks: number;
  focus: string;
  objectives: string[];
  recommendedTags: string[];
  acceptanceCriteria: string[];
  reviewAdvice: string;
  problems: LearningPlanProblemDraft[];
}

export interface LearningPlanDraftPlan {
  title: string;
  summary: string;
  intent: LearningPlanIntent;
  goal: string;
  durationWeeks: number;
  level: LearningPlanLevel;
  weeklyHours: number;
  programmingLanguage?: string;
  difficultyPreference?: LearningPlanDifficultyPreference;
  interviewOriented: boolean;
  topicPreferences: string[];
  profileSummary: string;
  phases: LearningPlanPhaseDraft[];
  metadata: Record<string, unknown>;
}

export interface LearningPlanDraftResponse {
  draftId: number;
  status: LearningPlanDraftStatus;
  assistantMessage?: string;
  missingFields: string[];
  draftPlan?: LearningPlanDraftPlan | null;
}

export interface LearningPlanConfirmResponse {
  planId: number;
  title: string;
  status: LearningPlanStatus;
}

export interface LearningPlanSummaryResponse {
  id: number;
  title: string;
  intent: LearningPlanIntent;
  goal: string;
  durationWeeks: number;
  level: LearningPlanLevel;
  programmingLanguage?: string;
  weeklyHours: number;
  status: LearningPlanStatus;
  createdAt: string;
}

export interface LearningPlanListQuery {
  page?: number;
  pageSize?: number;
}

export interface LearningPlanPageResponse {
  items: LearningPlanSummaryResponse[];
  total: number;
  page: number;
  pageSize: number;
  activeCount: number;
  archivedCount: number;
  latestCreatedAt?: string | null;
}

export interface LearningPlanDetailResponse extends LearningPlanDraftPlan {
  id: number;
  status: LearningPlanStatus;
  createdAt: string;
  updatedAt: string;
}

export type SseEventName =
  | 'agent_run_start'
  | 'agent_step_start'
  | 'agent_tool_start'
  | 'agent_tool_end'
  | 'tool_permission_request'
  | 'tool_permission_decision'
  | 'tool_permission_timeout'
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
  | 'agent_error'
  | 'work_start'
  | 'work_progress'
  | 'work_tool_start'
  | 'work_tool_end'
  | 'work_done'
  | 'work_error'
  | 'draft_ready'
  | 'draft_error';

export interface AgentConversationStreamRequest {
  taskId?: number;
  userId?: number;
  message: string;
  practice?: PracticeChatRequest;
}

export interface PracticeChatRequest {
  planId: number;
  phaseIndex: number;
  problemSlug: string;
  locale?: string;
}

export const AGENT_RUN_IN_PROGRESS_CODE = 'AGENT_RUN_IN_PROGRESS';

export interface SseStreamEvent {
  eventName: SseEventName;
  data: unknown;
}

export type AgentToolPermissionDecisionType = 'ALLOW' | 'DENY';

export interface AgentToolPermissionRequestEvent {
  runId: string;
  stepIndex: number;
  toolCallId: string;
  toolName: string;
  permissionRequestId: string;
  displayName: string;
  reason: string;
  preview: Record<string, unknown>;
  expiresAt: string;
}

export interface AgentToolStartEvent {
  runId: string;
  stepIndex: number;
  toolCallId: string;
  toolName: string;
}

export interface AgentToolEndEvent {
  runId: string;
  stepIndex: number;
  toolCallId: string;
  toolName: string;
  result: unknown;
}

export interface AgentToolPermissionDecisionEvent {
  runId: string;
  stepIndex: number;
  toolCallId: string;
  toolName: string;
  permissionRequestId: string;
  decision: AgentToolPermissionDecisionType;
  reason: string;
  decidedAt: string;
}

export interface AgentToolPermissionTimeoutEvent {
  runId: string;
  stepIndex: number;
  toolCallId: string;
  toolName: string;
  permissionRequestId: string;
  reason: string;
  expiredAt: string;
}

export interface AgentToolPermissionDecisionRequest {
  decision: AgentToolPermissionDecisionType;
  reason: string;
}

export interface AgentToolPermissionDecisionResponse {
  permissionRequestId: string;
  decision: AgentToolPermissionDecisionType;
  accepted: boolean;
}

export interface AgentWorkStatusEvent {
  runId?: string;
  scenario?: string;
  message?: string;
  preview?: string;
  toolName?: string;
  code?: string;
  retryable?: boolean;
}

export interface LearningPlanDraftErrorEvent {
  code?: string;
  message?: string;
  retryable?: boolean;
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
