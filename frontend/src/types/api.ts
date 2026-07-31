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

export interface DatabaseRestoreResponse {
  restoredAt: string;
  tableCount: number;
  dumpSizeBytes: number;
  loginRequired: boolean;
}

export type AuthRole = 'USER' | 'ADMIN';
export type AuthUserStatus = 'ACTIVE' | 'DISABLED' | 'DELETED';
export type AuthSessionAuthenticationMethod = 'PASSWORD' | 'OIDC' | 'OAUTH2';
export type OAuthProvider = 'google' | 'github';
export type UserGroupStatus = 'ACTIVE' | 'DISABLED';
export type AuthPermission =
  | 'learning-plan:read:own'
  | 'learning-plan:write:own'
  | 'practice-session:write:own'
  | 'problem:read'
  | 'problem:write'
  | 'user:manage'
  | 'policy:manage'
  | 'admin-overview:read'
  | 'beta-access:manage'
  | 'session:manage'
  | 'ai-governance:manage'
  | 'ai-run:read'
  | 'feedback:manage'
  | 'database-backup:manage';

export interface CurrentUser {
  id: number;
  email?: string;
  displayName?: string;
  avatarUrl?: string;
  roles: AuthRole[];
  permissions: AuthPermission[];
  status: AuthUserStatus;
  passwordChangeRequired: boolean;
  passwordConfigured: boolean;
  passwordLoginEnabled?: boolean;
  sessionAuthenticationMethod: AuthSessionAuthenticationMethod | null;
}

export interface AuthCapabilities {
  passwordLoginEnabled: boolean;
  passwordRegistrationEnabled: boolean;
  oauthProviders: OAuthProvider[];
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
  groups?: AdminUserGroupSummary[];
}

export interface AdminUserDetail extends AdminUserSummary {
  emailNormalized?: string;
  deletedAt?: string | null;
  deletedBy?: number | null;
  groups?: AdminUserGroupMembership[];
}

export interface AdminUserGroupSummary {
  id: number;
  code: string;
  name: string;
}

export interface AdminUserGroupMembership extends AdminUserGroupSummary {
  joinedAt?: string;
  expiresAt?: string | null;
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

export interface UserGroupSummary {
  id: number;
  code: string;
  name: string;
  description?: string | null;
  status: UserGroupStatus;
  activeMemberCount: number;
  createdAt: string;
  updatedAt: string;
}

export type UserGroupDetail = UserGroupSummary;

export interface UserGroupDeletionResponse {
  groupId: number;
  deleted: boolean;
  removedMembershipCount: number;
}

export interface UserGroupPage {
  items: UserGroupSummary[];
  total: number;
  page: number;
  pageSize: number;
}

export interface UserGroupListQuery {
  page?: number;
  pageSize?: number;
  keyword?: string;
  status?: UserGroupStatus | '';
}

export interface UserGroupCreateRequest {
  code: string;
  name: string;
  description?: string | null;
}

export interface UserGroupUpdateRequest {
  name: string;
  description?: string | null;
  status: UserGroupStatus;
}

export interface UserGroupMember {
  userId: number;
  email?: string | null;
  displayName?: string | null;
  avatarUrl?: string | null;
  status: AuthUserStatus;
  joinedAt: string;
  expiresAt?: string | null;
}

export interface UserGroupMemberPage {
  items: UserGroupMember[];
  total: number;
  page: number;
  pageSize: number;
}

export interface UserGroupMemberListQuery {
  page?: number;
  pageSize?: number;
  keyword?: string;
}

export interface UserGroupMemberAddRequest {
  userIds: number[];
  expiresAt?: string | null;
}

export type UserGroupMemberAddStatus = 'ADDED' | 'UPDATED' | 'USER_NOT_FOUND' | 'USER_DELETED' | 'INVALID_EXPIRY';

export interface UserGroupMemberAddResult {
  userId: number;
  status: UserGroupMemberAddStatus;
}

export interface UserGroupMemberBatchResponse {
  addedCount: number;
  updatedCount: number;
  failedCount: number;
  results: UserGroupMemberAddResult[];
}

export interface UserGroupMemberRemovalResponse {
  groupId: number;
  userId: number;
  removed: boolean;
}

export interface BetaAccessSettings {
  id: number;
  emailAllowlistEnabled: boolean;
  updatedBy?: number | null;
  updatedByDisplayName?: string | null;
  updatedAt: string;
}

export interface BetaAllowedEmail {
  id: number;
  email: string;
  registered: boolean;
  associatedUserId?: number | null;
  associatedUserStatus?: AuthUserStatus | null;
  createdBy: number;
  createdByDisplayName?: string | null;
  createdAt: string;
}

export interface BetaAccessPage {
  settings: BetaAccessSettings;
  items: BetaAllowedEmail[];
  total: number;
  page: number;
  pageSize: number;
}

export interface BetaAccessListQuery {
  page?: number;
  pageSize?: number;
  keyword?: string;
}

export interface BetaAccessSettingsUpdateRequest {
  emailAllowlistEnabled: boolean;
}

export type BetaAllowedEmailAddStatus = 'ADDED' | 'EXISTING' | 'INVALID';

export interface BetaAllowedEmailAddResult {
  email: string;
  status: BetaAllowedEmailAddStatus;
  allowedEmailId?: number | null;
}

export interface BetaAllowedEmailBatchResponse {
  addedCount: number;
  existingCount: number;
  invalidCount: number;
  results: BetaAllowedEmailAddResult[];
}

export interface BetaAllowedEmailRemovalResponse {
  allowedEmailId: number;
  associatedUserId?: number | null;
  associatedUserStatus?: AuthUserStatus | null;
  revokedSessionCount: number;
  sessionRevocationSucceeded: boolean;
}

export type AuthSessionActivity = 'ACTIVE' | 'IDLE';

export interface AdminAuthSession {
  sessionRef: string;
  userId: number;
  email?: string | null;
  displayName?: string | null;
  userStatus: AuthUserStatus;
  createdAt: string;
  lastAccessedAt: string;
  expiresAt: string;
  activity: AuthSessionActivity;
  current: boolean;
}

export interface AdminAuthSessionSummary {
  validSessionCount: number;
  activeSessionCount: number;
  validUserCount: number;
}

export interface AdminAuthSessionPage {
  items: AdminAuthSession[];
  total: number;
  page: number;
  pageSize: number;
  summary: AdminAuthSessionSummary;
  checkedAt: string;
}

export interface AdminAuthSessionListQuery {
  page?: number;
  pageSize?: number;
  keyword?: string;
  activity?: AuthSessionActivity | '';
}

export interface AdminAuthSessionRevocationResponse {
  sessionRef: string;
  userId?: number | null;
  revoked: boolean;
  alreadyOffline: boolean;
}

export type GenericPolicyStatus = 'ENABLED' | 'DISABLED';
export type PolicySubjectType = 'USER' | 'GROUP';

export interface PolicySubject {
  type: PolicySubjectType;
  id: number;
}

export interface PolicySubjectRange {
  allSubject: boolean;
  subjects: PolicySubject[];
}

export interface UserSessionPolicyContent {
  maxSessions: number;
  absoluteTimeoutSeconds: number;
}

export interface AdminGenericPolicy<TContent = UserSessionPolicyContent> {
  id: number;
  typeCode: string;
  name: string;
  description?: string | null;
  status: GenericPolicyStatus;
  priority: number;
  subjectRange: PolicySubjectRange;
  content: TContent;
  version: number;
  createdBy: number;
  createdAt: string;
  updatedBy: number;
  updatedAt: string;
}

export interface AdminGenericPolicyPage<TContent = UserSessionPolicyContent> {
  items: AdminGenericPolicy<TContent>[];
  total: number;
  page: number;
  pageSize: number;
}

export interface AdminGenericPolicyListQuery {
  typeCode: string;
  page?: number;
  pageSize?: number;
  keyword?: string;
  status?: GenericPolicyStatus | '';
}

export interface AdminGenericPolicyWriteRequest<TContent = UserSessionPolicyContent> {
  typeCode: string;
  name: string;
  description: string;
  status: GenericPolicyStatus;
  subjectRange: PolicySubjectRange;
  content: TContent;
}

export interface AdminGenericPolicyUpdateRequest<TContent = UserSessionPolicyContent> extends Omit<AdminGenericPolicyWriteRequest<TContent>, 'typeCode'> {
  version: number;
}

export interface AdminGenericPolicyOrderRequest {
  policyIds: number[];
  versions: Record<number, number>;
}

export interface ManagedSystemPromptPolicyContent { sectionOverrides: Record<string, string>; }
export interface SystemPromptTypeSummary { typeCode: string; categoryCode: string; displayName: string; description: string; sourceRevision: string; snapshotScope: string; sectionCount: number; configured: boolean; livePolicyCount: number; effectiveSource: string; }
export interface SystemPromptTypeDetail { typeCode: string; sourceRevision: string; snapshotScope: string; sections: SystemPromptSection[]; }
export interface SystemPromptSection { key: string; displayName: string; description: string; displayOrder: number; required: boolean; maxLength: number; defaultText: string; }
export interface SystemPromptEffectiveSection { key: string; text: string; source: string; contentHash: string; charCount: number; }
export interface SystemPromptEffective { typeCode: string; resolutionSource: string; policyId?: number | null; policyVersion?: number | null; matchSource?: string | null; matchedSubjectId?: number | null; combinedContentHash: string; sections: SystemPromptEffectiveSection[]; }

export interface AdminPasswordResetResponse {
  temporaryPassword: string;
  expiresAt: string;
}

export interface AdminAiSettings {
  aiEnabled: boolean;
  defaultDailyRequestLimit: number;
  updatedBy?: number | null;
  updatedByDisplayName?: string | null;
  updatedAt?: string | null;
}

export interface AdminAiSettingsUpdateRequest {
  aiEnabled: boolean;
  defaultDailyRequestLimit: number;
}

export interface AdminUserAiPolicy {
  userId: number;
  globalAiEnabled: boolean;
  aiEnabledOverride?: boolean | null;
  effectiveAiEnabled: boolean;
  effectiveDisabledReason?: 'GLOBAL' | 'USER' | string | null;
  globalDefaultDailyRequestLimit: number;
  dailyRequestLimitOverride?: number | null;
  effectiveDailyRequestLimit: number;
  updatedBy?: number | null;
  updatedByDisplayName?: string | null;
  updatedAt?: string | null;
}

export interface AdminUserAiPolicyUpdateRequest {
  aiEnabledOverride: boolean | null;
  dailyRequestLimitOverride: number | null;
}

/** Price and cost values remain decimal strings through the browser boundary. */
export interface AdminAiModelPrice {
  id: number;
  provider: string;
  model: string;
  currency: 'USD' | string;
  inputPricePerMillion: string;
  cachedInputPricePerMillion: string;
  outputPricePerMillion: string;
  costMultiplier: string;
  enabled: boolean;
  updatedBy?: number | null;
  updatedByDisplayName?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface AdminAiModelPriceWriteRequest {
  provider: string;
  model: string;
  inputPricePerMillion: string;
  cachedInputPricePerMillion: string;
  outputPricePerMillion: string;
  costMultiplier: string;
  enabled: boolean;
}

export interface AdminAiUnpricedModel {
  provider?: string | null;
  model?: string | null;
  modelCallCount: number;
  totalTokens: number;
  lastSeenAt?: string | null;
}

export interface AdminAiModelPricePage {
  items: AdminAiModelPrice[];
  unpricedModels: AdminAiUnpricedModel[];
}

export interface AdminAiProviderType { code: string; displayName: string; }
export interface AdminAiProvider {
  id: number;
  name: string;
  providerType: string;
  enabled: boolean;
  baseUrl?: string | null;
  config?: Record<string, unknown> | null;
  modelCount: number;
  createdAt?: string | null;
  updatedAt?: string | null;
}
export interface AdminAiProviderWriteRequest {
  name: string;
  providerType: string;
  enabled: boolean;
  config: Record<string, unknown>;
}
export interface AdminAiProviderUpdateRequest {
  name: string;
  enabled: boolean;
  config: Record<string, unknown>;
}
export interface AdminAiConfiguredModel {
  id: number;
  providerInstanceId: number;
  displayName: string;
  modelId: string;
  enabled: boolean;
  createdAt?: string | null;
  updatedAt?: string | null;
}
export interface AdminAiModelWriteRequest { displayName: string; modelId: string; enabled: boolean; }
export interface AiModelRoutePolicyContent { modelId: number; }
export interface AdminAiRoutingScenario {
  scenarioCode: string;
  categoryCode: string;
  displayName: string;
  description: string;
  policyTypeCode: string;
  configured: boolean;
  enabledPolicyCount: number;
  totalPolicyCount: number;
}
export interface AdminAiRouteModel {
  id: number;
  displayName?: string | null;
  modelId?: string | null;
  enabled: boolean;
  providerInstanceId?: number | null;
  providerInstanceName?: string | null;
  providerType?: string | null;
  providerEnabled: boolean;
  reason?: string | null;
}
export interface AdminAiEffectiveRoute {
  scenarioCode: string;
  configured: boolean;
  matched: boolean;
  policyId?: number | null;
  policyVersion?: number | null;
  priority?: number | null;
  matchSource?: string | null;
  matchedSubjectId?: number | null;
  reason?: string | null;
  model?: AdminAiRouteModel | null;
}

export interface AdminAiUsageMetrics {
  modelCallCount: number;
  inputTokens: number;
  cachedTokens: number;
  outputTokens: number;
  reasoningTokens: number;
  totalTokens: number;
  pricedCallCount: number;
  pricedTokenCount: number;
  estimatedCostUsd: string;
  unpricedCallCount: number;
  unpricedTokenCount: number;
}

export interface AdminAiUsageSummary {
  from: string;
  to: string;
  quotaZone: string;
  admittedEntryRequestCount: number;
  metrics: AdminAiUsageMetrics;
}

export interface AdminAiUsageByUser {
  userId: number;
  email?: string | null;
  displayName?: string | null;
  accountStatus?: AuthUserStatus | string | null;
  metrics: AdminAiUsageMetrics;
  todayEntryRequestCount: number;
  effectiveDailyRequestLimit: number;
  effectiveAiEnabled: boolean;
}

export interface AdminAiUsageByUserPage {
  items: AdminAiUsageByUser[];
  total: number;
  page: number;
  pageSize: number;
}

export interface AdminAiUsageByModel {
  provider?: string | null;
  model?: string | null;
  priced: boolean;
  metrics: AdminAiUsageMetrics;
}

export interface AdminAiUsageBySource {
  source: string;
  metrics: AdminAiUsageMetrics;
}

export interface AdminAiUsageQuery {
  from?: string;
  to?: string;
  userId?: number;
  provider?: string;
  model?: string;
  purpose?: string;
  source?: string;
}

export interface AdminAiUsageByUserQuery extends AdminAiUsageQuery {
  page?: number;
  pageSize?: number;
}

export type FeedbackCategory = 'BUG' | 'SUGGESTION' | 'OTHER';
export type FeedbackStatus = 'OPEN' | 'CLOSED';
export type FeedbackSenderType = 'USER' | 'ADMIN';

export interface FeedbackCreateRequest {
  category: FeedbackCategory;
  subject?: string;
  content: string;
  sourcePath?: string;
  sourceRequestId?: string;
  sourceRunId?: string;
}

export interface FeedbackMessageRequest {
  content: string;
}

export interface FeedbackUserSummary {
  id: number;
  email?: string | null;
  displayName?: string | null;
  status?: AuthUserStatus | string | null;
}

export interface FeedbackThreadSummary {
  id: number;
  category: FeedbackCategory;
  status: FeedbackStatus;
  subject?: string | null;
  lastSenderType: FeedbackSenderType;
  unreadMessageCount: number;
  sourceRunId?: string | null;
  createdAt: string;
  updatedAt: string;
  closedAt?: string | null;
  user?: FeedbackUserSummary | null;
}

export interface FeedbackMessage {
  id: number;
  senderType: FeedbackSenderType;
  senderUserId: number;
  content: string;
  readAt?: string | null;
  createdAt: string;
}

export interface FeedbackThreadDetail {
  id: number;
  category: FeedbackCategory;
  status: FeedbackStatus;
  subject?: string | null;
  sourcePath?: string | null;
  sourceRequestId?: string | null;
  sourceRunId?: string | null;
  createdAt: string;
  updatedAt: string;
  closedAt?: string | null;
  closedBy?: number | null;
  unreadMessageCount: number;
  messages: FeedbackMessage[];
}

export interface FeedbackThreadPage {
  items: FeedbackThreadSummary[];
  total: number;
  page: number;
  pageSize: number;
  unreadMessageCount: number;
}

export interface FeedbackListQuery {
  page?: number;
  pageSize?: number;
  status?: FeedbackStatus | '';
}

export interface AdminFeedbackListQuery extends FeedbackListQuery {
  category?: FeedbackCategory | '';
  userId?: number;
  unreadOnly?: boolean;
}

export interface FeedbackReadResult {
  threadId: number;
  markedReadCount: number;
  unreadMessageCount: number;
}

export interface BetaAccessUserMembership {
  userId: number;
  allowed: boolean;
  allowedEmailId?: number | null;
}

export interface AdminOverviewSection<T> {
  available: boolean;
  data?: T | null;
  errorCode?: string | null;
}

export interface AdminOverview {
  generatedAt: string;
  quotaDate: string;
  quotaZone: string;
  betaAccess: AdminOverviewSection<{ emailAllowlistEnabled: boolean; allowedEmailCount: number; registeredAllowedEmailCount: number }>;
  aiRuntime: AdminOverviewSection<{ aiEnabled: boolean; defaultDailyRequestLimit: number; updatedAt?: string | null }>;
  aiToday: AdminOverviewSection<{
    entryRequests: { total: number; completed: number; failed: number; cancelled: number; quotaRejected: number; inProgress: number; otherRejected: number };
    modelCallCount: number; inputTokens: number; cachedTokens: number; outputTokens: number; totalTokens: number;
    estimatedCostUsd: string; unpricedCallCount: number; unpricedTokenCount: number;
  }>;
  quotaRisks: AdminOverviewSection<{ thresholdPercent: number; items: Array<{ userId: number; email?: string | null; displayName?: string | null; requestCount: number; effectiveDailyRequestLimit: number; usagePercent: number; atLimit: boolean; effectiveAiEnabled: boolean }> }>;
  feedback: AdminOverviewSection<{ openThreadCount: number; adminUnreadMessageCount: number }>;
  recentFailedRuns: AdminOverviewSection<{ items: unknown[] }>;
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

export type LearnerProfileDocumentBlock = LearnerProfileHeadingBlock | LearnerProfileParagraphBlock;

export interface LearnerProfileHeadingBlock {
  type: 'HEADING';
  spans: LearnerProfileDocumentSpan[];
}

export interface LearnerProfileParagraphBlock {
  type: 'PARAGRAPH';
  spans: LearnerProfileDocumentSpan[];
}

export type LearnerProfileDocumentSpan = LearnerProfileTextSpan | LearnerProfileSupportedTextSpan;

export interface LearnerProfileTextSpan {
  type: 'TEXT';
  text: string;
  citationDisplayNumber?: null;
}

export interface LearnerProfileSupportedTextSpan {
  type: 'SUPPORTED_TEXT';
  text: string;
  citationDisplayNumber: number;
}

export type LearnerProfileEvidenceType = 'CODE_REVIEW' | 'USER_MESSAGE';

export type LearnerProfileReviewRole =
  | 'OBSERVED'
  | 'PERSISTED'
  | 'RESOLVED'
  | 'REGRESSED'
  | 'CONTRADICTS';

export type LearnerProfileMessageRole = 'DECLARED' | 'CORRECTED';

export interface LearnerProfileCodeReviewSource {
  reviewId: number;
  sessionId: number;
  planId: number;
  phaseIndex: number;
  problemSlug: string;
  versionNo: number;
  totalScore: number;
  passed: boolean;
}

export interface LearnerProfileUserMessageSource {
  excerpt: string;
}

export interface LearnerProfileCodeReviewEvidence {
  type: 'CODE_REVIEW';
  sourceId: number;
  occurredAt: string;
  reviewRole: LearnerProfileReviewRole;
  messageRole?: null;
  codeReview: LearnerProfileCodeReviewSource;
  userMessage?: null;
}

export interface LearnerProfileUserMessageEvidence {
  type: 'USER_MESSAGE';
  sourceId: number;
  occurredAt: string;
  reviewRole?: null;
  messageRole: LearnerProfileMessageRole;
  codeReview?: null;
  userMessage: LearnerProfileUserMessageSource;
}

export type LearnerProfileEvidenceItem =
  | LearnerProfileCodeReviewEvidence
  | LearnerProfileUserMessageEvidence;

export interface LearnerProfileCitation {
  displayNumber: number;
  statementRef: string;
  claimRevisionId: number;
  claimKey: string;
  origin: 'USER_EXPLICIT' | 'USER_CORRECTION' | 'SYSTEM_DERIVED';
  sourceSummary: string;
  evidenceCount: number;
  previewEvidence: LearnerProfileEvidenceItem[];
}

export interface LearnerProfileDocumentResponse {
  format: 'MARKDOWN_DOCUMENT_V1';
  projectorVersion: 'v1';
  locale: 'zh-CN' | 'en-US';
  documentRevision: string;
  title: string;
  blocks: LearnerProfileDocumentBlock[];
  citationMap: Record<string, LearnerProfileCitation>;
  updatedAt?: string | null;
}

export interface LearnerProfileEvidencePage {
  items: LearnerProfileEvidenceItem[];
  nextCursor?: string | null;
}

export interface LearnerProfileEvidenceQuery {
  cursor?: string | null;
  limit?: number;
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

export interface CompletePasswordResetRequest {
  newPassword: string;
  confirmPassword: string;
}

export interface UserPasswordUpdateRequest {
  currentPassword?: string;
  newPassword: string;
  confirmPassword: string;
}

export type UserPasswordUpdateOperation = 'CREATED' | 'UPDATED';

export interface UserPasswordUpdateResponse {
  passwordConfigured: true;
  operation: UserPasswordUpdateOperation;
  revokedSessionCount: number;
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
  recommendationReason?: string;
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
export type LearningPlanTemplateCatalogCategory =
  | 'SYSTEMATIC_LEARNING'
  | 'INTERVIEW_PREP'
  | 'TOPIC_BREAKTHROUGH'
  | 'LANGUAGE_AND_ROLE';
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
  catalogCategory: LearningPlanTemplateCatalogCategory;
  recommendedOrder?: number | null;
  intent: LearningPlanIntent;
  defaultDurationWeeks: number;
  level: LearningPlanLevel;
  defaultWeeklyHours: number;
  programmingLanguage?: string | null;
  difficultyPreference: LearningPlanDifficultyPreference;
  interviewOriented: boolean;
  topicPreferences: string[];
  targetAudience: string;
  expectedOutcome: string;
  plannedProblemCount: number;
  defaultLoadSummary?: LearningPlanLoadSummary;
  defaultRhythmSettings?: LearningPlanRhythmSettings;
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
  plannedProblemCount: number;
}

export interface LearningPlanTemplateDetailResponse extends LearningPlanTemplateSummaryResponse {
  goal: string;
  programmingLanguage?: string | null;
  prerequisites: string[];
  recommendedFor: string[];
  notRecommendedFor: string[];
  sourceName: string;
  sourceUrl: string;
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

export type LearnerDeclaredProfileToolStatus = 'UPDATED' | 'NO_CHANGE' | 'FAILED';

export interface LearnerDeclaredProfileToolResult {
  type: 'learner_declared_profile_update';
  status: LearnerDeclaredProfileToolStatus;
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

export type ReviewCardSource = 'REVIEW_FAILED' | 'REVIEW_PASSED' | 'USER_MARKED';
export type ReviewRating = 'AGAIN' | 'HARD' | 'GOOD' | 'EASY';

export interface ReviewCard {
  id: number;
  problemSlug: string;
  problemTitle?: string | null;
  problemDifficulty?: string | null;
  source: ReviewCardSource;
  sourceDetail: Record<string, unknown>;
  repetitions: number;
  intervalDays: number;
  fsrsState: 'LEARNING' | 'REVIEW' | 'RELEARNING';
  fsrsStep?: number | null;
  fsrsStability?: number | null;
  fsrsDifficulty?: number | null;
  dueAt: string;
  lapses: number;
  lastReviewedAt?: string | null;
  lastRating?: ReviewRating | null;
  archived: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ReviewProblem {
  slug: string;
  titleCn: string;
  difficulty: string;
  contentMarkdown: string;
}

export interface ReviewIntervalPreview {
  rating: ReviewRating;
  dueAt: string;
  intervalDays: number;
}

export interface ReviewSchedulingSnapshot {
  repetitions: number;
  intervalDays: number;
  lapses: number;
  fsrsState: 'LEARNING' | 'REVIEW' | 'RELEARNING';
  fsrsStep?: number | null;
  fsrsStability?: number | null;
  fsrsDifficulty?: number | null;
  dueAt: string;
  lastReviewedAt?: string | null;
  lastRating?: ReviewRating | null;
}

export interface ReviewAttempt {
  id: number;
  reviewCardId: number;
  clientAttemptId: string;
  rating: ReviewRating;
  schedulingBefore: ReviewSchedulingSnapshot;
  schedulingAfter: ReviewSchedulingSnapshot;
  reviewedAt: string;
  duplicate: boolean;
}

export type ProblemDataStructureKey =
  | 'ARRAY'
  | 'HASH_MAP'
  | 'LINKED_LIST'
  | 'STACK'
  | 'QUEUE'
  | 'HEAP'
  | 'TREE'
  | 'GRAPH'
  | 'TRIE'
  | 'UNION_FIND'
  | 'OTHER';

export type ProblemAlgorithmKey =
  | 'TWO_POINTERS'
  | 'SLIDING_WINDOW'
  | 'BINARY_SEARCH'
  | 'DFS'
  | 'BFS'
  | 'BACKTRACKING'
  | 'GREEDY'
  | 'DYNAMIC_PROGRAMMING'
  | 'PREFIX_SUM'
  | 'SORTING'
  | 'MONOTONIC_STACK'
  | 'DIJKSTRA'
  | 'OTHER';

export type ProblemComplexityKey =
  | 'O_1'
  | 'O_LOG_N'
  | 'O_N'
  | 'O_N_LOG_N'
  | 'O_N2'
  | 'O_N3'
  | 'O_2N'
  | 'OTHER';

export interface ProblemComplexityValue {
  key?: ProblemComplexityKey | null;
  customText?: string | null;
}

export interface ProblemSolutionOutlineV1 {
  schemaVersion: 1;
  coreIdea: string;
  dataStructures: ProblemDataStructureKey[];
  customDataStructures: string[];
  dataStructureNotes: string;
  algorithms: ProblemAlgorithmKey[];
  customAlgorithms: string[];
  algorithmNotes: string;
  timeComplexity: ProblemComplexityValue;
  spaceComplexity: ProblemComplexityValue;
  edgeCases: string;
}

export interface UserProblemNote {
  id?: number | null;
  problemSlug: string;
  outline: ProblemSolutionOutlineV1;
  noteMarkdown: string;
  revision: number;
  exists: boolean;
  hasContent: boolean;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface UserProblemNoteRequest {
  outline: ProblemSolutionOutlineV1;
  noteMarkdown: string;
  expectedRevision: number;
}

export interface ReviewCardContext {
  card: ReviewCard;
  problem: ReviewProblem;
  note: UserProblemNote;
  recentAttempts: ReviewAttempt[];
  intervalPreviews: ReviewIntervalPreview[];
}

export interface ReviewPreference {
  desiredRetention: number;
  dailyNewLimit: number;
  dailyLearningLimit: number;
  dailyReviewLimit: number;
  maximumIntervalDays: number;
  enableFuzzing: boolean;
}

export interface ReviewPreferenceRequest {
  desiredRetention?: number;
  dailyNewLimit?: number;
  dailyLearningLimit?: number;
  dailyReviewLimit?: number;
  maximumIntervalDays?: number;
  enableFuzzing?: boolean;
}

export interface ReviewQueueResponse {
  items: ReviewCard[];
  dueCount: number;
}

export interface ReviewSummaryResponse {
  dueCount: number;
  remainingTodayCount: number;
  nextDueAt?: string | null;
}
