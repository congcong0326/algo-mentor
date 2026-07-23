import type {
  LearningPlanDifficultyPreference,
  LearningPlanIntent,
  LearningPlanLevel,
  LearningPlanStatus,
  LearnerProfileDimension,
  ProblemDifficulty,
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
    needHelpPrefix: string;
    supportEmail: string;
    termsPrefix: string;
    termsLabel: string;
    termsConnector: string;
    privacyLabel: string;
    failed: string;
    betaAccessDenied: string;
    googleLogin: string;
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
    adminAi: string;
    feedback: string;
    adminOverview: string;
    adminFeedback: string;
    debug: string;
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
    labels: Record<'overview' | 'access' | 'ai' | 'content' | 'feedback' | 'development' | 'users' | 'userGroups' | 'betaAccess' | 'problems' | 'debug', string>;
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
    aiSuggestionLabel: string;
    aiSuggestionDescription: string;
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
    helpSuffix: string;
    accountTitle: string;
    accountDescription: string;
    signedInAs: string;
    activeStatus: string;
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
    abilityHeatmapTitle: string;
    abilityHeatmapHint: string;
    addHeatmapTag: (label: string) => string;
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
    memoryCategoryEmpty: string;
    memoryTabs: {
      declaredFacts: string;
      generalObservations: string;
      tagAssessments: string;
    };
    memoryTabLabel: (label: string, count: number) => string;
    memoryDimensionLabels: Record<LearnerProfileDimension, string>;
    memoryUpdatedAt: (value: string) => string;
    memoryRevision: (revision: number) => string;
    memoryShowAll: (hiddenCount: number) => string;
    memoryCollapse: string;
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
    loadingDetail: string;
    loadingPracticeChat: string;
    overviewTitle: string;
    overviewDescription: string;
    newPlan: string;
    overviewStats: string;
    active: string;
    archived: string;
    latestCreated: string;
    listTitle: string;
    totalPlans: (count: number) => string;
    emptyTitle: string;
    emptyDescription: string;
    planParameters: string;
    viewPlan: (title: string) => string;
    deletePlan: (title: string) => string;
    currentRhythm: string;
    rhythmOverview: string;
    noRhythm: string;
    activePlans: (count: number) => string;
    archivedPlans: (count: number) => string;
    maintainByScenario: string;
    maintainByScenarioDescription: string;
    latestCreatedLabel: (date: string) => string;
    latestCreatedDescription: string;
    unspecified: string;
    backToList: string;
    backToPlans: string;
    backToPlanDetail: string;
    backToPracticeChat: string;
    learningPlanEyebrow: string;
    practiceChatEyebrow: string;
    generateStart: string;
    generateFailed: string;
    followUpFailed: string;
    saveFailed: string;
    followUpRegeneratePrefix: (goal: string) => string;
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
    templateEmpty: string;
    templateGenerateStart: string;
    templateGenerateFailed: string;
    templateGenerateDraft: string;
    templateSelected: string;
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
    additionalThoughts: string;
    validationPositiveIntegers: string;
    validationTopicRequired: string;
    confirmDiscard: string;
    difficultyDistribution: string;
    distributionValueText: (label: string, easy: number, medium: number, hard: number) => string;
    easyPercent: (value: number) => string;
    mediumPercent: (value: number) => string;
    hardPercent: (value: number) => string;
    goalIntent: (value: string) => string;
    goalDuration: (value: number) => string;
    goalWeeklyHours: (value: number) => string;
    goalLevel: (value: string) => string;
    goalLanguage: (value: string) => string;
    goalDifficulty: (label: string, easy: number, medium: number, hard: number) => string;
    goalTopics: (topics: string) => string;
    goalTopicsAuto: string;
    goalAdditionalThoughts: (value: string) => string;
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
    weeklyReviewAdvice: (advice: string) => string;
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
    reviewDeductionReasons: string;
    reviewImprovementSuggestions: string;
    reviewEvidence: string;
    reviewContextSummary: string;
    completionGateFallback: string;
    completionRequiresPassedReview: string;
    practiceComposerPlaceholderReview: string;
    practiceComposerReviewHint: string;
    toolPermissionEyebrow: string;
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
    send: string;
    waitingGenerate: string;
    generatingPlan: string;
    generationDone: string;
  };
  debug: {
    controls: string;
    messagePlaceholder: string;
    firstRoundOptional: string;
    optional: string;
    start: string;
    stop: string;
    clear: string;
    key: string;
    auto: string;
    summary: string;
    outputTitle: string;
    outputEmpty: string;
    logTitle: string;
    logEmpty: string;
    connectionOpened: string;
    connectionStopped: string;
    streamFailed: string;
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
      brandKicker: 'ALGO MENTOR',
      brandName: 'Algo Mentor',
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
      needHelpPrefix: '需要帮助？联系 ',
      supportEmail: 'support@algomentor.local',
      termsPrefix: '继续即表示你理解并同意',
      termsLabel: '服务条款',
      termsConnector: ' 和 ',
      privacyLabel: '隐私政策',
      failed: '登录失败，请重新尝试。',
      betaAccessDenied: '当前邮箱不在内测准入名单中。',
      googleLogin: '使用 Google 登录',
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
      adminAi: 'AI 治理',
      feedback: '反馈',
      adminOverview: '概览',
      adminFeedback: '反馈',
      debug: 'AI 调试',
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
      labels: {
        overview: '运营概览',
        access: '用户与访问',
        ai: 'AI 治理',
        content: '内容管理',
        feedback: '反馈与支持',
        development: '开发工具',
        users: '用户管理',
        userGroups: '用户组管理',
        betaAccess: '内测准入',
        problems: '题库管理',
        debug: 'AI 调试',
      },
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
      reviewDescription: '决定复述后是否使用 AI 辅助评级，以及间隔重复的每日负载。',
      reviewLoading: '正在加载复习设置...',
      reviewLoadFailed: '复习设置加载失败',
      reviewSaveFailed: '复习设置保存失败',
      reviewSaving: '保存中',
      aiSuggestionLabel: '复习后启用 AI 建议评级',
      aiSuggestionDescription: 'AI 仅分析复述并给出建议，最终评级仍由用户确认；关闭后直接手动评级。',
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
      helpSuffix: '说明',
      accountTitle: '账户',
      accountDescription: '查看当前登录身份，或结束本次会话。',
      signedInAs: '当前登录',
      activeStatus: '账户正常',
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
      abilityHeatmapTitle: '全量 tag 能力热力图',
      abilityHeatmapHint: '色块深浅按能力分展示',
      addHeatmapTag: (label) => `添加 ${label}`,
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
      memoryCategoryEmpty: '这一类暂时没有内容。',
      memoryTabs: {
        declaredFacts: '我告诉 AI 的',
        generalObservations: 'AI 观察到的',
        tagAssessments: '专项能力判断',
      },
      memoryTabLabel: (label, count) => `${label}，${count} 条`,
      memoryDimensionLabels: {
        LEARNER_BACKGROUND: '学习背景',
        GOALS_AND_INTENTS: '目标与意图',
        TIME_AND_RESOURCE_CONSTRAINTS: '时间与资源',
        LEARNING_AND_INTERACTION_PREFERENCES: '学习与互动偏好',
        SELF_ABILITY_ASSESSMENT: '自我能力判断',
        PROBLEM_SOLVING_APPROACH: '解题方式',
        IMPLEMENTATION_AND_ERROR_PATTERN: '实现与错误模式',
        LEARNING_INTERACTION_AND_INDEPENDENCE: '互动与独立性',
        REVIEW_AND_GROWTH_PERFORMANCE: '复盘与成长表现',
        TAG_MASTERY: '专项能力',
      },
      memoryUpdatedAt: (value) => `更新于 ${value}`,
      memoryRevision: (revision) => `第 ${revision} 版`,
      memoryShowAll: (hiddenCount) => `查看其余 ${hiddenCount} 条`,
      memoryCollapse: '收起',
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
      loadingDetail: '正在加载方案详情...',
      loadingPracticeChat: '正在加载题目聊天页...',
      overviewTitle: '训练方案',
      overviewDescription: '按目标、时间、当前水平与自身想法生成训练方案。',
      newPlan: '新建方案',
      overviewStats: '方案概览',
      active: '进行中',
      archived: '已归档',
      latestCreated: '最近创建',
      listTitle: '方案库',
      totalPlans: (count) => `共 ${count} 个方案`,
      emptyTitle: '暂无正式方案',
      emptyDescription: '先新建一个训练方案，把目标、周期和题目安排统一起来。',
      planParameters: '方案参数',
      viewPlan: (title) => `查看 ${title}`,
      deletePlan: (title) => `删除 ${title}`,
      currentRhythm: '当前节奏',
      rhythmOverview: '方案执行概览',
      noRhythm: '还没有训练节奏',
      activePlans: (count) => `${count} 个方案正在推进`,
      archivedPlans: (count) => `${count} 个方案已沉淀为历史记录`,
      maintainByScenario: '按场景维护方案',
      maintainByScenarioDescription: '面试冲刺、专题突破和长期学习不要混在同一个方案里。',
      latestCreatedLabel: (date) => `最近创建：${date}`,
      latestCreatedDescription: '新方案保存后会出现在方案库顶部。',
      unspecified: '未指定',
      backToList: '返回方案库',
      backToPlans: '返回方案页',
      backToPlanDetail: '返回方案',
      backToPracticeChat: '返回聊天',
      learningPlanEyebrow: 'Learning Plan',
      practiceChatEyebrow: 'Practice Chat',
      generateStart: '开始生成训练方案',
      generateFailed: '训练方案生成失败',
      followUpFailed: '训练方案追问提交失败',
      saveFailed: '训练方案保存失败',
      followUpRegeneratePrefix: (goal) => `请按新的目标摘要重新生成训练方案：${goal}`,
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
      templateEmpty: '暂无可用模板',
      templateGenerateStart: '正在按模板生成训练方案',
      templateGenerateFailed: '按模板生成训练方案失败',
      templateGenerateDraft: '按模板生成草案',
      templateSelected: '当前模板',
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
      additionalThoughts: '补充想法',
      validationPositiveIntegers: '周期和每周投入必须是正整数。',
      validationTopicRequired: '专项突破需要至少选择一个主题。',
      confirmDiscard: '放弃当前填写的方案问卷？',
      difficultyDistribution: '难度分布',
      distributionValueText: (label, easy, medium, hard) => `${label}：简单 ${easy}%，中等 ${medium}%，困难 ${hard}%`,
      easyPercent: (value) => `简单 ${value}%`,
      mediumPercent: (value) => `中等 ${value}%`,
      hardPercent: (value) => `困难 ${value}%`,
      goalIntent: (value) => `训练场景：${value}`,
      goalDuration: (value) => `周期：${value} 周`,
      goalWeeklyHours: (value) => `每周投入：${value} 小时`,
      goalLevel: (value) => `当前水平：${value}`,
      goalLanguage: (value) => `编程语言：${value}`,
      goalDifficulty: (label, easy, medium, hard) => `难度分布：${label}（简单 ${easy}%，中等 ${medium}%，困难 ${hard}%）`,
      goalTopics: (topics) => `主题偏好：${topics}`,
      goalTopicsAuto: '主题偏好：由系统根据训练场景安排',
      goalAdditionalThoughts: (value) => `补充想法：${value}`,
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
      weeklyReviewAdvice: (advice) => `复盘建议：${advice}`,
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
      reviewDeductionReasons: '扣分原因',
      reviewImprovementSuggestions: '改进建议',
      reviewEvidence: '评审依据',
      reviewContextSummary: '上下文摘要',
      completionGateFallback: '完成状态需要等待代码提交记录结果。',
      completionRequiresPassedReview: '完成前需要先粘贴完整代码生成一次代码提交记录，并且通过后才能标记完成。',
      practiceComposerPlaceholderReview: '粘贴完整代码、LeetCode 通过/失败反馈，或继续追问思路...',
      practiceComposerReviewHint: '粘贴完整代码生成代码提交记录，并通过后才能标记完成。',
      toolPermissionEyebrow: '限时确认',
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
      send: '发送',
      waitingGenerate: '等待生成',
      generatingPlan: '正在生成训练方案',
      generationDone: '生成完成',
    },
    debug: {
      controls: 'SSE 请求控制',
      messagePlaceholder: '输入本轮用户消息',
      firstRoundOptional: '首轮可留空',
      optional: '可选',
      start: 'Start',
      stop: 'Stop',
      clear: 'Clear',
      key: 'Key',
      auto: 'auto',
      summary: '流式请求摘要',
      outputTitle: '模型输出',
      outputEmpty: '等待 content_delta 事件...',
      logTitle: '事件日志',
      logEmpty: '等待 SSE 事件...',
      connectionOpened: 'POST SSE connection opened.',
      connectionStopped: 'Connection stopped by user.',
      streamFailed: 'Conversation stream failed.',
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
      brandKicker: 'ALGO MENTOR',
      brandName: 'Algo Mentor',
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
      needHelpPrefix: 'Need help? Contact ',
      supportEmail: 'support@algomentor.local',
      termsPrefix: 'By continuing, you acknowledge and agree to the ',
      termsLabel: 'Terms & Conditions',
      termsConnector: ' and ',
      privacyLabel: 'Privacy Policy',
      failed: 'Sign-in failed. Please try again.',
      betaAccessDenied: 'This email is not currently allowed to access the private beta.',
      googleLogin: 'Sign in with Google',
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
      adminAi: 'AI Governance',
      feedback: 'Feedback',
      adminOverview: 'Overview',
      adminFeedback: 'Feedback',
      debug: 'AI Debug',
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
      labels: {
        overview: 'Operations',
        access: 'Users & Access',
        ai: 'AI Governance',
        content: 'Content',
        feedback: 'Feedback & Support',
        development: 'Developer Tools',
        users: 'Users',
        userGroups: 'User Groups',
        betaAccess: 'Beta Access',
        problems: 'Problem Library',
        debug: 'AI Debug',
      },
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
      reviewDescription: 'Choose whether AI assists with recall ratings and tune the daily spaced-repetition load.',
      reviewLoading: 'Loading review settings...',
      reviewLoadFailed: 'Failed to load review settings',
      reviewSaveFailed: 'Failed to save review settings',
      reviewSaving: 'Saving',
      aiSuggestionLabel: 'Enable AI rating suggestions after recall',
      aiSuggestionDescription: 'AI analyzes the recall and suggests a rating, but you still make the final choice. Disable this to rate manually.',
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
      helpSuffix: ' help',
      accountTitle: 'Account',
      accountDescription: 'Review the current identity or end this session.',
      signedInAs: 'Signed in as',
      activeStatus: 'Account active',
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
      abilityHeatmapTitle: 'All-tag Ability Heatmap',
      abilityHeatmapHint: 'Cell intensity follows ability score',
      addHeatmapTag: (label) => `Add ${label}`,
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
      memoryCategoryEmpty: 'Nothing in this category yet.',
      memoryTabs: {
        declaredFacts: 'What I told AI',
        generalObservations: 'AI observations',
        tagAssessments: 'Topic assessments',
      },
      memoryTabLabel: (label, count) => `${label}, ${count} ${count === 1 ? 'item' : 'items'}`,
      memoryDimensionLabels: {
        LEARNER_BACKGROUND: 'Learning background',
        GOALS_AND_INTENTS: 'Goals and intent',
        TIME_AND_RESOURCE_CONSTRAINTS: 'Time and resources',
        LEARNING_AND_INTERACTION_PREFERENCES: 'Learning preferences',
        SELF_ABILITY_ASSESSMENT: 'Self assessment',
        PROBLEM_SOLVING_APPROACH: 'Problem-solving approach',
        IMPLEMENTATION_AND_ERROR_PATTERN: 'Implementation and error patterns',
        LEARNING_INTERACTION_AND_INDEPENDENCE: 'Interaction and independence',
        REVIEW_AND_GROWTH_PERFORMANCE: 'Review and growth',
        TAG_MASTERY: 'Topic ability',
      },
      memoryUpdatedAt: (value) => `Updated ${value}`,
      memoryRevision: (revision) => `Revision ${revision}`,
      memoryShowAll: (hiddenCount) => `Show ${hiddenCount} more`,
      memoryCollapse: 'Show less',
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
      ctaDescription: 'Set your goal and time first, then let the system propose phases, problems, and review points.',
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
      loadingDetail: 'Loading plan details...',
      loadingPracticeChat: 'Loading problem chat...',
      overviewTitle: 'Learning Plans',
      overviewDescription: 'Generate training plans from goals, time, current level, and your own notes.',
      newPlan: 'New Plan',
      overviewStats: 'Plan overview',
      active: 'Active',
      archived: 'Archived',
      latestCreated: 'Latest',
      listTitle: 'Plan Library',
      totalPlans: (count) => `${count} ${count === 1 ? 'plan' : 'plans'}`,
      emptyTitle: 'No saved plans',
      emptyDescription: 'Create a plan to keep goals, timeline, and problem work in one place.',
      planParameters: 'Plan parameters',
      viewPlan: (title) => `View ${title}`,
      deletePlan: (title) => `Delete ${title}`,
      currentRhythm: 'Current Rhythm',
      rhythmOverview: 'Plan progress overview',
      noRhythm: 'No training rhythm yet',
      activePlans: (count) => `${count} ${count === 1 ? 'plan is' : 'plans are'} active`,
      archivedPlans: (count) => `${count} ${count === 1 ? 'plan has' : 'plans have'} been archived`,
      maintainByScenario: 'Organize by scenario',
      maintainByScenarioDescription: 'Keep interview sprints, topic breakthroughs, and long-term learning in separate plans.',
      latestCreatedLabel: (date) => `Latest: ${date}`,
      latestCreatedDescription: 'Newly saved plans appear at the top of the library.',
      unspecified: 'Not specified',
      backToList: 'Back to Library',
      backToPlans: 'Back to Plans',
      backToPlanDetail: 'Back to Plan',
      backToPracticeChat: 'Back to Chat',
      learningPlanEyebrow: 'Learning Plan',
      practiceChatEyebrow: 'Practice Chat',
      generateStart: 'Starting plan generation',
      generateFailed: 'Failed to generate learning plan',
      followUpFailed: 'Failed to submit follow-up',
      saveFailed: 'Failed to save learning plan',
      followUpRegeneratePrefix: (goal) => `Regenerate the learning plan from this updated goal summary: ${goal}`,
      revisionInstructionLabel: 'Want changes? Describe how to revise this plan',
      reviseDraft: 'Revise Plan',
      revisionFailed: 'Failed to revise the learning plan. Try again later.',
      extensionEntryLabel: 'Want to keep learning? Describe your next goal',
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
      templateEmpty: 'No templates available',
      templateGenerateStart: 'Generating a plan from the template',
      templateGenerateFailed: 'Failed to generate a plan from the template',
      templateGenerateDraft: 'Generate from Template',
      templateSelected: 'Selected Template',
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
      additionalThoughts: 'Additional Notes',
      validationPositiveIntegers: 'Duration and weekly hours must be positive integers.',
      validationTopicRequired: 'Topic breakthrough requires at least one selected topic.',
      confirmDiscard: 'Discard the current plan questionnaire?',
      difficultyDistribution: 'Difficulty Distribution',
      distributionValueText: (label, easy, medium, hard) => `${label}: Easy ${easy}%, Medium ${medium}%, Hard ${hard}%`,
      easyPercent: (value) => `Easy ${value}%`,
      mediumPercent: (value) => `Medium ${value}%`,
      hardPercent: (value) => `Hard ${value}%`,
      goalIntent: (value) => `Scenario: ${value}`,
      goalDuration: (value) => `Duration: ${value} weeks`,
      goalWeeklyHours: (value) => `Weekly hours: ${value}`,
      goalLevel: (value) => `Current level: ${value}`,
      goalLanguage: (value) => `Programming language: ${value}`,
      goalDifficulty: (label, easy, medium, hard) => `Difficulty distribution: ${label} (Easy ${easy}%, Medium ${medium}%, Hard ${hard}%)`,
      goalTopics: (topics) => `Topic preferences: ${topics}`,
      goalTopicsAuto: 'Topic preferences: Let the system choose based on the scenario',
      goalAdditionalThoughts: (value) => `Additional notes: ${value}`,
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
      weeklyReviewAdvice: (advice) => `Review advice: ${advice}`,
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
      reviewDeductionReasons: 'Deduction reasons',
      reviewImprovementSuggestions: 'Improvement suggestions',
      reviewEvidence: 'Evidence',
      reviewContextSummary: 'Context summary',
      completionGateFallback: 'Completion is waiting for a code submission result.',
      completionRequiresPassedReview: 'Paste complete code to generate a code submission record, then pass it before marking this practice complete.',
      practiceComposerPlaceholderReview: 'Paste complete code, LeetCode accepted/failed feedback, or continue asking...',
      practiceComposerReviewHint: 'Paste complete code to generate a code submission record, then pass it before marking this practice complete.',
      toolPermissionEyebrow: 'Timed confirmation',
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
      send: 'Send',
      waitingGenerate: 'Waiting to generate',
      generatingPlan: 'Generating learning plan',
      generationDone: 'Generation complete',
    },
    debug: {
      controls: 'SSE request controls',
      messagePlaceholder: 'Enter this user message',
      firstRoundOptional: 'Optional for first turn',
      optional: 'Optional',
      start: 'Start',
      stop: 'Stop',
      clear: 'Clear',
      key: 'Key',
      auto: 'auto',
      summary: 'Streaming request summary',
      outputTitle: 'Model Output',
      outputEmpty: 'Waiting for content_delta events...',
      logTitle: 'Event Log',
      logEmpty: 'Waiting for SSE events...',
      connectionOpened: 'POST SSE connection opened.',
      connectionStopped: 'Connection stopped by user.',
      streamFailed: 'Conversation stream failed.',
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
