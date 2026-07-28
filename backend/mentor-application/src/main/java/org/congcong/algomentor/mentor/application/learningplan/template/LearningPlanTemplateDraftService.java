package org.congcong.algomentor.mentor.application.learningplan.template;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftCommand;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftMetadataKeys;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftResult;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftStatus;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftValidator;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRhythmSettings;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCandidate;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCatalog;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;

public class LearningPlanTemplateDraftService {

  private static final int DRAFT_TTL_DAYS = 14;

  private final LearningPlanTemplateRepository templateRepository;
  private final LearningPlanDraftRepository draftRepository;
  private final LearningPlanProblemCatalog problemCatalog;
  private final LearningPlanDraftValidator validator;
  private final LearningPlanLoadService loadService;
  private final Clock clock;

  public LearningPlanTemplateDraftService(
      LearningPlanTemplateRepository templateRepository,
      LearningPlanDraftRepository draftRepository,
      LearningPlanProblemCatalog problemCatalog,
      LearningPlanDraftValidator validator,
      LearningPlanLoadService loadService,
      Clock clock
  ) {
    this.templateRepository = templateRepository;
    this.draftRepository = draftRepository;
    this.problemCatalog = problemCatalog;
    this.validator = validator;
    this.loadService = loadService;
    this.clock = clock;
  }

  public List<LearningPlanTemplate> listTemplates() {
    return templateRepository.findAllTemplates();
  }

  public LearningPlanTemplate getTemplate(String templateId) {
    return templateRepository.findByTemplateId(normalizeTemplateId(templateId))
        .orElseThrow(() -> new LearningPlanException("LEARNING_PLAN_TEMPLATE_NOT_FOUND", "学习计划模板不存在。"));
  }

  public LearningPlanDraftResult createDraft(long userId, LearningPlanTemplateDraftCommand command) {
    LearningPlanTemplate template = getTemplate(command == null ? null : command.templateId());
    LearningPlanRhythmSettings defaultRhythm = loadService.defaultRhythmSettings(template);
    int dailyProblemCount = command == null || command.dailyProblemCount() == null
        ? defaultRhythm.dailyProblemCount()
        : command.dailyProblemCount();
    int trainingDaysPerWeek = command == null || command.trainingDaysPerWeek() == null
        ? defaultRhythm.trainingDaysPerWeek()
        : command.trainingDaysPerWeek();
    loadService.validateRhythm(dailyProblemCount, trainingDaysPerWeek);
    String programmingLanguage = command == null || command.programmingLanguage() == null
        ? template.programmingLanguage()
        : command.programmingLanguage();
    LearningPlanDraftCommand draftCommand = new LearningPlanDraftCommand(
        template.intent(),
        template.goal(),
        template.defaultDurationWeeks(),
        template.level(),
        template.defaultWeeklyHours(),
        programmingLanguage,
        template.difficultyPreference(),
        template.interviewOriented(),
        template.topicPreferences());
    String recommendationReasonLocale = command == null
        ? LearningPlanTemplateDraftCommand.DEFAULT_RECOMMENDATION_REASON_LOCALE
        : command.recommendationReasonLocale();
    LearningPlanDraftPlan draftPlan = buildDraftPlan(
        template,
        draftCommand,
        dailyProblemCount,
        trainingDaysPerWeek,
        recommendationReasonLocale);
    validator.validateTemplatePlan(draftPlan);

    Instant now = clock.instant();
    LearningPlanDraft saved = draftRepository.save(new LearningPlanDraft(
        null,
        userId,
        LearningPlanDraftStatus.GENERATED,
        draftCommand,
        List.of("从学习计划模板生成草案：" + template.title()),
        List.of(),
        "已根据模板生成学习计划草案。",
        draftPlan,
        null,
        now.plus(DRAFT_TTL_DAYS, ChronoUnit.DAYS),
        now,
        now));
    return LearningPlanDraftResult.fromDraft(saved);
  }

  private LearningPlanDraftPlan buildDraftPlan(
      LearningPlanTemplate template,
      LearningPlanDraftCommand command,
      int dailyProblemCount,
      int trainingDaysPerWeek,
      String recommendationReasonLocale
  ) {
    if (template.phases().isEmpty()) {
      throw new LearningPlanException("LEARNING_PLAN_DRAFT_INVALID", "学习计划模板没有可用阶段。");
    }
    if (command.durationWeeks() < template.phases().size()) {
      throw new LearningPlanException("LEARNING_PLAN_DRAFT_INVALID", "模板学习计划周期不能少于阶段数。");
    }
    List<Integer> phaseWeeks = splitWeeks(command.durationWeeks(), template.phases().size());
    List<LearningPlanPhaseDraft> phases = new ArrayList<>();
    boolean incomplete = template.missingProblemCount() > 0;

    for (int index = 0; index < template.phases().size(); index++) {
      LearningPlanTemplatePhase templatePhase = template.phases().get(index);
      List<LearningPlanTemplateProblemRef> refs = templatePhase.problemRefs();
      List<LearningPlanProblemDraft> problems = selectProblems(refs, recommendationReasonLocale);
      incomplete = incomplete || problems.size() < refs.size();
      phases.add(toDraftPhase(index + 1, phaseWeeks.get(index), templatePhase, problems));
    }

    LearningPlanDraftPlan plan = new LearningPlanDraftPlan(
        template.title(),
        template.summary(),
        template.intent(),
        template.goal(),
        command.durationWeeks(),
        template.level(),
        command.weeklyHours(),
        command.programmingLanguage(),
        template.difficultyPreference(),
        template.interviewOriented(),
        template.topicPreferences(),
        profileSummary(template, command),
        phases,
        draftMetadata(template, incomplete));
    return loadService.withRhythmMetadata(plan, dailyProblemCount, trainingDaysPerWeek);
  }

  private List<LearningPlanProblemDraft> selectProblems(
      List<LearningPlanTemplateProblemRef> refs,
      String recommendationReasonLocale
  ) {
    List<LearningPlanProblemDraft> problems = new ArrayList<>();
    for (LearningPlanTemplateProblemRef ref : refs) {
      if (!ref.matchedProblem()) {
        continue;
      }
      LearningPlanProblemCandidate candidate = problemCatalog
          .findBySlug(ref.problemSlug(), recommendationReasonLocale)
          .orElse(null);
      if (candidate == null) {
        continue;
      }
      String reason = candidate.recommendationReason();
      if (reason == null || reason.isBlank()) {
        throw new LearningPlanException(
            "LEARNING_PLAN_TEMPLATE_PROBLEM_REASON_MISSING",
            "题库数据不完整：模板题目缺少推荐理由：" + ref.problemSlug() + "。");
      }
      problems.add(LearningPlanProblemDraft.fromCandidate(
          candidate,
          problems.size() + 1,
          reason));
    }
    return problems;
  }

  private LearningPlanPhaseDraft toDraftPhase(
      int phaseIndex,
      int durationWeeks,
      LearningPlanTemplatePhase templatePhase,
      List<LearningPlanProblemDraft> problems
  ) {
    return new LearningPlanPhaseDraft(
        phaseIndex,
        templatePhase.title(),
        durationWeeks,
        templatePhase.focus(),
        templatePhase.objectives(),
        templatePhase.recommendedTags(),
        templatePhase.acceptanceCriteria(),
        templatePhase.reviewAdvice(),
        problems);
  }

  private List<Integer> splitWeeks(int durationWeeks, int phaseCount) {
    List<Integer> weeks = new ArrayList<>();
    int base = durationWeeks / phaseCount;
    int remainder = durationWeeks % phaseCount;
    for (int index = 0; index < phaseCount; index++) {
      weeks.add(base + (index < remainder ? 1 : 0));
    }
    return weeks;
  }

  private Map<String, Object> draftMetadata(LearningPlanTemplate template, boolean incomplete) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put(LearningPlanDraftMetadataKeys.PROBLEM_RECOMMENDATION_INCOMPLETE, incomplete);
    metadata.put(LearningPlanDraftMetadataKeys.DRAFT_SOURCE, LearningPlanDraftMetadataKeys.DRAFT_SOURCE_TEMPLATE);
    Map<String, Object> templateMetadata = new LinkedHashMap<>();
    templateMetadata.put(LearningPlanDraftMetadataKeys.TEMPLATE_ID, template.templateId());
    templateMetadata.put(LearningPlanDraftMetadataKeys.SOURCE_NAME, template.sourceName());
    templateMetadata.put(LearningPlanDraftMetadataKeys.SOURCE_URL, template.sourceUrl());
    templateMetadata.put(LearningPlanDraftMetadataKeys.SOURCE_COMMIT, template.sourceCommit());
    templateMetadata.put(LearningPlanDraftMetadataKeys.SOURCE_DATA_PATH, template.sourceDataPath());
    templateMetadata.put(LearningPlanDraftMetadataKeys.PROBLEM_COUNT, template.problemCount());
    templateMetadata.put(LearningPlanDraftMetadataKeys.MATCHED_PROBLEM_COUNT, template.matchedProblemCount());
    templateMetadata.put(LearningPlanDraftMetadataKeys.MISSING_PROBLEM_COUNT, template.missingProblemCount());
    templateMetadata.put(LearningPlanDraftMetadataKeys.PROBLEM_REFS, problemRefMetadata(template));
    metadata.put(LearningPlanDraftMetadataKeys.TEMPLATE, templateMetadata);
    return metadata;
  }

  private List<Map<String, Object>> problemRefMetadata(LearningPlanTemplate template) {
    return template.phases().stream()
        .flatMap(phase -> phase.problemRefs().stream())
        .map(ref -> {
          Map<String, Object> item = new LinkedHashMap<>();
          item.put(LearningPlanDraftMetadataKeys.PHASE_INDEX, ref.phaseIndex());
          item.put(LearningPlanDraftMetadataKeys.SORT_ORDER, ref.sortOrder());
          item.put(LearningPlanDraftMetadataKeys.SOURCE_ORDER, ref.sourceOrder());
          item.put(LearningPlanDraftMetadataKeys.PROBLEM_SLUG, ref.problemSlug());
          item.put(LearningPlanDraftMetadataKeys.SOURCE_TITLE, ref.sourceTitle());
          item.put(LearningPlanDraftMetadataKeys.SOURCE_DIFFICULTY, ref.sourceDifficulty());
          item.put(LearningPlanDraftMetadataKeys.PATTERN, ref.pattern());
          item.put(LearningPlanDraftMetadataKeys.SOURCE_URL, ref.sourceUrl());
          item.put(LearningPlanDraftMetadataKeys.MATCHED_PROBLEM, ref.matchedProblem());
          return item;
        })
        .toList();
  }

  private String profileSummary(LearningPlanTemplate template, LearningPlanDraftCommand command) {
    return template.targetAudience()
        + " 当前模板级别：" + template.level()
        + "，建议每周 " + command.weeklyHours() + " 小时"
        + (command.programmingLanguage() == null ? "" : "，语言：" + command.programmingLanguage());
  }

  private String normalizeTemplateId(String templateId) {
    if (templateId == null || templateId.isBlank()) {
      throw new LearningPlanException("LEARNING_PLAN_TEMPLATE_NOT_FOUND", "学习计划模板不存在。");
    }
    return templateId.trim();
  }
}
