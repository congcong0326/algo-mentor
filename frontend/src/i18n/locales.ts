import type {
  LearningPlanDifficultyPreference,
  LearningPlanIntent,
  LearningPlanLevel,
  LearningPlanStatus,
  ProblemDifficulty,
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
  };
  nav: {
    home: string;
    my: string;
    learningPlans: string;
    problems: string;
    debug: string;
    forbidden: string;
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
    workspaceSubtitle: string;
    workspaceSectionsLabel: string;
    recentPracticeTitle: string;
    recentPracticeEmpty: string;
    planPreviewTitle: string;
    planPreviewEmpty: string;
    reviewQueueTitle: string;
    reviewQueueEmpty: string;
    abilityRadarTitle: string;
    abilityRadarSubtitle: string;
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
    listTitle: string;
    totalCount: (count: number) => string;
    loadingList: string;
    emptyList: string;
    previousPage: string;
    nextPage: string;
    loadingDetail: string;
    sampleInput: string;
    pythonTemplate: string;
    selectProblem: string;
    listLoadFailed: string;
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
    createTitle: string;
    generatePlan: string;
    generateDraft: string;
    generating: string;
    scenario: string;
    duration: string;
    durationInput: string;
    weeklyHours: string;
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
    toolPermissionLanguage: string;
    toolPermissionCodeLength: string;
    toolPermissionCodeLengthValue: (count: number) => string;
    toolPermissionContext: string;
    toolPermissionContextAvailable: string;
    toolPermissionContextUnavailable: string;
    toolPermissionCodePreview: string;
    toolPermissionEffects: string;
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
      googleLogin: '使用 Google 登录',
      emailLabel: '邮箱',
      emailPlaceholder: 'you@example.com',
      passwordLabel: '密码',
      passwordPlaceholder: '至少 8 个字符',
      displayNameLabel: '昵称',
      displayNamePlaceholder: '可选',
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
    },
    nav: {
      home: '首页',
      my: '我的',
      learningPlans: '方案',
      problems: '题库',
      debug: 'AI 调试',
      forbidden: '无权访问',
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
      workspaceSubtitle: '把最近练习、训练计划、待复盘和能力画像放在同一屏，先看状态，再进入具体任务。',
      workspaceSectionsLabel: '主页工作台模块',
      recentPracticeTitle: '最近练习',
      recentPracticeEmpty: '后续展示最近进入的题目和 Review 状态。',
      planPreviewTitle: '学习计划',
      planPreviewEmpty: '后续展示当前推进中的阶段和推荐任务。',
      reviewQueueTitle: '待复盘',
      reviewQueueEmpty: '后续汇总需要回看的代码 Review 和错题。',
      abilityRadarTitle: '能力雷达图',
      abilityRadarSubtitle: '常见 tag · 保守诊断 · 满分 10 分',
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
      listTitle: '题目列表',
      totalCount: (count) => `${count} 题`,
      loadingList: '加载题库...',
      emptyList: '没有匹配的题目',
      previousPage: '上一页',
      nextPage: '下一页',
      loadingDetail: '加载详情...',
      sampleInput: '样例输入',
      pythonTemplate: 'Python3 模板',
      selectProblem: '选择一道题查看详情',
      listLoadFailed: '题库列表加载失败',
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
      createTitle: '新建训练方案',
      generatePlan: '生成训练方案',
      generateDraft: '生成方案草案',
      generating: '生成中',
      scenario: '训练场景',
      duration: '周期',
      durationInput: '训练周期',
      weeklyHours: '每周投入',
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
      toolPermissionEyebrow: '需要确认',
      toolPermissionProblem: '题目',
      toolPermissionLanguage: '语言',
      toolPermissionCodeLength: '代码长度',
      toolPermissionCodeLengthValue: (count) => `${count} 字符`,
      toolPermissionContext: '上下文',
      toolPermissionContextAvailable: '可用',
      toolPermissionContextUnavailable: '不可用',
      toolPermissionCodePreview: '代码预览',
      toolPermissionEffects: '影响',
      toolPermissionAllow: '允许',
      toolPermissionDeny: '拒绝',
      toolPermissionDecisionFailed: '提交授权决定失败，请重试。',
      toolPermissionTimeoutNotice: '本次未执行。',
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
      googleLogin: 'Sign in with Google',
      emailLabel: 'Email',
      emailPlaceholder: 'you@example.com',
      passwordLabel: 'Password',
      passwordPlaceholder: 'At least 8 characters',
      displayNameLabel: 'Display name',
      displayNamePlaceholder: 'Optional',
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
    },
    nav: {
      home: 'Dashboard',
      my: 'Me',
      learningPlans: 'Plans',
      problems: 'Problems',
      debug: 'AI Debug',
      forbidden: 'Not authorized',
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
      workspaceSubtitle: 'Recent practice, plans, review queue, and the ability profile live together so status comes before task switching.',
      workspaceSectionsLabel: 'Dashboard workbench modules',
      recentPracticeTitle: 'Recent Practice',
      recentPracticeEmpty: 'Recent problems and review state will appear here.',
      planPreviewTitle: 'Learning Plan',
      planPreviewEmpty: 'Current phases and recommended tasks will appear here.',
      reviewQueueTitle: 'Review Queue',
      reviewQueueEmpty: 'Code reviews and missed problems to revisit will appear here.',
      abilityRadarTitle: 'Ability Radar',
      abilityRadarSubtitle: 'Common tags · conservative diagnosis · 10-point scale',
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
      listTitle: 'Problem List',
      totalCount: (count) => `${count} ${count === 1 ? 'problem' : 'problems'}`,
      loadingList: 'Loading problems...',
      emptyList: 'No matching problems',
      previousPage: 'Previous page',
      nextPage: 'Next page',
      loadingDetail: 'Loading details...',
      sampleInput: 'Sample Input',
      pythonTemplate: 'Python3 Template',
      selectProblem: 'Select a problem to view details',
      listLoadFailed: 'Failed to load problem list',
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
      createTitle: 'Create Learning Plan',
      generatePlan: 'Generate Plan',
      generateDraft: 'Generate Draft',
      generating: 'Generating',
      scenario: 'Scenario',
      duration: 'Duration',
      durationInput: 'Training Duration',
      weeklyHours: 'Weekly Hours',
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
      toolPermissionEyebrow: 'Confirmation needed',
      toolPermissionProblem: 'Problem',
      toolPermissionLanguage: 'Language',
      toolPermissionCodeLength: 'Code length',
      toolPermissionCodeLengthValue: (count) => `${count} chars`,
      toolPermissionContext: 'Context',
      toolPermissionContextAvailable: 'Available',
      toolPermissionContextUnavailable: 'Unavailable',
      toolPermissionCodePreview: 'Code preview',
      toolPermissionEffects: 'Effects',
      toolPermissionAllow: 'Allow',
      toolPermissionDeny: 'Deny',
      toolPermissionDecisionFailed: 'Failed to submit the decision. Please retry.',
      toolPermissionTimeoutNotice: 'This action was not run.',
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
