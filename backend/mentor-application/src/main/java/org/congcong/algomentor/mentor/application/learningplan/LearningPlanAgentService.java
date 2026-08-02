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

  public LearningPlanAgentResult run(LearningPlanDraftCommand command, List<String> missingFields) {
    if (!missingFields.isEmpty()) {
      return LearningPlanAgentResult.askClarification(
          clarificationFor(missingFields.get(0), command.contentLocale()),
          missingFields);
    }
    LearningPlanDraftPlan draftPlan = generateDraftPlan(command);
    String message = isEnglish(command)
        ? "All required information is available. The learning plan draft has been generated."
        : "信息已齐全，已生成学习计划草案。";
    return LearningPlanAgentResult.generated(message, draftPlan);
  }

  private LearningPlanDraftPlan generateDraftPlan(LearningPlanDraftCommand command) {
    int durationWeeks = command.durationWeeks();
    int phaseCount = validator.expectedPhaseCount(durationWeeks);
    List<Integer> phaseWeeks = splitWeeks(durationWeeks, phaseCount);
    List<String> preferredTags = command.topicPreferences().isEmpty()
        ? List.of("Array", "Hash Table", "Two Pointers", "Dynamic Programming")
        : command.topicPreferences();
    List<LearningPlanPhaseDraft> phases = new ArrayList<>();
    boolean incomplete = false;

    for (int index = 0; index < phaseCount; index++) {
      String tag = preferredTags.get(index % preferredTags.size());
      List<LearningPlanProblemCandidate> candidates = problemCatalog.searchProblems(new LearningPlanProblemSearch(
          tag,
          command.difficultyPreference() == null ? null : command.difficultyPreference().name(),
          PROBLEMS_PER_PHASE)).stream()
          .map(candidate -> problemCatalog.findBySlug(
                  candidate.slug(),
                  command.contentLocale().languageTag())
              .orElse(candidate))
          .toList();
      List<LearningPlanProblemDraft> problems = candidates.stream()
          .limit(PROBLEMS_PER_PHASE)
          .map(candidate -> LearningPlanProblemDraft.fromCandidate(
              candidate,
              candidates.indexOf(candidate) + 1,
              problemReason(command, tag)))
          .toList();
      incomplete = incomplete || problems.size() < 3;
      int phaseIndex = index + 1;
      phases.add(phase(command, phaseIndex, phaseWeeks.get(index), tag, problems));
    }

    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put(LearningPlanDraftMetadataKeys.PROBLEM_RECOMMENDATION_INCOMPLETE, incomplete);
    metadata.put(LearningPlanDraftMetadataKeys.CONTENT_LOCALE, command.contentLocale().languageTag());

    return new LearningPlanDraftPlan(
        titleFor(command),
        summaryFor(command),
        command.intent(),
        command.goal(),
        durationWeeks,
        command.level(),
        command.weeklyHours(),
        command.programmingLanguage(),
        command.difficultyPreference(),
        command.interviewOriented(),
        command.topicPreferences(),
        profileSummary(command),
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

  private String titleFor(LearningPlanDraftCommand command) {
    if (isEnglish(command)) {
      String language = command.programmingLanguage() == null ? "" : command.programmingLanguage() + " ";
      return command.durationWeeks() + "-Week " + language + intentLabelEn(command.intent()) + " Plan";
    }
    String language = command.programmingLanguage() == null ? "" : command.programmingLanguage() + " ";
    return command.durationWeeks() + " 周" + language + intentLabel(command.intent()) + "计划";
  }

  private String profileSummary(LearningPlanDraftCommand command) {
    if (isEnglish(command)) {
      return "Current level: " + command.level()
          + ", " + command.weeklyHours() + " hours per week"
          + (command.programmingLanguage() == null ? "" : ", language: " + command.programmingLanguage());
    }
    return "当前水平：" + command.level()
        + "，每周 " + command.weeklyHours() + " 小时"
        + (command.programmingLanguage() == null ? "" : "，语言：" + command.programmingLanguage());
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
      LearningPlanDraftCommand command,
      int phaseIndex,
      int durationWeeks,
      String tag,
      List<LearningPlanProblemDraft> problems
  ) {
    if (isEnglish(command)) {
      return new LearningPlanPhaseDraft(
          phaseIndex,
          "Phase " + phaseIndex + ": " + tag + " Practice",
          durationWeeks,
          tag,
          List.of(
              "Build a reliable problem-solving method for " + tag,
              "Complete the recommended problems and review key mistakes"),
          List.of(tag),
          List.of(
              "Explain the core approach for this phase independently",
              "Summarize at least one reusable pattern or boundary-case lesson"),
          "Record root causes, complexity analysis, and reusable patterns.",
          problems);
    }
    return new LearningPlanPhaseDraft(
        phaseIndex,
        "第 " + phaseIndex + " 阶段：" + tag + " 训练",
        durationWeeks,
        tag,
        List.of("围绕 " + tag + " 建立稳定解题方法", "完成推荐题并复盘关键错误"),
        List.of(tag),
        List.of("能独立说明本阶段题目的核心思路", "能总结至少 1 条可复用模板或边界经验"),
        "记录错误原因、复杂度分析和可复用模板。",
        problems);
  }

  private String summaryFor(LearningPlanDraftCommand command) {
    return isEnglish(command)
        ? "Break " + command.goal() + " into focused training phases and recommend problems from the local catalog."
        : "围绕 " + command.goal() + " 拆分阶段训练，并使用本地题库推荐题目。";
  }

  private String problemReason(LearningPlanDraftCommand command, String tag) {
    return isEnglish(command)
        ? "Practice " + tag + " to support the current goal: " + command.goal()
        : "围绕 " + tag + " 训练，匹配当前目标：" + command.goal();
  }

  private boolean isEnglish(LearningPlanDraftCommand command) {
    return command.contentLocale() == LearningPlanContentLocale.EN_US;
  }

  private String clarificationFor(String field, LearningPlanContentLocale locale) {
    if (locale == LearningPlanContentLocale.EN_US) {
      return switch (field) {
        case "intent" -> "What kind of learning plan do you want, such as an interview sprint, topic breakthrough, or long-term learning plan?";
        case "goal" -> "What is the learning goal for this plan, such as preparing for Java backend algorithm interviews?";
        case "durationWeeks" -> "How many weeks should the plan cover?";
        case "level" -> "Is your current algorithm level closer to beginner, intermediate, or advanced?";
        case "weeklyHours" -> "About how many hours per week can you spend studying algorithms?";
        default -> "Please provide the most important missing detail so the plan can be generated.";
      };
    }
    return switch (field) {
      case "intent" -> "你想创建哪类学习计划？例如面试冲刺、专题突破或长期学习。";
      case "goal" -> "请补充这份计划的学习目标，例如准备 Java 后端算法面试。";
      case "durationWeeks" -> "你希望计划持续几周？";
      case "level" -> "你当前算法水平更接近入门、中级还是高级？";
      case "weeklyHours" -> "你每周大约可以投入几小时学习算法？";
      default -> "请补充一个最关键的信息，方便继续生成计划。";
    };
  }
}
