import type {
  LearningPlanDifficultyPreference,
  LearningPlanIntent,
  LearningPlanLevel,
  LearningPlanStatus,
  ProblemDifficulty,
  ProblemAlgorithmKey,
  ProblemComplexityKey,
  ProblemDataStructureKey,
  ReviewCardSource,
  ReviewRating,
  AuthUserStatus,
} from '../types/api';

export const SUPPORTED_LOCALES = ['zh-CN', 'en-US'] as const;

export type SupportedLocale = typeof SUPPORTED_LOCALES[number];

export interface LocaleResources {
  app: {
    brandKicker: string;
    brandName: string;
    mainNavigation: string;
    loginStatus: string;
    checkingLogin: string;
    checkingLoginStatus: string;
    loading: string;
    loginCheckFailed: string;
    retry: string;
    logout: string;
    loggingOut: string;
    logoutFailed: string;
    switchToDarkMode: string;
    switchToLightMode: string;
    unknownUser: (id: number) => string;
  };
  auth: {
    subtitle: string;
    welcomeEyebrow: string;
    welcomeTitle: string;
    welcomeDescription: string;
    featurePlanTitle: string;
    featurePlanDescription: string;
    featurePracticeTitle: string;
    featurePracticeDescription: string;
    featureReviewTitle: string;
    featureReviewDescription: string;
    previewTitle: string;
    previewSubtitle: string;
    previewFocusLabel: string;
    previewFocusValue: string;
    previewItems: Array<{
      title: string;
      detail: string;
    }>;
    emailAuthDivider: string;
    socialAuthDivider: string;
    supportContact: string;
    termsPrefix: string;
    termsLabel: string;
    termsConnector: string;
    privacyLabel: string;
    failed: string;
    betaAccessDenied: string;
    googleLogin: string;
    githubLogin: string;
    emailLabel: string;
    emailPlaceholder: string;
    passwordLabel: string;
    passwordPlaceholder: string;
    displayNameLabel: string;
    displayNamePlaceholder: string;
    passwordLogin: string;
    passwordRegister: string;
    loggingIn: string;
    registering: string;
    showLogin: string;
    showRegister: string;
    loginModeTitle: string;
    registerModeTitle: string;
    oauthModeTitle: string;
    oauthModeDescription: string;
    loginAction: string;
    validationEmailRequired: string;
    validationPasswordRequired: string;
    validationDisplayNameRequired: string;
  };
  nav: {
    home: string;
    my: string;
    settings: string;
    learningPlans: string;
    mistakes: string;
    problems: string;
    adminBetaAccess: string;
    adminUsers: string;
    adminUserGroups: string;
    adminMonitoring: string;
    adminDatabaseBackup: string;
    adminSessions: string;
    adminSessionPolicies: string;
    adminLearningPlanPolicies: string;
    adminLearningPlanAiRevisionPolicies: string;
    adminSystemPrompts: string;
    adminAi: string;
    feedback: string;
    adminOverview: string;
    adminFeedback: string;
    forbidden: string;
  };
  adminShell: {
    workspace: string;
    returnToLearning: string;
    openNavigation: string;
    closeNavigation: string;
    collapseNavigation: string;
    expandNavigation: string;
    businessNavigation: string;
    navigation: string;
    pageNavigation: string;
    expandSection: (label: string) => string;
    collapseSection: (label: string) => string;
    labels: Record<'overview' | 'access' | 'monitoring' | 'systemStatus' | 'databaseBackup' | 'sessions' | 'sessionPolicies' | 'learningPlanPolicies' | 'learningPlanAiRevisionPolicies' | 'systemPrompts' | 'ai' | 'aiPlatform' | 'operations' | 'modelResources' | 'costGovernance' | 'aiProviders' | 'aiRouting' | 'aiUsage' | 'aiPricing' | 'aiAudit' | 'content' | 'feedback' | 'users' | 'userGroups' | 'betaAccess' | 'problems', string>;
  };
  adminFeedback: {
    listLoadFailed: string;
    detailLoadFailed: string;
    markReadFailed: string;
    replyFailed: string;
    statusUpdateFailed: string;
    requestFailed: string;
    title: string;
    refresh: string;
    status: string;
    allStatuses: string;
    category: string;
    allCategories: string;
    unreadOnly: string;
    unread: string;
    user: string;
    subject: string;
    updatedAt: string;
    untitled: string;
    close: string;
    reopen: string;
    selectThread: string;
  };
  adminOverview: {
    loadFailed: string;
    loading: string;
    title: string;
    retry: string;
    generatedMeta: (date: string, zone: string) => string;
    refresh: string;
    betaAccess: string;
    enabled: string;
    disabled: string;
    betaSummary: (allowed: number, registered: number) => string;
    aiRuntime: string;
    aiRuntimeSummary: (limit: number) => string;
    aiToday: string;
    entryRequests: string;
    success: string;
    failed: string;
    quotaRejected: string;
    modelCalls: string;
    estimatedCost: string;
    unpricedCalls: (count: number) => string;
    quotaRiskUsers: string;
    userFallback: (id: number) => string;
    noQuotaRisks: string;
    feedbackTasks: string;
    adminUnread: (count: number) => string;
    recentFailedRuns: string;
    failedRuns: (count: number) => string;
    noRunQuery: string;
    sectionUnavailable: string;
  };
  adminUserSupport: {
    title: string;
    allowlist: string;
    unavailable: string;
    allowed: (id?: number | null) => string;
    notAllowed: string;
    loading: string;
    openFeedback: string;
    feedbackCount: (count: number) => string;
  };
  adminSystemPrompts: {
    loadCatalogFailed: string;
    loadDetailFailed: string;
    loadPoliciesFailed: string;
    defaultPolicyName: (displayName: string) => string;
    invalidSubjectId: string;
    nameRequired: string;
    subjectRequired: string;
    saved: string;
    saveFailed: string;
    disabledSuccess: string;
    enabledSuccess: string;
    statusUpdateFailed: string;
    deleted: string;
    deleteFailed: string;
    priorityUpdated: string;
    priorityUpdateFailed: string;
    invalidUserId: string;
    simulationFailed: string;
    ariaLabel: string;
    title: string;
    description: string;
    createPolicy: string;
    refresh: string;
    searchTypes: string;
    searchPlaceholder: string;
    category: string;
    allCategories: string;
    typeNavigation: string;
    policyCount: (count: number) => string;
    codeDefault: string;
    loading: string;
    codeRevision: string;
    snapshotScope: string;
    policies: string;
    priority: string;
    name: string;
    scope: string;
    status: string;
    actions: string;
    enabled: string;
    disabled: string;
    moveUp: string;
    moveDown: string;
    edit: string;
    delete: string;
    emptyPolicies: string;
    defaultSections: string;
    editOverrides: string;
    createOverrides: string;
    cancel: string;
    policyName: string;
    policyDescription: string;
    effectiveScope: string;
    allUsers: string;
    selectedSubjects: string;
    subjectType: string;
    user: string;
    group: string;
    subjectId: string;
    add: string;
    removeSubject: (type: string, id: number) => string;
    subjectLabel: (type: string, id: number) => string;
    overrideSection: string;
    characterCount: (count: number, max: number) => string;
    databaseOverride: string;
    restoreDefault: string;
    saving: string;
    savePolicy: string;
    simulateByUser: string;
    userId: string;
    simulate: string;
    simulationSummary: (source: string, policy: string, matchSource?: string | null) => string;
    simulationSectionSummary: (source: string, count: number) => string;
    deleteTitle: string;
    deleteDescription: (name: string) => string;
    scopeSummary: (users: number, groups: number) => string;
  };
  feedback: {
    openDialog: string;
    openDialogUnread: string;
    closeDialog: string;
    title: string;
    refresh: string;
    filterLabel: string;
    all: string;
    open: string;
    closed: string;
    newFeedback: string;
    createFormLabel: string;
    category: string;
    categoryBug: string;
    categorySuggestion: string;
    categoryOther: string;
    subject: string;
    content: string;
    cancel: string;
    create: string;
    creating: string;
    loading: string;
    detailLoading: string;
    threadListLabel: string;
    selectThread: string;
    backToList: string;
    untitled: string;
    replyContent: string;
    replyPlaceholder: string;
    sendReply: string;
    replyAndReopen: string;
    sending: string;
    user: string;
    administrator: string;
    listLoadFailed: string;
    detailLoadFailed: string;
    markReadFailed: string;
    createFailed: string;
    replyFailed: string;
  };
  adminUsers: {
    ariaLabel: string;
    title: string;
    searchPlaceholder: string;
    statusAll: string;
    statusActive: string;
    statusDisabled: string;
    statusDeleted: string;
    refresh: string;
    loading: string;
    empty: string;
    loadFailed: string;
    forbidden: string;
    backHome: string;
    detailAriaLabel: string;
    id: string;
    email: string;
    displayName: string;
    roles: string;
    groups: string;
    status: string;
    createdAt: string;
    lastLoginAt: string;
    updatedAt: string;
    deletedAt: string;
    deletedBy: string;
    actions: string;
    disable: string;
    restore: string;
    delete: string;
    confirm: string;
    confirmDisableTitle: string;
    confirmRestoreTitle: string;
    confirmDeleteTitle: string;
    confirmDeleteDescription: string;
    operationFailed: string;
    operationSucceeded: string;
    resetPassword: string;
    confirmPasswordResetTitle: string;
    confirmPasswordResetDescription: string;
    temporaryPasswordTitle: string;
    temporaryPasswordNotice: string;
    temporaryPasswordExpiresAt: string;
    copyTemporaryPassword: string;
    temporaryPasswordCopied: string;
  };
  adminGroups: {
    title: string;
    detailTitle: string;
    create: string;
    edit: string;
    delete: string;
    deleting: string;
    refresh: string;
    searchPlaceholder: string;
    memberSearchPlaceholder: string;
    status: string;
    statusAll: string;
    statusActive: string;
    statusDisabled: string;
    name: string;
    code: string;
    description: string;
    activeMembers: string;
    createdAt: string;
    updatedAt: string;
    actions: string;
    loading: string;
    loadFailed: string;
    detailLoadFailed: string;
    empty: string;
    total: (count: number) => string;
    editGroup: (name: string) => string;
    deleteGroup: (name: string) => string;
    deleteRequiresDisabled: string;
    deleteTitle: string;
    deleteDescription: (name: string, code: string) => string;
    deleteFailed: string;
    createTitle: string;
    editTitle: string;
    createDescription: string;
    editDescription: string;
    codeInvalid: string;
    nameRequired: string;
    saveFailed: string;
    saving: string;
    save: string;
    backToGroups: string;
    disabledCannotAdd: string;
    membersTitle: string;
    membersLoadFailed: string;
    membersLoading: string;
    membersEmpty: string;
    memberUser: string;
    email: string;
    accountStatus: string;
    joinedAt: string;
    expiresAt: string;
    expiresAtOptional: string;
    neverExpires: string;
    userStatuses: Record<AuthUserStatus, string>;
    remove: string;
    removing: string;
    removeMember: (name: string) => string;
    removeMemberTitle: string;
    removeMemberDescription: (user: string, group: string) => string;
    memberRemoveFailed: string;
    addMembers: string;
    addMembersTitle: string;
    addMembersDescription: (code: string) => string;
    userSearchPlaceholder: string;
    userSearching: string;
    userSearchFailed: string;
    userSearchEmpty: string;
    alreadyMember: string;
    expiryInvalid: string;
    noUsersSelected: string;
    selectedUsers: (count: number, names: string[]) => string;
    addSelected: (count: number) => string;
    addingMembers: string;
    memberAddFailed: string;
    addCompleted: string;
    addResult: (added: number, updated: number, failed: number) => string;
    userGroupsTitle: string;
    userGroupsDescription: string;
    addToGroup: string;
    addToGroupTitle: string;
    addToGroupDescription: (user: string) => string;
    targetGroup: string;
    selectGroup: string;
    noAvailableGroups: string;
    noUserGroups: string;
    expiresOn: (date: string) => string;
  };
  adminAi: {
    ariaLabel: string;
    title: string;
    settingsTitle: string;
    globalStatus: string;
    enabled: string;
    disabled: string;
    defaultDailyRequestLimit: string;
    lastUpdated: string;
    editLimit: string;
    save: string;
    saving: string;
    refresh: string;
    limitHint: string;
    limitInvalid: string;
    settingsLoadFailed: string;
    settingsUpdateFailed: string;
    usageLoadFailed: string;
    pricingLoadFailed: string;
    priceSaveFailed: string;
    confirm: string;
    cancel: string;
    confirmEnableTitle: string;
    confirmEnableDescription: string;
    confirmDisableTitle: string;
    confirmDisableDescription: string;
    usageTab: string;
    pricingTab: string;
    usageTitle: string;
    pricingTitle: string;
    today: string;
    last7Days: string;
    last30Days: string;
    customRange: string;
    from: string;
    to: string;
    userId: string;
    provider: string;
    model: string;
    purpose: string;
    source: string;
    all: string;
    apply: string;
    clear: string;
    byUser: string;
    byModel: string;
    bySource: string;
    admittedEntryRequests: string;
    modelCalls: string;
    inputTokens: string;
    cachedTokens: string;
    outputTokens: string;
    totalTokens: string;
    estimatedCost: string;
    estimatedAtCurrentPrices: string;
    unpricedCalls: string;
    unpricedTokens: string;
    noResults: string;
    actions: string;
    viewUser: string;
    filterUser: string;
    active: string;
    inactive: string;
    requestQuota: string;
    accountStatus: string;
    priceStatus: string;
    unpriced: string;
    priced: string;
    unpricedModels: string;
    lastSeenAt: string;
    addPrice: string;
    configurePrice: string;
    editPrice: string;
    providerRequired: string;
    modelRequired: string;
    inputPricePerMillion: string;
    cachedInputPricePerMillion: string;
    outputPricePerMillion: string;
    costMultiplier: string;
    priceEnabled: string;
    createPrice: string;
    updatePrice: string;
    priceDialogTitleCreate: string;
    priceDialogTitleEdit: string;
    priceInvalid: string;
    disablePrice: string;
    enablePrice: string;
    confirmDisablePriceTitle: string;
    confirmDisablePriceDescription: string;
    historicalPriceNotice: string;
    updatedBy: string;
    updatedAt: string;
    auditTitle: string;
    auditDescription: string;
    auditLoadFailed: string;
    auditRunLoadFailed: string;
    auditStepLoadFailed: string;
    auditToolResultLoadFailed: string;
    auditRefresh: string;
    auditFrom: string;
    auditTo: string;
    auditScenario: string;
    auditTaskId: string;
    auditTurnId: string;
    auditStatus: string;
    auditFinishReason: string;
    auditAttempt: string;
    auditRunId: string;
    auditMinCachedTokens: string;
    auditMaxCachedTokens: string;
    auditMinCacheRatio: string;
    auditMaxCacheRatio: string;
    auditSort: string;
    auditSortDirection: string;
    auditSortRequestedAt: string;
    auditSortOverBudget: string;
    auditSortCacheRatio: string;
    auditSortDescending: string;
    auditSortAscending: string;
    auditStatistics: string;
    auditStatisticsRuns: string;
    auditStatisticsOverBudget: string;
    auditStatisticsCache: string;
    auditStatisticsCompaction: string;
    auditFilter: string;
    auditClear: string;
    auditOnlyWithTools: string;
    auditOnlyCompacted: string;
    auditOnlyOverBudget: string;
    auditOnlyProviderError: string;
    auditNoResults: string;
    auditTime: string;
    auditRun: string;
    auditUser: string;
    auditProviderModel: string;
    auditSteps: string;
    auditTools: string;
    auditEstimateBudget: string;
    auditActualInput: string;
    auditCached: string;
    auditOutputTokens: string;
    auditReasoningTokens: string;
    auditTotalTokens: string;
    auditCompaction: string;
    auditState: string;
    auditOpen: string;
    auditBackToRuns: string;
    auditTimeline: string;
    auditSessionTurns: string;
    auditRequest: string;
    auditMessages: string;
    auditRawJson: string;
    auditOverview: string;
    auditToolCalls: string;
    auditNoSnapshot: string;
    auditMessageCount: string;
    auditToolsCount: string;
    auditFinalEstimate: string;
    auditAssemblyEstimate: string;
    auditRemainingBudget: string;
    auditCacheRatio: string;
    auditDuration: string;
    auditBudgetStatus: string;
    auditFailedTools: string;
    auditUncachedInput: string;
    auditStartedAt: string;
    auditMessageRole: string;
    auditMessageRoles: string;
    auditMessageSource: string;
    auditMessageSection: string;
    auditMessageToolCallId: string;
    auditMessageCharacters: string;
    auditMessageTokenEstimate: string;
    auditMessageContent: string;
    auditHistoricalMessages: string;
    auditNoHistoricalMessages: string;
    auditToolSchema: string;
    auditToolSchemaCount: string;
    auditToolSchemaEstimate: string;
    auditToolName: string;
    auditToolDescription: string;
    auditToolParameters: string;
    auditToolArguments: string;
    auditToolResult: string;
    auditToolResultPreview: string;
    auditToolResultStorageMode: string;
    auditToolResultReference: string;
    auditCompactionBefore: string;
    auditCompactionAfter: string;
    auditCompactionActions: string;
    auditRunAttempts: string;
    auditError: string;
    auditViewContent: string;
    auditContentUnavailable: string;
    auditPrevious: string;
    auditNext: string;
  };
  adminMonitoring: {
    ariaLabel: string;
    title: string;
    serviceHealth: string;
    apiService: string;
    status: string;
    healthy: string;
    unavailable: string;
    checking: string;
    lastChecked: string;
    notChecked: string;
    refresh: string;
    loadFailed: string;
  };
  databaseBackup: {
    ariaLabel: string;
    title: string;
    backupTitle: string;
    download: string;
    downloading: string;
    restoreTitle: string;
    restoreWarning: string;
    chooseFile: string;
    noFileSelected: string;
    clearFile: string;
    overwrite: string;
    restoring: string;
    confirmTitle: string;
    confirmDescription: string;
    cancel: string;
    confirmOverwrite: string;
    downloadFailed: string;
    restoreFailed: string;
  };
  sessionMonitoring: {
    ariaLabel: string;
    title: string;
    lastRefreshed: string;
    notRefreshed: string;
    refresh: string;
    validSessions: string;
    activeSessions: string;
    validUsers: string;
    searchPlaceholder: string;
    search: string;
    activityLabel: string;
    activityFilters: Record<'ALL' | 'ACTIVE' | 'IDLE', string>;
    user: string;
    userStatus: string;
    sessionStatus: string;
    createdAt: string;
    lastAccessedAt: string;
    expiresAt: string;
    currentSession: string;
    actions: string;
    userStatuses: Record<'ACTIVE' | 'DISABLED' | 'DELETED', string>;
    activities: Record<'ACTIVE' | 'IDLE', string>;
    empty: string;
    loading: string;
    loadFailed: string;
    operationFailed: string;
    alreadyOffline: string;
    revoke: string;
    revokeSession: (user: string) => string;
    currentSessionHint: string;
    confirmTitle: string;
    confirmDescription: (user: string, lastAccessedAt: string) => string;
    connectionNotice: string;
    revoking: string;
  };
  sessionPolicy: {
    ariaLabel: string;
    title: string;
    pageDescription: string;
    create: string;
    createTitle: string;
    editTitle: string;
    dialogDescription: string;
    refresh: string;
    searchPlaceholder: string;
    search: string;
    priority: string;
    name: string;
    description: string;
    scope: string;
    allUsers: string;
    selectedSubjects: string;
    scopeSummary: (userCount: number, groupCount: number) => string;
    subjectType: string;
    subjectTypes: Record<'USER' | 'GROUP', string>;
    subjectSearchPlaceholder: string;
    subjectLoading: string;
    subjectLoadFailed: string;
    savedUserSubject: string;
    savedGroupSubject: string;
    removeSubject: (label: string) => string;
    remove: string;
    subjectEmpty: string;
    subjectRequired: string;
    maxSessions: string;
    absoluteTimeout: string;
    absoluteTimeoutSeconds: string;
    duration: (value: string, unit: 'days' | 'hours' | 'minutes' | 'seconds') => string;
    status: string;
    statusAll: string;
    statuses: Record<'ENABLED' | 'DISABLED', string>;
    updatedAt: string;
    actions: string;
    empty: string;
    loading: string;
    loadFailed: string;
    nameRequired: string;
    valueInvalid: string;
    save: string;
    saving: string;
    saveFailed: string;
    saveSucceeded: string;
    editPolicy: (name: string) => string;
    delete: string;
    deleting: string;
    deleteTitle: string;
    deleteDescription: (name: string) => string;
    deletePolicy: (name: string) => string;
    deleteFailed: string;
    deleteSucceeded: string;
  };
  learningPlanPolicy: {
    ariaLabel: string;
    title: string;
    pageDescription: string;
    create: string;
    createTitle: string;
    editTitle: string;
    dialogDescription: string;
    refresh: string;
    priority: string;
    name: string;
    description: string;
    scope: string;
    allUsers: string;
    selectedSubjects: string;
    scopeSummary: (userCount: number, groupCount: number) => string;
    subjectType: string;
    subjectTypes: Record<'USER' | 'GROUP', string>;
    subjectSearchPlaceholder: string;
    subjectLoading: string;
    subjectLoadFailed: string;
    savedUserSubject: string;
    savedGroupSubject: string;
    removeSubject: (label: string) => string;
    remove: string;
    subjectEmpty: string;
    subjectRequired: string;
    maxSavedPlans: string;
    dailyDraftCreationLimit: string;
    draftRetentionDays: string;
    retentionValue: (days: string) => string;
    status: string;
    statuses: Record<'ENABLED' | 'DISABLED', string>;
    updatedAt: string;
    actions: string;
    empty: string;
    loading: string;
    loadFailed: string;
    nameRequired: string;
    valueInvalid: string;
    save: string;
    saving: string;
    saveFailed: string;
    saveSucceeded: string;
    moveUpPolicy: (name: string) => string;
    moveDownPolicy: (name: string) => string;
    orderFailed: string;
    orderSucceeded: string;
    editPolicy: (name: string) => string;
    delete: string;
    deleting: string;
    deleteTitle: string;
    deleteDescription: (name: string) => string;
    deletePolicy: (name: string) => string;
    deleteFailed: string;
    deleteSucceeded: string;
  };
  adminUserAi: {
    title: string;
    loading: string;
    loadFailed: string;
    policyLoadFailed: string;
    usageLoadFailed: string;
    effectiveStatus: string;
    normal: string;
    globalDisabled: string;
    userPaused: string;
    inherited: string;
    paused: string;
    pauseUser: string;
    resumeUser: string;
    defaultLimit: string;
    overrideLimit: string;
    effectiveLimit: string;
    saveOverride: string;
    restoreInheritance: string;
    todayEntryRequests: string;
    todayTokens: string;
    todayEstimatedCost: string;
    last7DaysEstimatedCost: string;
    last30DaysEstimatedCost: string;
    viewFullUsage: string;
    confirmPauseTitle: string;
    confirmPauseDescription: string;
    confirmResumeTitle: string;
    confirmResumeDescription: string;
    limitInvalid: string;
    saveFailed: string;
  };
  betaAccess: {
    ariaLabel: string;
    title: string;
    searchPlaceholder: string;
    search: string;
    refresh: string;
    loading: string;
    empty: string;
    loadFailed: string;
    forbidden: string;
    backHome: string;
    allowlistSetting: string;
    enabled: string;
    disabled: string;
    batchAdd: string;
    batchPlaceholder: string;
    batchLimit: string;
    emailInputRequired: string;
    add: string;
    saving: string;
    batchResult: string;
    batchSummary: (added: number, existing: number, invalid: number) => string;
    addStatus: Record<'ADDED' | 'EXISTING' | 'INVALID', string>;
    email: string;
    registration: string;
    registered: string;
    unregistered: string;
    userStatus: string;
    userStatuses: Record<'ACTIVE' | 'DISABLED' | 'DELETED', string>;
    createdBy: string;
    createdAt: string;
    actions: string;
    remove: string;
    removeEmail: (email: string) => string;
    confirm: string;
    confirmEnableTitle: string;
    confirmEnableDescription: string;
    confirmEnableEmptyDescription: string;
    confirmDisableTitle: string;
    confirmDisableDescription: string;
    confirmRemoveTitle: string;
    confirmRegisteredRemoval: (email: string) => string;
    confirmRemoval: (email: string) => string;
    sessionRevocationWarning: string;
    operationFailed: string;
  };
  passwordChange: {
    ariaLabel: string;
    title: string;
    description: string;
    newPassword: string;
    confirmPassword: string;
    submit: string;
    submitting: string;
    logout: string;
    passwordMismatch: string;
    passwordTooShort: string;
    failed: string;
  };
  common: {
    cancel: string;
    create: string;
    delete: string;
    deleting: string;
    view: string;
    previousPage: string;
    nextPage: string;
    pageStatus: (page: number, totalPages: number) => string;
    empty: string;
    week: (count: number) => string;
    hoursPerWeek: (count: number) => string;
    characterCount: (current: number, max: number) => string;
    byteCount: (current: number, max: number) => string;
    itemInputLimit: (count: number, maxCount: number, maxChars: number) => string;
    created: string;
    close: string;
  };
  language: {
    label: string;
    zhCN: string;
    enUS: string;
  };
  aiPreference: {
    title: string;
    subtitle: string;
    loading: string;
    loadFailed: string;
    saveFailed: string;
    saved: string;
    saving: string;
    coachStyle: string;
    coachStyleLabels: Record<'GUIDED' | 'DIRECT', string>;
    coachStyleDescriptions: Record<'GUIDED' | 'DIRECT', string>;
  };
  settingsPage: {
    ariaLabel: string;
    kicker: string;
    title: string;
    subtitle: string;
    learningTitle: string;
    learningDescription: string;
    currentCoach: string;
    reviewTitle: string;
    reviewDescription: string;
    reviewLoading: string;
    reviewLoadFailed: string;
    reviewSaveFailed: string;
    reviewSaving: string;
    advancedReviewTitle: string;
    advancedReviewDescription: string;
    fsrsParameters: string;
    desiredRetention: string;
    desiredRetentionDescription: string;
    dailyNewLimit: string;
    dailyNewLimitDescription: string;
    dailyLearningLimit: string;
    dailyLearningLimitDescription: string;
    dailyReviewLimit: string;
    dailyReviewLimitDescription: string;
    maximumIntervalDays: string;
    maximumIntervalDaysDescription: string;
    enableFuzzing: string;
    enableFuzzingDescription: string;
    helpSuffix: string;
    accountTitle: string;
    accountDescription: string;
    signedInAs: string;
    activeStatus: string;
    passwordTitle: string;
    passwordNotConfigured: string;
    passwordConfigured: string;
    passwordEmailUnavailable: string;
    setPassword: string;
    changePassword: string;
    passwordDialogSetTitle: string;
    passwordDialogChangeTitle: string;
    passwordDialogSetDescription: string;
    passwordDialogChangeDescription: string;
    currentPassword: string;
    newPassword: string;
    confirmNewPassword: string;
    passwordMinimumLength: string;
    showPassword: string;
    hidePassword: string;
    passwordUpdating: string;
    passwordUpdate: string;
    passwordMismatch: string;
    passwordTooShort: string;
    passwordUpdateFailed: string;
    passwordCreated: string;
    passwordUpdated: string;
  };
  myPage: {
    profileKicker: string;
    title: string;
    coachPanelEyebrow: string;
    abilityPanelEyebrow: string;
    selectedCoach: string;
    dataPending: string;
    noData: string;
    statEvaluatedTags: string;
    statAverageScore: string;
    statReviewedProblems: string;
    statPrimaryStrength: string;
    diagnosisSummaryTitle: string;
    currentStrength: string;
    currentStrengthDetail: (label: string, score: string, reviewedProblems: number) => string;
    breakthroughAdvice: string;
    breakthroughAdviceDetail: (label: string) => string;
    abilitySummaryTitle: string;
    strongestTag: string;
    topAbilities: string;
    selectedAbilityTags: string;
    expandAbilityProfile: string;
    abilityDetailTitle: string;
    abilityDetailSubtitle: (max: number) => string;
    closeAbilityDetail: string;
    selectedTagCount: (selected: number, max: number) => string;
    minimumTagCount: (min: number) => string;
    minimumSelectionNotice: (min: number) => string;
    maximumSelectionNotice: (max: number) => string;
    removeSelectedTag: (label: string) => string;
    abilityReplacementHint: string;
    abilityHeatmapEyebrow: string;
    abilityHeatmapTitle: string;
    abilityHeatmapHint: string;
    addHeatmapTag: (label: string) => string;
    selectedHeatmapTag: (label: string) => string;
    removeHeatmapTag: (label: string) => string;
    catalogProblemsValue: (count: number) => string;
    noTopAbilities: string;
    tagCoverage: (reviewed: number, total: number) => string;
    scoreValue: (score: string) => string;
    reviewedProblemsValue: (count: number) => string;
    memoryEyebrow: string;
    memoryTitle: string;
    memorySubtitle: string;
    memoryLoading: string;
    memoryLoadFailed: string;
    memoryEmpty: string;
    memoryUpdatedAt: (value: string) => string;
    openCitation: (displayNumber: number) => string;
    evidenceEyebrow: string;
    evidenceDrawerTitle: (displayNumber: number) => string;
    closeEvidenceDrawer: string;
    evidenceLoading: string;
    evidenceLoadFailed: string;
    evidenceEmpty: string;
    evidenceLoadMore: string;
    evidenceLoadingMore: string;
    evidenceEnd: string;
    evidenceRoles: {
      OBSERVED: string;
      PERSISTED: string;
      RESOLVED: string;
      REGRESSED: string;
      CONTRADICTS: string;
      DECLARED: string;
      CORRECTED: string;
    };
    reviewEvidenceSummary: (versionNo: number, score: number, passed: boolean) => string;
    viewReviewSubmission: string;
    messageEvidenceTitle: (role: 'DECLARED' | 'CORRECTED') => string;
  };
  home: {
    ariaLabel: string;
    kicker: string;
    title: string;
    titleHighlight: string;
    subtitle: string;
    generatePlan: string;
    startUsing: string;
    browseProblems: string;
    previewLabel: string;
    previewFocusLabel: string;
    previewFocusValue: string;
    previewTaskOne: string;
    previewTaskOneStatus: string;
    previewTaskTwo: string;
    previewTaskTwoStatus: string;
    previewTaskThree: string;
    previewTaskThreeStatus: string;
    companyStripLabel: string;
    companyStripTitle: string;
    reviewCardCta: string;
    capabilitiesKicker: string;
    capabilitiesTitle: string;
    featurePlanTitle: string;
    featurePlanDescription: string;
    featureProblemTitle: string;
    featureProblemDescription: string;
    featureAiTitle: string;
    featureAiDescription: string;
    loopKicker: string;
    loopTitle: string;
    loopLabel: string;
    stepPickTitle: string;
    stepPickDescription: string;
    stepPracticeTitle: string;
    stepPracticeDescription: string;
    stepExplainTitle: string;
    stepExplainDescription: string;
    stepReviewTitle: string;
    stepReviewDescription: string;
    ctaLabel: string;
    ctaTitle: string;
    ctaDescription: string;
    enterPlans: string;
    workspaceAriaLabel: string;
    workspaceKicker: string;
    workspaceTitle: string;
    workspaceSectionsLabel: string;
    recentPracticeTitle: string;
    recentPracticeEmpty: string;
    planPreviewTitle: string;
    planPreviewEmpty: string;
    reviewQueueTitle: string;
    reviewQueueEmpty: string;
    abilityMapTitle: string;
    abilityMapSubtitle: string;
    abilityLoading: string;
    abilityLoadFailed: string;
    abilityEmpty: string;
  };
  todayPack: {
    homeLoadFailed: string;
    reviewSummaryLoadFailed: string;
    loadingStatus: string;
    trainingStatusUnavailable: string;
    reviewStatusLoading: string;
    reviewStatusUnavailable: string;
    reviewDue: (count: number) => string;
    reviewUpcoming: (count: number, time: string) => string;
    todayCompleted: string;
    reviewStart: (count: number) => string;
    reviewSchedule: string;
    homeAriaLabel: string;
    trainingEntryAriaLabel: string;
    todayPack: string;
    startTraining: string;
    activePlanRhythm: (daily: number, daysPerWeek: number, remaining: number) => string;
    noPlanGuidance: string;
    startTodayTraining: string;
    choosePlan: string;
    reviewEntryAriaLabel: string;
    reviewCenter: string;
    reviewDescription: string;
    diagnosisTitle: string;
    diagnosisDescription: string;
    viewFullProfile: string;
    abilityUnavailable: string;
    averageAbility: string;
    currentStrength: string;
    none: string;
    strengthEvidence: (count: number) => string;
    nextBreakthrough: string;
    breakthroughFallback: string;
    breakthroughAdvice: (label: string) => string;
    weeklyRhythm: string;
    dailyTraining: string;
    weeklyTarget: string;
    remainingProblems: string;
    problemCount: (count: number) => string;
    managePlan: string;
    noActivePlan: string;
    noActivePlanDescription: string;
    viewPlans: string;
    packLoadFailed: string;
    resetConfirm: string;
    packResetFailed: string;
    loadingPack: string;
    packAriaLabel: string;
    activePackSummary: (title: string, startDate: string, daily: number, localDate: string) => string;
    packIntroduction: string;
    restart: string;
    restartHelpAriaLabel: string;
    restartHelp: string;
    emptyPlanTitle: string;
    emptyPlanDescription: string;
    planCompletedTitle: string;
    planCompletedDescription: string;
    doneTodayTitle: string;
    doneTodayDescription: string;
    stopToday: string;
    nextPack: string;
    futurePack: (date: string) => string;
    sectionProblemCount: (count: number) => string;
    carryover: (days: number, date: string) => string;
    scheduledDate: (date: string) => string;
    emptyDay: string;
    packTotal: (count: number) => string;
    backToday: string;
    statusNoActivePlan: string;
    statusPlanCompleted: string;
    statusDoneWithNext: (date: string) => string;
    statusDone: string;
    statusDue: (count: number) => string;
    statusEmpty: string;
  };
  reviewCenter: {
    sourceLabels: Record<ReviewCardSource, string>;
    ratingLabels: Record<ReviewRating, string>;
    ratingDescriptions: Record<ReviewRating, string>;
    fsrsStateLabels: Record<'LEARNING' | 'REVIEW' | 'RELEARNING', string>;
    loadTodayReview: string;
    startTodayReview: (count: number) => string;
    availableAt: (time: string) => string;
    todayCompleted: string;
    cardLoadFailed: string;
    summaryLoadFailed: string;
    archiveUpdateFailed: string;
    detailLoadFailed: string;
    title: string;
    overviewAriaLabel: string;
    remainingToday: string;
    reviewProblems: string;
    mistakes: string;
    filtersAriaLabel: string;
    searchPlaceholder: string;
    mistakesOnly: string;
    refreshCards: string;
    loadingCards: string;
    emptyCards: string;
    codeReviewTimelineAriaLabel: string;
    codeReviewTimelinePointAriaLabel: (version: number, score: string, language: string, time: string) => string;
    codeReviewTimelineScore: (version: number, score: string) => string;
    codeReviewTimelineFeedback: (feedback: string) => string;
    codeReviewTimelineFallbackFeedback: string;
    codeReviewTimelineManualAriaLabel: string;
    codeReviewTimelineManualTitle: string;
    codeReviewTimelineManualDescription: string;
    codeReviewTimelineUnavailable: string;
    lastRating: (rating: string) => string;
    forgottenCount: (count: number) => string;
    viewCardDetail: (title: string) => string;
    viewDetail: string;
    restoreReview: string;
    removeFromReview: string;
    closeDetail: string;
    loadingDetail: string;
    recentHistory: string;
    intervalChange: (before: number, after: number) => string;
    noHistory: string;
    dueUnknown: string;
    overdue: (days: number) => string;
    dueToday: string;
    reviewTomorrow: string;
    reviewInDays: (days: number) => string;
    queueLoadFailed: string;
    ratingSubmitFailed: string;
    discardNoteConfirm: string;
    backToReviewCenter: string;
    spacedReview: string;
    unknownDifficulty: string;
    preparingQueue: string;
    queueCompleted: string;
    loadingStatement: string;
    fullStatementAriaLabel: string;
    history: string;
    recentCount: (count: number) => string;
    historyAfterRating: string;
    resultAriaLabel: string;
    nextReview: (value: string) => string;
    ratingAriaLabel: string;
    nextProblem: string;
    calculating: string;
    minutesLater: (minutes: number) => string;
    reviewLater: string;
    ratingButtonAriaLabel: (label: string, description: string, interval: string) => string;
  };
  problemNotes: {
    loadFailed: string;
    saveFailed: string;
    loading: string;
    unsaved: string;
    existing: string;
    empty: string;
    title: string;
    updatedAt: (value: string) => string;
    loadingDetail: string;
    retry: string;
    coachSummary: string;
    coachSummaryPresent: string;
    coachSummaryNotGenerated: string;
    coachSummaryEmpty: string;
    coachSummaryHint: string;
    conflict: string;
    reload: string;
    saving: string;
    save: string;
    inputLimitExceeded: string;
    coreIdea: string;
    dataStructures: string;
    customDataStructures: string;
    dataStructureNotes: string;
    dataStructureNotesPlaceholder: string;
    algorithms: string;
    customAlgorithms: string;
    algorithmNotes: string;
    algorithmNotesPlaceholder: string;
    timeComplexity: string;
    spaceComplexity: string;
    edgeCases: string;
    complexityEmpty: string;
    customValueAriaLabel: (label: string) => string;
    customValuePlaceholder: string;
    dataStructureLabels: Record<ProblemDataStructureKey, string>;
    algorithmLabels: Record<ProblemAlgorithmKey, string>;
    complexityLabels: Record<ProblemComplexityKey, string>;
  };
  problems: {
    ariaLabel: string;
    searchLabel: string;
    searchPlaceholder: string;
    difficulty: string;
    difficultyFilter: string;
    allDifficulty: string;
    company: string;
    companyFilter: string;
    allCompanies: string;
    role: string;
    roleFilter: string;
    allRoles: string;
    recencyBucket: string;
    recencyBucketFilter: string;
    allRecencyBuckets: string;
    sort: string;
    sortFilter: string;
    sortFrontendAsc: string;
    sortCompanyFrequencyDesc: string;
    companySignalBadge: string;
    cnOnlyBadge: string;
    listTitle: string;
    totalCount: (count: number) => string;
    loadingList: string;
    emptyList: string;
    previousPage: string;
    nextPage: string;
    loadingDetail: string;
    recommendationReason: string;
    sampleInput: string;
    pythonTemplate: string;
    selectProblem: string;
    listLoadFailed: string;
    filtersLoadFailed: string;
    detailLoadFailed: string;
  };
  learningPlans: {
    ariaLabel: string;
    detailAriaLabel: string;
    createAriaLabel: string;
    listLoadFailed: string;
    detailLoadFailed: string;
    deleteFailed: string;
    confirmDelete: string;
    activateConfirm: string;
    activateFailed: string;
    currentActive: string;
    todayPack: string;
    activating: string;
    activate: string;
    todayPackProblem: string;
    loadingDetail: string;
    loadingPracticeChat: string;
    overviewTitle: string;
    overviewDescription: string;
    newPlan: string;
    overviewStats: string;
    total: string;
    latestCreated: string;
    listTitle: string;
    totalPlans: (count: number) => string;
    emptyTitle: string;
    emptyDescription: string;
    planParameters: string;
    planProgressCount: (completed: number, total: number) => string;
    planProgressAriaLabel: (completed: number, total: number, percent: number) => string;
    planProgressHelper: string;
    viewPlan: (title: string) => string;
    deletePlan: (title: string) => string;
    currentRhythm: string;
    unspecified: string;
    backToList: string;
    backToPlans: string;
    backToPlanDetail: string;
    backToPracticeChat: string;
    backToLearnerProfile: string;
    learningPlanEyebrow: string;
    practiceChatEyebrow: string;
    generateStart: string;
    generateFailed: string;
    followUpFailed: string;
    saveFailed: string;
    revisionInstructionLabel: string;
    reviseDraft: string;
    revisionFailed: string;
    extensionEntryLabel: string;
    generateExtension: string;
    pendingExtensionTitle: string;
    reviseExtensionLabel: string;
    reviseExtension: string;
    applyExtension: string;
    discardExtension: string;
    extensionFailed: string;
    extensionApplyFailed: string;
    createTitle: string;
    generatePlan: string;
    generateDraft: string;
    generating: string;
    createMode: string;
    createWithAi: string;
    createFromTemplate: string;
    templateLoading: string;
    templateDetailLoading: string;
    templateLoadFailed: string;
    templateDetailLoadFailed: string;
    templateViewContent: string;
    templateViewContentFor: (title: string) => string;
    templateDetailEyebrow: string;
    templateOverview: string;
    templateGoal: string;
    templatePhaseRoute: string;
    templatePhaseRouteTitle: string;
    templatePhaseSummary: (weeks: number, problems: number) => string;
    templateFitEyebrow: string;
    templateFitTitle: string;
    templatePrerequisites: string;
    templateRecommendedFor: string;
    templateNotRecommendedFor: string;
    templateSource: string;
    templateOpenSource: string;
    templateEmpty: string;
    templateGenerateStart: string;
    templateGenerateFailed: string;
    templateGenerateDraft: string;
    templateSelected: string;
    templateCatalog: string;
    templateCatalogRecommended: string;
    templateCatalogSystematicLearning: string;
    templateCatalogInterviewPrep: string;
    templateCatalogTopicBreakthrough: string;
    templateCatalogLanguageAndRole: string;
    templateCatalogEmpty: string;
    templateRecommendedBadge: string;
    templateDefaultRhythm: (weeks: number, hours: number) => string;
    templateRouteSummary: (problems: number, weeks: number, hours: number) => string;
    templateRhythm: string;
    dailyProblemCount: string;
    trainingDaysPerWeek: string;
    rhythmEstimateLine: (problems: number, weeks: number) => string;
    standardRhythmTitle: string;
    standardRhythmMainLine: (dailyProblems: number, trainingDays: number, recommendedWeeks: number) => string;
    standardRhythmReason: (problems: number, recommendedWeeks: number, trainingDays: number) => string;
    currentRhythmEstimateLine: (
      dailyProblems: number,
      trainingDays: number,
      problems: number,
      weeks: number,
    ) => string;
    standardRhythmRemainingLine: (remainingProblems: number, weeks: number) => string;
    rhythmFasterThanStandard: (weeks: number) => string;
    rhythmSlowerThanStandard: (weeks: number) => string;
    rhythmSameAsStandard: string;
    rhythmConfigLine: (dailyProblems: number, trainingDays: number) => string;
    totalProblemCountLine: (problems: number) => string;
    remainingWeeksLine: (weeks: number) => string;
    problemCount: (problems: number) => string;
    adjustRhythm: string;
    adjustedRhythm: string;
    saveRhythm: string;
    savingRhythm: string;
    rhythmUpdateFailed: string;
    rhythmLabels: Record<'RECOMMENDED' | 'RELAXED' | 'SPRINT', string>;
    rhythmCompletionLine: (weeks: number, problems: number) => string;
    rhythmWeeklyTimeLine: (hours: number, minDays: number, maxDays: number) => string;
    rhythmDailyLine: (minProblems: number, maxProblems: number, hasReview: boolean) => string;
    rhythmScopeLabels: Record<'FULL_ROUTE' | 'FULL_ROUTE_WITH_REVIEW_BUFFER' | 'FULL_ROUTE_FAST' | 'FIT_USER_BUDGET', string>;
    templateSourceCommit: (commit: string) => string;
    templateProblemStats: (matched: number, missing: number, total: number) => string;
    templateMissingNotice: (missing: number) => string;
    templateDurationTooShort: (minimumWeeks: number) => string;
    templateTargetAudience: string;
    templateExpectedOutcome: string;
    scenario: string;
    duration: string;
    durationInput: string;
    weeklyHours: string;
    aiBudgetHint: (weeks: number, hours: number, capacity: number) => string;
    level: string;
    programmingLanguage: string;
    topicPreferences: string;
    objective: string;
    additionalConstraints: string;
    personalizationEnabled: string;
    validationPositiveIntegers: string;
    validationNumericRange: (maxWeeks: number, maxHours: number) => string;
    validationInputLimit: string;
    validationTopicRequired: string;
    confirmDiscard: string;
    difficultyDistribution: string;
    distributionValueText: (label: string, easy: number, medium: number, hard: number) => string;
    easyPercent: (value: number) => string;
    mediumPercent: (value: number) => string;
    hardPercent: (value: number) => string;
    draftQuestion: string;
    followUpAnswer: string;
    sendFollowUp: string;
    draftPreview: string;
    goalSummary: string;
    regenerateByGoal: string;
    editGoalSummary: string;
    savePlan: string;
    draftUnavailableFailed: string;
    draftUnavailable: string;
    restartWizard: string;
    previewDuration: string;
    previewLevel: string;
    previewTime: string;
    executionSummary: (weeks: number, days: number, minutes: number) => string;
    nextTrainingPackage: string;
    nextTrainingPackageLine: (newProblems: number, minutes: number) => string;
    nextTrainingPackageReview: (reviewTask: string) => string;
    nextTrainingPackagePriority: string;
    planRouteSummary: (problems: number, weeks: number, hours: number, intensity: string) => string;
    loadSummary: string;
    loadIntensityLabels: Record<'RELAXED' | 'RECOMMENDED' | 'TIGHT' | 'OVERLOADED', string>;
    loadSummaryLine: (problems: number, load: number, capacity: number, intensity: string) => string;
    weeklyBuckets: string;
    weeklyBucketLine: (week: number, problems: number, load: number) => string;
    weeklyPlan: string;
    weeklyPlanTitle: (week: number, title: string) => string;
    weeklyBucketStats: (problems: number, load: number) => string;
    weeklyReviewBuffer: string;
    weeklyMissingProblem: string;
    phaseDetails: string;
    routeProgressTitle: string;
    routeProgressLine: (completed: number, total: number, percent: number) => string;
    estimatedCompletionDate: (date: string) => string;
    openProblemsLine: (open: number, skipped: number) => string;
    visibleStatusLabels: Record<'ON_TRACK' | 'NEEDS_REBALANCE' | 'PAUSED' | 'COMPLETED' | 'CLOSED_OUT', string>;
    startNextTrainingPackage: string;
    contractDateMovedEarlier: (date: string) => string;
    contractDateMovedLater: (date: string) => string;
    completionSummaryTitle: string;
    completionSummaryLine: (rate: number, days: number, completed: number, skipped: number, open: number) => string;
    weakTagsLabel: string;
    paceTitle: string;
    paceCurrentWeek: (current: number, total: number) => string;
    paceCurrentTarget: (problems: number, load: number) => string;
    paceCurrentCompleted: (completed: number) => string;
    paceStatusLabels: Record<'AHEAD' | 'ON_TRACK' | 'AT_RISK' | 'BEHIND', string>;
    paceLoadGap: (gap: number) => string;
    problemTraining: string;
    detailLoadProblemFailed: string;
    statementUnavailable: string;
    openLeetCode: string;
    leetcodeUnavailable: string;
    practiceLeetCodeGuidance: string;
    phaseFallback: (phaseIndex: number) => string;
    notStarted: string;
    inProgress: string;
    completed: string;
    skipped: string;
    markCompleted: string;
    practiceMoreActions: string;
    skipProblem: string;
    skipProblemConfirmTitle: string;
    skipProblemConfirmDescription: string;
    confirmSkipProblem: string;
    organizingThoughts: string;
    replyFailed: string;
    practiceSessionLoadFailed: string;
    practiceMessageFailed: string;
    practiceMessageBlocked: string;
    progressUpdateFailed: string;
    reviewHistory: string;
    reviewHistoryUnavailable: string;
    reviewEmptyTitle: string;
    reviewEmptyDescription: string;
    reviewLoading: string;
    reviewLoadFailed: string;
    reviewDetailLoading: string;
    reviewDetailLoadFailed: string;
    requestedReviewUnavailable: string;
    reviewPassed: string;
    reviewFailed: string;
    reviewToolRunning: string;
    reviewToolScoreSummary: (statusLabel: string, scoreText: string) => string;
    learnerProfileToolRunning: string;
    learnerProfileToolUpdated: string;
    learnerProfileToolNoChange: string;
    learnerProfileToolFailed: string;
    reviewVersionLabel: (versionNo: number) => string;
    reviewScoreText: (score: number, passScore?: number) => string;
    reviewPassScoreLabel: (passScore: number) => string;
    reviewNoReview: string;
    reviewCodeSnapshot: string;
    reviewScoreBreakdown: string;
    reviewScoreContribution: (score: number, maximum: number) => string;
    reviewScoreDimensions: Record<'correctness' | 'complexity' | 'edgeCases' | 'codeQuality' | 'problemFit', string>;
    reviewScoreLevels: Record<'excellent' | 'good' | 'needsImprovement' | 'weak', string>;
    reviewScoreDetailAction: (dimension: string) => string;
    reviewScoreTooltipTitle: (dimension: string, level: string, contribution: string) => string;
    reviewScoreDetailUnavailable: string;
    reviewScoreTimeComplexity: (value: string) => string;
    reviewScoreSpaceComplexity: (value: string) => string;
    reviewScoreExpectedComplexity: (value: string) => string;
    reviewScoreAnalysisBasis: Record<
      'SERVER_EXECUTION' | 'USER_REPORTED_EXECUTION' | 'STATIC_ANALYSIS' | 'INSUFFICIENT_CONTEXT',
      string
    >;
    reviewDeductionReasons: string;
    reviewImprovementSuggestions: string;
    completionGateFallback: string;
    completionRequiresPassedReview: string;
    completionGateMessages: Record<'NO_REVIEW' | 'LATEST_REVIEW_FAILED' | 'PASSED' | 'ALREADY_COMPLETED', string>;
    practiceComposerPlaceholderReview: string;
    coachSummarySave: string;
    coachSummaryReplace: string;
    coachSummaryApplying: string;
    coachSummaryApplyRetry: string;
    coachSummaryApplied: string;
    coachSummarySuperseded: string;
    coachSummaryApplyFailed: string;
    toolPermissionEyebrow: string;
    toolPermissionReviewTitle: string;
    toolPermissionReviewReason: string;
    toolPermissionProblem: string;
    toolPermissionContextWarning: string;
    toolPermissionCodePreview: string;
    toolPermissionEffectSummary: string;
    toolPermissionCountdownLabel: string;
    toolPermissionCountdownHint: string;
    toolPermissionExpired: string;
    toolPermissionExpiredHint: string;
    toolPermissionAllow: string;
    toolPermissionDeny: string;
    toolPermissionDecisionFailed: string;
    toolPermissionTimeoutNotice: string;
    chatMessages: string;
    coach: string;
    you: string;
    loadingStatement: string;
    sendMessage: string;
    composerLabel: string;
    composerPlaceholder: string;
    practiceMessageTooLong: (maxBytes: number) => string;
    send: string;
    waitingGenerate: string;
    generatingPlan: string;
    generationDone: string;
  };
  labels: {
    difficulties: Record<ProblemDifficulty | LearningPlanDifficultyPreference, string>;
    planStatus: Record<LearningPlanStatus, string>;
    levels: Record<LearningPlanLevel, string>;
    intents: Record<LearningPlanIntent, string>;
    planScenarios: Record<'INTERVIEW_SPRINT' | 'TOPIC_BREAKTHROUGH' | 'PRACTICE_GOAL' | 'MISTAKE_REVIEW' | 'LONG_TERM_LEARNING', string>;
    difficultyDistribution: {
      beginner: string;
      balanced: string;
      sprint: string;
    };
    topics: Record<string, string>;
  };
}

export const localeResources: Record<SupportedLocale, LocaleResources> = {
  'zh-CN': {
    app: {
      brandKicker: 'LEET MENTOR',
      brandName: 'Leet Mentor',
      mainNavigation: '主导航',
      loginStatus: '登录状态',
      checkingLogin: '检查登录状态',
      checkingLoginStatus: '正在检查登录状态...',
      loading: '正在加载',
      loginCheckFailed: '登录状态检查失败，请稍后重试。',
      retry: '重试',
      logout: '退出登录',
      loggingOut: '退出中',
      logoutFailed: '退出登录失败',
      switchToDarkMode: '切换为深色模式',
      switchToLightMode: '切换为浅色模式',
      unknownUser: (id) => `用户 #${id}`,
    },
    auth: {
      subtitle: '算法学习、刷题训练和 AI 训练方案生成工具',
      welcomeEyebrow: '面向算法学习者的 AI 训练工作台',
      welcomeTitle: '把刷题变成可持续的训练节奏',
      welcomeDescription: '让学习计划、题库练习、AI 讲解和错题复盘连成一个闭环，减少随机刷题，把注意力放回真正需要突破的知识点。',
      featurePlanTitle: 'AI 方案生成',
      featurePlanDescription: '按目标、时间和薄弱主题拆出阶段计划。',
      featurePracticeTitle: '题库训练',
      featurePracticeDescription: '围绕难度、标签和进度快速进入练习。',
      featureReviewTitle: '错题复盘',
      featureReviewDescription: '把讲解、代码反馈和历史记录沉淀下来。',
      previewTitle: '本周训练节奏',
      previewSubtitle: '示例学习流',
      previewFocusLabel: '当前重点',
      previewFocusValue: '动态规划 · 中等题',
      previewItems: [
        {
          title: '生成 4 周学习计划',
          detail: '根据面试冲刺目标安排每日主题。',
        },
        {
          title: '完成 12 道核心题',
          detail: '优先处理最长递增子序列和背包变体。',
        },
        {
          title: '复盘 3 次代码审查',
          detail: '记录边界条件、复杂度和可读性问题。',
        },
      ],
      emailAuthDivider: '或使用邮箱继续',
      socialAuthDivider: '或继续使用',
      supportContact: '内测期间如需帮助，请联系邀请人或项目部署方。',
      termsPrefix: '继续即表示你理解并同意',
      termsLabel: '服务条款',
      termsConnector: ' 和 ',
      privacyLabel: '隐私政策',
      failed: '登录失败，请重新尝试。',
      betaAccessDenied: '当前邮箱不在内测准入名单中。',
      googleLogin: '使用 Google 登录',
      githubLogin: '使用 GitHub 登录',
      emailLabel: '邮箱',
      emailPlaceholder: 'you@example.com',
      passwordLabel: '密码',
      passwordPlaceholder: '至少 8 个字符',
      displayNameLabel: '昵称',
      displayNamePlaceholder: '请输入昵称',
      passwordLogin: '邮箱登录',
      passwordRegister: '注册并登录',
      loggingIn: '登录中',
      registering: '注册中',
      showLogin: '已有账号，去登录',
      showRegister: '创建邮箱账号',
      loginModeTitle: '邮箱密码登录',
      registerModeTitle: '注册邮箱账号',
      oauthModeTitle: '选择登录方式',
      oauthModeDescription: '选择已关联的账号继续',
      loginAction: '登录',
      validationEmailRequired: '请输入邮箱。',
      validationPasswordRequired: '请输入密码。',
      validationDisplayNameRequired: '请输入昵称。',
    },
    nav: {
      home: '首页',
      my: '学习画像',
      settings: '设置',
      learningPlans: '方案',
      mistakes: '复习中心',
      problems: '题库',
      adminBetaAccess: '内测准入',
      adminUsers: '用户管理',
      adminUserGroups: '用户组管理',
      adminMonitoring: '系统监控',
      adminDatabaseBackup: '数据备份',
      adminSessions: '会话监控',
      adminSessionPolicies: '会话策略',
      adminLearningPlanPolicies: '学习计划策略',
      adminLearningPlanAiRevisionPolicies: '学习计划 AI 修订策略',
      adminSystemPrompts: '系统提示词',
      adminAi: 'AI 治理',
      feedback: '反馈',
      adminOverview: '概览',
      adminFeedback: '反馈',
      forbidden: '无权访问',
    },
    adminShell: {
      workspace: '管理后台',
      returnToLearning: '返回学习端',
      openNavigation: '打开管理导航',
      closeNavigation: '关闭管理导航',
      collapseNavigation: '折叠左侧栏',
      expandNavigation: '展开左侧栏',
      businessNavigation: '管理业务域',
      navigation: '管理导航',
      pageNavigation: '当前业务页面',
      expandSection: (label) => `展开${label}`,
      collapseSection: (label) => `收起${label}`,
      labels: {
        overview: '运营概览',
        access: '身份与访问',
        monitoring: '系统监控',
        systemStatus: '运行状态',
        databaseBackup: '数据备份',
        sessions: '会话监控',
        sessionPolicies: '会话策略',
        learningPlanPolicies: '学习计划策略',
        learningPlanAiRevisionPolicies: '学习计划 AI 修订策略',
        systemPrompts: '系统提示词',
        ai: 'AI 治理',
        aiPlatform: 'AI 平台',
        operations: '运营与支持',
        modelResources: '模型资源',
        costGovernance: '成本治理',
        aiProviders: 'Provider 与模型',
        aiRouting: '模型路由',
        aiUsage: '用量与成本',
        aiPricing: '模型定价',
        aiAudit: '请求审计',
        content: '内容管理',
        feedback: '反馈与支持',
        users: '用户管理',
        userGroups: '用户组管理',
        betaAccess: '内测准入',
        problems: '题库管理',
      },
    },
    adminFeedback: {
      listLoadFailed: '反馈列表加载失败', detailLoadFailed: '反馈详情加载失败', markReadFailed: '标记已读失败',
      replyFailed: '回复失败', statusUpdateFailed: '状态更新失败', requestFailed: '请求失败', title: '反馈管理',
      refresh: '刷新', status: '状态', allStatuses: '全部状态', category: '分类', allCategories: '全部分类',
      unreadOnly: '仅未读', unread: '未读', user: '用户', subject: '主题', updatedAt: '更新时间',
      untitled: '未命名反馈', close: '关闭', reopen: '重新打开', selectThread: '选择一条反馈进行处理。',
    },
    adminOverview: {
      loadFailed: '管理员概览加载失败', loading: '正在加载管理员概览...', title: '管理员概览', retry: '重试',
      generatedMeta: (date, zone) => `数据时间：${date} · 配额时区：${zone}`, refresh: '刷新', betaAccess: '内测准入',
      enabled: '已开启', disabled: '已关闭', betaSummary: (allowed, registered) => `${allowed} 人 / 已注册 ${registered} 人`,
      aiRuntime: 'AI 运行策略', aiRuntimeSummary: (limit) => `默认 ${limit} 次`, aiToday: '今日 AI 使用',
      entryRequests: '入口请求', success: '成功', failed: '失败', quotaRejected: '额度拒绝', modelCalls: '模型调用',
      estimatedCost: '按当前价格估算', unpricedCalls: (count) => `${count} 次未定价调用`, quotaRiskUsers: '额度风险用户',
      userFallback: (id) => `用户 ${id}`, noQuotaRisks: '暂无接近额度上限的用户。', feedbackTasks: '反馈待办',
      adminUnread: (count) => `管理员未读 ${count}`, recentFailedRuns: '最近失败 run',
      failedRuns: (count) => `${count} 个失败 run`, noRunQuery: '暂未提供 run 查询。', sectionUnavailable: '此区块暂不可用',
    },
    adminUserSupport: {
      title: '内测准入与支持', allowlist: '白名单', unavailable: '暂不可用',
      allowed: (id) => id ? `已在白名单（记录 ${id}）` : '已在白名单', notAllowed: '不在白名单',
      loading: '正在加载...', openFeedback: 'OPEN 反馈', feedbackCount: (count) => `${count} 条`,
    },
    adminSystemPrompts: {
      loadCatalogFailed: '无法加载系统提示词目录。', loadDetailFailed: '无法加载提示词详情。',
      loadPoliciesFailed: '无法加载策略。', defaultPolicyName: (displayName) => `${displayName} 配置`,
      invalidSubjectId: '请输入有效的用户或用户组 ID。', nameRequired: '策略名称不能为空。',
      subjectRequired: '指定范围至少需要一个用户或用户组。', saved: '策略已保存。', saveFailed: '无法保存策略。',
      disabledSuccess: '策略已禁用。', enabledSuccess: '策略已启用。', statusUpdateFailed: '无法更新策略状态。',
      deleted: '策略已删除。', deleteFailed: '无法删除策略。', priorityUpdated: '策略优先级已更新。',
      priorityUpdateFailed: '无法更新策略优先级。', invalidUserId: '请输入有效的用户 ID。', simulationFailed: '无法模拟策略命中。',
      ariaLabel: '系统提示词', title: '系统提示词', description: '代码默认值始终可用，管理员策略仅保存 section 覆盖项。',
      createPolicy: '新建策略', refresh: '刷新', searchTypes: '搜索提示词类型', searchPlaceholder: '搜索类型',
      category: '分类', allCategories: '全部分类', typeNavigation: '提示词类型',
      policyCount: (count) => `${count} 条策略`, codeDefault: '代码默认', loading: '加载中...',
      codeRevision: '代码 revision', snapshotScope: '快照范围', policies: '管理员策略', priority: '优先级',
      name: '名称', scope: '范围', status: '状态', actions: '操作', enabled: '启用', disabled: '禁用',
      moveUp: '上移', moveDown: '下移', edit: '编辑', delete: '删除', emptyPolicies: '尚未配置，运行时使用代码默认值。',
      defaultSections: '代码默认 section', editOverrides: '编辑策略覆盖', createOverrides: '新建策略覆盖', cancel: '取消',
      policyName: '策略名称', policyDescription: '说明', effectiveScope: '生效范围', allUsers: '全部用户',
      selectedSubjects: '指定用户或用户组', subjectType: '主体类型', user: '用户', group: '用户组', subjectId: '主体 ID', add: '添加',
      removeSubject: (type, id) => `移除 ${type} ${id}`, subjectLabel: (type, id) => `${type} #${id}`,
      overrideSection: '覆盖此 section', characterCount: (count, max) => `${count} / ${max} 字符`,
      databaseOverride: '数据库覆盖', restoreDefault: '恢复代码默认', saving: '保存中...', savePolicy: '保存策略',
      simulateByUser: '按用户模拟', userId: '用户 ID', simulate: '模拟',
      simulationSummary: (source, policy, matchSource) => `来源：${source}；命中策略：${policy}${matchSource ? `（${matchSource}）` : ''}`,
      simulationSectionSummary: (source, count) => `${source}，${count} 字符`, deleteTitle: '删除策略',
      deleteDescription: (name) => `删除“${name}”后，受影响用户会重新匹配下一条策略或使用代码默认值。`,
      scopeSummary: (users, groups) => `用户 ${users}，用户组 ${groups}`,
    },
    feedback: {
      openDialog: '打开反馈信箱',
      openDialogUnread: '打开反馈信箱，有未读管理员回复',
      closeDialog: '关闭反馈信箱',
      title: '反馈信箱',
      refresh: '刷新',
      filterLabel: '反馈状态筛选',
      all: '全部',
      open: '处理中',
      closed: '已关闭',
      newFeedback: '新建反馈',
      createFormLabel: '新建反馈',
      category: '分类',
      categoryBug: '问题',
      categorySuggestion: '建议',
      categoryOther: '其他',
      subject: '主题（可选）',
      content: '正文',
      cancel: '取消',
      create: '提交反馈',
      creating: '提交中...',
      loading: '正在加载...',
      detailLoading: '正在加载反馈详情...',
      threadListLabel: '反馈会话列表',
      selectThread: '从左侧选择一条反馈查看详情。',
      backToList: '返回反馈列表',
      untitled: '未命名反馈',
      replyContent: '回复内容',
      replyPlaceholder: '输入回复...',
      sendReply: '发送回复',
      replyAndReopen: '回复并重新打开',
      sending: '发送中...',
      user: '你',
      administrator: '管理员',
      listLoadFailed: '反馈列表加载失败',
      detailLoadFailed: '反馈详情加载失败',
      markReadFailed: '标记已读失败',
      createFailed: '创建反馈失败',
      replyFailed: '反馈回复失败',
    },
    adminUsers: {
      ariaLabel: '用户管理',
      title: '用户管理',
      searchPlaceholder: '搜索邮箱、昵称或 ID',
      statusAll: '全部状态',
      statusActive: '正常',
      statusDisabled: '已禁用',
      statusDeleted: '已删除',
      refresh: '刷新',
      loading: '正在加载用户...',
      empty: '没有匹配的用户',
      loadFailed: '用户列表加载失败',
      forbidden: '没有权限管理用户',
      backHome: '返回首页',
      detailAriaLabel: '用户详情',
      id: 'ID',
      email: '邮箱',
      displayName: '昵称',
      roles: '角色',
      groups: '用户组',
      status: '状态',
      createdAt: '创建时间',
      lastLoginAt: '最近登录',
      updatedAt: '更新时间',
      deletedAt: '删除时间',
      deletedBy: '删除人',
      actions: '操作',
      disable: '禁用',
      restore: '恢复',
      delete: '删除',
      confirm: '确认',
      confirmDisableTitle: '确认禁用该用户',
      confirmRestoreTitle: '确认恢复该用户',
      confirmDeleteTitle: '确认删除该用户',
      confirmDeleteDescription: '删除后用户会进入软删除状态，无法再执行管理操作。',
      operationFailed: '操作失败，请稍后重试。',
      operationSucceeded: '操作已完成。',
      resetPassword: '重置密码',
      confirmPasswordResetTitle: '生成一次性临时密码',
      confirmPasswordResetDescription: '继续后会立即吊销该用户的全部登录 Session，旧密码失效。',
      temporaryPasswordTitle: '一次性临时密码',
      temporaryPasswordNotice: '该密码关闭后无法再次查看。请通过安全渠道交给用户。',
      temporaryPasswordExpiresAt: '有效期至',
      copyTemporaryPassword: '复制临时密码',
      temporaryPasswordCopied: '已复制',
    },
    adminGroups: {
      title: '用户组管理',
      detailTitle: '用户组详情',
      create: '创建用户组',
      edit: '编辑',
      delete: '删除',
      deleting: '删除中...',
      refresh: '刷新',
      searchPlaceholder: '搜索名称或编码',
      memberSearchPlaceholder: '搜索用户 ID、邮箱或昵称',
      status: '状态',
      statusAll: '全部状态',
      statusActive: '正常',
      statusDisabled: '已停用',
      name: '名称',
      code: '编码',
      description: '说明',
      activeMembers: '有效成员数',
      createdAt: '创建时间',
      updatedAt: '更新时间',
      actions: '操作',
      loading: '正在加载用户组...',
      loadFailed: '用户组列表加载失败。',
      detailLoadFailed: '用户组详情加载失败。',
      empty: '没有匹配的用户组。',
      total: (count) => `共 ${count} 项`,
      editGroup: (name) => `编辑用户组 ${name}`,
      deleteGroup: (name) => `删除用户组 ${name}`,
      deleteRequiresDisabled: '请先停用用户组后再删除',
      deleteTitle: '确认删除用户组',
      deleteDescription: (name, code) => `确认删除“${name}”（${code}）？删除后不可恢复，并会清理该组的全部成员关系。`,
      deleteFailed: '用户组删除失败。',
      createTitle: '创建用户组',
      editTitle: '编辑用户组',
      createDescription: '编码创建后不可修改，请使用稳定的业务标识。',
      editDescription: '可以修改名称、说明和状态，编码保持不变。',
      codeInvalid: '编码必须以大写字母开头，并且只能包含大写字母、数字和下划线。',
      nameRequired: '请输入用户组名称。',
      saveFailed: '用户组保存失败。',
      saving: '保存中...',
      save: '保存',
      backToGroups: '返回用户组列表',
      disabledCannotAdd: '停用的用户组不能添加成员',
      membersTitle: '组内成员',
      membersLoadFailed: '成员列表加载失败。',
      membersLoading: '正在加载成员...',
      membersEmpty: '没有匹配的有效成员。',
      memberUser: '用户',
      email: '邮箱',
      accountStatus: '账号状态',
      joinedAt: '加入时间',
      expiresAt: '到期时间',
      expiresAtOptional: '到期时间（可选）',
      neverExpires: '长期有效',
      userStatuses: { ACTIVE: '正常', DISABLED: '已禁用', DELETED: '已删除' },
      remove: '移除',
      removing: '移除中...',
      removeMember: (name) => `移除成员 ${name}`,
      removeMemberTitle: '确认移除成员',
      removeMemberDescription: (user, group) => `确认将“${user}”从“${group}”中移除？用户账号和业务数据不会被修改。`,
      memberRemoveFailed: '移除成员失败。',
      addMembers: '添加用户',
      addMembersTitle: '添加用户到组',
      addMembersDescription: (code) => `搜索并选择要加入 ${code} 的用户，单次最多 100 人。`,
      userSearchPlaceholder: '搜索用户 ID、邮箱或昵称',
      userSearching: '正在搜索用户...',
      userSearchFailed: '用户搜索失败。',
      userSearchEmpty: '没有匹配的用户。',
      alreadyMember: '已在组内',
      expiryInvalid: '到期时间必须晚于当前时间。',
      noUsersSelected: '尚未选择用户。',
      selectedUsers: (count, names) => `已选择 ${count} 人：${names.join('、')}`,
      addSelected: (count) => `添加 ${count} 人`,
      addingMembers: '添加中...',
      memberAddFailed: '添加成员失败。',
      addCompleted: '成员处理完成',
      addResult: (added, updated, failed) => `新增 ${added} 人，更新 ${updated} 人，失败 ${failed} 人。`,
      userGroupsTitle: '所属用户组',
      userGroupsDescription: '仅展示当前有效的用户组关系。',
      addToGroup: '添加到用户组',
      addToGroupTitle: '添加到用户组',
      addToGroupDescription: (user) => `为“${user}”选择目标用户组和可选到期时间。`,
      targetGroup: '目标用户组',
      selectGroup: '请选择用户组',
      noAvailableGroups: '没有可添加的正常用户组。',
      noUserGroups: '当前没有有效用户组。',
      expiresOn: (date) => `${date} 到期`,
    },
    adminAi: {
      ariaLabel: 'AI 治理',
      title: 'AI 治理',
      settingsTitle: '运行策略',
      globalStatus: '全局 AI 状态',
      enabled: '已开启',
      disabled: '已关闭',
      defaultDailyRequestLimit: '默认每日 AI 入口请求上限',
      lastUpdated: '最近更新',
      editLimit: '编辑额度',
      save: '保存',
      saving: '保存中',
      refresh: '刷新',
      limitHint: '允许范围：1 - 10000',
      limitInvalid: '请输入 1 到 10000 之间的整数。',
      settingsLoadFailed: 'AI 运行策略加载失败。',
      settingsUpdateFailed: 'AI 运行策略保存失败。',
      usageLoadFailed: 'AI 用量加载失败。',
      pricingLoadFailed: '模型定价加载失败。',
      priceSaveFailed: '模型定价保存失败。',
      confirm: '确认',
      cancel: '取消',
      confirmEnableTitle: '开启全局 AI',
      confirmEnableDescription: '开启后仍会继续执行用户暂停、每日入口额度和业务场景策略。',
      confirmDisableTitle: '关闭全局 AI',
      confirmDisableDescription: '关闭后，下一次用户 AI 准入会被拒绝，复习卡后台 AI 生成也会停止。',
      usageTab: '用量与成本',
      pricingTab: '模型定价',
      usageTitle: '用量与成本',
      pricingTitle: '模型定价',
      today: '今天',
      last7Days: '近 7 天',
      last30Days: '近 30 天',
      customRange: '自定义区间',
      from: '开始日期',
      to: '结束日期',
      userId: '用户 ID',
      provider: 'Provider',
      model: '模型',
      purpose: '用途',
      source: '业务场景',
      all: '全部',
      apply: '应用筛选',
      clear: '清除筛选',
      byUser: '按用户',
      byModel: '按模型',
      bySource: '按场景',
      admittedEntryRequests: '已准入入口请求',
      modelCalls: '实际模型调用',
      inputTokens: '输入 Token',
      cachedTokens: '缓存输入 Token',
      outputTokens: '输出 Token',
      totalTokens: '总 Token',
      estimatedCost: '估算成本',
      estimatedAtCurrentPrices: '按当前价格估算',
      unpricedCalls: '未定价调用',
      unpricedTokens: '未定价 Token',
      noResults: '当前筛选没有可展示的数据。',
      actions: '操作',
      viewUser: '查看用户',
      filterUser: '仅看该用户',
      active: '启用',
      inactive: '停用',
      requestQuota: '今日入口请求',
      accountStatus: '账号状态',
      priceStatus: '定价状态',
      unpriced: '未定价',
      priced: '已定价',
      unpricedModels: '出现过的未定价模型',
      lastSeenAt: '最近出现',
      addPrice: '新增价格',
      configurePrice: '配置价格',
      editPrice: '编辑价格',
      providerRequired: '请输入 provider。',
      modelRequired: '请输入精确模型 ID。',
      inputPricePerMillion: '非缓存输入价格（USD / 1M Token）',
      cachedInputPricePerMillion: '缓存输入价格（USD / 1M Token）',
      outputPricePerMillion: '输出价格（USD / 1M Token）',
      costMultiplier: '成本倍率',
      priceEnabled: '启用价格',
      createPrice: '创建价格',
      updatePrice: '保存价格',
      priceDialogTitleCreate: '新增模型价格',
      priceDialogTitleEdit: '编辑模型价格',
      priceInvalid: '请输入合法的十进制价格，价格不得小于 0，倍率必须大于 0。',
      disablePrice: '停用价格',
      enablePrice: '启用价格',
      confirmDisablePriceTitle: '停用模型价格',
      confirmDisablePriceDescription: '停用后，历史调用会重新显示为未定价，不会作为 $0 计入成本。',
      historicalPriceNotice: '历史区间会按当前启用价格重新估算。',
      updatedBy: '更新人',
      updatedAt: '更新时间',
      auditTitle: '请求审计',
      auditDescription: '只读还原 Agent 的最终脱敏请求、工具交互、上下文压缩与 provider 用量。',
      auditLoadFailed: '审计 run 列表加载失败。',
      auditRunLoadFailed: '审计 run 详情加载失败。',
      auditStepLoadFailed: '审计 step 详情加载失败。',
      auditToolResultLoadFailed: '工具结果加载失败。',
      auditRefresh: '刷新审计列表',
      auditFrom: '开始时间',
      auditTo: '结束时间',
      auditScenario: '场景',
      auditTaskId: 'Task ID',
      auditTurnId: 'Turn ID',
      auditStatus: '状态',
      auditFinishReason: '完成原因',
      auditAttempt: '运行尝试',
      auditRunId: 'Run ID',
      auditMinCachedTokens: '最少缓存 Token',
      auditMaxCachedTokens: '最多缓存 Token',
      auditMinCacheRatio: '最小缓存比例',
      auditMaxCacheRatio: '最大缓存比例',
      auditSort: '排序字段',
      auditSortDirection: '排序方向',
      auditSortRequestedAt: '最近请求时间',
      auditSortOverBudget: '超预算 Token',
      auditSortCacheRatio: '缓存比例',
      auditSortDescending: '降序',
      auditSortAscending: '升序',
      auditStatistics: '当前筛选统计',
      auditStatisticsRuns: 'Run 数',
      auditStatisticsOverBudget: '超预算',
      auditStatisticsCache: '缓存 Token / 输入',
      auditStatisticsCompaction: '发生压缩',
      auditFilter: '筛选',
      auditClear: '清除',
      auditOnlyWithTools: '仅工具调用',
      auditOnlyCompacted: '仅发生压缩',
      auditOnlyOverBudget: '仅超预算',
      auditOnlyProviderError: '仅 provider 错误',
      auditNoResults: '没有符合条件的审计 run。',
      auditTime: '时间',
      auditRun: 'Task / Turn / Run',
      auditUser: '用户',
      auditProviderModel: 'Provider / 模型',
      auditSteps: '步骤',
      auditTools: '工具',
      auditEstimateBudget: '估算 / 预算',
      auditActualInput: '实际输入',
      auditCached: '缓存',
      auditOutputTokens: '输出 Token',
      auditReasoningTokens: '推理 Token',
      auditTotalTokens: '总 Token',
      auditCompaction: '压缩',
      auditState: '状态',
      auditOpen: '查看',
      auditBackToRuns: '返回列表',
      auditTimeline: '执行时间线',
      auditSessionTurns: '所在会话',
      auditRequest: '请求快照',
      auditMessages: 'Messages',
      auditRawJson: 'Raw JSON',
      auditOverview: '概要',
      auditToolCalls: '工具调用',
      auditNoSnapshot: '该 step 没有可用的请求快照。',
      auditMessageCount: 'Messages 估算',
      auditToolsCount: '工具 Schema 估算',
      auditFinalEstimate: '最终请求估算',
      auditAssemblyEstimate: 'Prompt Assembly 估算',
      auditRemainingBudget: '剩余预算',
      auditCacheRatio: '缓存命中',
      auditDuration: '耗时',
      auditBudgetStatus: '预算状态',
      auditFailedTools: '失败工具',
      auditUncachedInput: '未缓存输入',
      auditStartedAt: '开始时间',
      auditMessageRole: '角色',
      auditMessageRoles: '角色分布',
      auditMessageSource: '来源',
      auditMessageSection: 'Prompt Section',
      auditMessageToolCallId: '工具调用 ID',
      auditMessageCharacters: '字符数',
      auditMessageTokenEstimate: 'Token 估算',
      auditMessageContent: '脱敏正文',
      auditHistoricalMessages: '当前请求使用的历史消息',
      auditNoHistoricalMessages: '当前请求未使用历史消息。',
      auditToolSchema: '工具 Schema',
      auditToolSchemaCount: 'Schema 数量',
      auditToolSchemaEstimate: 'Schema 估算',
      auditToolName: '名称',
      auditToolDescription: '描述',
      auditToolParameters: '参数 Schema',
      auditToolArguments: '调用参数',
      auditToolResult: '工具结果',
      auditToolResultPreview: '工具结果 Preview',
      auditToolResultStorageMode: '结果存储方式',
      auditToolResultReference: '原始结果引用',
      auditCompactionBefore: '压缩前',
      auditCompactionAfter: '压缩后',
      auditCompactionActions: '压缩动作',
      auditRunAttempts: '关联运行',
      auditError: '错误',
      auditViewContent: '读取结果内容',
      auditContentUnavailable: '结果正文不可用或已超出留存期。',
      auditPrevious: '上一页',
      auditNext: '下一页',
    },
    adminMonitoring: {
      ariaLabel: '系统监控',
      title: '运行状态',
      serviceHealth: '服务健康检查',
      apiService: 'API 服务',
      status: '当前状态',
      healthy: '运行正常',
      unavailable: '不可用',
      checking: '检查中',
      lastChecked: '最近检查',
      notChecked: '尚未检查',
      refresh: '刷新运行状态',
      loadFailed: '服务健康检查失败。',
    },
    databaseBackup: {
      ariaLabel: '数据备份',
      title: '数据备份',
      backupTitle: '全表备份',
      download: '下载全表备份',
      downloading: '正在准备备份',
      restoreTitle: '覆盖恢复',
      restoreWarning: '恢复会覆盖当前全部数据，完成后需要重新登录。',
      chooseFile: '选择备份文件',
      noFileSelected: '尚未选择文件',
      clearFile: '清除已选文件',
      overwrite: '覆盖全部数据',
      restoring: '正在恢复',
      confirmTitle: '确认覆盖全部数据',
      confirmDescription: '此操作不可撤销，当前全部数据将被备份文件覆盖。',
      cancel: '取消',
      confirmOverwrite: '确认覆盖',
      downloadFailed: '下载数据库备份失败。',
      restoreFailed: '恢复数据库备份失败。',
    },
    sessionMonitoring: {
      ariaLabel: '会话监控',
      title: '会话监控',
      lastRefreshed: '最后刷新',
      notRefreshed: '尚未刷新',
      refresh: '刷新会话列表',
      validSessions: '有效会话',
      activeSessions: '活跃会话',
      validUsers: '涉及用户',
      searchPlaceholder: '搜索用户 ID、邮箱或昵称',
      search: '搜索',
      activityLabel: '会话状态',
      activityFilters: { ALL: '全部', ACTIVE: '活跃', IDLE: '空闲' },
      user: '用户',
      userStatus: '用户状态',
      sessionStatus: '会话状态',
      createdAt: '创建时间',
      lastAccessedAt: '最后访问',
      expiresAt: '过期时间',
      currentSession: '当前会话',
      actions: '操作',
      userStatuses: { ACTIVE: '正常', DISABLED: '已禁用', DELETED: '已删除' },
      activities: { ACTIVE: '活跃', IDLE: '空闲' },
      empty: '没有符合条件的有效会话。',
      loading: '正在加载会话...',
      loadFailed: '会话列表加载失败。',
      operationFailed: '会话下线失败。',
      alreadyOffline: '目标会话已经离线。',
      revoke: '下线',
      revokeSession: (user) => `下线 ${user} 的会话`,
      currentSessionHint: '请使用退出登录结束当前会话。',
      confirmTitle: '下线会话',
      confirmDescription: (user, lastAccessedAt) => `将下线 ${user} 的会话，最后访问于 ${lastAccessedAt}。`,
      connectionNotice: '下线后，该会话的后续请求需要重新登录；已建立的请求或流式连接可能继续到当前操作结束。',
      revoking: '下线中...',
    },
    sessionPolicy: {
      ariaLabel: '会话策略',
      title: '会话策略',
      pageDescription: '新登录会话按优先级命中一条启用策略；已创建的会话不会因策略变更而调整。',
      create: '新建策略',
      createTitle: '新建会话策略',
      editTitle: '编辑会话策略',
      dialogDescription: '策略类型固定为 auth.user-session.v1，优先级由通用策略排序管理。',
      refresh: '刷新策略列表',
      searchPlaceholder: '搜索策略名称或描述',
      search: '搜索',
      priority: '优先级',
      name: '策略名称',
      description: '说明',
      scope: '适用范围',
      allUsers: '全体用户',
      selectedSubjects: '指定用户或用户组',
      scopeSummary: (userCount, groupCount) => `指定 ${userCount} 位用户、${groupCount} 个用户组`,
      subjectType: '主体类型',
      subjectTypes: { USER: '用户', GROUP: '用户组' },
      subjectSearchPlaceholder: '搜索用户昵称、邮箱或用户组名称',
      subjectLoading: '正在查找可选对象...',
      subjectLoadFailed: '可选用户或用户组加载失败。',
      savedUserSubject: '已选用户',
      savedGroupSubject: '已选用户组',
      removeSubject: (label) => `移除 ${label}`,
      remove: '移除',
      subjectEmpty: '没有符合条件的可选对象。',
      subjectRequired: '指定范围时至少需要一个用户或用户组。',
      maxSessions: '最大有效会话数',
      absoluteTimeout: '绝对超时',
      absoluteTimeoutSeconds: '绝对超时（秒）',
      duration: (value, unit) => `${value} ${({ days: '天', hours: '小时', minutes: '分钟', seconds: '秒' })[unit]}`,
      status: '状态',
      statusAll: '全部状态',
      statuses: { ENABLED: '已启用', DISABLED: '已停用' },
      updatedAt: '更新时间',
      actions: '操作',
      empty: '没有符合条件的会话策略。',
      loading: '正在加载会话策略...',
      loadFailed: '会话策略列表加载失败。',
      nameRequired: '请输入策略名称。',
      valueInvalid: '最大有效会话数和绝对超时必须是正整数。',
      save: '保存',
      saving: '保存中...',
      saveFailed: '会话策略保存失败。',
      saveSucceeded: '会话策略已保存。',
      editPolicy: (name) => `编辑策略 ${name}`,
      delete: '删除',
      deleting: '删除中...',
      deleteTitle: '删除会话策略',
      deleteDescription: (name) => `将删除策略“${name}”。此操作不会调整已创建的会话。`,
      deletePolicy: (name) => `删除策略 ${name}`,
      deleteFailed: '会话策略删除失败。',
      deleteSucceeded: '会话策略已删除。',
    },
    learningPlanPolicy: {
      ariaLabel: '学习计划策略',
      title: '学习计划策略',
      pageDescription: '按优先级为全体用户、用户组或指定用户配置学习计划创建额度。',
      create: '新建策略',
      createTitle: '新建学习计划策略',
      editTitle: '编辑学习计划策略',
      dialogDescription: '策略类型固定为 learning-plan.creation.v1。',
      refresh: '刷新策略列表',
      priority: '优先级',
      name: '策略名称',
      description: '说明',
      scope: '适用范围',
      allUsers: '全体用户',
      selectedSubjects: '指定用户或用户组',
      scopeSummary: (userCount, groupCount) => `指定 ${userCount} 位用户、${groupCount} 个用户组`,
      subjectType: '主体类型',
      subjectTypes: { USER: '用户', GROUP: '用户组' },
      subjectSearchPlaceholder: '搜索用户昵称、邮箱或用户组名称',
      subjectLoading: '正在查找可选对象...',
      subjectLoadFailed: '可选用户或用户组加载失败。',
      savedUserSubject: '已选用户',
      savedGroupSubject: '已选用户组',
      removeSubject: (label) => `移除 ${label}`,
      remove: '移除',
      subjectEmpty: '没有符合条件的可选对象。',
      subjectRequired: '指定范围时至少需要一个用户或用户组。',
      maxSavedPlans: '正式计划上限',
      dailyDraftCreationLimit: '每日草案额度',
      draftRetentionDays: '草案保留天数',
      retentionValue: (days) => `${days} 天`,
      status: '状态',
      statuses: { ENABLED: '已启用', DISABLED: '已停用' },
      updatedAt: '更新时间',
      actions: '操作',
      empty: '尚未配置学习计划策略，运行时使用代码默认值。',
      loading: '正在加载学习计划策略...',
      loadFailed: '学习计划策略列表加载失败。',
      nameRequired: '请输入策略名称。',
      valueInvalid: '正式计划上限和每日草案额度必须为 0–1000 的整数，草案保留天数必须为 1–365 的整数。',
      save: '保存',
      saving: '保存中...',
      saveFailed: '学习计划策略保存失败。',
      saveSucceeded: '学习计划策略已保存。',
      moveUpPolicy: (name) => `上移策略 ${name}`,
      moveDownPolicy: (name) => `下移策略 ${name}`,
      orderFailed: '学习计划策略优先级更新失败。',
      orderSucceeded: '学习计划策略优先级已更新。',
      editPolicy: (name) => `编辑策略 ${name}`,
      delete: '删除',
      deleting: '删除中...',
      deleteTitle: '删除学习计划策略',
      deleteDescription: (name) => `将删除策略“${name}”，受影响用户会重新匹配下一条策略或使用代码默认值。`,
      deletePolicy: (name) => `删除策略 ${name}`,
      deleteFailed: '学习计划策略删除失败。',
      deleteSucceeded: '学习计划策略已删除。',
    },
    adminUserAi: {
      title: 'AI 使用与控制',
      loading: '正在加载 AI 信息...',
      loadFailed: 'AI 信息加载失败。',
      policyLoadFailed: 'AI 策略加载失败。',
      usageLoadFailed: 'AI 用量摘要加载失败。',
      effectiveStatus: '最终 AI 状态',
      normal: '正常启用',
      globalDisabled: '全局已关闭',
      userPaused: '用户已暂停',
      inherited: '继承',
      paused: '已暂停',
      pauseUser: '暂停 AI',
      resumeUser: '恢复继承',
      defaultLimit: '全局默认额度',
      overrideLimit: '用户额度覆盖',
      effectiveLimit: '最终有效额度',
      saveOverride: '保存额度',
      restoreInheritance: '恢复继承',
      todayEntryRequests: '今日入口请求',
      todayTokens: '今日 Token',
      todayEstimatedCost: '今日估算成本',
      last7DaysEstimatedCost: '近 7 天估算成本',
      last30DaysEstimatedCost: '近 30 天估算成本',
      viewFullUsage: '查看完整用量',
      confirmPauseTitle: '暂停该用户的 AI',
      confirmPauseDescription: '暂停后，该用户后续受治理 AI 调用将被拒绝。',
      confirmResumeTitle: '恢复该用户的 AI 继承策略',
      confirmResumeDescription: '恢复后，该用户会重新继承全局 AI 状态和额度。',
      limitInvalid: '请输入 1 到 10000 之间的整数，或恢复继承。',
      saveFailed: '用户 AI 策略保存失败。',
    },
    betaAccess: {
      ariaLabel: '内测准入管理',
      title: '内测准入',
      searchPlaceholder: '搜索白名单邮箱',
      search: '搜索',
      refresh: '刷新',
      loading: '正在加载白名单...',
      empty: '没有匹配的白名单邮箱',
      loadFailed: '内测准入数据加载失败',
      forbidden: '没有权限管理内测准入',
      backHome: '返回首页',
      allowlistSetting: '邮箱白名单',
      enabled: '已开启',
      disabled: '已关闭',
      batchAdd: '批量添加邮箱',
      batchPlaceholder: '每行一个邮箱，也可使用逗号分隔',
      batchLimit: '单次最多 100 个邮箱',
      emailInputRequired: '请至少输入一个邮箱。',
      add: '添加',
      saving: '处理中',
      batchResult: '添加结果',
      batchSummary: (added, existing, invalid) => `新增 ${added}，已存在 ${existing}，无效 ${invalid}`,
      addStatus: {
        ADDED: '已新增',
        EXISTING: '已存在',
        INVALID: '无效',
      },
      email: '邮箱',
      registration: '注册状态',
      registered: '已注册',
      unregistered: '未注册',
      userStatus: '用户状态',
      userStatuses: {
        ACTIVE: '正常',
        DISABLED: '已禁用',
        DELETED: '已删除',
      },
      createdBy: '添加人',
      createdAt: '添加时间',
      actions: '操作',
      remove: '移除',
      removeEmail: (email) => `移除 ${email}`,
      confirm: '确认',
      confirmEnableTitle: '开启邮箱白名单',
      confirmEnableDescription: '开启后，非管理员账号只有命中白名单才能注册、登录和继续访问。',
      confirmEnableEmptyDescription: '当前白名单为空。开启后除受信管理员外，其他账号会立即无法访问。',
      confirmDisableTitle: '关闭邮箱白名单',
      confirmDisableDescription: '关闭后，邮箱白名单不再限制注册、登录和已登录请求。',
      confirmRemoveTitle: '移除白名单邮箱',
      confirmRegisteredRemoval: (email) => `移除 ${email} 后会立即吊销关联用户的全部 Session，但不会删除账号和学习数据。`,
      confirmRemoval: (email) => `确认从白名单移除 ${email}？`,
      sessionRevocationWarning: '白名单记录已移除，但 Session 吊销失败；该用户后续请求仍会被实时准入检查拒绝。',
      operationFailed: '内测准入操作失败，请稍后重试。',
    },
    passwordChange: {
      ariaLabel: '修改临时密码',
      title: '设置新密码',
      description: '当前 Session 仅能修改密码或退出登录。',
      newPassword: '新密码',
      confirmPassword: '确认新密码',
      submit: '完成改密',
      submitting: '正在更新',
      logout: '退出登录',
      passwordMismatch: '两次输入的密码不一致。',
      passwordTooShort: '新密码至少需要 8 个字符。',
      failed: '密码更新失败，请稍后重试。',
    },
    common: {
      cancel: '取消',
      create: '创建',
      delete: '删除',
      deleting: '删除中',
      view: '查看',
      previousPage: '上一页',
      nextPage: '下一页',
      pageStatus: (page, totalPages) => `第 ${page} / ${totalPages} 页`,
      empty: '暂无',
      week: (count) => `${count} 周`,
      hoursPerWeek: (count) => `${count}h/周`,
      characterCount: (current, max) => `${current} / ${max} 字`,
      byteCount: (current, max) => `${current} / ${max} 字节`,
      itemInputLimit: (count, maxCount, maxChars) => `${count} / ${maxCount} 项，每项最多 ${maxChars} 字`,
      created: '创建',
      close: '关闭',
    },
    language: {
      label: '语言',
      zhCN: '中文',
      enUS: 'English',
    },
    aiPreference: {
      title: 'AI 教练偏好',
      subtitle: '选择题目聊天中的讲解方式、追问密度和反馈力度。',
      loading: '正在加载 AI 教练偏好...',
      loadFailed: 'AI 教练偏好加载失败',
      saveFailed: 'AI 教练偏好保存失败',
      saved: '已保存',
      saving: '保存中',
      coachStyle: '教练风格',
      coachStyleLabels: {
        GUIDED: '引导型教练',
        DIRECT: '直给型教练',
      },
      coachStyleDescriptions: {
        GUIDED: '先给方向和关键提示，卡住再逐步展开，不直接甩答案。',
        DIRECT: '直接给完整思路、复杂度、坑点和可运行代码。',
      },
    },
    settingsPage: {
      ariaLabel: '个人设置',
      kicker: 'PREFERENCES',
      title: '设置',
      subtitle: '管理 AI 教练、复习策略、界面偏好和当前账户。低频选项集中在这里，不打断日常训练。',
      learningTitle: 'AI 教练',
      learningDescription: '控制题目聊天中的讲解方式。修改后会应用到下一次 AI 回复。',
      currentCoach: '当前教练',
      reviewTitle: '复习策略',
      reviewDescription: '调整 FSRS 目标记忆率、每日复习负载和最长复习间隔。',
      reviewLoading: '正在加载复习设置...',
      reviewLoadFailed: '复习设置加载失败',
      reviewSaveFailed: '复习设置保存失败',
      reviewSaving: '保存中',
      advancedReviewTitle: '高级复习设置',
      advancedReviewDescription: 'FSRS 参数会直接影响复习频率。没有明确需求时建议保持默认值。',
      fsrsParameters: 'FSRS 参数',
      desiredRetention: '目标记忆率',
      desiredRetentionDescription: '数值越高，复习安排越频繁、遗忘风险越低。',
      dailyNewLimit: '每日新卡',
      dailyNewLimitDescription: '当天首次进入队列的卡片数量，0 表示不安排新卡。',
      dailyLearningLimit: '学习中上限',
      dailyLearningLimitDescription: '当天处于学习或重新学习状态的到期卡数量。',
      dailyReviewLimit: '复习卡上限',
      dailyReviewLimitDescription: '当天处于复习状态的到期卡数量。',
      maximumIntervalDays: '最长复习间隔',
      maximumIntervalDaysDescription: '限制 FSRS 排出的最长天数，避免单次间隔无限增长。',
      enableFuzzing: '启用间隔扰动',
      enableFuzzingDescription: '在相近日期内轻微分散复习时间，避免大量卡片集中到同一天。',
      helpSuffix: '说明',
      accountTitle: '账户',
      accountDescription: '查看当前登录身份，或结束本次会话。',
      signedInAs: '当前登录',
      activeStatus: '账户正常',
      passwordTitle: '登录密码',
      passwordNotConfigured: '未设置，可添加邮箱密码登录方式',
      passwordConfigured: '已设置，可使用邮箱和密码登录',
      passwordEmailUnavailable: '账号信息异常，暂时无法设置邮箱登录密码',
      setPassword: '设置密码',
      changePassword: '修改密码',
      passwordDialogSetTitle: '设置登录密码',
      passwordDialogChangeTitle: '修改登录密码',
      passwordDialogSetDescription: '设置后可使用当前邮箱和此密码登录，第三方登录仍然有效。',
      passwordDialogChangeDescription: '修改后，其他已登录的会话将退出。',
      currentPassword: '当前密码',
      newPassword: '新密码',
      confirmNewPassword: '确认新密码',
      passwordMinimumLength: '至少 8 个字符',
      showPassword: '显示密码',
      hidePassword: '隐藏密码',
      passwordUpdating: '更新中...',
      passwordUpdate: '更新密码',
      passwordMismatch: '两次输入的新密码不一致',
      passwordTooShort: '新密码至少需要 8 个字符',
      passwordUpdateFailed: '密码更新失败',
      passwordCreated: '已设置登录密码，以后可使用当前邮箱和此密码登录。',
      passwordUpdated: '密码已更新，其他已登录的会话已退出。',
    },
    myPage: {
      profileKicker: 'PERSONAL TRAINING CENTER',
      title: '我的学习画像',
      coachPanelEyebrow: 'COACHING MODE',
      abilityPanelEyebrow: 'ABILITY PROFILE',
      selectedCoach: '当前教练',
      dataPending: '同步中',
      noData: '暂无',
      statEvaluatedTags: '覆盖标签',
      statAverageScore: '平均能力',
      statReviewedProblems: '已复盘题量',
      statPrimaryStrength: '当前主攻优势',
      diagnosisSummaryTitle: '诊断报告摘要',
      currentStrength: '当前优势',
      currentStrengthDetail: (label, score, reviewedProblems) => `${label} 当前能力为 ${score}，已复盘 ${reviewedProblems} 题，可作为今天训练的稳定支点。`,
      breakthroughAdvice: '突破建议',
      breakthroughAdviceDetail: (label) => `建议今天开启一题“${label}”基础练习，补齐能力图谱中的薄弱领域。`,
      abilitySummaryTitle: '能力画像摘要',
      strongestTag: '当前优势',
      topAbilities: '优势标签',
      selectedAbilityTags: '当前能力气泡',
      expandAbilityProfile: '放大能力画像',
      abilityDetailTitle: '能力画像详情',
      abilityDetailSubtitle: (max) => `探索当前选择的专项能力，最多展示 ${max} 个 tag。`,
      closeAbilityDetail: '关闭能力画像详情',
      selectedTagCount: (selected, max) => `${selected}/${max} 个 tag`,
      minimumTagCount: (min) => `至少保留 ${min} 个 tag`,
      minimumSelectionNotice: (min) => `至少保留 ${min} 个 tag，让能力图谱保持有效。`,
      maximumSelectionNotice: (max) => `最多选择 ${max} 个 tag。`,
      removeSelectedTag: (label) => `移除 ${label}`,
      abilityReplacementHint: '从下方选择新 tag 后，会替换最早加入的一项。',
      abilityHeatmapEyebrow: '能力覆盖',
      abilityHeatmapTitle: '全量 tag 能力热力图',
      abilityHeatmapHint: '色块深浅按能力分展示',
      addHeatmapTag: (label) => `添加 ${label}`,
      selectedHeatmapTag: (label) => `已选择 ${label}`,
      removeHeatmapTag: (label) => `移除 ${label}`,
      catalogProblemsValue: (count) => `题库 ${count} 题`,
      noTopAbilities: '完成更多代码复盘后，这里会显示优势标签。',
      tagCoverage: (reviewed, total) => `${reviewed}/${total}`,
      scoreValue: (score) => `${score} 分`,
      reviewedProblemsValue: (count) => `${count} 题`,
      memoryEyebrow: 'LEARNING MEMORY',
      memoryTitle: '学习记忆',
      memorySubtitle: '当前学习背景、长期观察和专项能力判断。',
      memoryLoading: '正在加载学习记忆...',
      memoryLoadFailed: '学习记忆加载失败',
      memoryEmpty: '还没有形成学习记忆。完成更多练习或在对话中告诉 AI 你的目标与偏好后，这里会逐步出现内容。',
      memoryUpdatedAt: (value) => `更新于 ${value}`,
      openCitation: (displayNumber) => `打开第 ${displayNumber} 条判断的依据`,
      evidenceEyebrow: 'EVIDENCE',
      evidenceDrawerTitle: (displayNumber) => `第 ${displayNumber} 条判断的依据`,
      closeEvidenceDrawer: '关闭依据抽屉',
      evidenceLoading: '正在加载依据...',
      evidenceLoadFailed: '依据加载失败',
      evidenceEmpty: '暂时没有可展示的依据。',
      evidenceLoadMore: '加载更多',
      evidenceLoadingMore: '正在加载...',
      evidenceEnd: '已显示全部依据。',
      evidenceRoles: {
        OBSERVED: '观察到',
        PERSISTED: '后续仍存在',
        RESOLVED: '后续已修正',
        REGRESSED: '再次出现',
        CONTRADICTS: '相反记录',
        DECLARED: '你的陈述',
        CORRECTED: '你的纠正',
      },
      reviewEvidenceSummary: (versionNo, score, passed) => `第 ${versionNo} 版 · ${score} 分 · ${passed ? '已通过' : '未通过'}`,
      viewReviewSubmission: '查看本次提交',
      messageEvidenceTitle: (role) => role === 'DECLARED' ? '来自你在题目聊天中的陈述' : '来自你在题目聊天中的纠正',
    },
    home: {
      ariaLabel: '首页',
      kicker: 'ALGORITHM LEARNING SYSTEM',
      title: '用 AI 掌握算法刷题',
      titleHighlight: '智能复盘系统',
      subtitle: 'make your LeetCode review easier',
      generatePlan: 'Start Reviewing',
      startUsing: '开始使用',
      browseProblems: '浏览题库',
      previewLabel: '学习工作台预览',
      previewFocusLabel: '本周重点',
      previewFocusValue: '数组、哈希表、双指针',
      previewTaskOne: '完成 5 道基础题',
      previewTaskOneStatus: '进行中',
      previewTaskTwo: '复盘 Two Sum 思路',
      previewTaskTwoStatus: '今天',
      previewTaskThree: '更新下周训练方案',
      previewTaskThreeStatus: '周日',
      companyStripLabel: '训练目标公司',
      companyStripTitle: 'PREP FOR INTERVIEWS AT',
      reviewCardCta: 'REVIEW CARD',
      capabilitiesKicker: 'CAPABILITIES',
      capabilitiesTitle: '高频训练入口放在第一屏之后',
      featurePlanTitle: '训练方案',
      featurePlanDescription: '按目标、时间和强弱项生成阶段安排，把刷题节奏拆成可执行任务。',
      featureProblemTitle: '题库训练',
      featureProblemDescription: '集中管理题目、难度和标签，快速进入当前最该练的一类问题。',
      featureAiTitle: 'AI 讲解',
      featureAiDescription: '围绕思路、边界条件和复杂度追问，让每道题沉淀成可复用模式。',
      loopKicker: 'LEARNING LOOP',
      loopTitle: '一套简单循环，长期记住题型',
      loopLabel: '算法学习闭环',
      stepPickTitle: '选题',
      stepPickDescription: '从方案或题库选择本轮重点。',
      stepPracticeTitle: '练习',
      stepPracticeDescription: '先独立推导，再记录阻塞点。',
      stepExplainTitle: '讲解',
      stepExplainDescription: '用 AI 补齐思路、模板和边界。',
      stepReviewTitle: '复盘',
      stepReviewDescription: '按方案回看，避免刷完就忘。',
      ctaLabel: '开始学习',
      ctaTitle: '从一份方案开始今天的训练',
      ctaDescription: '先确定目标和时间，再让系统给出阶段、题目和复盘建议。',
      enterPlans: '进入训练方案',
      workspaceAriaLabel: '学习工作台',
      workspaceKicker: 'WORKBENCH',
      workspaceTitle: '今日学习工作台',
      workspaceSectionsLabel: '主页工作台模块',
      recentPracticeTitle: '最近练习',
      recentPracticeEmpty: '后续展示最近进入的题目和 Review 状态。',
      planPreviewTitle: '学习计划',
      planPreviewEmpty: '后续展示当前推进中的阶段和推荐任务。',
      reviewQueueTitle: '待复盘',
      reviewQueueEmpty: '后续汇总需要回看的代码 Review 和错题。',
      abilityMapTitle: '能力水球图',
      abilityMapSubtitle: '常见 tag · 复盘样本 · 10 分制',
      abilityLoading: '正在加载能力画像...',
      abilityLoadFailed: '能力画像加载失败',
      abilityEmpty: '暂无能力画像数据',
    },
    todayPack: {
      homeLoadFailed: '首页入口加载失败',
      reviewSummaryLoadFailed: '复习摘要加载失败',
      loadingStatus: '正在加载',
      trainingStatusUnavailable: '暂时无法读取今日训练状态。',
      reviewStatusLoading: '正在加载复习状态',
      reviewStatusUnavailable: '复习状态暂不可用',
      reviewDue: (count) => `今日待复习 ${count} 题`,
      reviewUpcoming: (count, time) => `今日还有 ${count} 题，${time}可复习`,
      todayCompleted: '今日已完成',
      reviewStart: (count) => `开始今日复习 ${count} 题`,
      reviewSchedule: '查看今日复习安排',
      homeAriaLabel: '首页',
      trainingEntryAriaLabel: '题包入口',
      todayPack: '今日题包',
      startTraining: '开始训练',
      activePlanRhythm: (daily, daysPerWeek, remaining) => `${daily} 题/天 · 每周 ${daysPerWeek} 天 · 计划剩余 ${remaining} 题`,
      noPlanGuidance: '先采用一份学习方案，首页会按节奏整理每天最该完成的训练。',
      startTodayTraining: '开始今日训练',
      choosePlan: '去方案页创建或采用一个',
      reviewEntryAriaLabel: '复习中心入口',
      reviewCenter: '复习中心',
      reviewDescription: '先复述、再评级，让错题按遗忘风险回到今天，而不是堆成一份静态清单。',
      diagnosisTitle: '学习诊断',
      diagnosisDescription: '由练习与复盘持续更新。',
      viewFullProfile: '查看完整画像',
      abilityUnavailable: '能力画像暂不可用，今日训练入口不受影响。',
      averageAbility: '平均能力',
      currentStrength: '当前优势',
      none: '暂无',
      strengthEvidence: (count) => `基于 ${count} 道复盘题。`,
      nextBreakthrough: '下一步突破',
      breakthroughFallback: '继续积累复盘数据',
      breakthroughAdvice: (label) => `今天优先补一题“${label}”基础练习。`,
      weeklyRhythm: '本周节奏',
      dailyTraining: '每日训练',
      weeklyTarget: '每周目标',
      remainingProblems: '剩余题目',
      problemCount: (count) => `${count} 题`,
      managePlan: '管理学习方案',
      noActivePlan: '还没有进行中的学习方案',
      noActivePlanDescription: '创建方案后，这里会显示每周训练节奏和剩余任务。',
      viewPlans: '查看训练方案',
      packLoadFailed: '今日题包加载失败',
      resetConfirm: '今日题包将从今天重新排布，已完成和已跳过题目不会被删除。',
      packResetFailed: '今日题包重置失败',
      loadingPack: '正在加载今日题包',
      packAriaLabel: '今日题包',
      activePackSummary: (title, startDate, daily, localDate) => `${title} · 开始时间 ${startDate} · ${daily} 题/天 · 今日 ${localDate}`,
      packIntroduction: '从一个推荐计划开始，之后可以在方案页自由切换。',
      restart: '一键清账重新开始',
      restartHelpAriaLabel: '一键清账重新开始说明',
      restartHelp: '将题包起点重置为今天，清掉顺延积压；已完成和已跳过记录会保留。',
      emptyPlanTitle: '还没有采用的训练方案',
      emptyPlanDescription: '回到方案页创建或采用一个方案后，今日题包会按计划节奏生成。',
      planCompletedTitle: '整条计划已清完',
      planCompletedDescription: '已完成或跳过当前采用计划中的所有题目。你仍然可以在方案页浏览历史题目和继续对话。',
      doneTodayTitle: '今天到此为止',
      doneTodayDescription: '当前没有需要补做或今天安排的新题。',
      stopToday: '到此为止',
      nextPack: '再来一包',
      futurePack: (date) => `${date} 的题包`,
      sectionProblemCount: (count) => `${count} 题`,
      carryover: (days, date) => `顺延 ${days} 天 · ${date}`,
      scheduledDate: (date) => `安排日期 ${date}`,
      emptyDay: '这一天没有安排题目',
      packTotal: (count) => `本包共 ${count} 题`,
      backToday: '回到今天',
      statusNoActivePlan: '还没有采用的训练方案',
      statusPlanCompleted: '整条计划已清完',
      statusDoneWithNext: (date) => `今天已完成，下一包 ${date}`,
      statusDone: '今天已完成',
      statusDue: (count) => `今日待练 ${count} 题`,
      statusEmpty: '今日暂无安排',
    },
    reviewCenter: {
      sourceLabels: { REVIEW_FAILED: '错题', REVIEW_PASSED: '复习', USER_MARKED: '手动标记' },
      ratingLabels: { AGAIN: '重来', HARD: '困难', GOOD: '良好', EASY: '简单' },
      ratingDescriptions: {
        AGAIN: '基本没有想起来',
        HARD: '想起来了，但过程费力或不完整',
        GOOD: '独立回忆出主要思路',
        EASY: '快速、完整地回忆出来',
      },
      fsrsStateLabels: { LEARNING: '学习中', REVIEW: '复习中', RELEARNING: '重新学习' },
      loadTodayReview: '加载今日复习...',
      startTodayReview: (count) => `开始今日复习 ${count} 题`,
      availableAt: (time) => `${time}可复习`,
      todayCompleted: '今日已完成',
      cardLoadFailed: '复习卡加载失败',
      summaryLoadFailed: '复习摘要加载失败',
      archiveUpdateFailed: '更新归档状态失败',
      detailLoadFailed: '复习卡详情加载失败',
      title: '复习中心',
      overviewAriaLabel: '复习概览',
      remainingToday: '今日剩余',
      reviewProblems: '复习题',
      mistakes: '错题',
      filtersAriaLabel: '复习筛选',
      searchPlaceholder: '搜索题目或笔记',
      mistakesOnly: '仅看错题',
      refreshCards: '刷新复习卡',
      loadingCards: '正在加载复习卡...',
      emptyCards: '暂无复习卡。',
      codeReviewTimelineAriaLabel: '代码 Review 时间线',
      codeReviewTimelinePointAriaLabel: (version, score, language, time) => `查看代码 Review V${version}，${score} / 10，${language}，${time}`,
      codeReviewTimelineScore: (version, score) => `V${version} · ${score} / 10`,
      codeReviewTimelineFeedback: (feedback) => `主要反馈：${feedback}`,
      codeReviewTimelineFallbackFeedback: '未发现明显问题',
      codeReviewTimelineManualAriaLabel: '手动标记，尚无代码 Review',
      codeReviewTimelineManualTitle: '尚无代码 Review',
      codeReviewTimelineManualDescription: '该题由手动标记加入复习中心，尚未产生代码 Review',
      codeReviewTimelineUnavailable: 'Review 记录暂不可用',
      lastRating: (rating) => `上次 ${rating}`,
      forgottenCount: (count) => `忘记 ${count} 次`,
      viewCardDetail: (title) => `查看复习卡详情 ${title}`,
      viewDetail: '查看详情',
      restoreReview: '恢复复习',
      removeFromReview: '移出复习',
      closeDetail: '关闭复习卡详情',
      loadingDetail: '正在加载复习卡详情...',
      recentHistory: '最近复习记录',
      intervalChange: (before, after) => `间隔 ${before} 天 → ${after} 天`,
      noHistory: '暂无复习记录。',
      dueUnknown: '复习时间待确认',
      overdue: (days) => `已逾期 ${days} 天`,
      dueToday: '今日到期',
      reviewTomorrow: '明天复习',
      reviewInDays: (days) => `${days} 天后复习`,
      queueLoadFailed: '复习队列加载失败',
      ratingSubmitFailed: '复习评级提交失败',
      discardNoteConfirm: '题目笔记还有未保存修改。点击“确定”放弃修改，点击“取消”返回保存。',
      backToReviewCenter: '返回复习中心',
      spacedReview: '间隔复习',
      unknownDifficulty: '难度未知',
      preparingQueue: '正在准备复习队列...',
      queueCompleted: '今日待复习已完成',
      loadingStatement: '正在加载完整题面...',
      fullStatementAriaLabel: '完整题面',
      history: '复习记录',
      recentCount: (count) => `最近 ${count} 次`,
      historyAfterRating: '完成本题评级后，记录会显示在这里。',
      resultAriaLabel: '复习确认结果',
      nextReview: (value) => `下次复习：${value}`,
      ratingAriaLabel: '复习评级',
      nextProblem: '下一题',
      calculating: '计算中',
      minutesLater: (minutes) => `${minutes} 分钟后`,
      reviewLater: '稍后复习',
      ratingButtonAriaLabel: (label, description, interval) => `${label}，${description}，${interval}`,
    },
    problemNotes: {
      loadFailed: '题目笔记加载失败',
      saveFailed: '题目笔记保存失败',
      loading: '加载中',
      unsaved: '有未保存修改',
      existing: '已有笔记',
      empty: '暂无笔记',
      title: '我的题目笔记',
      updatedAt: (value) => ` · 更新于 ${value}`,
      loadingDetail: '正在加载题目笔记...',
      retry: '重试',
      coachSummary: '教练总结',
      coachSummaryPresent: '已有总结',
      coachSummaryNotGenerated: '尚未生成',
      coachSummaryEmpty: '暂无教练总结。',
      coachSummaryHint: '可在题目训练过程中请教练总结内容，经你确认后生成到这里。',
      conflict: '笔记已在其他页面更新，请重新加载后再编辑。',
      reload: '重新加载',
      saving: '保存中',
      save: '保存笔记',
      inputLimitExceeded: '有内容超过输入上限，请根据计数提示调整后再保存。',
      coreIdea: '核心思路',
      dataStructures: '数据结构',
      customDataStructures: '自定义数据结构',
      dataStructureNotes: '数据结构说明',
      dataStructureNotesPlaceholder: '记录这些数据结构在本题中的作用',
      algorithms: '算法',
      customAlgorithms: '自定义算法',
      algorithmNotes: '算法说明',
      algorithmNotesPlaceholder: '记录算法在本题中的使用方式或关键步骤',
      timeComplexity: '时间复杂度',
      spaceComplexity: '空间复杂度',
      edgeCases: '边界与易错点',
      complexityEmpty: '未填写',
      customValueAriaLabel: (label) => `${label}自定义值`,
      customValuePlaceholder: '例如 O(m+n)',
      dataStructureLabels: {
        ARRAY: '数组', HASH_MAP: '哈希表', LINKED_LIST: '链表', STACK: '栈', QUEUE: '队列',
        HEAP: '堆', TREE: '树', GRAPH: '图', TRIE: '字典树', UNION_FIND: '并查集', OTHER: '其他',
      },
      algorithmLabels: {
        TWO_POINTERS: '双指针', SLIDING_WINDOW: '滑动窗口', BINARY_SEARCH: '二分查找',
        DFS: '深度优先搜索', BFS: '广度优先搜索', BACKTRACKING: '回溯', GREEDY: '贪心',
        DYNAMIC_PROGRAMMING: '动态规划', PREFIX_SUM: '前缀和', SORTING: '排序',
        MONOTONIC_STACK: '单调栈', DIJKSTRA: 'Dijkstra', OTHER: '其他',
      },
      complexityLabels: {
        O_1: 'O(1)', O_LOG_N: 'O(log n)', O_N: 'O(n)', O_N_LOG_N: 'O(n log n)',
        O_N2: 'O(n²)', O_N3: 'O(n³)', O_2N: 'O(2ⁿ)', OTHER: '其他',
      },
    },
    problems: {
      ariaLabel: '题库',
      searchLabel: '搜索题目',
      searchPlaceholder: '搜索标题、slug 或编号',
      difficulty: '难度',
      difficultyFilter: '难度筛选',
      allDifficulty: '全部难度',
      company: '公司',
      companyFilter: '公司筛选',
      allCompanies: '全部公司',
      role: '岗位',
      roleFilter: '岗位筛选',
      allRoles: '全部岗位',
      recencyBucket: '时间',
      recencyBucketFilter: '时间桶筛选',
      allRecencyBuckets: '全部时间',
      sort: '排序',
      sortFilter: '排序方式',
      sortFrontendAsc: '题号升序',
      sortCompanyFrequencyDesc: '公司高频',
      companySignalBadge: '公司题频',
      cnOnlyBadge: '中文题面',
      listTitle: '题目列表',
      totalCount: (count) => `${count} 题`,
      loadingList: '加载题库...',
      emptyList: '没有匹配的题目',
      previousPage: '上一页',
      nextPage: '下一页',
      loadingDetail: '加载详情...',
      recommendationReason: '推荐理由',
      sampleInput: '样例输入',
      pythonTemplate: 'Python3 模板',
      selectProblem: '选择一道题查看详情',
      listLoadFailed: '题库列表加载失败',
      filtersLoadFailed: '题库筛选项加载失败',
      detailLoadFailed: '题目详情加载失败',
    },
    learningPlans: {
      ariaLabel: '训练方案',
      detailAriaLabel: '训练方案详情',
      createAriaLabel: '新建训练方案',
      listLoadFailed: '训练方案列表加载失败',
      detailLoadFailed: '训练方案详情加载失败',
      deleteFailed: '训练方案删除失败',
      confirmDelete: '确认删除这个训练方案？',
      activateConfirm: '今日题包将按新计划生成，原计划进度不会被删除。',
      activateFailed: '学习计划切换失败',
      currentActive: '当前采用',
      todayPack: '今日题包',
      activating: '切换中',
      activate: '采用',
      todayPackProblem: '此题在今日题包中',
      loadingDetail: '正在加载方案详情...',
      loadingPracticeChat: '正在加载题目聊天页...',
      overviewTitle: '训练方案',
      overviewDescription: '按目标、时间、当前水平与自身想法生成训练方案。',
      newPlan: '新建方案',
      overviewStats: '方案概览',
      total: '方案总数',
      latestCreated: '最近创建',
      listTitle: '方案库',
      totalPlans: (count) => `共 ${count} 个方案`,
      emptyTitle: '暂无正式方案',
      emptyDescription: '先新建一个训练方案，把目标、周期和题目安排统一起来。',
      planParameters: '方案参数',
      planProgressCount: (completed, total) => `${completed} / ${total} 题`,
      planProgressAriaLabel: (completed, total, percent) => `计划完成进度：已完成 ${completed}/${total} 题，${percent}%`,
      planProgressHelper: '已完成',
      viewPlan: (title) => `查看 ${title}`,
      deletePlan: (title) => `删除 ${title}`,
      currentRhythm: '当前节奏',
      unspecified: '未指定',
      backToList: '返回方案库',
      backToPlans: '返回方案页',
      backToPlanDetail: '返回方案',
      backToPracticeChat: '返回聊天',
      backToLearnerProfile: '返回学习画像',
      learningPlanEyebrow: 'Learning Plan',
      practiceChatEyebrow: 'Practice Chat',
      generateStart: '开始生成训练方案',
      generateFailed: '训练方案生成失败',
      followUpFailed: '训练方案追问提交失败',
      saveFailed: '训练方案保存失败',
      revisionInstructionLabel: '对当前计划不满意？输入调整要求',
      reviseDraft: '按要求调整计划',
      revisionFailed: '调整学习计划失败，请稍后重试。',
      extensionEntryLabel: '想继续学习？描述接下来的目标',
      generateExtension: '生成扩展建议',
      pendingExtensionTitle: '待追加内容',
      reviseExtensionLabel: '对扩展建议不满意？输入调整要求',
      reviseExtension: '按要求调整扩展',
      applyExtension: '应用扩展',
      discardExtension: '放弃',
      extensionFailed: '生成扩展建议失败，请稍后重试。',
      extensionApplyFailed: '应用扩展失败，请重新生成后再试。',
      createTitle: '新建训练方案',
      generatePlan: '生成训练方案',
      generateDraft: '生成方案草案',
      generating: '生成中',
      createMode: '创建方式',
      createWithAi: 'AI 个性化生成',
      createFromTemplate: '从模板创建',
      templateLoading: '正在加载模板...',
      templateDetailLoading: '正在加载模板详情...',
      templateLoadFailed: '学习计划模板加载失败',
      templateDetailLoadFailed: '学习计划模板详情加载失败',
      templateViewContent: '查看模板内容',
      templateViewContentFor: (title) => `查看 ${title} 的模板内容`,
      templateDetailEyebrow: '模板内容',
      templateOverview: '模板概览',
      templateGoal: '训练目标',
      templatePhaseRoute: '训练路线',
      templatePhaseRouteTitle: '分阶段内容',
      templatePhaseSummary: (weeks, problems) => `${weeks} 周 · ${problems} 题`,
      templateFitEyebrow: '使用建议',
      templateFitTitle: '开始前请确认',
      templatePrerequisites: '前置要求',
      templateRecommendedFor: '推荐选择',
      templateNotRecommendedFor: '暂不推荐',
      templateSource: '模板来源',
      templateOpenSource: '查看原始资料',
      templateEmpty: '暂无可用模板',
      templateGenerateStart: '正在按模板生成训练方案',
      templateGenerateFailed: '按模板生成训练方案失败',
      templateGenerateDraft: '按模板生成草案',
      templateSelected: '当前模板',
      templateCatalog: '模板目录',
      templateCatalogRecommended: '推荐',
      templateCatalogSystematicLearning: '系统学习',
      templateCatalogInterviewPrep: '面试备战',
      templateCatalogTopicBreakthrough: '专题突破',
      templateCatalogLanguageAndRole: '语言与岗位',
      templateCatalogEmpty: '该分类暂无可用模板',
      templateRecommendedBadge: '推荐',
      templateDefaultRhythm: (weeks, hours) => `默认 ${weeks} 周 · ${hours}h/周`,
      templateRouteSummary: (problems, weeks, hours) => (
        `${problems} 道可练题 · 推荐 ${weeks} 周 · 每周 ${hours}h`
      ),
      templateRhythm: '训练节奏',
      dailyProblemCount: '每天题目数',
      trainingDaysPerWeek: '每周训练天数',
      rhythmEstimateLine: (problems, weeks) => `按这个配置，完成全部 ${problems} 题大约需要 ${weeks} 周`,
      standardRhythmTitle: '标准方案',
      standardRhythmMainLine: (dailyProblems, trainingDays, recommendedWeeks) => (
        `每天 ${dailyProblems} 题 · 每周 ${trainingDays} 天 · 推荐 ${recommendedWeeks} 周`
      ),
      standardRhythmReason: (problems, recommendedWeeks, trainingDays) => (
        `按 ${problems} 题 / 推荐 ${recommendedWeeks} 周 / 每周 ${trainingDays} 天反算，适合作为稳定推进的起点；周内训练，周末留给复盘或缓冲。`
      ),
      currentRhythmEstimateLine: (dailyProblems, trainingDays, problems, weeks) => (
        `你当前选择：每天 ${dailyProblems} 题 · 每周 ${trainingDays} 天，完成全部 ${problems} 题大约需要 ${weeks} 周`
      ),
      standardRhythmRemainingLine: (remainingProblems, weeks) => (
        `当前剩余 ${remainingProblems} 题，按标准大约需要 ${weeks} 周`
      ),
      rhythmFasterThanStandard: (weeks) => `比标准约快 ${weeks} 周`,
      rhythmSlowerThanStandard: (weeks) => `比标准约慢 ${weeks} 周`,
      rhythmSameAsStandard: '与标准基本一致',
      rhythmConfigLine: (dailyProblems, trainingDays) => `每天 ${dailyProblems} 题 · 每周 ${trainingDays} 天`,
      totalProblemCountLine: (problems) => `共 ${problems} 题`,
      remainingWeeksLine: (weeks) => `还需约 ${weeks} 周`,
      problemCount: (problems) => `${problems} 题`,
      adjustRhythm: '调整节奏',
      adjustedRhythm: '调整后',
      saveRhythm: '保存节奏',
      savingRhythm: '保存中',
      rhythmUpdateFailed: '训练节奏更新失败，请稍后重试。',
      rhythmLabels: {
        RECOMMENDED: '标准',
        RELAXED: '舒缓',
        SPRINT: '冲刺',
      },
      rhythmCompletionLine: (weeks, problems) => `${weeks} 周完成 ${problems} 题`,
      rhythmWeeklyTimeLine: (hours, minDays, maxDays) => (
        minDays === maxDays ? `每周 ${hours}h · 训练 ${minDays} 天` : `每周 ${hours}h · 训练 ${minDays}-${maxDays} 天`
      ),
      rhythmDailyLine: (minProblems, maxProblems, _hasReview) => {
        return minProblems === maxProblems ? `每天约 ${minProblems} 题` : `每天约 ${minProblems}-${maxProblems} 题`;
      },
      rhythmScopeLabels: {
        FULL_ROUTE: '覆盖完整题单',
        FULL_ROUTE_WITH_REVIEW_BUFFER: '覆盖完整题单（含缓冲周）',
        FULL_ROUTE_FAST: '覆盖完整题单，压缩周期',
        FIT_USER_BUDGET: '按时间预算估算覆盖范围',
      },
      templateSourceCommit: (commit) => `来源 commit：${commit}`,
      templateProblemStats: (matched, missing, total) => `题目匹配 ${matched}/${total}，缺失 ${missing}`,
      templateMissingNotice: (missing) => `缺失的 ${missing} 道题不会进入草稿推荐题。`,
      templateDurationTooShort: (minimumWeeks) => `模板周期不能少于 ${minimumWeeks} 周。`,
      templateTargetAudience: '适合人群',
      templateExpectedOutcome: '完成目标',
      scenario: '训练场景',
      duration: '周期',
      durationInput: '训练周期',
      weeklyHours: '每周投入',
      aiBudgetHint: (weeks, hours) => `接下来 ${weeks} 周，每周投入 ${hours} 小时，适合围绕核心题和复盘稳定推进。`,
      level: '当前水平',
      programmingLanguage: '编程语言',
      topicPreferences: '主题偏好',
      objective: '具体目标（可选）',
      additionalConstraints: '其他限制（可选）',
      personalizationEnabled: '参考我的学习数据',
      validationPositiveIntegers: '周期和每周投入必须是正整数。',
      validationNumericRange: (maxWeeks, maxHours) => `训练周期最多 ${maxWeeks} 周，每周投入最多 ${maxHours} 小时。`,
      validationInputLimit: '有内容超过输入上限，请根据计数提示调整后再生成。',
      validationTopicRequired: '专项突破需要至少选择一个主题。',
      confirmDiscard: '放弃当前填写的方案问卷？',
      difficultyDistribution: '难度分布',
      distributionValueText: (label, easy, medium, hard) => `${label}：简单 ${easy}%，中等 ${medium}%，困难 ${hard}%`,
      easyPercent: (value) => `简单 ${value}%`,
      mediumPercent: (value) => `中等 ${value}%`,
      hardPercent: (value) => `困难 ${value}%`,
      draftQuestion: 'Agent 追问',
      followUpAnswer: '补充回答',
      sendFollowUp: '发送补充',
      draftPreview: '训练方案',
      goalSummary: '目标摘要',
      regenerateByGoal: '按新目标重新生成',
      editGoalSummary: '编辑目标摘要',
      savePlan: '保存方案',
      draftUnavailableFailed: '草案生成失败或已过期，请重新填写问卷后生成。',
      draftUnavailable: '草案暂不可预览，请重新填写问卷后生成。',
      restartWizard: '重新填写问卷',
      previewDuration: '周期',
      previewLevel: '水平',
      previewTime: '时间',
      executionSummary: (weeks, days, minutes) => `接下来 ${weeks} 周，每周训练 ${days} 天，每天约 ${minutes} 分钟`,
      nextTrainingPackage: '下一次训练包',
      nextTrainingPackageLine: (newProblems, minutes) => `新题 ${newProblems} 道 · 预计 ${minutes} 分钟`,
      nextTrainingPackageReview: (reviewTask) => `复盘任务：${reviewTask}`,
      nextTrainingPackagePriority: '优先题目',
      planRouteSummary: (problems, weeks, hours, intensity) => `${problems} 题 · ${weeks} 周 · 每周 ${hours}h · 强度${intensity}`,
      loadSummary: '强度评估',
      loadIntensityLabels: {
        RELAXED: '舒缓',
        RECOMMENDED: '合理',
        TIGHT: '偏紧',
        OVERLOADED: '过载',
      },
      loadSummaryLine: (problems, _load, _capacity, intensity) => `${problems} 题 · 强度${intensity}`,
      weeklyBuckets: '每周目标',
      weeklyBucketLine: (week, problems) => `第 ${week} 周 · ${problems} 题`,
      weeklyPlan: '按周执行计划',
      weeklyPlanTitle: (week, title) => `第 ${week} 周：${title}`,
      weeklyBucketStats: (problems) => `${problems} 题`,
      weeklyReviewBuffer: '这周保留为复盘/缓冲，不安排新题。',
      weeklyMissingProblem: '模板题目暂未匹配，先按 slug 记录。',
      phaseDetails: '阶段详情',
      routeProgressTitle: '路线进度',
      routeProgressLine: (completed, total, percent) => `已完成 ${completed}/${total} 题 · ${percent}%`,
      estimatedCompletionDate: (date) => `预计学完：${date}`,
      openProblemsLine: (open, skipped) => `待清 ${open} 题 · 已跳过 ${skipped} 题`,
      visibleStatusLabels: {
        ON_TRACK: '正常推进',
        NEEDS_REBALANCE: '需要回归调整',
        PAUSED: '暂停中',
        COMPLETED: '已完成',
        CLOSED_OUT: '已收尾',
      },
      startNextTrainingPackage: '开始下一包',
      contractDateMovedEarlier: (date) => `预计日前移到 ${date}`,
      contractDateMovedLater: (date) => `预计日顺延到 ${date}`,
      completionSummaryTitle: '终点结算',
      completionSummaryLine: (rate, days, completed, skipped, open) => `完成率 ${rate}% · 用时 ${days} 天 · 完成 ${completed} / 跳过 ${skipped} / 未清 ${open}`,
      weakTagsLabel: '薄弱标签',
      paceTitle: '本周节奏',
      paceCurrentWeek: (current, total) => `第 ${current} 周 / 共 ${total} 周`,
      paceCurrentTarget: (problems) => `本周目标：${problems} 题`,
      paceCurrentCompleted: (completed) => `本周已完成：${completed} 题`,
      paceStatusLabels: {
        AHEAD: '超前',
        ON_TRACK: '正常',
        AT_RISK: '有风险',
        BEHIND: '落后',
      },
      paceLoadGap: (gap) => `进度偏差：${gap > 0 ? '+' : ''}${gap} 小时`,
      problemTraining: '题目训练',
      detailLoadProblemFailed: '题目详情加载失败',
      statementUnavailable: '题面暂未收录。',
      openLeetCode: '打开 LeetCode 题目',
      leetcodeUnavailable: 'LeetCode 链接暂不可用',
      practiceLeetCodeGuidance: '题面内容为大模型生成，本站不内置题库，最终以 LeetCode 为准。代码测试推荐在 LeetCode 上完成，成功或失败都建议把提交结果、报错或反馈粘贴到对话框，让 AI 分析并沉淀用户画像，后续更方便推荐题目。',
      phaseFallback: (phaseIndex) => `第 ${phaseIndex} 阶段`,
      notStarted: '未开始',
      inProgress: '进行中',
      completed: '已完成',
      skipped: '已跳过',
      markCompleted: '标记完成',
      practiceMoreActions: '更多操作',
      skipProblem: '跳过本题',
      skipProblemConfirmTitle: '跳过本题？',
      skipProblemConfirmDescription: '跳过后仍可查看此题的对话，并可稍后标记完成。',
      confirmSkipProblem: '确认跳过',
      organizingThoughts: '正在整理思路...',
      replyFailed: '回复失败，请重试。',
      practiceSessionLoadFailed: '训练会话加载失败',
      practiceMessageFailed: '消息发送失败，请稍后重试。',
      practiceMessageBlocked: '当前回复仍在生成中，请稍后再试。',
      progressUpdateFailed: '进度更新失败，请稍后重试。',
      reviewHistory: '代码提交记录',
      reviewHistoryUnavailable: '代码提交记录暂未开放。',
      reviewEmptyTitle: '暂无代码提交记录',
      reviewEmptyDescription: '提交包含完整代码的练习消息后，系统会在这里展示代码提交版本。',
      reviewLoading: '正在加载代码提交记录...',
      reviewLoadFailed: '代码提交记录加载失败，请稍后重试。',
      reviewDetailLoading: '正在加载代码提交详情...',
      reviewDetailLoadFailed: '代码提交详情加载失败，请稍后重试。',
      requestedReviewUnavailable: '该提交不可用。',
      reviewPassed: '已通过',
      reviewFailed: '未通过',
      reviewToolRunning: '正在生成代码提交记录...',
      reviewToolScoreSummary: (statusLabel, scoreText) => `代码提交记录已生成：${statusLabel}，${scoreText}。`,
      learnerProfileToolRunning: '正在更新学习记忆...',
      learnerProfileToolUpdated: '已更新学习记忆',
      learnerProfileToolNoChange: '学习记忆无需更新',
      learnerProfileToolFailed: '学习记忆暂未更新',
      reviewVersionLabel: (versionNo) => `V${versionNo}`,
      reviewScoreText: (score, passScore) => passScore === undefined ? `${score} 分` : `${score} / ${passScore} 分`,
      reviewPassScoreLabel: (passScore) => `通过分 ${passScore}`,
      reviewNoReview: '暂无代码提交记录',
      reviewCodeSnapshot: '代码快照',
      reviewScoreBreakdown: '评分明细',
      reviewScoreContribution: (score, maximum) => `${score} / ${maximum}`,
      reviewScoreDimensions: {
        correctness: '正确性',
        complexity: '复杂度',
        edgeCases: '边界条件',
        codeQuality: '代码质量',
        problemFit: '题目要求符合度',
      },
      reviewScoreLevels: {
        excellent: '表现优秀',
        good: '表现良好',
        needsImprovement: '需要改进',
        weak: '问题明显',
      },
      reviewScoreDetailAction: (dimension) => `查看${dimension}评分说明`,
      reviewScoreTooltipTitle: (dimension, level, contribution) => `${dimension} · ${level} · ${contribution}`,
      reviewScoreDetailUnavailable: '本次评审没有提供更细的分项说明。',
      reviewScoreTimeComplexity: (value) => `时间复杂度：${value}`,
      reviewScoreSpaceComplexity: (value) => `空间复杂度：${value}`,
      reviewScoreExpectedComplexity: (value) => `目标复杂度：${value}`,
      reviewScoreAnalysisBasis: {
        SERVER_EXECUTION: '判断方式：服务端执行结果',
        USER_REPORTED_EXECUTION: '判断方式：基于你提供的运行结果',
        STATIC_ANALYSIS: '判断方式：静态分析，未实际运行代码',
        INSUFFICIENT_CONTEXT: '判断方式：上下文不足，无法确认',
      },
      reviewDeductionReasons: '扣分原因',
      reviewImprovementSuggestions: '改进建议',
      completionGateFallback: '完成状态需要等待代码提交记录结果。',
      completionRequiresPassedReview: '完成前需要先粘贴完整代码生成一次代码提交记录，并且通过后才能标记完成。',
      completionGateMessages: {
        NO_REVIEW: '完成前需要先粘贴完整代码生成一次代码提交记录，并且通过后才能标记完成。',
        LATEST_REVIEW_FAILED: '最近一次代码提交记录未通过，请修改后重新提交。',
        PASSED: '最近一次代码提交记录已通过，可以标记完成。',
        ALREADY_COMPLETED: '这道题已经完成。',
      },
      practiceComposerPlaceholderReview: '粘贴完整代码、LeetCode 通过/失败反馈，或继续追问思路...',
      coachSummarySave: '保存为教练总结',
      coachSummaryReplace: '替换教练总结',
      coachSummaryApplying: '正在保存...',
      coachSummaryApplyRetry: '重试保存',
      coachSummaryApplied: '已保存到教练总结',
      coachSummarySuperseded: '已有更新的教练总结候选',
      coachSummaryApplyFailed: '保存教练总结失败，请重试。',
      toolPermissionEyebrow: '限时确认',
      toolPermissionReviewTitle: '提交代码记录',
      toolPermissionReviewReason: '模型请求生成一次代码提交记录。',
      toolPermissionProblem: '题目',
      toolPermissionContextWarning: '暂时无法读取完整练习上下文，请确认代码和题目是否匹配。',
      toolPermissionCodePreview: '将提交的代码',
      toolPermissionEffectSummary: '确认后将生成代码提交记录，并可能影响题目完成状态。',
      toolPermissionCountdownLabel: '超时后自动取消',
      toolPermissionCountdownHint: '请在倒计时结束前确认，本次对话不会因取消而中断。',
      toolPermissionExpired: '确认时间已结束',
      toolPermissionExpiredHint: '正在取消本次代码 Review…',
      toolPermissionAllow: '确认生成',
      toolPermissionDeny: '暂不生成',
      toolPermissionDecisionFailed: '提交确认结果失败，请重试。',
      toolPermissionTimeoutNotice: '确认已超时，本次未生成代码提交记录。',
      chatMessages: '聊天消息',
      coach: '教练',
      you: '你',
      loadingStatement: '正在加载题面...',
      sendMessage: '发送消息',
      composerLabel: '输入你的思路、问题、代码或 LeetCode 反馈',
      composerPlaceholder: '输入你的思路、问题、代码或 LeetCode 反馈...',
      practiceMessageTooLong: (maxBytes) => `消息最多允许 ${maxBytes} 字节，请缩短后再发送。`,
      send: '发送',
      waitingGenerate: '等待生成',
      generatingPlan: '正在生成训练方案',
      generationDone: '生成完成',
    },
    labels: {
      difficulties: {
        EASY: '简单',
        MEDIUM: '中等',
        HARD: '困难',
        MIXED: '混合',
      },
      planStatus: {
        ACTIVE: '进行中',
        ARCHIVED: '已归档',
      },
      levels: {
        BEGINNER: '入门',
        INTERMEDIATE: '中级',
        ADVANCED: '高级',
      },
      intents: {
        PRACTICE_GOAL: '刷题目标',
        ABILITY_DIAGNOSIS: '能力诊断',
        INTERVIEW_SPRINT: '面试冲刺',
        TOPIC_BREAKTHROUGH: '专题突破',
        MISTAKE_REVIEW: '错题复盘',
        LONG_TERM_LEARNING: '长期学习',
      },
      planScenarios: {
        INTERVIEW_SPRINT: '面试冲刺',
        TOPIC_BREAKTHROUGH: '专项突破',
        PRACTICE_GOAL: '基础巩固',
        MISTAKE_REVIEW: '错题复盘',
        LONG_TERM_LEARNING: '长期学习',
      },
      difficultyDistribution: {
        beginner: '入门',
        balanced: '均衡',
        sprint: '冲刺',
      },
      topics: {
        Array: '数组',
        'Hash Table': '哈希表',
        String: '字符串',
        'Two Pointers': '双指针',
        'Sliding Window': '滑动窗口',
        Stack: '栈',
        Queue: '队列',
        'Linked List': '链表',
        'Binary Tree': '二叉树',
        Graph: '图',
        'Depth-First Search': 'DFS/BFS',
        'Binary Search': '二分查找',
        'Dynamic Programming': '动态规划',
        Greedy: '贪心',
        Heap: '堆',
        Backtracking: '回溯',
        'Bit Manipulation': '位运算',
        Math: '数学',
        'Divide and Conquer': '分治',
      },
    },
  },
  'en-US': {
    app: {
      brandKicker: 'LEET MENTOR',
      brandName: 'Leet Mentor',
      mainNavigation: 'Main navigation',
      loginStatus: 'Login status',
      checkingLogin: 'Checking sign-in',
      checkingLoginStatus: 'Checking sign-in status...',
      loading: 'Loading',
      loginCheckFailed: 'Unable to check sign-in status. Please try again later.',
      retry: 'Retry',
      logout: 'Log out',
      loggingOut: 'Logging out',
      logoutFailed: 'Log out failed',
      switchToDarkMode: 'Switch to dark mode',
      switchToLightMode: 'Switch to light mode',
      unknownUser: (id) => `User #${id}`,
    },
    auth: {
      subtitle: 'Algorithm practice, problem training, and AI learning plan generation.',
      welcomeEyebrow: 'AI training workspace for algorithm learners',
      welcomeTitle: 'Turn practice into a sustainable training rhythm',
      welcomeDescription: 'Connect learning plans, problem practice, AI explanations, and review history into one loop so every session focuses on the concepts that need work.',
      featurePlanTitle: 'AI plan generation',
      featurePlanDescription: 'Break goals, available time, and weak topics into staged plans.',
      featurePracticeTitle: 'Problem training',
      featurePracticeDescription: 'Jump into practice by difficulty, tags, and progress.',
      featureReviewTitle: 'Mistake review',
      featureReviewDescription: 'Keep explanations, code feedback, and history in one place.',
      previewTitle: 'This week training rhythm',
      previewSubtitle: 'Example study flow',
      previewFocusLabel: 'Current focus',
      previewFocusValue: 'Dynamic programming · Medium',
      previewItems: [
        {
          title: 'Generate a 4-week plan',
          detail: 'Schedule daily themes around the interview sprint.',
        },
        {
          title: 'Finish 12 core problems',
          detail: 'Prioritize LIS and knapsack variants.',
        },
        {
          title: 'Review 3 code reviews',
          detail: 'Capture edge cases, complexity, and readability issues.',
        },
      ],
      emailAuthDivider: 'Or continue with email',
      socialAuthDivider: 'or continue with',
      supportContact: 'During beta, contact the person who invited you or the operator of this deployment for help.',
      termsPrefix: 'By continuing, you acknowledge and agree to the ',
      termsLabel: 'Terms of Service',
      termsConnector: ' and ',
      privacyLabel: 'Privacy Policy',
      failed: 'Sign-in failed. Please try again.',
      betaAccessDenied: 'This email is not currently allowed to access the private beta.',
      googleLogin: 'Sign in with Google',
      githubLogin: 'Sign in with GitHub',
      emailLabel: 'Email',
      emailPlaceholder: 'you@example.com',
      passwordLabel: 'Password',
      passwordPlaceholder: 'At least 8 characters',
      displayNameLabel: 'Display name',
      displayNamePlaceholder: 'Enter a display name',
      passwordLogin: 'Sign in',
      passwordRegister: 'Create account',
      loggingIn: 'Signing in',
      registering: 'Creating account',
      showLogin: 'Already have an account',
      showRegister: 'Create email account',
      loginModeTitle: 'Email sign-in',
      registerModeTitle: 'Create email account',
      oauthModeTitle: 'Choose a sign-in method',
      oauthModeDescription: 'Continue with a connected account',
      loginAction: 'Log in',
      validationEmailRequired: 'Enter your email.',
      validationPasswordRequired: 'Enter your password.',
      validationDisplayNameRequired: 'Enter a display name.',
    },
    nav: {
      home: 'Home',
      my: 'Learning Profile',
      settings: 'Settings',
      learningPlans: 'Plans',
      mistakes: 'Review Center',
      problems: 'Problems',
      adminBetaAccess: 'Beta Access',
      adminUsers: 'Users',
      adminUserGroups: 'User Groups',
      adminMonitoring: 'System monitoring',
      adminDatabaseBackup: 'Data backup',
      adminSessions: 'Session monitoring',
      adminSessionPolicies: 'Session policies',
      adminLearningPlanPolicies: 'Learning plan policies',
      adminLearningPlanAiRevisionPolicies: 'Learning plan AI revision policies',
      adminSystemPrompts: 'System prompts',
      adminAi: 'AI Governance',
      feedback: 'Feedback',
      adminOverview: 'Overview',
      adminFeedback: 'Feedback',
      forbidden: 'Not authorized',
    },
    adminShell: {
      workspace: 'Admin Console',
      returnToLearning: 'Back to learning',
      openNavigation: 'Open admin navigation',
      closeNavigation: 'Close admin navigation',
      collapseNavigation: 'Collapse sidebar',
      expandNavigation: 'Expand sidebar',
      businessNavigation: 'Admin business areas',
      navigation: 'Admin navigation',
      pageNavigation: 'Current area pages',
      expandSection: (label) => `Expand ${label}`,
      collapseSection: (label) => `Collapse ${label}`,
      labels: {
        overview: 'Operations',
        access: 'Identity & Access',
        monitoring: 'System monitoring',
        systemStatus: 'Runtime status',
        databaseBackup: 'Data backup',
        sessions: 'Session monitoring',
        sessionPolicies: 'Session policies',
        learningPlanPolicies: 'Learning plan policies',
        learningPlanAiRevisionPolicies: 'Learning plan AI revision policies',
        systemPrompts: 'System prompts',
        ai: 'AI Governance',
        aiPlatform: 'AI Platform',
        operations: 'Operations & Support',
        modelResources: 'Model resources',
        costGovernance: 'Cost governance',
        aiProviders: 'Providers & Models',
        aiRouting: 'Model routing',
        aiUsage: 'Usage & Cost',
        aiPricing: 'Model pricing',
        aiAudit: 'Request audit',
        content: 'Content',
        feedback: 'Feedback & Support',
        users: 'Users',
        userGroups: 'User Groups',
        betaAccess: 'Beta Access',
        problems: 'Problem Library',
      },
    },
    adminFeedback: {
      listLoadFailed: 'Failed to load feedback', detailLoadFailed: 'Failed to load feedback details',
      markReadFailed: 'Failed to mark feedback as read', replyFailed: 'Failed to reply',
      statusUpdateFailed: 'Failed to update status', requestFailed: 'Request failed', title: 'Feedback Management',
      refresh: 'Refresh', status: 'Status', allStatuses: 'All statuses', category: 'Category', allCategories: 'All categories',
      unreadOnly: 'Unread only', unread: 'Unread', user: 'User', subject: 'Subject', updatedAt: 'Updated',
      untitled: 'Untitled feedback', close: 'Close', reopen: 'Reopen', selectThread: 'Select feedback to handle.',
    },
    adminOverview: {
      loadFailed: 'Failed to load the admin overview', loading: 'Loading admin overview...', title: 'Admin Overview', retry: 'Retry',
      generatedMeta: (date, zone) => `Data as of ${date} · Quota timezone: ${zone}`, refresh: 'Refresh', betaAccess: 'Beta Access',
      enabled: 'Enabled', disabled: 'Disabled', betaSummary: (allowed, registered) => `${allowed} allowed / ${registered} registered`,
      aiRuntime: 'AI Runtime Policy', aiRuntimeSummary: (limit) => `${limit} requests by default`, aiToday: 'AI Usage Today',
      entryRequests: 'Entry Requests', success: 'Succeeded', failed: 'Failed', quotaRejected: 'Quota Rejected', modelCalls: 'Model Calls',
      estimatedCost: 'Estimated at Current Pricing', unpricedCalls: (count) => `${count} unpriced ${count === 1 ? 'call' : 'calls'}`,
      quotaRiskUsers: 'Users Near Quota', userFallback: (id) => `User ${id}`, noQuotaRisks: 'No users are near their quota limit.',
      feedbackTasks: 'Feedback Tasks', adminUnread: (count) => `${count} unread for administrators`, recentFailedRuns: 'Recent Failed Runs',
      failedRuns: (count) => `${count} failed ${count === 1 ? 'run' : 'runs'}`, noRunQuery: 'Run lookup is not available yet.',
      sectionUnavailable: 'This section is temporarily unavailable',
    },
    adminUserSupport: {
      title: 'Beta Access & Support', allowlist: 'Allowlist', unavailable: 'Unavailable',
      allowed: (id) => id ? `Allowed (record ${id})` : 'Allowed', notAllowed: 'Not allowed', loading: 'Loading...',
      openFeedback: 'Open Feedback', feedbackCount: (count) => `${count} ${count === 1 ? 'thread' : 'threads'}`,
    },
    adminSystemPrompts: {
      loadCatalogFailed: 'Failed to load the system prompt catalog.', loadDetailFailed: 'Failed to load prompt details.',
      loadPoliciesFailed: 'Failed to load policies.', defaultPolicyName: (displayName) => `${displayName} configuration`,
      invalidSubjectId: 'Enter a valid user or group ID.', nameRequired: 'Policy name is required.',
      subjectRequired: 'Add at least one user or group for a selected-subject policy.', saved: 'Policy saved.',
      saveFailed: 'Failed to save the policy.', disabledSuccess: 'Policy disabled.', enabledSuccess: 'Policy enabled.',
      statusUpdateFailed: 'Failed to update policy status.', deleted: 'Policy deleted.', deleteFailed: 'Failed to delete the policy.',
      priorityUpdated: 'Policy priority updated.', priorityUpdateFailed: 'Failed to update policy priority.',
      invalidUserId: 'Enter a valid user ID.', simulationFailed: 'Failed to simulate policy resolution.',
      ariaLabel: 'System prompts', title: 'System Prompts',
      description: 'Code defaults are always available. Administrator policies store section overrides only.',
      createPolicy: 'New Policy', refresh: 'Refresh', searchTypes: 'Search prompt types', searchPlaceholder: 'Search types',
      category: 'Category', allCategories: 'All categories', typeNavigation: 'Prompt types',
      policyCount: (count) => `${count} ${count === 1 ? 'policy' : 'policies'}`, codeDefault: 'Code default', loading: 'Loading...',
      codeRevision: 'Code revision', snapshotScope: 'Snapshot scope', policies: 'Administrator Policies', priority: 'Priority',
      name: 'Name', scope: 'Scope', status: 'Status', actions: 'Actions', enabled: 'Enabled', disabled: 'Disabled',
      moveUp: 'Move up', moveDown: 'Move down', edit: 'Edit', delete: 'Delete',
      emptyPolicies: 'No policies configured. Runtime uses the code default.', defaultSections: 'Code Default Sections',
      editOverrides: 'Edit Policy Overrides', createOverrides: 'Create Policy Overrides', cancel: 'Cancel',
      policyName: 'Policy Name', policyDescription: 'Description', effectiveScope: 'Effective Scope', allUsers: 'All users',
      selectedSubjects: 'Selected users or groups', subjectType: 'Subject type', user: 'User', group: 'Group', subjectId: 'Subject ID', add: 'Add',
      removeSubject: (type, id) => `Remove ${type} ${id}`, subjectLabel: (type, id) => `${type} #${id}`,
      overrideSection: 'Override this section', characterCount: (count, max) => `${count} / ${max} characters`,
      databaseOverride: 'Database override', restoreDefault: 'Restore code default', saving: 'Saving...', savePolicy: 'Save Policy',
      simulateByUser: 'Simulate by User', userId: 'User ID', simulate: 'Simulate',
      simulationSummary: (source, policy, matchSource) => `Source: ${source}; matched policy: ${policy}${matchSource ? ` (${matchSource})` : ''}`,
      simulationSectionSummary: (source, count) => `${source}, ${count} characters`, deleteTitle: 'Delete Policy',
      deleteDescription: (name) => `After deleting “${name}”, affected users will match the next policy or use the code default.`,
      scopeSummary: (users, groups) => `${users} ${users === 1 ? 'user' : 'users'}, ${groups} ${groups === 1 ? 'group' : 'groups'}`,
    },
    feedback: {
      openDialog: 'Open feedback inbox',
      openDialogUnread: 'Open feedback inbox with unread administrator replies',
      closeDialog: 'Close feedback inbox',
      title: 'Feedback inbox',
      refresh: 'Refresh',
      filterLabel: 'Feedback status filter',
      all: 'All',
      open: 'In progress',
      closed: 'Closed',
      newFeedback: 'New feedback',
      createFormLabel: 'New feedback',
      category: 'Category',
      categoryBug: 'Issue',
      categorySuggestion: 'Suggestion',
      categoryOther: 'Other',
      subject: 'Subject (optional)',
      content: 'Details',
      cancel: 'Cancel',
      create: 'Submit feedback',
      creating: 'Submitting...',
      loading: 'Loading...',
      detailLoading: 'Loading feedback details...',
      threadListLabel: 'Feedback conversations',
      selectThread: 'Select feedback from the list to view details.',
      backToList: 'Back to feedback list',
      untitled: 'Untitled feedback',
      replyContent: 'Reply',
      replyPlaceholder: 'Write a reply...',
      sendReply: 'Send reply',
      replyAndReopen: 'Reply and reopen',
      sending: 'Sending...',
      user: 'You',
      administrator: 'Administrator',
      listLoadFailed: 'Failed to load feedback list',
      detailLoadFailed: 'Failed to load feedback details',
      markReadFailed: 'Failed to mark feedback as read',
      createFailed: 'Failed to create feedback',
      replyFailed: 'Failed to send feedback reply',
    },
    adminUsers: {
      ariaLabel: 'User management',
      title: 'User management',
      searchPlaceholder: 'Search email, name, or ID',
      statusAll: 'All statuses',
      statusActive: 'Active',
      statusDisabled: 'Disabled',
      statusDeleted: 'Deleted',
      refresh: 'Refresh',
      loading: 'Loading users...',
      empty: 'No matching users',
      loadFailed: 'Failed to load users',
      forbidden: 'You do not have permission to manage users',
      backHome: 'Back to dashboard',
      detailAriaLabel: 'User detail',
      id: 'ID',
      email: 'Email',
      displayName: 'Display name',
      roles: 'Roles',
      groups: 'Groups',
      status: 'Status',
      createdAt: 'Created',
      lastLoginAt: 'Last login',
      updatedAt: 'Updated',
      deletedAt: 'Deleted at',
      deletedBy: 'Deleted by',
      actions: 'Actions',
      disable: 'Disable',
      restore: 'Restore',
      delete: 'Delete',
      confirm: 'Confirm',
      confirmDisableTitle: 'Disable this user?',
      confirmRestoreTitle: 'Restore this user?',
      confirmDeleteTitle: 'Delete this user?',
      confirmDeleteDescription: 'The user will be soft deleted and management actions will no longer be available.',
      operationFailed: 'Operation failed. Please try again later.',
      operationSucceeded: 'Operation completed.',
      resetPassword: 'Reset password',
      confirmPasswordResetTitle: 'Generate a one-time temporary password',
      confirmPasswordResetDescription: 'Continuing immediately revokes all sessions for this user and invalidates the old password.',
      temporaryPasswordTitle: 'One-time temporary password',
      temporaryPasswordNotice: 'This password cannot be retrieved after closing. Share it through a secure channel.',
      temporaryPasswordExpiresAt: 'Expires at',
      copyTemporaryPassword: 'Copy temporary password',
      temporaryPasswordCopied: 'Copied',
    },
    adminGroups: {
      title: 'User groups',
      detailTitle: 'User group detail',
      create: 'Create group',
      edit: 'Edit',
      delete: 'Delete',
      deleting: 'Deleting...',
      refresh: 'Refresh',
      searchPlaceholder: 'Search name or code',
      memberSearchPlaceholder: 'Search user ID, email, or name',
      status: 'Status',
      statusAll: 'All statuses',
      statusActive: 'Active',
      statusDisabled: 'Disabled',
      name: 'Name',
      code: 'Code',
      description: 'Description',
      activeMembers: 'Active members',
      createdAt: 'Created',
      updatedAt: 'Updated',
      actions: 'Actions',
      loading: 'Loading user groups...',
      loadFailed: 'Failed to load user groups.',
      detailLoadFailed: 'Failed to load the user group.',
      empty: 'No matching user groups.',
      total: (count) => `${count} items`,
      editGroup: (name) => `Edit user group ${name}`,
      deleteGroup: (name) => `Delete user group ${name}`,
      deleteRequiresDisabled: 'Disable the group before deleting it',
      deleteTitle: 'Delete user group?',
      deleteDescription: (name, code) => `Delete “${name}” (${code})? This cannot be undone and all memberships in the group will be removed.`,
      deleteFailed: 'Failed to delete the user group.',
      createTitle: 'Create user group',
      editTitle: 'Edit user group',
      createDescription: 'The code cannot be changed after creation. Use a stable business identifier.',
      editDescription: 'You can update the name, description, and status. The code remains fixed.',
      codeInvalid: 'The code must start with an uppercase letter and contain only uppercase letters, digits, and underscores.',
      nameRequired: 'Enter a user group name.',
      saveFailed: 'Failed to save the user group.',
      saving: 'Saving...',
      save: 'Save',
      backToGroups: 'Back to user groups',
      disabledCannotAdd: 'Members cannot be added to a disabled group',
      membersTitle: 'Group members',
      membersLoadFailed: 'Failed to load group members.',
      membersLoading: 'Loading members...',
      membersEmpty: 'No matching active members.',
      memberUser: 'User',
      email: 'Email',
      accountStatus: 'Account status',
      joinedAt: 'Joined',
      expiresAt: 'Expires',
      expiresAtOptional: 'Expiration (optional)',
      neverExpires: 'No expiration',
      userStatuses: { ACTIVE: 'Active', DISABLED: 'Disabled', DELETED: 'Deleted' },
      remove: 'Remove',
      removing: 'Removing...',
      removeMember: (name) => `Remove member ${name}`,
      removeMemberTitle: 'Remove group member?',
      removeMemberDescription: (user, group) => `Remove “${user}” from “${group}”? The user account and business data will not be changed.`,
      memberRemoveFailed: 'Failed to remove the member.',
      addMembers: 'Add users',
      addMembersTitle: 'Add users to group',
      addMembersDescription: (code) => `Search and select users to add to ${code}. Up to 100 users can be added at once.`,
      userSearchPlaceholder: 'Search user ID, email, or name',
      userSearching: 'Searching users...',
      userSearchFailed: 'Failed to search users.',
      userSearchEmpty: 'No matching users.',
      alreadyMember: 'Already a member',
      expiryInvalid: 'The expiration must be later than the current time.',
      noUsersSelected: 'No users selected.',
      selectedUsers: (count, names) => `${count} selected: ${names.join(', ')}`,
      addSelected: (count) => `Add ${count}`,
      addingMembers: 'Adding...',
      memberAddFailed: 'Failed to add members.',
      addCompleted: 'Member update completed',
      addResult: (added, updated, failed) => `${added} added, ${updated} updated, ${failed} failed.`,
      userGroupsTitle: 'User groups',
      userGroupsDescription: 'Only currently active group memberships are shown.',
      addToGroup: 'Add to group',
      addToGroupTitle: 'Add to user group',
      addToGroupDescription: (user) => `Choose a target group and optional expiration for “${user}”.`,
      targetGroup: 'Target group',
      selectGroup: 'Select a group',
      noAvailableGroups: 'There are no active groups available to add.',
      noUserGroups: 'No active user groups.',
      expiresOn: (date) => `Expires ${date}`,
    },
    adminAi: {
      ariaLabel: 'AI governance',
      title: 'AI governance',
      settingsTitle: 'Runtime policy',
      globalStatus: 'Global AI status',
      enabled: 'Enabled',
      disabled: 'Disabled',
      defaultDailyRequestLimit: 'Default daily AI entry request limit',
      lastUpdated: 'Last updated',
      editLimit: 'Edit limit',
      save: 'Save',
      saving: 'Saving',
      refresh: 'Refresh',
      limitHint: 'Allowed range: 1 - 10000',
      limitInvalid: 'Enter a whole number from 1 to 10000.',
      settingsLoadFailed: 'Failed to load AI runtime policy.',
      settingsUpdateFailed: 'Failed to save AI runtime policy.',
      usageLoadFailed: 'Failed to load AI usage.',
      pricingLoadFailed: 'Failed to load model pricing.',
      priceSaveFailed: 'Failed to save model pricing.',
      confirm: 'Confirm',
      cancel: 'Cancel',
      confirmEnableTitle: 'Enable global AI',
      confirmEnableDescription: 'User pauses, daily entry limits, and purpose policies will continue to apply.',
      confirmDisableTitle: 'Disable global AI',
      confirmDisableDescription: 'The next user AI admission will be rejected and background AI review-card generation will stop.',
      usageTab: 'Usage & cost',
      pricingTab: 'Model pricing',
      usageTitle: 'Usage & cost',
      pricingTitle: 'Model pricing',
      today: 'Today',
      last7Days: 'Last 7 days',
      last30Days: 'Last 30 days',
      customRange: 'Custom range',
      from: 'From',
      to: 'To',
      userId: 'User ID',
      provider: 'Provider',
      model: 'Model',
      purpose: 'Purpose',
      source: 'Business source',
      all: 'All',
      apply: 'Apply filters',
      clear: 'Clear filters',
      byUser: 'By user',
      byModel: 'By model',
      bySource: 'By source',
      admittedEntryRequests: 'Admitted entry requests',
      modelCalls: 'Actual model calls',
      inputTokens: 'Input tokens',
      cachedTokens: 'Cached input tokens',
      outputTokens: 'Output tokens',
      totalTokens: 'Total tokens',
      estimatedCost: 'Estimated cost',
      estimatedAtCurrentPrices: 'Estimated at current prices',
      unpricedCalls: 'Unpriced calls',
      unpricedTokens: 'Unpriced tokens',
      noResults: 'No data matches the current filters.',
      actions: 'Actions',
      viewUser: 'View user',
      filterUser: 'Filter to user',
      active: 'Enabled',
      inactive: 'Disabled',
      requestQuota: 'Today entry requests',
      accountStatus: 'Account status',
      priceStatus: 'Pricing status',
      unpriced: 'Unpriced',
      priced: 'Priced',
      unpricedModels: 'Observed unpriced models',
      lastSeenAt: 'Last seen',
      addPrice: 'Add price',
      configurePrice: 'Configure price',
      editPrice: 'Edit price',
      providerRequired: 'Enter a provider.',
      modelRequired: 'Enter the exact model ID.',
      inputPricePerMillion: 'Uncached input price (USD / 1M tokens)',
      cachedInputPricePerMillion: 'Cached input price (USD / 1M tokens)',
      outputPricePerMillion: 'Output price (USD / 1M tokens)',
      costMultiplier: 'Cost multiplier',
      priceEnabled: 'Enable price',
      createPrice: 'Create price',
      updatePrice: 'Save price',
      priceDialogTitleCreate: 'Add model price',
      priceDialogTitleEdit: 'Edit model price',
      priceInvalid: 'Enter valid decimal prices. Prices cannot be negative and the multiplier must be greater than zero.',
      disablePrice: 'Disable price',
      enablePrice: 'Enable price',
      confirmDisablePriceTitle: 'Disable model price',
      confirmDisablePriceDescription: 'After disabling, historical calls become unpriced again instead of being included as $0 cost.',
      historicalPriceNotice: 'Historical ranges are recalculated at current enabled prices.',
      updatedBy: 'Updated by',
      updatedAt: 'Updated at',
      auditTitle: 'Request audit',
      auditDescription: 'Read-only reconstruction of redacted final requests, tool interactions, context compaction, and provider usage.',
      auditLoadFailed: 'Failed to load audit runs.',
      auditRunLoadFailed: 'Failed to load audit run details.',
      auditStepLoadFailed: 'Failed to load audit step details.',
      auditToolResultLoadFailed: 'Failed to load tool results.',
      auditRefresh: 'Refresh audit runs',
      auditFrom: 'From',
      auditTo: 'To',
      auditScenario: 'Scenario',
      auditTaskId: 'Task ID',
      auditTurnId: 'Turn ID',
      auditStatus: 'Status',
      auditFinishReason: 'Finish reason',
      auditAttempt: 'Run attempt',
      auditRunId: 'Run ID',
      auditMinCachedTokens: 'Min cached tokens',
      auditMaxCachedTokens: 'Max cached tokens',
      auditMinCacheRatio: 'Min cache ratio',
      auditMaxCacheRatio: 'Max cache ratio',
      auditSort: 'Sort field',
      auditSortDirection: 'Sort direction',
      auditSortRequestedAt: 'Most recent request',
      auditSortOverBudget: 'Over-budget tokens',
      auditSortCacheRatio: 'Cache ratio',
      auditSortDescending: 'Descending',
      auditSortAscending: 'Ascending',
      auditStatistics: 'Current filter statistics',
      auditStatisticsRuns: 'Runs',
      auditStatisticsOverBudget: 'Over budget',
      auditStatisticsCache: 'Cached / input tokens',
      auditStatisticsCompaction: 'Compacted',
      auditFilter: 'Filter',
      auditClear: 'Clear',
      auditOnlyWithTools: 'Tools only',
      auditOnlyCompacted: 'Compacted only',
      auditOnlyOverBudget: 'Over budget only',
      auditOnlyProviderError: 'Provider errors only',
      auditNoResults: 'No audit runs match the current filters.',
      auditTime: 'Time',
      auditRun: 'Task / Turn / Run',
      auditUser: 'User',
      auditProviderModel: 'Provider / Model',
      auditSteps: 'Steps',
      auditTools: 'Tools',
      auditEstimateBudget: 'Estimate / Budget',
      auditActualInput: 'Actual input',
      auditCached: 'Cached',
      auditOutputTokens: 'Output tokens',
      auditReasoningTokens: 'Reasoning tokens',
      auditTotalTokens: 'Total tokens',
      auditCompaction: 'Compaction',
      auditState: 'State',
      auditOpen: 'Open',
      auditBackToRuns: 'Back to runs',
      auditTimeline: 'Execution timeline',
      auditSessionTurns: 'Conversation turns',
      auditRequest: 'Request snapshot',
      auditMessages: 'Messages',
      auditRawJson: 'Raw JSON',
      auditOverview: 'Overview',
      auditToolCalls: 'Tool calls',
      auditNoSnapshot: 'No request snapshot is available for this step.',
      auditMessageCount: 'Messages estimate',
      auditToolsCount: 'Tool schema estimate',
      auditFinalEstimate: 'Final request estimate',
      auditAssemblyEstimate: 'Prompt assembly estimate',
      auditRemainingBudget: 'Remaining budget',
      auditCacheRatio: 'Cache hit',
      auditDuration: 'Duration',
      auditBudgetStatus: 'Budget status',
      auditFailedTools: 'Failed tools',
      auditUncachedInput: 'Uncached input',
      auditStartedAt: 'Started at',
      auditMessageRole: 'Role',
      auditMessageRoles: 'Role counts',
      auditMessageSource: 'Source',
      auditMessageSection: 'Prompt section',
      auditMessageToolCallId: 'Tool call ID',
      auditMessageCharacters: 'Characters',
      auditMessageTokenEstimate: 'Token estimate',
      auditMessageContent: 'Redacted content',
      auditHistoricalMessages: 'Historical messages used by this request',
      auditNoHistoricalMessages: 'This request did not use historical messages.',
      auditToolSchema: 'Tool schema',
      auditToolSchemaCount: 'Schema count',
      auditToolSchemaEstimate: 'Schema estimate',
      auditToolName: 'Name',
      auditToolDescription: 'Description',
      auditToolParameters: 'Parameter schema',
      auditToolArguments: 'Call arguments',
      auditToolResult: 'Tool result',
      auditToolResultPreview: 'Tool result preview',
      auditToolResultStorageMode: 'Result storage mode',
      auditToolResultReference: 'Original result reference',
      auditCompactionBefore: 'Before compaction',
      auditCompactionAfter: 'After compaction',
      auditCompactionActions: 'Compaction actions',
      auditRunAttempts: 'Linked runs',
      auditError: 'Error',
      auditViewContent: 'Read result content',
      auditContentUnavailable: 'Result content is unavailable or outside its retention period.',
      auditPrevious: 'Previous',
      auditNext: 'Next',
    },
    adminMonitoring: {
      ariaLabel: 'System monitoring',
      title: 'Runtime status',
      serviceHealth: 'Service health check',
      apiService: 'API service',
      status: 'Current status',
      healthy: 'Operational',
      unavailable: 'Unavailable',
      checking: 'Checking',
      lastChecked: 'Last checked',
      notChecked: 'Not checked yet',
      refresh: 'Refresh runtime status',
      loadFailed: 'Service health check failed.',
    },
    databaseBackup: {
      ariaLabel: 'Data backup',
      title: 'Data backup',
      backupTitle: 'Full data backup',
      download: 'Download full backup',
      downloading: 'Preparing backup',
      restoreTitle: 'Overwrite restore',
      restoreWarning: 'Restoring replaces all current data and requires a new sign-in.',
      chooseFile: 'Choose backup file',
      noFileSelected: 'No file selected',
      clearFile: 'Clear selected file',
      overwrite: 'Overwrite all data',
      restoring: 'Restoring',
      confirmTitle: 'Overwrite all data?',
      confirmDescription: 'This cannot be undone. The backup file replaces all current data.',
      cancel: 'Cancel',
      confirmOverwrite: 'Overwrite data',
      downloadFailed: 'Database backup download failed.',
      restoreFailed: 'Database backup restore failed.',
    },
    sessionMonitoring: {
      ariaLabel: 'Session monitoring',
      title: 'Session monitoring',
      lastRefreshed: 'Last refreshed',
      notRefreshed: 'Not refreshed yet',
      refresh: 'Refresh session list',
      validSessions: 'Valid sessions',
      activeSessions: 'Active sessions',
      validUsers: 'Users represented',
      searchPlaceholder: 'Search user ID, email, or name',
      search: 'Search',
      activityLabel: 'Session status',
      activityFilters: { ALL: 'All', ACTIVE: 'Active', IDLE: 'Idle' },
      user: 'User',
      userStatus: 'User status',
      sessionStatus: 'Session status',
      createdAt: 'Created',
      lastAccessedAt: 'Last accessed',
      expiresAt: 'Expires',
      currentSession: 'Current session',
      actions: 'Actions',
      userStatuses: { ACTIVE: 'Active', DISABLED: 'Disabled', DELETED: 'Deleted' },
      activities: { ACTIVE: 'Active', IDLE: 'Idle' },
      empty: 'No valid sessions match the current filters.',
      loading: 'Loading sessions...',
      loadFailed: 'Failed to load session list.',
      operationFailed: 'Failed to sign out the session.',
      alreadyOffline: 'The target session is already offline.',
      revoke: 'Sign out',
      revokeSession: (user) => `Sign out ${user}'s session`,
      currentSessionHint: 'Use sign out to end the current session.',
      confirmTitle: 'Sign out session',
      confirmDescription: (user, lastAccessedAt) => `This signs out ${user}'s session, last accessed ${lastAccessedAt}.`,
      connectionNotice: 'After sign-out, later requests need a new sign-in. Established requests or streams may continue until the current operation completes.',
      revoking: 'Signing out...',
    },
    sessionPolicy: {
      ariaLabel: 'Session policies',
      title: 'Session policies',
      pageDescription: 'New sessions use the highest-priority enabled match. Existing sessions are not changed when policies are updated.',
      create: 'New policy',
      createTitle: 'New session policy',
      editTitle: 'Edit session policy',
      dialogDescription: 'The policy type is fixed to auth.user-session.v1. Priority is managed by generic policy ordering.',
      refresh: 'Refresh policy list',
      searchPlaceholder: 'Search policy name or description',
      search: 'Search',
      priority: 'Priority',
      name: 'Policy name',
      description: 'Description',
      scope: 'Scope',
      allUsers: 'All users',
      selectedSubjects: 'Selected users or groups',
      scopeSummary: (userCount, groupCount) => `${userCount} selected users, ${groupCount} selected user groups`,
      subjectType: 'Subject type',
      subjectTypes: { USER: 'User', GROUP: 'User group' },
      subjectSearchPlaceholder: 'Search name, email, or user group',
      subjectLoading: 'Finding available subjects...',
      subjectLoadFailed: 'Failed to load available users or user groups.',
      savedUserSubject: 'Saved user',
      savedGroupSubject: 'Saved user group',
      removeSubject: (label) => `Remove ${label}`,
      remove: 'Remove',
      subjectEmpty: 'No available subjects match the search.',
      subjectRequired: 'A selected scope needs at least one user or user group.',
      maxSessions: 'Maximum valid sessions',
      absoluteTimeout: 'Absolute timeout',
      absoluteTimeoutSeconds: 'Absolute timeout (seconds)',
      duration: (value, unit) => `${value} ${value === '1' ? unit.slice(0, -1) : unit}`,
      status: 'Status',
      statusAll: 'All statuses',
      statuses: { ENABLED: 'Enabled', DISABLED: 'Disabled' },
      updatedAt: 'Updated',
      actions: 'Actions',
      empty: 'No session policies match the current filters.',
      loading: 'Loading session policies...',
      loadFailed: 'Failed to load session policies.',
      nameRequired: 'Enter a policy name.',
      valueInvalid: 'Maximum valid sessions and absolute timeout must be positive integers.',
      save: 'Save',
      saving: 'Saving...',
      saveFailed: 'Failed to save session policy.',
      saveSucceeded: 'Session policy saved.',
      editPolicy: (name) => `Edit policy ${name}`,
      delete: 'Delete',
      deleting: 'Deleting...',
      deleteTitle: 'Delete session policy',
      deleteDescription: (name) => `Delete policy "${name}". Existing sessions are not changed.`,
      deletePolicy: (name) => `Delete policy ${name}`,
      deleteFailed: 'Failed to delete session policy.',
      deleteSucceeded: 'Session policy deleted.',
    },
    learningPlanPolicy: {
      ariaLabel: 'Learning plan policies',
      title: 'Learning plan policies',
      pageDescription: 'Configure learning plan creation limits for all users, user groups, or selected users by priority.',
      create: 'New policy',
      createTitle: 'New learning plan policy',
      editTitle: 'Edit learning plan policy',
      dialogDescription: 'The policy type is fixed to learning-plan.creation.v1.',
      refresh: 'Refresh policy list',
      priority: 'Priority',
      name: 'Policy name',
      description: 'Description',
      scope: 'Scope',
      allUsers: 'All users',
      selectedSubjects: 'Selected users or groups',
      scopeSummary: (userCount, groupCount) => `${userCount} selected users, ${groupCount} selected user groups`,
      subjectType: 'Subject type',
      subjectTypes: { USER: 'User', GROUP: 'User group' },
      subjectSearchPlaceholder: 'Search name, email, or user group',
      subjectLoading: 'Finding available subjects...',
      subjectLoadFailed: 'Failed to load available users or user groups.',
      savedUserSubject: 'Saved user',
      savedGroupSubject: 'Saved user group',
      removeSubject: (label) => `Remove ${label}`,
      remove: 'Remove',
      subjectEmpty: 'No available subjects match the search.',
      subjectRequired: 'A selected scope needs at least one user or user group.',
      maxSavedPlans: 'Saved plan limit',
      dailyDraftCreationLimit: 'Daily draft limit',
      draftRetentionDays: 'Draft retention days',
      retentionValue: (days) => `${days} days`,
      status: 'Status',
      statuses: { ENABLED: 'Enabled', DISABLED: 'Disabled' },
      updatedAt: 'Updated',
      actions: 'Actions',
      empty: 'No learning plan policies are configured. Runtime code defaults are active.',
      loading: 'Loading learning plan policies...',
      loadFailed: 'Failed to load learning plan policies.',
      nameRequired: 'Enter a policy name.',
      valueInvalid: 'Saved plan and daily draft limits must be integers from 0 to 1000. Draft retention must be an integer from 1 to 365.',
      save: 'Save',
      saving: 'Saving...',
      saveFailed: 'Failed to save learning plan policy.',
      saveSucceeded: 'Learning plan policy saved.',
      moveUpPolicy: (name) => `Move policy ${name} up`,
      moveDownPolicy: (name) => `Move policy ${name} down`,
      orderFailed: 'Failed to update learning plan policy priority.',
      orderSucceeded: 'Learning plan policy priority updated.',
      editPolicy: (name) => `Edit policy ${name}`,
      delete: 'Delete',
      deleting: 'Deleting...',
      deleteTitle: 'Delete learning plan policy',
      deleteDescription: (name) => `Delete policy "${name}". Affected users will match the next policy or runtime defaults.`,
      deletePolicy: (name) => `Delete policy ${name}`,
      deleteFailed: 'Failed to delete learning plan policy.',
      deleteSucceeded: 'Learning plan policy deleted.',
    },
    adminUserAi: {
      title: 'AI usage & controls',
      loading: 'Loading AI information...',
      loadFailed: 'Failed to load AI information.',
      policyLoadFailed: 'Failed to load AI policy.',
      usageLoadFailed: 'Failed to load AI usage summary.',
      effectiveStatus: 'Effective AI status',
      normal: 'Enabled normally',
      globalDisabled: 'Disabled globally',
      userPaused: 'User paused',
      inherited: 'Inherited',
      paused: 'Paused',
      pauseUser: 'Pause AI',
      resumeUser: 'Restore inheritance',
      defaultLimit: 'Global default limit',
      overrideLimit: 'User limit override',
      effectiveLimit: 'Effective limit',
      saveOverride: 'Save limit',
      restoreInheritance: 'Restore inheritance',
      todayEntryRequests: 'Today entry requests',
      todayTokens: 'Today tokens',
      todayEstimatedCost: 'Today estimated cost',
      last7DaysEstimatedCost: 'Last 7 days estimated cost',
      last30DaysEstimatedCost: 'Last 30 days estimated cost',
      viewFullUsage: 'View full usage',
      confirmPauseTitle: 'Pause this user\'s AI',
      confirmPauseDescription: 'After pausing, this user\'s governed AI calls will be rejected.',
      confirmResumeTitle: 'Restore this user\'s inherited AI policy',
      confirmResumeDescription: 'After restoring, this user will inherit the global AI state and limit again.',
      limitInvalid: 'Enter a whole number from 1 to 10000, or restore inheritance.',
      saveFailed: 'Failed to save user AI policy.',
    },
    betaAccess: {
      ariaLabel: 'Beta access management',
      title: 'Beta access',
      searchPlaceholder: 'Search allowlisted email',
      search: 'Search',
      refresh: 'Refresh',
      loading: 'Loading allowlist...',
      empty: 'No matching allowlisted emails',
      loadFailed: 'Failed to load beta access data',
      forbidden: 'You do not have permission to manage beta access',
      backHome: 'Back to dashboard',
      allowlistSetting: 'Email allowlist',
      enabled: 'Enabled',
      disabled: 'Disabled',
      batchAdd: 'Add emails in bulk',
      batchPlaceholder: 'One email per line, or separate with commas',
      batchLimit: 'Up to 100 emails per request',
      emailInputRequired: 'Enter at least one email.',
      add: 'Add',
      saving: 'Working',
      batchResult: 'Add results',
      batchSummary: (added, existing, invalid) => `Added ${added}, existing ${existing}, invalid ${invalid}`,
      addStatus: {
        ADDED: 'Added',
        EXISTING: 'Existing',
        INVALID: 'Invalid',
      },
      email: 'Email',
      registration: 'Registration',
      registered: 'Registered',
      unregistered: 'Not registered',
      userStatus: 'User status',
      userStatuses: {
        ACTIVE: 'Active',
        DISABLED: 'Disabled',
        DELETED: 'Deleted',
      },
      createdBy: 'Added by',
      createdAt: 'Added at',
      actions: 'Actions',
      remove: 'Remove',
      removeEmail: (email) => `Remove ${email}`,
      confirm: 'Confirm',
      confirmEnableTitle: 'Enable the email allowlist',
      confirmEnableDescription: 'Once enabled, non-admin accounts must be allowlisted to register, sign in, or keep using the app.',
      confirmEnableEmptyDescription: 'The allowlist is empty. Enabling it immediately blocks every account except trusted administrators.',
      confirmDisableTitle: 'Disable the email allowlist',
      confirmDisableDescription: 'The allowlist will no longer restrict registration, sign-in, or authenticated requests.',
      confirmRemoveTitle: 'Remove allowlisted email',
      confirmRegisteredRemoval: (email) => `Removing ${email} immediately revokes the linked user's sessions without deleting the account or learning data.`,
      confirmRemoval: (email) => `Remove ${email} from the allowlist?`,
      sessionRevocationWarning: 'The allowlist entry was removed, but session revocation failed. Real-time access checks will still reject subsequent requests.',
      operationFailed: 'Beta access operation failed. Please try again later.',
    },
    passwordChange: {
      ariaLabel: 'Change temporary password',
      title: 'Set a new password',
      description: 'This session can only change the password or sign out.',
      newPassword: 'New password',
      confirmPassword: 'Confirm new password',
      submit: 'Change password',
      submitting: 'Updating',
      logout: 'Log out',
      passwordMismatch: 'The passwords do not match.',
      passwordTooShort: 'The new password must contain at least 8 characters.',
      failed: 'Failed to update the password. Please try again later.',
    },
    common: {
      cancel: 'Cancel',
      create: 'Create',
      delete: 'Delete',
      deleting: 'Deleting',
      view: 'View',
      previousPage: 'Previous',
      nextPage: 'Next',
      pageStatus: (page, totalPages) => `Page ${page} / ${totalPages}`,
      empty: 'None',
      week: (count) => `${count} ${count === 1 ? 'week' : 'weeks'}`,
      hoursPerWeek: (count) => `${count}h/week`,
      characterCount: (current, max) => `${current} / ${max} characters`,
      byteCount: (current, max) => `${current} / ${max} bytes`,
      itemInputLimit: (count, maxCount, maxChars) => `${count} / ${maxCount} items, ${maxChars} characters each`,
      created: 'created',
      close: 'Close',
    },
    language: {
      label: 'Language',
      zhCN: '中文',
      enUS: 'English',
    },
    aiPreference: {
      title: 'AI Coach Preferences',
      subtitle: 'Choose the explanation style, follow-up pressure, and feedback tone for practice chat.',
      loading: 'Loading AI coach preferences...',
      loadFailed: 'Failed to load AI coach preferences',
      saveFailed: 'Failed to save AI coach preferences',
      saved: 'Saved',
      saving: 'Saving',
      coachStyle: 'Coach Style',
      coachStyleLabels: {
        GUIDED: 'Guided Coach',
        DIRECT: 'Direct Explainer',
      },
      coachStyleDescriptions: {
        GUIDED: 'Starts with hints and key observations, escalates only when you are stuck; will not hand over the answer by default.',
        DIRECT: 'Gives the full approach, complexity, pitfalls, and runnable code up front.',
      },
    },
    settingsPage: {
      ariaLabel: 'Personal settings',
      kicker: 'PREFERENCES',
      title: 'Settings',
      subtitle: 'Manage AI coaching, review strategy, appearance, and your current account without interrupting daily practice.',
      learningTitle: 'AI Coach',
      learningDescription: 'Controls how practice chat explains solutions. Changes apply to the next AI response.',
      currentCoach: 'Current coach',
      reviewTitle: 'Review Strategy',
      reviewDescription: 'Tune FSRS retention, daily review load, and the maximum review interval.',
      reviewLoading: 'Loading review settings...',
      reviewLoadFailed: 'Failed to load review settings',
      reviewSaveFailed: 'Failed to save review settings',
      reviewSaving: 'Saving',
      advancedReviewTitle: 'Advanced review settings',
      advancedReviewDescription: 'FSRS parameters directly affect review frequency. Keep the defaults unless you have a specific reason to change them.',
      fsrsParameters: 'FSRS Parameters',
      desiredRetention: 'Desired retention',
      desiredRetentionDescription: 'Higher values schedule reviews more often and reduce forgetting risk.',
      dailyNewLimit: 'Daily new cards',
      dailyNewLimitDescription: 'Cards entering the queue for the first time today. Use 0 to pause new cards.',
      dailyLearningLimit: 'Learning limit',
      dailyLearningLimitDescription: 'Due cards currently in learning or relearning state.',
      dailyReviewLimit: 'Review limit',
      dailyReviewLimitDescription: 'Due cards currently in review state.',
      maximumIntervalDays: 'Maximum interval',
      maximumIntervalDaysDescription: 'Caps the longest interval FSRS can schedule.',
      enableFuzzing: 'Enable interval fuzzing',
      enableFuzzingDescription: 'Slightly spreads nearby due dates to avoid review spikes.',
      helpSuffix: ' help',
      accountTitle: 'Account',
      accountDescription: 'Review the current identity or end this session.',
      signedInAs: 'Signed in as',
      activeStatus: 'Account active',
      passwordTitle: 'Sign-in password',
      passwordNotConfigured: 'Not configured. Add email and password sign-in.',
      passwordConfigured: 'Configured. You can sign in with email and password.',
      passwordEmailUnavailable: 'Account information is incomplete, so a password cannot be set yet.',
      setPassword: 'Set password',
      changePassword: 'Change password',
      passwordDialogSetTitle: 'Set sign-in password',
      passwordDialogChangeTitle: 'Change sign-in password',
      passwordDialogSetDescription: 'You can then sign in with your current email and this password. Third-party sign-in remains available.',
      passwordDialogChangeDescription: 'Changing the password signs out your other active sessions.',
      currentPassword: 'Current password',
      newPassword: 'New password',
      confirmNewPassword: 'Confirm new password',
      passwordMinimumLength: 'At least 8 characters',
      showPassword: 'Show password',
      hidePassword: 'Hide password',
      passwordUpdating: 'Updating...',
      passwordUpdate: 'Update password',
      passwordMismatch: 'The new passwords do not match',
      passwordTooShort: 'The new password must contain at least 8 characters',
      passwordUpdateFailed: 'Password update failed',
      passwordCreated: 'Sign-in password set. You can now use your current email and this password.',
      passwordUpdated: 'Password updated. Your other active sessions have been signed out.',
    },
    myPage: {
      profileKicker: 'PERSONAL TRAINING CENTER',
      title: 'My Learning Profile',
      coachPanelEyebrow: 'COACHING MODE',
      abilityPanelEyebrow: 'ABILITY PROFILE',
      selectedCoach: 'Current coach',
      dataPending: 'Syncing',
      noData: 'No data',
      statEvaluatedTags: 'Covered Tags',
      statAverageScore: 'Average Ability',
      statReviewedProblems: 'Reviewed Problems',
      statPrimaryStrength: 'Primary Strength',
      diagnosisSummaryTitle: 'Diagnostic Summary',
      currentStrength: 'Current Strength',
      currentStrengthDetail: (label, score, reviewedProblems) => `${label} is currently at ${score} across ${reviewedProblems} reviewed ${reviewedProblems === 1 ? 'problem' : 'problems'}, giving today\'s practice a stable anchor.`,
      breakthroughAdvice: 'Breakthrough Advice',
      breakthroughAdviceDetail: (label) => `Start one foundational ${label} problem today to strengthen a weaker area in the ability map.`,
      abilitySummaryTitle: 'Ability profile summary',
      strongestTag: 'Strongest',
      topAbilities: 'Top Abilities',
      selectedAbilityTags: 'Selected ability bubbles',
      expandAbilityProfile: 'Expand ability profile',
      abilityDetailTitle: 'Ability Profile Details',
      abilityDetailSubtitle: (max) => `Explore the selected ability topics, with up to ${max} tags.`,
      closeAbilityDetail: 'Close ability profile details',
      selectedTagCount: (selected, max) => `${selected}/${max} tags`,
      minimumTagCount: (min) => `Keep at least ${min} tags`,
      minimumSelectionNotice: (min) => `Keep at least ${min} tags so the ability map remains useful.`,
      maximumSelectionNotice: (max) => `Select up to ${max} tags.`,
      removeSelectedTag: (label) => `Remove ${label}`,
      abilityReplacementHint: 'Selecting a new tag below replaces the earliest selected tag.',
      abilityHeatmapEyebrow: 'ABILITY COVERAGE',
      abilityHeatmapTitle: 'All-tag Ability Heatmap',
      abilityHeatmapHint: 'Cell intensity follows ability score',
      addHeatmapTag: (label) => `Add ${label}`,
      selectedHeatmapTag: (label) => `${label} is selected`,
      removeHeatmapTag: (label) => `Remove ${label}`,
      catalogProblemsValue: (count) => `${count} catalog ${count === 1 ? 'problem' : 'problems'}`,
      noTopAbilities: 'Finish more code reviews to surface top ability tags here.',
      tagCoverage: (reviewed, total) => `${reviewed}/${total}`,
      scoreValue: (score) => `${score} pts`,
      reviewedProblemsValue: (count) => `${count} ${count === 1 ? 'problem' : 'problems'}`,
      memoryEyebrow: 'LEARNING MEMORY',
      memoryTitle: 'Learning Memory',
      memorySubtitle: 'Current learning background, long-term observations, and topic assessments.',
      memoryLoading: 'Loading learning memory...',
      memoryLoadFailed: 'Failed to load learning memory',
      memoryEmpty: 'No learning memory yet. More practice and conversations about your goals or preferences will add context here over time.',
      memoryUpdatedAt: (value) => `Updated ${value}`,
      openCitation: (displayNumber) => `Open evidence for statement ${displayNumber}`,
      evidenceEyebrow: 'EVIDENCE',
      evidenceDrawerTitle: (displayNumber) => `Evidence for statement ${displayNumber}`,
      closeEvidenceDrawer: 'Close evidence drawer',
      evidenceLoading: 'Loading evidence...',
      evidenceLoadFailed: 'Failed to load evidence',
      evidenceEmpty: 'No displayable evidence yet.',
      evidenceLoadMore: 'Load more',
      evidenceLoadingMore: 'Loading...',
      evidenceEnd: 'All evidence is shown.',
      evidenceRoles: {
        OBSERVED: 'Observed',
        PERSISTED: 'Persisted later',
        RESOLVED: 'Resolved later',
        REGRESSED: 'Regressed later',
        CONTRADICTS: 'Contradicting record',
        DECLARED: 'Your statement',
        CORRECTED: 'Your correction',
      },
      reviewEvidenceSummary: (versionNo, score, passed) => `Version ${versionNo} · ${score} points · ${passed ? 'Passed' : 'Not passed'}`,
      viewReviewSubmission: 'View this submission',
      messageEvidenceTitle: (role) => role === 'DECLARED'
        ? 'From your statement in problem chat'
        : 'From your correction in problem chat',
    },
    home: {
      ariaLabel: 'Dashboard',
      kicker: 'ALGORITHM LEARNING SYSTEM',
      title: 'Master LeetCode with',
      titleHighlight: 'Smart Review System',
      subtitle: 'make your LeetCode review easier',
      generatePlan: 'Start Reviewing',
      startUsing: 'Start using',
      browseProblems: 'Browse Problems',
      previewLabel: 'Learning workspace preview',
      previewFocusLabel: 'This Week',
      previewFocusValue: 'Array, Hash Table, Two Pointers',
      previewTaskOne: 'Finish 5 foundation problems',
      previewTaskOneStatus: 'In progress',
      previewTaskTwo: 'Review Two Sum approach',
      previewTaskTwoStatus: 'Today',
      previewTaskThree: 'Update next week plan',
      previewTaskThreeStatus: 'Sunday',
      companyStripLabel: 'Target companies',
      companyStripTitle: 'PREP FOR INTERVIEWS AT',
      reviewCardCta: 'REVIEW CARD',
      capabilitiesKicker: 'CAPABILITIES',
      capabilitiesTitle: 'High-frequency training entry points stay close',
      featurePlanTitle: 'Learning Plans',
      featurePlanDescription: 'Generate staged practice plans from goals, time, strengths, and weak spots.',
      featureProblemTitle: 'Problem Practice',
      featureProblemDescription: 'Manage problems, difficulty, and tags so the next useful practice set is easy to reach.',
      featureAiTitle: 'AI Explanations',
      featureAiDescription: 'Ask through ideas, edge cases, and complexity so each problem becomes a reusable pattern.',
      loopKicker: 'LEARNING LOOP',
      loopTitle: 'A simple loop for long-term retention',
      loopLabel: 'Algorithm learning loop',
      stepPickTitle: 'Pick',
      stepPickDescription: 'Choose the focus from a plan or the library.',
      stepPracticeTitle: 'Practice',
      stepPracticeDescription: 'Reason independently first, then record blockers.',
      stepExplainTitle: 'Explain',
      stepExplainDescription: 'Use AI to fill in ideas, templates, and edge cases.',
      stepReviewTitle: 'Review',
      stepReviewDescription: 'Revisit by plan so solved problems do not disappear.',
      ctaLabel: 'Start learning',
      ctaTitle: 'Start today from one plan',
      ctaDescription: 'Set your learning objective and time first, then let the system propose phases, problems, and review points.',
      enterPlans: 'Open Plans',
      workspaceAriaLabel: 'Learning workbench',
      workspaceKicker: 'WORKBENCH',
      workspaceTitle: 'Today Workbench',
      workspaceSectionsLabel: 'Dashboard workbench modules',
      recentPracticeTitle: 'Recent Practice',
      recentPracticeEmpty: 'Recent problems and review state will appear here.',
      planPreviewTitle: 'Learning Plan',
      planPreviewEmpty: 'Current phases and recommended tasks will appear here.',
      reviewQueueTitle: 'Review Queue',
      reviewQueueEmpty: 'Code reviews and missed problems to revisit will appear here.',
      abilityMapTitle: 'Ability Pool',
      abilityMapSubtitle: 'Common tags · review evidence · 10-point scale',
      abilityLoading: 'Loading ability profile...',
      abilityLoadFailed: 'Failed to load ability profile',
      abilityEmpty: 'No ability profile data',
    },
    todayPack: {
      homeLoadFailed: 'Failed to load the dashboard',
      reviewSummaryLoadFailed: 'Failed to load the review summary',
      loadingStatus: 'Loading',
      trainingStatusUnavailable: 'Today\'s training status is temporarily unavailable.',
      reviewStatusLoading: 'Loading review status',
      reviewStatusUnavailable: 'Review status is temporarily unavailable',
      reviewDue: (count) => `${count} ${count === 1 ? 'review' : 'reviews'} due today`,
      reviewUpcoming: (count, time) => `${count} more ${count === 1 ? 'review' : 'reviews'} today, available ${time}`,
      todayCompleted: 'Done for today',
      reviewStart: (count) => `Start ${count} ${count === 1 ? 'review' : 'reviews'}`,
      reviewSchedule: 'View today\'s review schedule',
      homeAriaLabel: 'Dashboard',
      trainingEntryAriaLabel: 'Today pack entry',
      todayPack: 'Today Pack',
      startTraining: 'Start Training',
      activePlanRhythm: (daily, daysPerWeek, remaining) => `${daily}/day · ${daysPerWeek} days/week · ${remaining} remaining`,
      noPlanGuidance: 'Activate a learning plan to have the dashboard organize the most useful training for each day.',
      startTodayTraining: 'Start Today\'s Training',
      choosePlan: 'Create or activate a plan',
      reviewEntryAriaLabel: 'Review center entry',
      reviewCenter: 'Review Center',
      reviewDescription: 'Recall first, then rate your memory so missed problems return when they matter instead of becoming a static list.',
      diagnosisTitle: 'Learning Diagnosis',
      diagnosisDescription: 'Continuously updated from practice and review.',
      viewFullProfile: 'View Full Profile',
      abilityUnavailable: 'The ability profile is temporarily unavailable. Today\'s training remains accessible.',
      averageAbility: 'Average Ability',
      currentStrength: 'Current Strength',
      none: 'None yet',
      strengthEvidence: (count) => `Based on ${count} reviewed ${count === 1 ? 'problem' : 'problems'}.`,
      nextBreakthrough: 'Next Breakthrough',
      breakthroughFallback: 'Keep building review evidence',
      breakthroughAdvice: (label) => `Prioritize one foundational ${label} problem today.`,
      weeklyRhythm: 'This Week\'s Rhythm',
      dailyTraining: 'Daily Training',
      weeklyTarget: 'Weekly Target',
      remainingProblems: 'Remaining',
      problemCount: (count) => `${count} ${count === 1 ? 'problem' : 'problems'}`,
      managePlan: 'Manage Learning Plan',
      noActivePlan: 'No active learning plan',
      noActivePlanDescription: 'Create a plan to see its weekly rhythm and remaining work here.',
      viewPlans: 'View Training Plans',
      packLoadFailed: 'Failed to load today\'s pack',
      resetConfirm: 'Today\'s pack will restart from today. Completed and skipped problems will be kept.',
      packResetFailed: 'Failed to reset today\'s pack',
      loadingPack: 'Loading Today Pack',
      packAriaLabel: 'Today Pack',
      activePackSummary: (title, startDate, daily, localDate) => `${title} · Started ${startDate} · ${daily}/day · Today ${localDate}`,
      packIntroduction: 'Start with a recommended plan, then switch plans freely from the plans page.',
      restart: 'Restart From Today',
      restartHelpAriaLabel: 'About restarting from today',
      restartHelp: 'Reset the pack starting point to today and clear carried-over backlog. Completed and skipped records are kept.',
      emptyPlanTitle: 'No active training plan',
      emptyPlanDescription: 'Create or activate a plan to generate today\'s pack from its training rhythm.',
      planCompletedTitle: 'Plan Complete',
      planCompletedDescription: 'Every problem in the active plan is completed or skipped. You can still browse its history and continue conversations.',
      doneTodayTitle: 'Done for Today',
      doneTodayDescription: 'There are no carried-over or newly scheduled problems for today.',
      stopToday: 'Finish for Today',
      nextPack: 'Load Next Pack',
      futurePack: (date) => `Pack for ${date}`,
      sectionProblemCount: (count) => `${count} ${count === 1 ? 'problem' : 'problems'}`,
      carryover: (days, date) => `Carried over ${days} ${days === 1 ? 'day' : 'days'} · ${date}`,
      scheduledDate: (date) => `Scheduled ${date}`,
      emptyDay: 'No problems scheduled for this day',
      packTotal: (count) => `${count} ${count === 1 ? 'problem' : 'problems'} in this pack`,
      backToday: 'Back to Today',
      statusNoActivePlan: 'No active training plan',
      statusPlanCompleted: 'The active plan is complete',
      statusDoneWithNext: (date) => `Done for today, next pack ${date}`,
      statusDone: 'Done for today',
      statusDue: (count) => `${count} ${count === 1 ? 'problem' : 'problems'} to practice today`,
      statusEmpty: 'Nothing scheduled today',
    },
    reviewCenter: {
      sourceLabels: { REVIEW_FAILED: 'Mistake', REVIEW_PASSED: 'Review', USER_MARKED: 'Manually added' },
      ratingLabels: { AGAIN: 'Again', HARD: 'Hard', GOOD: 'Good', EASY: 'Easy' },
      ratingDescriptions: {
        AGAIN: 'Could not recall the approach',
        HARD: 'Recalled it with difficulty or gaps',
        GOOD: 'Recalled the main approach independently',
        EASY: 'Recalled it quickly and completely',
      },
      fsrsStateLabels: { LEARNING: 'Learning', REVIEW: 'Review', RELEARNING: 'Relearning' },
      loadTodayReview: 'Loading today\'s reviews...',
      startTodayReview: (count) => `Start ${count} ${count === 1 ? 'review' : 'reviews'}`,
      availableAt: (time) => `Available ${time}`,
      todayCompleted: 'Done for today',
      cardLoadFailed: 'Failed to load review cards',
      summaryLoadFailed: 'Failed to load the review summary',
      archiveUpdateFailed: 'Failed to update review status',
      detailLoadFailed: 'Failed to load review card details',
      title: 'Review Center',
      overviewAriaLabel: 'Review overview',
      remainingToday: 'Remaining Today',
      reviewProblems: 'Review Problems',
      mistakes: 'Mistakes',
      filtersAriaLabel: 'Review filters',
      searchPlaceholder: 'Search problems or notes',
      mistakesOnly: 'Mistakes only',
      refreshCards: 'Refresh review cards',
      loadingCards: 'Loading review cards...',
      emptyCards: 'No review cards.',
      codeReviewTimelineAriaLabel: 'Code review timeline',
      codeReviewTimelinePointAriaLabel: (version, score, language, time) => `View code review V${version}, ${score} / 10, ${language}, ${time}`,
      codeReviewTimelineScore: (version, score) => `V${version} · ${score} / 10`,
      codeReviewTimelineFeedback: (feedback) => `Primary feedback: ${feedback}`,
      codeReviewTimelineFallbackFeedback: 'No clear issues found',
      codeReviewTimelineManualAriaLabel: 'Manually added, no code review yet',
      codeReviewTimelineManualTitle: 'No code review yet',
      codeReviewTimelineManualDescription: 'This problem was added manually and has no code review yet',
      codeReviewTimelineUnavailable: 'Review records are temporarily unavailable',
      lastRating: (rating) => `Last: ${rating}`,
      forgottenCount: (count) => `Forgotten ${count} ${count === 1 ? 'time' : 'times'}`,
      viewCardDetail: (title) => `View review card details for ${title}`,
      viewDetail: 'View details',
      restoreReview: 'Restore to reviews',
      removeFromReview: 'Remove from reviews',
      closeDetail: 'Close review card details',
      loadingDetail: 'Loading review card details...',
      recentHistory: 'Recent Review History',
      intervalChange: (before, after) => `Interval ${before}d → ${after}d`,
      noHistory: 'No review history.',
      dueUnknown: 'Review time pending',
      overdue: (days) => `${days} ${days === 1 ? 'day' : 'days'} overdue`,
      dueToday: 'Due today',
      reviewTomorrow: 'Review tomorrow',
      reviewInDays: (days) => `Review in ${days} days`,
      queueLoadFailed: 'Failed to load the review queue',
      ratingSubmitFailed: 'Failed to submit the review rating',
      discardNoteConfirm: 'The problem note has unsaved changes. Select OK to discard them or Cancel to return and save.',
      backToReviewCenter: 'Back to Review Center',
      spacedReview: 'Spaced Review',
      unknownDifficulty: 'Unknown difficulty',
      preparingQueue: 'Preparing the review queue...',
      queueCompleted: 'Today\'s Reviews Are Complete',
      loadingStatement: 'Loading the full problem statement...',
      fullStatementAriaLabel: 'Full problem statement',
      history: 'Review History',
      recentCount: (count) => `${count} recent ${count === 1 ? 'attempt' : 'attempts'}`,
      historyAfterRating: 'Your review history will appear here after rating this problem.',
      resultAriaLabel: 'Review result',
      nextReview: (value) => `Next review: ${value}`,
      ratingAriaLabel: 'Review rating',
      nextProblem: 'Next Problem',
      calculating: 'Calculating',
      minutesLater: (minutes) => `In ${minutes} minutes`,
      reviewLater: 'Review later',
      ratingButtonAriaLabel: (label, description, interval) => `${label}, ${description}, ${interval}`,
    },
    problemNotes: {
      loadFailed: 'Failed to load problem notes',
      saveFailed: 'Failed to save problem notes',
      loading: 'Loading',
      unsaved: 'Unsaved changes',
      existing: 'Note saved',
      empty: 'No note yet',
      title: 'My Problem Notes',
      updatedAt: (value) => ` · Updated ${value}`,
      loadingDetail: 'Loading problem notes...',
      retry: 'Retry',
      coachSummary: 'Coach Summary',
      coachSummaryPresent: 'Summary available',
      coachSummaryNotGenerated: 'Not generated yet',
      coachSummaryEmpty: 'No coach summary yet.',
      coachSummaryHint: 'During problem training, ask your coach to summarize the session and, after your confirmation, generate the content here.',
      conflict: 'This note was updated elsewhere. Reload it before editing again.',
      reload: 'Reload',
      saving: 'Saving',
      save: 'Save Note',
      inputLimitExceeded: 'Some content exceeds its input limit. Use the counters to shorten it before saving.',
      coreIdea: 'Core Idea',
      dataStructures: 'Data Structures',
      customDataStructures: 'Custom Data Structures',
      dataStructureNotes: 'Data Structure Notes',
      dataStructureNotesPlaceholder: 'Record how these data structures are used in this problem',
      algorithms: 'Algorithms',
      customAlgorithms: 'Custom Algorithms',
      algorithmNotes: 'Algorithm Notes',
      algorithmNotesPlaceholder: 'Record how the algorithm is used or its key steps',
      timeComplexity: 'Time Complexity',
      spaceComplexity: 'Space Complexity',
      edgeCases: 'Edge Cases and Pitfalls',
      complexityEmpty: 'Not filled in',
      customValueAriaLabel: (label) => `Custom value for ${label}`,
      customValuePlaceholder: 'For example, O(m+n)',
      dataStructureLabels: {
        ARRAY: 'Array', HASH_MAP: 'Hash Map', LINKED_LIST: 'Linked List', STACK: 'Stack', QUEUE: 'Queue',
        HEAP: 'Heap', TREE: 'Tree', GRAPH: 'Graph', TRIE: 'Trie', UNION_FIND: 'Union Find', OTHER: 'Other',
      },
      algorithmLabels: {
        TWO_POINTERS: 'Two Pointers', SLIDING_WINDOW: 'Sliding Window', BINARY_SEARCH: 'Binary Search',
        DFS: 'Depth-First Search', BFS: 'Breadth-First Search', BACKTRACKING: 'Backtracking', GREEDY: 'Greedy',
        DYNAMIC_PROGRAMMING: 'Dynamic Programming', PREFIX_SUM: 'Prefix Sum', SORTING: 'Sorting',
        MONOTONIC_STACK: 'Monotonic Stack', DIJKSTRA: 'Dijkstra', OTHER: 'Other',
      },
      complexityLabels: {
        O_1: 'O(1)', O_LOG_N: 'O(log n)', O_N: 'O(n)', O_N_LOG_N: 'O(n log n)',
        O_N2: 'O(n²)', O_N3: 'O(n³)', O_2N: 'O(2ⁿ)', OTHER: 'Other',
      },
    },
    problems: {
      ariaLabel: 'Problems',
      searchLabel: 'Search problems',
      searchPlaceholder: 'Search title, slug, or ID',
      difficulty: 'Difficulty',
      difficultyFilter: 'Difficulty filter',
      allDifficulty: 'All Difficulty',
      company: 'Company',
      companyFilter: 'Company filter',
      allCompanies: 'All Companies',
      role: 'Role',
      roleFilter: 'Role filter',
      allRoles: 'All Roles',
      recencyBucket: 'Recency',
      recencyBucketFilter: 'Recency filter',
      allRecencyBuckets: 'All Recency',
      sort: 'Sort',
      sortFilter: 'Sort order',
      sortFrontendAsc: 'ID ascending',
      sortCompanyFrequencyDesc: 'Company frequency',
      companySignalBadge: 'Company signal',
      cnOnlyBadge: 'Chinese only',
      listTitle: 'Problem List',
      totalCount: (count) => `${count} ${count === 1 ? 'problem' : 'problems'}`,
      loadingList: 'Loading problems...',
      emptyList: 'No matching problems',
      previousPage: 'Previous page',
      nextPage: 'Next page',
      loadingDetail: 'Loading details...',
      recommendationReason: 'Recommendation reason',
      sampleInput: 'Sample Input',
      pythonTemplate: 'Python3 Template',
      selectProblem: 'Select a problem to view details',
      listLoadFailed: 'Failed to load problem list',
      filtersLoadFailed: 'Failed to load problem filters',
      detailLoadFailed: 'Failed to load problem details',
    },
    learningPlans: {
      ariaLabel: 'Learning plans',
      detailAriaLabel: 'Learning plan details',
      createAriaLabel: 'Create learning plan',
      listLoadFailed: 'Failed to load learning plans',
      detailLoadFailed: 'Failed to load learning plan details',
      deleteFailed: 'Failed to delete learning plan',
      confirmDelete: 'Delete this learning plan?',
      activateConfirm: 'Today\'s pack will be generated from the new plan. Progress in the previous plan will be kept.',
      activateFailed: 'Failed to switch learning plans',
      currentActive: 'Active',
      todayPack: 'Today Pack',
      activating: 'Switching...',
      activate: 'Activate',
      todayPackProblem: 'In Today Pack',
      loadingDetail: 'Loading plan details...',
      loadingPracticeChat: 'Loading problem chat...',
      overviewTitle: 'Learning Plans',
      overviewDescription: 'Generate training plans from goals, time, current level, and your own notes.',
      newPlan: 'New Plan',
      overviewStats: 'Plan overview',
      total: 'Plans',
      latestCreated: 'Latest',
      listTitle: 'Plan Library',
      totalPlans: (count) => `${count} ${count === 1 ? 'plan' : 'plans'}`,
      emptyTitle: 'No saved plans',
      emptyDescription: 'Create a plan to keep goals, timeline, and problem work in one place.',
      planParameters: 'Plan parameters',
      planProgressCount: (completed, total) => `${completed} / ${total} problems`,
      planProgressAriaLabel: (completed, total, percent) => `Plan progress: ${completed} of ${total} completed, ${percent}%`,
      planProgressHelper: 'Completed',
      viewPlan: (title) => `View ${title}`,
      deletePlan: (title) => `Delete ${title}`,
      currentRhythm: 'Current Rhythm',
      unspecified: 'Not specified',
      backToList: 'Back to Library',
      backToPlans: 'Back to Plans',
      backToPlanDetail: 'Back to Plan',
      backToPracticeChat: 'Back to Chat',
      backToLearnerProfile: 'Back to Learning Profile',
      learningPlanEyebrow: 'Learning Plan',
      practiceChatEyebrow: 'Practice Chat',
      generateStart: 'Starting plan generation',
      generateFailed: 'Failed to generate learning plan',
      followUpFailed: 'Failed to submit follow-up',
      saveFailed: 'Failed to save learning plan',
      revisionInstructionLabel: 'Want changes? Describe how to revise this plan',
      reviseDraft: 'Revise Plan',
      revisionFailed: 'Failed to revise the learning plan. Try again later.',
      extensionEntryLabel: 'Want to keep learning? Describe your next objective',
      generateExtension: 'Generate Extension',
      pendingExtensionTitle: 'Pending Extension',
      reviseExtensionLabel: 'Want changes to this extension? Describe the adjustment',
      reviseExtension: 'Revise Extension',
      applyExtension: 'Apply Extension',
      discardExtension: 'Discard',
      extensionFailed: 'Failed to generate the extension. Please try again later.',
      extensionApplyFailed: 'Failed to apply the extension. Generate a new one and try again.',
      createTitle: 'Create Learning Plan',
      generatePlan: 'Generate Plan',
      generateDraft: 'Generate Draft',
      generating: 'Generating',
      createMode: 'Creation method',
      createWithAi: 'AI Personalization',
      createFromTemplate: 'From Template',
      templateLoading: 'Loading templates...',
      templateDetailLoading: 'Loading template details...',
      templateLoadFailed: 'Failed to load learning plan templates',
      templateDetailLoadFailed: 'Failed to load learning plan template details',
      templateViewContent: 'View Template',
      templateViewContentFor: (title) => `View ${title} template content`,
      templateDetailEyebrow: 'Template Content',
      templateOverview: 'Template overview',
      templateGoal: 'Training Goal',
      templatePhaseRoute: 'Training Route',
      templatePhaseRouteTitle: 'Phase-by-Phase Content',
      templatePhaseSummary: (weeks, problems) => (
        `${weeks} ${weeks === 1 ? 'week' : 'weeks'} · ${problems} ${problems === 1 ? 'problem' : 'problems'}`
      ),
      templateFitEyebrow: 'Fit Check',
      templateFitTitle: 'Before You Start',
      templatePrerequisites: 'Prerequisites',
      templateRecommendedFor: 'Recommended For',
      templateNotRecommendedFor: 'Not Recommended For',
      templateSource: 'Template source',
      templateOpenSource: 'View source material',
      templateEmpty: 'No templates available',
      templateGenerateStart: 'Generating a plan from the template',
      templateGenerateFailed: 'Failed to generate a plan from the template',
      templateGenerateDraft: 'Generate from Template',
      templateSelected: 'Selected Template',
      templateCatalog: 'Template catalog',
      templateCatalogRecommended: 'Recommended',
      templateCatalogSystematicLearning: 'Systematic Learning',
      templateCatalogInterviewPrep: 'Interview Prep',
      templateCatalogTopicBreakthrough: 'Topic Breakthrough',
      templateCatalogLanguageAndRole: 'Language & Role',
      templateCatalogEmpty: 'No templates are available in this category',
      templateRecommendedBadge: 'Recommended',
      templateDefaultRhythm: (weeks, hours) => `${weeks} ${weeks === 1 ? 'week' : 'weeks'} · ${hours}h/week default`,
      templateRouteSummary: (problems, weeks, hours) => (
        `${problems} practice problems · recommended ${weeks} ${weeks === 1 ? 'week' : 'weeks'} · ${hours}h/week`
      ),
      templateRhythm: 'Training Rhythm',
      dailyProblemCount: 'Problems per day',
      trainingDaysPerWeek: 'Training days/week',
      rhythmEstimateLine: (problems, weeks) => `At this rhythm, all ${problems} problems take about ${weeks} weeks`,
      standardRhythmTitle: 'Standard Plan',
      standardRhythmMainLine: (dailyProblems, trainingDays, recommendedWeeks) => (
        `${dailyProblems} per day · ${trainingDays} days/week · recommended ${recommendedWeeks} ${recommendedWeeks === 1 ? 'week' : 'weeks'}`
      ),
      standardRhythmReason: (problems, recommendedWeeks, trainingDays) => (
        `Calculated from ${problems} problems / recommended ${recommendedWeeks} ${recommendedWeeks === 1 ? 'week' : 'weeks'} / ${trainingDays} days per week, suitable as a stable starting point with weekends reserved for review or buffer.`
      ),
      currentRhythmEstimateLine: (dailyProblems, trainingDays, problems, weeks) => (
        `Your current choice: ${dailyProblems} per day · ${trainingDays} days/week; all ${problems} problems take about ${weeks} ${weeks === 1 ? 'week' : 'weeks'}`
      ),
      standardRhythmRemainingLine: (remainingProblems, weeks) => (
        `${remainingProblems} ${remainingProblems === 1 ? 'problem' : 'problems'} left; standard pace takes about ${weeks} ${weeks === 1 ? 'week' : 'weeks'}`
      ),
      rhythmFasterThanStandard: (weeks) => `About ${weeks} ${weeks === 1 ? 'week' : 'weeks'} faster than standard`,
      rhythmSlowerThanStandard: (weeks) => `About ${weeks} ${weeks === 1 ? 'week' : 'weeks'} slower than standard`,
      rhythmSameAsStandard: 'About the same as standard',
      rhythmConfigLine: (dailyProblems, trainingDays) => `${dailyProblems} per day · ${trainingDays} days/week`,
      totalProblemCountLine: (problems) => `${problems} total ${problems === 1 ? 'problem' : 'problems'}`,
      remainingWeeksLine: (weeks) => `About ${weeks} weeks left`,
      problemCount: (problems) => `${problems} ${problems === 1 ? 'problem' : 'problems'}`,
      adjustRhythm: 'Adjust Rhythm',
      adjustedRhythm: 'Adjusted',
      saveRhythm: 'Save Rhythm',
      savingRhythm: 'Saving',
      rhythmUpdateFailed: 'Failed to update training rhythm. Please try again later.',
      rhythmLabels: {
        RECOMMENDED: 'Standard',
        RELAXED: 'Relaxed',
        SPRINT: 'Sprint',
      },
      rhythmCompletionLine: (weeks, problems) => (
        `${weeks} ${weeks === 1 ? 'week' : 'weeks'} to finish ${problems} problems`
      ),
      rhythmWeeklyTimeLine: (hours, minDays, maxDays) => (
        minDays === maxDays ? `${hours}h/week · ${minDays} training days` : `${hours}h/week · ${minDays}-${maxDays} training days`
      ),
      rhythmDailyLine: (minProblems, maxProblems, _hasReview) => {
        return minProblems === maxProblems
          ? `about ${minProblems} problem/day`
          : `about ${minProblems}-${maxProblems} problems/day`;
      },
      rhythmScopeLabels: {
        FULL_ROUTE: 'Full problem set',
        FULL_ROUTE_WITH_REVIEW_BUFFER: 'Full problem set with buffer weeks',
        FULL_ROUTE_FAST: 'Full problem set, compressed timeline',
        FIT_USER_BUDGET: 'Coverage estimated from available time',
      },
      templateSourceCommit: (commit) => `Source commit: ${commit}`,
      templateProblemStats: (matched, missing, total) => `${matched}/${total} problems matched, ${missing} missing`,
      templateMissingNotice: (missing) => `${missing} missing ${missing === 1 ? 'problem is' : 'problems are'} kept out of draft recommendations.`,
      templateDurationTooShort: (minimumWeeks) => `Template duration must be at least ${minimumWeeks} ${minimumWeeks === 1 ? 'week' : 'weeks'}.`,
      templateTargetAudience: 'Audience',
      templateExpectedOutcome: 'Outcome',
      scenario: 'Scenario',
      duration: 'Duration',
      durationInput: 'Training Duration',
      weeklyHours: 'Weekly Hours',
      aiBudgetHint: (weeks, hours) => `${weeks} ${weeks === 1 ? 'week' : 'weeks'} at ${hours}h/week is suited for core problems and steady review.`,
      level: 'Current Level',
      programmingLanguage: 'Programming Language',
      topicPreferences: 'Topic Preferences',
      objective: 'Specific objective (optional)',
      additionalConstraints: 'Additional constraints (optional)',
      personalizationEnabled: 'Use my learning data as reference',
      validationPositiveIntegers: 'Duration and weekly hours must be positive integers.',
      validationNumericRange: (maxWeeks, maxHours) => `Duration is limited to ${maxWeeks} weeks and weekly time to ${maxHours} hours.`,
      validationInputLimit: 'Some content exceeds its input limit. Use the counters to shorten it before generating.',
      validationTopicRequired: 'Topic breakthrough requires at least one selected topic.',
      confirmDiscard: 'Discard the current plan questionnaire?',
      difficultyDistribution: 'Difficulty Distribution',
      distributionValueText: (label, easy, medium, hard) => `${label}: Easy ${easy}%, Medium ${medium}%, Hard ${hard}%`,
      easyPercent: (value) => `Easy ${value}%`,
      mediumPercent: (value) => `Medium ${value}%`,
      hardPercent: (value) => `Hard ${value}%`,
      draftQuestion: 'Agent Follow-up',
      followUpAnswer: 'Follow-up Answer',
      sendFollowUp: 'Send Follow-up',
      draftPreview: 'Learning Plan',
      goalSummary: 'Goal Summary',
      regenerateByGoal: 'Regenerate from New Goal',
      editGoalSummary: 'Edit Goal Summary',
      savePlan: 'Save Plan',
      draftUnavailableFailed: 'The draft failed or expired. Fill out the questionnaire again to generate a new one.',
      draftUnavailable: 'The draft is not available for preview. Fill out the questionnaire again to generate a new one.',
      restartWizard: 'Restart Questionnaire',
      previewDuration: 'Duration',
      previewLevel: 'Level',
      previewTime: 'Time',
      executionSummary: (weeks, days, minutes) => (
        `For the next ${weeks} ${weeks === 1 ? 'week' : 'weeks'}, train ${days} days/week for about ${minutes} minutes/day`
      ),
      nextTrainingPackage: 'Next Training Package',
      nextTrainingPackageLine: (newProblems, minutes) => `${newProblems} new ${newProblems === 1 ? 'problem' : 'problems'} · about ${minutes} minutes`,
      nextTrainingPackageReview: (reviewTask) => `Review: ${reviewTask}`,
      nextTrainingPackagePriority: 'Priority problems',
      planRouteSummary: (problems, weeks, hours, intensity) => (
        `${problems} problems · ${weeks} ${weeks === 1 ? 'week' : 'weeks'} · ${hours}h/week · ${intensity} intensity`
      ),
      loadSummary: 'Intensity',
      loadIntensityLabels: {
        RELAXED: 'Relaxed',
        RECOMMENDED: 'Balanced',
        TIGHT: 'Tight',
        OVERLOADED: 'Overloaded',
      },
      loadSummaryLine: (problems, _load, _capacity, intensity) => `${problems} problems · ${intensity} intensity`,
      weeklyBuckets: 'Weekly Targets',
      weeklyBucketLine: (week, problems) => `Week ${week} · ${problems} problems`,
      weeklyPlan: 'Weekly Execution Plan',
      weeklyPlanTitle: (week, title) => `Week ${week}: ${title}`,
      weeklyBucketStats: (problems) => `${problems} problems`,
      weeklyReviewBuffer: 'Keep this week for review and buffer work, with no new problems scheduled.',
      weeklyMissingProblem: 'This template problem is not matched yet, so the slug is shown.',
      phaseDetails: 'Phase Details',
      routeProgressTitle: 'Route Progress',
      routeProgressLine: (completed, total, percent) => `${completed}/${total} completed · ${percent}%`,
      estimatedCompletionDate: (date) => `Estimated finish: ${date}`,
      openProblemsLine: (open, skipped) => `${open} open · ${skipped} skipped`,
      visibleStatusLabels: {
        ON_TRACK: 'On track',
        NEEDS_REBALANCE: 'Needs rebalance',
        PAUSED: 'Paused',
        COMPLETED: 'Completed',
        CLOSED_OUT: 'Closed out',
      },
      startNextTrainingPackage: 'Start next pack',
      contractDateMovedEarlier: (date) => `Estimate moved earlier to ${date}`,
      contractDateMovedLater: (date) => `Estimate moved later to ${date}`,
      completionSummaryTitle: 'Completion Summary',
      completionSummaryLine: (rate, days, completed, skipped, open) => `${rate}% complete · ${days} days · ${completed} completed / ${skipped} skipped / ${open} open`,
      weakTagsLabel: 'Weak tags',
      paceTitle: 'This Week',
      paceCurrentWeek: (current, total) => `Week ${current} / ${total}`,
      paceCurrentTarget: (problems) => `Target: ${problems} problems`,
      paceCurrentCompleted: (completed) => `Completed this week: ${completed}`,
      paceStatusLabels: {
        AHEAD: 'Ahead',
        ON_TRACK: 'On track',
        AT_RISK: 'At risk',
        BEHIND: 'Behind',
      },
      paceLoadGap: (gap) => `Progress gap: ${gap > 0 ? '+' : ''}${gap}h`,
      problemTraining: 'Problem Practice',
      detailLoadProblemFailed: 'Failed to load problem details',
      statementUnavailable: 'Problem statement is not available.',
      openLeetCode: 'Open LeetCode problem',
      leetcodeUnavailable: 'LeetCode link is not available',
      practiceLeetCodeGuidance: 'Problem statements are generated by a large language model. This site does not include a built-in problem bank, and LeetCode remains the source of truth. Run code tests on LeetCode, then paste accepted or failed submissions, errors, or feedback into the chat so AI can analyze them and build your learning profile for better future recommendations.',
      phaseFallback: (phaseIndex) => `Phase ${phaseIndex}`,
      notStarted: 'Not started',
      inProgress: 'In progress',
      completed: 'Completed',
      skipped: 'Skipped',
      markCompleted: 'Mark completed',
      practiceMoreActions: 'More actions',
      skipProblem: 'Skip this problem',
      skipProblemConfirmTitle: 'Skip this problem?',
      skipProblemConfirmDescription: 'You can still view this problem\'s conversation and mark it complete later.',
      confirmSkipProblem: 'Skip problem',
      organizingThoughts: 'Organizing thoughts...',
      replyFailed: 'Reply failed. Please retry.',
      practiceSessionLoadFailed: 'Failed to load practice session',
      practiceMessageFailed: 'Failed to send message. Try again later.',
      practiceMessageBlocked: 'The current response is still being generated. Try again later.',
      progressUpdateFailed: 'Failed to update progress. Try again later.',
      reviewHistory: 'Code submission history',
      reviewHistoryUnavailable: 'Code submission history is not available yet.',
      reviewEmptyTitle: 'No code submissions yet',
      reviewEmptyDescription: 'Submit a practice message with complete code, and code submission versions will appear here.',
      reviewLoading: 'Loading code submission history...',
      reviewLoadFailed: 'Failed to load code submission history. Try again later.',
      reviewDetailLoading: 'Loading code submission details...',
      reviewDetailLoadFailed: 'Failed to load code submission details. Try again later.',
      requestedReviewUnavailable: 'This submission is unavailable.',
      reviewPassed: 'Passed',
      reviewFailed: 'Failed',
      reviewToolRunning: 'Generating code submission record...',
      reviewToolScoreSummary: (statusLabel, scoreText) => `Code submission record generated: ${statusLabel}, ${scoreText}.`,
      learnerProfileToolRunning: 'Updating learning memory...',
      learnerProfileToolUpdated: 'Learning memory updated',
      learnerProfileToolNoChange: 'Learning memory did not need an update',
      learnerProfileToolFailed: 'Learning memory was not updated',
      reviewVersionLabel: (versionNo) => `V${versionNo}`,
      reviewScoreText: (score, passScore) => passScore === undefined ? `${score} pts` : `${score} / ${passScore} pts`,
      reviewPassScoreLabel: (passScore) => `Pass score ${passScore}`,
      reviewNoReview: 'No code submission yet',
      reviewCodeSnapshot: 'Code snapshot',
      reviewScoreBreakdown: 'Score breakdown',
      reviewScoreContribution: (score, maximum) => `${score} / ${maximum}`,
      reviewScoreDimensions: {
        correctness: 'Correctness',
        complexity: 'Complexity',
        edgeCases: 'Edge cases',
        codeQuality: 'Code quality',
        problemFit: 'Requirement fit',
      },
      reviewScoreLevels: {
        excellent: 'Excellent',
        good: 'Good',
        needsImprovement: 'Needs improvement',
        weak: 'Significant issues',
      },
      reviewScoreDetailAction: (dimension) => `View ${dimension} score details`,
      reviewScoreTooltipTitle: (dimension, level, contribution) => `${dimension} · ${level} · ${contribution}`,
      reviewScoreDetailUnavailable: 'No additional explanation was provided for this dimension.',
      reviewScoreTimeComplexity: (value) => `Time complexity: ${value}`,
      reviewScoreSpaceComplexity: (value) => `Space complexity: ${value}`,
      reviewScoreExpectedComplexity: (value) => `Expected complexity: ${value}`,
      reviewScoreAnalysisBasis: {
        SERVER_EXECUTION: 'Basis: server execution result',
        USER_REPORTED_EXECUTION: 'Basis: execution result you provided',
        STATIC_ANALYSIS: 'Basis: static analysis; the code was not executed',
        INSUFFICIENT_CONTEXT: 'Basis: insufficient context to confirm',
      },
      reviewDeductionReasons: 'Deduction reasons',
      reviewImprovementSuggestions: 'Improvement suggestions',
      completionGateFallback: 'Completion is waiting for a code submission result.',
      completionRequiresPassedReview: 'Paste complete code to generate a code submission record, then pass it before marking this practice complete.',
      completionGateMessages: {
        NO_REVIEW: 'Paste complete code to generate a code submission record, then pass it before marking this practice complete.',
        LATEST_REVIEW_FAILED: 'The latest code submission did not pass. Revise it and submit again.',
        PASSED: 'The latest code submission passed. This practice can be marked complete.',
        ALREADY_COMPLETED: 'This practice is already complete.',
      },
      practiceComposerPlaceholderReview: 'Paste complete code, LeetCode accepted/failed feedback, or continue asking...',
      coachSummarySave: 'Save as coach summary',
      coachSummaryReplace: 'Replace coach summary',
      coachSummaryApplying: 'Saving...',
      coachSummaryApplyRetry: 'Retry save',
      coachSummaryApplied: 'Saved to coach summary',
      coachSummarySuperseded: 'A newer coach summary proposal is available',
      coachSummaryApplyFailed: 'Failed to save the coach summary. Please retry.',
      toolPermissionEyebrow: 'Timed confirmation',
      toolPermissionReviewTitle: 'Submit code for review',
      toolPermissionReviewReason: 'The model is requesting permission to create a code submission record.',
      toolPermissionProblem: 'Problem',
      toolPermissionContextWarning: 'The full practice context is temporarily unavailable. Confirm that the code matches this problem.',
      toolPermissionCodePreview: 'Code to submit',
      toolPermissionEffectSummary: 'Confirmation creates a code submission record and may affect problem completion.',
      toolPermissionCountdownLabel: 'Cancels automatically at timeout',
      toolPermissionCountdownHint: 'Confirm before the timer ends. Cancelling will not interrupt the conversation.',
      toolPermissionExpired: 'Confirmation time has ended',
      toolPermissionExpiredHint: 'Cancelling this code review…',
      toolPermissionAllow: 'Generate review',
      toolPermissionDeny: 'Not now',
      toolPermissionDecisionFailed: 'Failed to submit your confirmation. Please retry.',
      toolPermissionTimeoutNotice: 'Confirmation timed out. No code submission record was created.',
      chatMessages: 'Chat messages',
      coach: 'Coach',
      you: 'You',
      loadingStatement: 'Loading problem statement...',
      sendMessage: 'Send message',
      composerLabel: 'Enter your approach, question, code, or LeetCode feedback',
      composerPlaceholder: 'Enter your approach, question, code, or LeetCode feedback...',
      practiceMessageTooLong: (maxBytes) => `Messages are limited to ${maxBytes} bytes. Shorten this message before sending.`,
      send: 'Send',
      waitingGenerate: 'Waiting to generate',
      generatingPlan: 'Generating learning plan',
      generationDone: 'Generation complete',
    },
    labels: {
      difficulties: {
        EASY: 'Easy',
        MEDIUM: 'Medium',
        HARD: 'Hard',
        MIXED: 'Mixed',
      },
      planStatus: {
        ACTIVE: 'Active',
        ARCHIVED: 'Archived',
      },
      levels: {
        BEGINNER: 'Beginner',
        INTERMEDIATE: 'Intermediate',
        ADVANCED: 'Advanced',
      },
      intents: {
        PRACTICE_GOAL: 'Practice Goal',
        ABILITY_DIAGNOSIS: 'Ability Diagnosis',
        INTERVIEW_SPRINT: 'Interview Sprint',
        TOPIC_BREAKTHROUGH: 'Topic Breakthrough',
        MISTAKE_REVIEW: 'Mistake Review',
        LONG_TERM_LEARNING: 'Long-term Learning',
      },
      planScenarios: {
        INTERVIEW_SPRINT: 'Interview Sprint',
        TOPIC_BREAKTHROUGH: 'Topic Breakthrough',
        PRACTICE_GOAL: 'Foundation Practice',
        MISTAKE_REVIEW: 'Mistake Review',
        LONG_TERM_LEARNING: 'Long-term Learning',
      },
      difficultyDistribution: {
        beginner: 'Beginner',
        balanced: 'Balanced',
        sprint: 'Sprint',
      },
      topics: {
        Array: 'Array',
        'Hash Table': 'Hash Table',
        String: 'String',
        'Two Pointers': 'Two Pointers',
        'Sliding Window': 'Sliding Window',
        Stack: 'Stack',
        Queue: 'Queue',
        'Linked List': 'Linked List',
        'Binary Tree': 'Binary Tree',
        Graph: 'Graph',
        'Depth-First Search': 'DFS/BFS',
        'Binary Search': 'Binary Search',
        'Dynamic Programming': 'Dynamic Programming',
        Greedy: 'Greedy',
        Heap: 'Heap',
        Backtracking: 'Backtracking',
        'Bit Manipulation': 'Bit Manipulation',
        Math: 'Math',
        'Divide and Conquer': 'Divide and Conquer',
      },
    },
  },
};
