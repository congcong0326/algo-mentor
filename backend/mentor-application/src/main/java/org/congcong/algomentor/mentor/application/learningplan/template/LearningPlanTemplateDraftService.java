package org.congcong.algomentor.mentor.application.learningplan.template;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanBrief;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftMetadataKeys;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftResult;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftStatus;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftValidator;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyDistributions;
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
    return listTemplates(LearningPlanContentLocale.ZH_CN);
  }

  public List<LearningPlanTemplate> listTemplates(LearningPlanContentLocale locale) {
    return templateRepository.findAllTemplates(locale);
  }

  public LearningPlanTemplate getTemplate(String templateId) {
    return getTemplate(templateId, LearningPlanContentLocale.ZH_CN);
  }

  public LearningPlanTemplate getTemplate(String templateId, LearningPlanContentLocale locale) {
    return templateRepository.findByTemplateId(normalizeTemplateId(templateId), locale)
        .orElseThrow(() -> new LearningPlanException("LEARNING_PLAN_TEMPLATE_NOT_FOUND", "学习计划模板不存在。"));
  }

  public LearningPlanDraftResult createDraft(long userId, LearningPlanTemplateDraftCommand command) {
    LearningPlanContentLocale requestedLocale = command == null
        ? LearningPlanContentLocale.ZH_CN
        : command.contentLocale();
    LearningPlanTemplate template = getTemplate(command == null ? null : command.templateId(), requestedLocale);
    LearningPlanContentLocale contentLocale = template.resolveContentLocale(requestedLocale);
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
    LearningPlanBrief draftBrief = new LearningPlanBrief(
        template.intent(),
        template.goal(contentLocale),
        template.defaultDurationWeeks(),
        template.level(),
        template.defaultWeeklyHours(),
        programmingLanguage,
        LearningPlanDifficultyDistributions.forTemplate(template.difficultyPreference()),
        template.interviewOriented(),
        template.topicPreferences(),
        null,
        false,
        contentLocale);
    LearningPlanDraftPlan draftPlan = buildDraftPlan(
        template,
        draftBrief,
        dailyProblemCount,
        trainingDaysPerWeek,
        contentLocale);
    validator.validateTemplatePlan(draftPlan);

    Instant now = clock.instant();
    LearningPlanDraft saved = draftRepository.save(new LearningPlanDraft(
        null,
        userId,
        LearningPlanDraftStatus.GENERATED,
        draftBrief,
        List.of(contentLocale == LearningPlanContentLocale.EN_US
            ? "Generated from learning plan template: " + template.title(contentLocale)
            : "从学习计划模板生成草案：" + template.title(contentLocale)),
        List.of(),
        contentLocale == LearningPlanContentLocale.EN_US
            ? "The learning plan draft was generated from the selected template."
            : "已根据模板生成学习计划草案。",
        draftPlan,
        null,
        now.plus(DRAFT_TTL_DAYS, ChronoUnit.DAYS),
        now,
        now));
    return LearningPlanDraftResult.fromDraft(saved);
  }

  private LearningPlanDraftPlan buildDraftPlan(
      LearningPlanTemplate template,
      LearningPlanBrief brief,
      int dailyProblemCount,
      int trainingDaysPerWeek,
      LearningPlanContentLocale contentLocale
  ) {
    if (template.phases().isEmpty()) {
      throw new LearningPlanException("LEARNING_PLAN_DRAFT_INVALID", "学习计划模板没有可用阶段。");
    }
    if (brief.durationWeeks() < template.phases().size()) {
      throw new LearningPlanException("LEARNING_PLAN_DRAFT_INVALID", "模板学习计划周期不能少于阶段数。");
    }
    List<Integer> phaseWeeks = splitWeeks(brief.durationWeeks(), template.phases().size());
    List<LearningPlanPhaseDraft> phases = new ArrayList<>();
    boolean incomplete = template.missingProblemCount() > 0;

    for (int index = 0; index < template.phases().size(); index++) {
      LearningPlanTemplatePhase templatePhase = template.phases().get(index);
      List<LearningPlanTemplateProblemRef> refs = templatePhase.problemRefs();
      List<LearningPlanProblemDraft> problems = selectProblems(refs, contentLocale.languageTag());
      incomplete = incomplete || problems.size() < refs.size();
      phases.add(toDraftPhase(index + 1, phaseWeeks.get(index), templatePhase, problems, contentLocale));
    }

    LearningPlanDraftPlan plan = new LearningPlanDraftPlan(
        template.title(contentLocale),
        template.summary(contentLocale),
        brief.intent(),
        brief.objective(),
        brief.durationWeeks(),
        brief.level(),
        brief.weeklyHours(),
        brief.programmingLanguage(),
        brief.difficultyDistribution(),
        brief.interviewOriented(),
        brief.topicPreferences(),
        brief.additionalConstraints(),
        phases,
        draftMetadata(template, incomplete, contentLocale));
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
      List<LearningPlanProblemDraft> problems,
      LearningPlanContentLocale contentLocale
  ) {
    return new LearningPlanPhaseDraft(
        phaseIndex,
        templatePhase.title(contentLocale),
        durationWeeks,
        templatePhase.focus(contentLocale),
        templatePhase.objectives(contentLocale),
        templatePhase.recommendedTags(),
        templatePhase.acceptanceCriteria(contentLocale),
        templatePhase.reviewAdvice(contentLocale),
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

  private Map<String, Object> draftMetadata(
      LearningPlanTemplate template,
      boolean incomplete,
      LearningPlanContentLocale contentLocale
  ) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put(LearningPlanDraftMetadataKeys.PROBLEM_RECOMMENDATION_INCOMPLETE, incomplete);
    metadata.put(LearningPlanDraftMetadataKeys.DRAFT_SOURCE, LearningPlanDraftMetadataKeys.DRAFT_SOURCE_TEMPLATE);
    metadata.put(LearningPlanDraftMetadataKeys.CONTENT_LOCALE, contentLocale.languageTag());
    metadata.put(LearningPlanDraftMetadataKeys.PERSONALIZATION_ENABLED, false);
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

  private String normalizeTemplateId(String templateId) {
    if (templateId == null || templateId.isBlank()) {
      throw new LearningPlanException("LEARNING_PLAN_TEMPLATE_NOT_FOUND", "学习计划模板不存在。");
    }
    return templateId.trim();
  }
}
