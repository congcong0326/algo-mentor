package org.congcong.algomentor.mentor.application.learningplan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LearningPlanAgentService {

  private static final int PROBLEMS_PER_PHASE = 5;

  private final LearningPlanProblemCatalog problemCatalog;
  private final LearningPlanDraftValidator validator = new LearningPlanDraftValidator();

  public LearningPlanAgentService(LearningPlanProblemCatalog problemCatalog) {
    this.problemCatalog = problemCatalog;
  }

  public LearningPlanAgentResult run(LearningPlanBrief brief, List<String> missingFields) {
    if (!missingFields.isEmpty()) {
      return LearningPlanAgentResult.askClarification(
          LearningPlanClarificationMessages.forField(missingFields.get(0), brief.contentLocale()),
          missingFields);
    }
    LearningPlanDraftPlan draftPlan = generateDraftPlan(brief);
    String message = isEnglish(brief)
        ? "All required information is available. The learning plan draft has been generated."
        : "信息已齐全，已生成学习计划草案。";
    return LearningPlanAgentResult.generated(message, draftPlan);
  }

  private LearningPlanDraftPlan generateDraftPlan(LearningPlanBrief brief) {
    int durationWeeks = brief.durationWeeks();
    int phaseCount = brief.targetProblemCount() == null
        ? validator.expectedPhaseCount(durationWeeks)
        : LearningPlanTargetSize.expectedPhaseCount(brief.targetProblemCount());
    List<Integer> phaseWeeks = splitWeeks(durationWeeks, phaseCount);
    List<String> preferredTags = brief.topicPreferences().isEmpty()
        ? List.of("Array", "Hash Table", "Two Pointers", "Dynamic Programming")
        : brief.topicPreferences();
    List<LearningPlanPhaseDraft> phases = new ArrayList<>();

    for (int index = 0; index < phaseCount; index++) {
      String tag = preferredTags.get(index % preferredTags.size());
      List<LearningPlanProblemCandidate> candidates = problemCatalog.searchProblems(new LearningPlanProblemSearch(
          tag,
          preferredDifficulty(brief.difficultyDistribution()),
          PROBLEMS_PER_PHASE)).stream()
          .map(candidate -> problemCatalog.findBySlug(
                  candidate.slug(),
                  brief.contentLocale().languageTag())
              .orElse(candidate))
          .toList();
      List<LearningPlanProblemDraft> problems = candidates.stream()
          .limit(PROBLEMS_PER_PHASE)
          .map(candidate -> LearningPlanProblemDraft.fromCandidate(
              candidate,
              candidates.indexOf(candidate) + 1,
              problemReason(brief, tag)))
          .toList();
      int phaseIndex = index + 1;
      phases.add(phase(brief, phaseIndex, phaseWeeks.get(index), tag, problems));
    }

    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put(LearningPlanDraftMetadataKeys.CONTENT_LOCALE, brief.contentLocale().languageTag());
    metadata.put(LearningPlanDraftMetadataKeys.PERSONALIZATION_ENABLED, brief.personalizationEnabled());
    if (brief.targetProblemCount() != null) {
      metadata.put(LearningPlanDraftMetadataKeys.TARGET_PROBLEM_COUNT, brief.targetProblemCount());
    }

    return new LearningPlanDraftPlan(
        titleFor(brief),
        summaryFor(brief),
        brief.intent(),
        brief.objective(),
        brief.durationWeeks(),
        brief.level(),
        brief.weeklyHours(),
        brief.programmingLanguage(),
        brief.difficultyDistribution(),
        brief.topicPreferences(),
        brief.additionalConstraints(),
        phases,
        metadata);
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

  private String titleFor(LearningPlanBrief brief) {
    if (isEnglish(brief)) {
      String language = brief.programmingLanguage() == null ? "" : brief.programmingLanguage() + " ";
      return brief.durationWeeks() + "-Week " + language + intentLabelEn(brief.intent()) + " Plan";
    }
    String language = brief.programmingLanguage() == null ? "" : brief.programmingLanguage() + " ";
    return brief.durationWeeks() + " 周" + language + intentLabel(brief.intent()) + "计划";
  }

  private String intentLabel(LearningPlanIntent intent) {
    if (intent == LearningPlanIntent.INTERVIEW_SPRINT) {
      return "面试冲刺";
    }
    if (intent == LearningPlanIntent.TOPIC_BREAKTHROUGH) {
      return "专题突破";
    }
    if (intent == LearningPlanIntent.LONG_TERM_LEARNING) {
      return "长期学习";
    }
    if (intent == LearningPlanIntent.ABILITY_DIAGNOSIS) {
      return "能力诊断";
    }
    if (intent == LearningPlanIntent.MISTAKE_REVIEW) {
      return "错题复盘";
    }
    return "刷题目标";
  }

  private String intentLabelEn(LearningPlanIntent intent) {
    if (intent == LearningPlanIntent.INTERVIEW_SPRINT) {
      return "Interview Sprint";
    }
    if (intent == LearningPlanIntent.TOPIC_BREAKTHROUGH) {
      return "Topic Breakthrough";
    }
    if (intent == LearningPlanIntent.LONG_TERM_LEARNING) {
      return "Long-Term Learning";
    }
    if (intent == LearningPlanIntent.ABILITY_DIAGNOSIS) {
      return "Ability Diagnosis";
    }
    if (intent == LearningPlanIntent.MISTAKE_REVIEW) {
      return "Mistake Review";
    }
    return "Practice Goal";
  }

  private LearningPlanPhaseDraft phase(
      LearningPlanBrief brief,
      int phaseIndex,
      int durationWeeks,
      String tag,
      List<LearningPlanProblemDraft> problems
  ) {
    if (isEnglish(brief)) {
      return new LearningPlanPhaseDraft(
          phaseIndex,
          "Phase " + phaseIndex + ": " + tag + " Practice",
          durationWeeks,
          tag,
          problems);
    }
    return new LearningPlanPhaseDraft(
        phaseIndex,
        "第 " + phaseIndex + " 阶段：" + tag + " 训练",
        durationWeeks,
        tag,
        problems);
  }

  private String summaryFor(LearningPlanBrief brief) {
    return isEnglish(brief)
        ? "Break " + brief.objective() + " into focused training phases and recommend problems from the local catalog."
        : "围绕 " + brief.objective() + " 拆分阶段训练，并使用本地题库推荐题目。";
  }

  private String problemReason(LearningPlanBrief brief, String tag) {
    return isEnglish(brief)
        ? "Practice " + tag + " to support the current objective: " + brief.objective()
        : "围绕 " + tag + " 训练，匹配当前目标：" + brief.objective();
  }

  private String preferredDifficulty(LearningPlanDifficultyDistribution distribution) {
    if (distribution.hardPercent() > distribution.mediumPercent()
        && distribution.hardPercent() >= distribution.easyPercent()) {
      return "HARD";
    }
    if (distribution.easyPercent() > distribution.mediumPercent()) {
      return "EASY";
    }
    return "MEDIUM";
  }

  private boolean isEnglish(LearningPlanBrief brief) {
    return brief.contentLocale() == LearningPlanContentLocale.EN_US;
  }

}
