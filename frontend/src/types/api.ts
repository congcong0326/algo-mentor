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

export type PracticeCoachStyle = 'GUIDED' | 'DIRECT';

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
  company?: string;
  role?: string;
  recencyBucket?: string;
  sort?: 'frontend_id_asc' | 'frontend_id_desc' | 'title_asc' | 'updated_desc' | 'company_frequency_desc';
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
  frontendDisplayId?: string;
  title: string;
  difficulty?: ProblemDifficulty;
  tags: ProblemTag[];
  contentStatus?: 'BILINGUAL' | 'CN_ONLY';
  companyFrequencyScore?: number | null;
  companySignalCount?: number;
}

export interface ProblemDetail extends ProblemListItem {
  contentMarkdown: string;
  leetcodeUrl?: string;
  sampleTestCase?: string;
  python3Template?: string;
  sourceCommit?: string;
}

export interface ProblemFilterOption {
  value: string;
  label: string;
  problemCount: number;
}

export interface ProblemCategoryFilterOption {
  slug: string;
  name: string;
  problemCount: number;
}

export interface ProblemFilters {
  problemCount: number;
  difficulties: ProblemFilterOption[];
  tags: ProblemFilterOption[];
  categories: ProblemCategoryFilterOption[];
  companies: ProblemFilterOption[];
  roles: ProblemFilterOption[];
  recencyBuckets: ProblemFilterOption[];
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
export type LearningPlanCoveragePolicy =
  | 'FULL_ROUTE'
  | 'FULL_ROUTE_WITH_REVIEW_BUFFER'
  | 'FULL_ROUTE_FAST'
  | 'FIT_USER_BUDGET';
export type LearningPlanLoadIntensity = 'RELAXED' | 'RECOMMENDED' | 'TIGHT' | 'OVERLOADED';
export type LearningPlanPaceStatus = 'AHEAD' | 'ON_TRACK' | 'AT_RISK' | 'BEHIND';
export type LearningPlanProposalRevisionStatus =
  | 'GENERATING'
  | 'READY'
  | 'SUPERSEDED'
  | 'FAILED'
  | 'APPLIED'
  | 'DISCARDED'
  | 'EXPIRED';

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

export interface LearningPlanLoadSummary {
  durationWeeks: number;
  weeklyHours: number;
  weeklyCapacityPoints: number;
  totalCapacityPoints: number;
  plannedLoadPoints: number;
  loadRatio: number;
  plannedProblemCount: number;
  averageProblemsPerWeek: number;
  intensity: LearningPlanLoadIntensity | string;
  reviewBufferIncluded: boolean;
  suggestions: string[];
}

export interface LearningPlanWeeklyBucket {
  weekIndex: number;
  title: string;
  plannedProblemCount: number;
  plannedLoadPoints: number;
  problemSlugs: string[];
  reviewAdvice?: string | null;
}

export interface LearningPlanTrainingPackage {
  weekIndex: number;
  newProblemCount: number;
  reviewTask: string;
  estimatedMinutes: number;
  priorityProblemSlugs: string[];
}

export interface LearningPlanRhythmSettings {
  dailyProblemCount: number;
  trainingDaysPerWeek: number;
  totalProblemCount: number;
  completedProblemCount: number;
  skippedProblemCount: number;
  remainingProblemCount: number;
  estimatedRemainingWeeks: number;
}

export interface LearningPlanPaceSummary {
  currentWeek: number;
  totalWeeks: number;
  currentBucket?: LearningPlanWeeklyBucket | null;
  currentWeekCompletedProblemCount: number;
  plannedProblemCountToDate: number;
  completedProblemCountToDate: number;
  skippedProblemCount: number;
  plannedLoadPointsToDate: number;
  completedLoadPoints: number;
  loadGapPoints: number;
  status: LearningPlanPaceStatus;
  recommendation: string;
}

export type LearningPlanVisibleStatus = 'ON_TRACK' | 'NEEDS_REBALANCE' | 'PAUSED' | 'COMPLETED' | 'CLOSED_OUT';
export type LearningPlanEstimationSource = 'COLD_START_PLAN_QUOTA' | 'RECENT_COMPLETION_RATE' | 'FROZEN' | 'COMPLETED';

export interface LearningPlanCompletionSummary {
  completionRate: number;
  totalDurationDays: number;
  completedProblemCount: number;
  skippedProblemCount: number;
  openProblemCount: number;
  strongTags: string[];
  weakTags: string[];
  unresolvedProblemSlugs: string[];
}

export interface LearningPlanLivingContractSummary {
  totalProblemCount: number;
  completedProblemCount: number;
  skippedProblemCount: number;
  openProblemCount: number;
  progressPercent: number;
  estimatedCompletionDate?: string | null;
  estimationSource: LearningPlanEstimationSource;
  visibleStatus: LearningPlanVisibleStatus;
  nextTrainingPackage?: LearningPlanTrainingPackage | null;
  notice?: string | null;
  completionSummary?: LearningPlanCompletionSummary | null;
}

export interface LearningPlanTemplateSummaryResponse {
  templateId: string;
  title: string;
  summary: string;
  intent: LearningPlanIntent;
  defaultDurationWeeks: number;
  level: LearningPlanLevel;
  defaultWeeklyHours: number;
  difficultyPreference: LearningPlanDifficultyPreference;
  interviewOriented: boolean;
  topicPreferences: string[];
  targetAudience: string;
  difficultyMix: Record<string, unknown>;
  expectedOutcome: string;
  sourceName: string;
  sourceCommit?: string | null;
  problemCount: number;
  matchedProblemCount: number;
  missingProblemCount: number;
  defaultLoadSummary?: LearningPlanLoadSummary;
  defaultRhythmSettings?: LearningPlanRhythmSettings;
}

export interface LearningPlanTemplateProblemRefResponse {
  phaseIndex: number;
  sortOrder: number;
  sourceOrder: number;
  problemSlug?: string | null;
  sourceTitle: string;
  sourceDifficulty?: string | null;
  pattern?: string | null;
  sourceUrl?: string | null;
  matchedProblem: boolean;
  metadata: Record<string, unknown>;
}

export interface LearningPlanTemplatePhaseResponse {
  phaseIndex: number;
  title: string;
  durationWeeks: number;
  focus: string;
  objectives: string[];
  recommendedTags: string[];
  acceptanceCriteria: string[];
  reviewAdvice: string;
  problemRefs: LearningPlanTemplateProblemRefResponse[];
}

export interface LearningPlanTemplateDetailResponse extends LearningPlanTemplateSummaryResponse {
  goal: string;
  programmingLanguage?: string | null;
  prerequisites: string[];
  recommendedFor: string[];
  notRecommendedFor: string[];
  sourceUrl?: string | null;
  sourceDataPath?: string | null;
  sourceDescription: string;
  curationNotes: string;
  licenseNotice: string;
  metadata: Record<string, unknown>;
  phases: LearningPlanTemplatePhaseResponse[];
}

export interface LearningPlanTemplateDraftRequest {
  templateId: string;
  programmingLanguage?: string;
  dailyProblemCount?: number;
  trainingDaysPerWeek?: number;
}

export interface LearningPlanRhythmUpdateRequest {
  dailyProblemCount: number;
  trainingDaysPerWeek: number;
}

export interface LearningPlanMessageRequest {
  message: string;
}

export interface LearningPlanRevisionRequest {
  instruction: string;
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

export interface LearningPlanDetailProblemResponse extends LearningPlanProblemDraft {
  progressStatus: PracticeProgressStatus;
}

export interface LearningPlanDetailPhaseResponse extends Omit<LearningPlanPhaseDraft, 'problems'> {
  problems: LearningPlanDetailProblemResponse[];
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
  loadSummary?: LearningPlanLoadSummary;
  weeklyBuckets?: LearningPlanWeeklyBucket[];
  nextTrainingPackage?: LearningPlanTrainingPackage;
  rhythmSettings?: LearningPlanRhythmSettings;
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

export interface LearningPlanActivationResponse {
  planId: number;
  activatedAt: string;
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
  activePlanId?: number | null;
}

export interface LearningPlanDetailResponse extends LearningPlanDraftPlan {
  id: number;
  status: LearningPlanStatus;
  phases: LearningPlanDetailPhaseResponse[];
  loadSummary?: LearningPlanLoadSummary;
  weeklyBuckets?: LearningPlanWeeklyBucket[];
  nextTrainingPackage?: LearningPlanTrainingPackage;
  rhythmSettings?: LearningPlanRhythmSettings;
  paceSummary?: LearningPlanPaceSummary;
  livingContractSummary?: LearningPlanLivingContractSummary;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export type TodayPackState = 'NO_ACTIVE_PLAN' | 'READY' | 'DONE_TODAY' | 'PLAN_COMPLETED';
export type TodayPackSectionType = 'CARRYOVER' | 'TODAY' | 'FUTURE';

export interface TodayPackActivePlanResponse {
  planId: number;
  title: string;
  activatedAt: string;
  dailyProblemCount: number;
  trainingDaysPerWeek: number;
  remainingProblemCount: number;
}

export interface TodayPackRecommendedPlanResponse {
  templateId: string;
  title: string;
  summary: string;
}

export interface TodayPackProblemResponse {
  planId: number;
  phaseIndex: number;
  slug: string;
  frontendId?: number | null;
  title: string;
  titleCn?: string | null;
  difficulty?: ProblemDifficulty | string | null;
  tags: string[];
  progressStatus: PracticeProgressStatus;
  scheduledDate: string;
  carryoverDays: number;
}

export interface TodayPackSectionResponse {
  type: TodayPackSectionType;
  title: string;
  date?: string | null;
  problems: TodayPackProblemResponse[];
}

export interface TodayPackResponse {
  state: TodayPackState;
  localDate: string;
  timezone: string;
  packOffset: number;
  activePlan?: TodayPackActivePlanResponse | null;
  sections: TodayPackSectionResponse[];
  notice?: string | null;
  recommendedPlan?: TodayPackRecommendedPlanResponse | null;
  nextPackDate?: string | null;
}

export interface LearningPlanExtensionDraft {
  summary: string;
  newPhases: LearningPlanPhaseDraft[];
  metadata: Record<string, unknown>;
}

export interface LearningPlanDraftRevisionReadyEvent {
  proposalGroupId: number;
  proposalId: number;
  draftId: number;
  revisionNo: number;
  status: LearningPlanProposalRevisionStatus;
  supersededProposalIds: number[];
  draft: LearningPlanDraftResponse;
}

export interface LearningPlanExtensionReadyEvent {
  proposalGroupId: number;
  proposalId: number;
  planId: number;
  revisionNo: number;
  status: LearningPlanProposalRevisionStatus;
  supersededProposalIds: number[];
  summary: string;
  extensionDraft: LearningPlanExtensionDraft;
}

export interface LearningPlanExtensionApplyResponse {
  planId: number;
  proposalGroupId: number;
  proposalId: number;
  status: 'APPLIED';
  appendedPhaseCount: number;
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
  | 'draft_error'
  | 'draft_revision_ready'
  | 'draft_revision_error'
  | 'plan_extension_ready'
  | 'plan_extension_error';

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

export type MistakeSource = 'REVIEW_FAILED' | 'REVIEW_PASSED' | 'USER_MARKED' | 'AI_WEAK';
export type ReviewRating = 'AGAIN' | 'HARD' | 'GOOD' | 'EASY';
export type CardVariant = 'STATIC' | 'RULE_BASED' | 'AI_GENERATED';

export interface MistakeNote {
  id: number;
  problemSlug: string;
  problemTitle?: string | null;
  problemLocale?: string | null;
  problemDifficulty?: string | null;
  source: MistakeSource;
  sourceDetail: Record<string, unknown>;
  repetitions: number;
  intervalDays: number;
  fsrsState?: 'LEARNING' | 'REVIEW' | 'RELEARNING' | string;
  fsrsStep?: number | null;
  fsrsStability?: number | null;
  fsrsDifficulty?: number | null;
  dueAt: string;
  lapses: number;
  lastReviewedAt?: string | null;
  lastRating?: ReviewRating | null;
  archived: boolean;
  userNotePersistent?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ReviewCardPrompt {
  key: string;
  label: string;
  hint?: string | null;
}

export interface ReviewCardScaffold {
  templateMarkdown: string;
  maxInputChars: number;
}

export interface ReviewProblemStatementSummary {
  summary: string;
  hasFullContent: boolean;
}

export interface ReviewRecallHistory {
  id: number;
  rating: ReviewRating;
  userRecallText?: string | null;
  userNoteTransient?: string | null;
  reviewedAt: string;
  intervalAfter: number;
}

export interface ReviewCard {
  cardVariant: CardVariant;
  problemRef: {
    slug: string;
    titleCn: string;
    difficulty: string;
  };
  problemStatement?: ReviewProblemStatementSummary | null;
  contextSummary: string;
  prompts: ReviewCardPrompt[];
  scaffold?: ReviewCardScaffold | null;
  revealPolicy: string;
  expectedEffort: 'LIGHT' | 'MEDIUM' | 'HEAVY' | string;
  userNotePersistent?: string | null;
  recentRecallHistory: ReviewRecallHistory[];
}

export interface ReviewProblemStatementResponse {
  slug: string;
  titleCn: string;
  difficulty: string;
  contentMarkdown: string;
}

export interface RecallReviewResult {
  suggestedRating: ReviewRating;
  hitPoints: string[];
  missedPoints: string[];
  gapSummary: string;
  nextDueAt: string;
  intervalDays: number;
  repetitions: number;
}

export interface ReviewIntervalPreview {
  rating: ReviewRating;
  dueAt: string;
  intervalDays: number;
}

export interface RecallEvaluationResult {
  evaluationId: number;
  suggestedRating?: ReviewRating | null;
  hitPoints: string[];
  missedPoints: string[];
  gapSummary: string;
  aiSuggested: boolean;
  createdAt: string;
  intervals: ReviewIntervalPreview[];
}

export interface RecallConfirmResult {
  rating: ReviewRating;
  suggestedRating?: ReviewRating | null;
  nextDueAt: string;
  intervalDays: number;
  repetitions: number;
  aiSuggested: boolean;
}

export interface ReviewPreference {
  desiredRetention: number;
  dailyNewLimit: number;
  dailyLearningLimit: number;
  dailyReviewLimit: number;
  aiSuggestionEnabled: boolean;
}

export interface ReviewPreferenceRequest {
  desiredRetention?: number;
  dailyNewLimit?: number;
  dailyLearningLimit?: number;
  dailyReviewLimit?: number;
  aiSuggestionEnabled?: boolean;
}

export interface ReviewQueueResponse {
  items: MistakeNote[];
  dueCount: number;
}

export interface ReviewSummaryResponse {
  dueCount: number;
}
