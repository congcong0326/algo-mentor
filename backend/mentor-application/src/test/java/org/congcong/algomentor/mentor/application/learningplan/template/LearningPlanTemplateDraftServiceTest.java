package org.congcong.algomentor.mentor.application.learningplan.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanConfirmResult;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftMetadataKeys;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftResult;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftStatus;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftValidator;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCandidate;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCatalog;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemSearch;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanStatus;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanAgentService;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanCreationPolicy;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanCreationPolicyConstants;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanCreationPolicyService;

class LearningPlanTemplateDraftServiceTest {

  private final Clock clock = Clock.fixed(Instant.parse("2026-07-06T00:00:00Z"), ZoneOffset.UTC);
  private final InMemoryTemplateRepository templateRepository = new InMemoryTemplateRepository();
  private final InMemoryDraftRepository draftRepository = new InMemoryDraftRepository();
  private final InMemoryPlanRepository planRepository = new InMemoryPlanRepository();
  private final FakeProblemCatalog problemCatalog = new FakeProblemCatalog();
  private final LearningPlanDraftValidator validator = new LearningPlanDraftValidator();
  private final LearningPlanLoadService loadService = new LearningPlanLoadService(clock);
  private final LearningPlanTemplateDraftService templateDraftService = new LearningPlanTemplateDraftService(
      templateRepository,
      draftRepository,
      problemCatalog,
      validator,
      loadService,
      clock);
  private final LearningPlanDraftService draftService = new LearningPlanDraftService(
      draftRepository,
      planRepository,
      new LearningPlanAgentService(problemCatalog),
      validator,
      loadService,
      clock);

  @org.junit.jupiter.api.Test
  void blind75TemplateCreatesFourWeekDraftWithAllMatchedProblemsAndCanBeConfirmed() {
    templateRepository.saveTemplate(blind75Template());

    LearningPlanDraftResult result = templateDraftService.createDraft(
        7L,
        new LearningPlanTemplateDraftCommand("neetcode_blind_75_interview_core", "Java", 2, 5));

    assertThat(result.status()).isEqualTo(LearningPlanDraftStatus.GENERATED);
    assertThat(result.draftPlan().durationWeeks()).isEqualTo(4);
    assertThat(result.draftPlan().phases()).hasSize(4);
    assertThat(result.draftPlan().phases())
        .flatExtracting(LearningPlanPhaseDraft::problems)
        .extracting(LearningPlanProblemDraft::slug)
        .doesNotContain("missing-problem");
    assertThat(result.draftPlan().phases())
        .flatExtracting(LearningPlanPhaseDraft::problems)
        .hasSize(9);
    assertThat(result.draftPlan().metadata()).containsKey("template");
    assertThat(result.draftPlan().metadata())
        .containsEntry("dailyProblemCount", 2)
        .containsEntry("trainingDaysPerWeek", 5)
        .containsEntry(LearningPlanDraftMetadataKeys.CONTENT_LOCALE, "zh-CN")
        .containsEntry(LearningPlanDraftMetadataKeys.PERSONALIZATION_ENABLED, false)
        .containsKey("loadSummary")
        .doesNotContainKeys("rhythmMode", "weeklyBuckets", "nextTrainingPackage");
    assertThat(result.draftPlan().objective()).isEqualTo("准备算法面试");
    assertThat(result.draftPlan().difficultyDistribution().easyPercent()).isEqualTo(35);
    assertThat(result.draftPlan().difficultyDistribution().mediumPercent()).isEqualTo(55);
    assertThat(result.draftPlan().difficultyDistribution().hardPercent()).isEqualTo(10);
    assertThat(result.draftPlan().additionalConstraints()).isNull();

    LearningPlanConfirmResult confirmed = draftService.confirmDraft(7L, result.draftId());

    assertThat(confirmed.status()).isEqualTo(LearningPlanStatus.ACTIVE);
    assertThat(planRepository.plans).hasSize(1);
  }

  @org.junit.jupiter.api.Test
  void neetcode150TemplateCreatesFullTwelvePhaseDraftAndCanBeConfirmed() {
    templateRepository.saveTemplate(generatedTemplate("neetcode_150_systematic_interview", 12, 12, 150, 1));

    LearningPlanDraftResult result = templateDraftService.createDraft(
        7L,
        new LearningPlanTemplateDraftCommand("neetcode_150_systematic_interview", null, null, null));

    assertThat(result.draftPlan().durationWeeks()).isEqualTo(12);
    assertThat(result.draftPlan().phases()).hasSize(12);
    assertThat(result.draftPlan().phases().get(0).problems()).hasSizeGreaterThan(5);
    assertThat(result.draftPlan().phases())
        .flatExtracting(LearningPlanPhaseDraft::problems)
        .hasSize(149);
    Map<?, ?> metadata = (Map<?, ?>) result.draftPlan().metadata().get("template");
    assertThat(metadata.get("templateId")).isEqualTo("neetcode_150_systematic_interview");
    assertThat(metadata.get("matchedProblemCount")).isEqualTo(149);
    assertThat(metadata.keySet().stream().map(String::valueOf).toList())
        .containsExactlyInAnyOrder("templateId", "matchedProblemCount");

    LearningPlanConfirmResult confirmed = draftService.confirmDraft(7L, result.draftId());

    assertThat(confirmed.status()).isEqualTo(LearningPlanStatus.ACTIVE);
  }

  @org.junit.jupiter.api.Test
  void twoWeekTemplateCreatesTwoPhaseDraftWithAllMatchedProblems() {
    assertTemplateDraftMatchesLocalRefs("topic_binary_search_boundaries", 2, 2, 18, 0);
  }

  @org.junit.jupiter.api.Test
  void fiveWeekTemplateKeepsMissingProblemsOnlyInMetadata() {
    assertTemplateDraftMatchesLocalRefs("tih_best_practice_50_5weeks", 5, 5, 61, 6);
  }

  @org.junit.jupiter.api.Test
  void hot100TemplateCreatesCompleteTenWeekDraftAndKeepsMissingProblemInMetadata() {
    assertTemplateDraftMatchesLocalRefs("leetcode_top_100_liked_revision", 10, 10, 100, 1);
  }

  @org.junit.jupiter.api.Test
  void twelveWeekBeginnerTemplateCanUseMoreThanOneWeekPhases() {
    assertTemplateDraftMatchesLocalRefs("cn_algorithm_foundation_12weeks", 12, 9, 46, 0);
  }

  @org.junit.jupiter.api.Test
  void tihAlgorithmEssentialsCreatesCompleteSixWeekDraft() {
    assertTemplateDraftMatchesLocalRefs("tih_algorithm_essentials", 6, 6, 119, 9);
  }

  @org.junit.jupiter.api.Test
  void advancedDynamicProgrammingCreatesCompleteFourWeekDraft() {
    assertTemplateDraftMatchesLocalRefs("topic_dp_advanced", 4, 4, 31, 0);
  }

  @org.junit.jupiter.api.Test
  void swordOfferCreatesCompleteEightWeekDraft() {
    assertTemplateDraftMatchesLocalRefs("sword_offer_classic", 8, 8, 75, 0);
  }

  @org.junit.jupiter.api.Test
  void beginnerPatternRoadmapCreatesCompleteTenWeekDraft() {
    assertTemplateDraftMatchesLocalRefs("leetcode_patterns_beginner_roadmap", 10, 10, 68, 4);
  }

  @org.junit.jupiter.api.Test
  void crackingCodingInterviewCreatesCompleteTenWeekDraft() {
    assertTemplateDraftMatchesLocalRefs("cracking_coding_interview_classic", 10, 10, 109, 0);
  }

  @org.junit.jupiter.api.Test
  void leetcode75CreatesCompleteSixWeekDraft() {
    assertTemplateDraftMatchesLocalRefs("leetcode_75_core_sprint", 6, 6, 75, 0);
  }

  @org.junit.jupiter.api.Test
  void topInterview150CreatesCompleteTenWeekDraft() {
    assertTemplateDraftMatchesLocalRefs("leetcode_top_interview_150", 10, 10, 150, 0);
  }

  @org.junit.jupiter.api.Test
  void carlFullRoadmapCreatesCompleteSixteenWeekDraft() {
    assertTemplateDraftMatchesLocalRefs("carl_algorithm_roadmap_full", 16, 11, 144, 0);
  }

  @org.junit.jupiter.api.Test
  void labuladongThinkingRouteCreatesCompleteEightWeekDraft() {
    assertTemplateDraftMatchesLocalRefs("labuladong_algo_thinking", 8, 8, 64, 0);
  }

  @org.junit.jupiter.api.Test
  void sql50CreatesCompleteSevenWeekDraft() {
    assertTemplateDraftMatchesLocalRefs("leetcode_sql_50", 7, 7, 50, 0);
  }

  @org.junit.jupiter.api.Test
  void javascript30DaysCreatesCompleteFiveWeekDraft() {
    assertTemplateDraftMatchesLocalRefs("leetcode_javascript_30_days", 5, 5, 30, 0);
  }

  @org.junit.jupiter.api.Test
  void pandasIntroductionCreatesCompleteFourWeekDraft() {
    assertTemplateDraftMatchesLocalRefs("leetcode_pandas_introduction", 4, 4, 15, 0);
  }

  @org.junit.jupiter.api.Test
  void pandas30DaysKeepsFiveMissingProblemsOnlyInMetadata() {
    assertTemplateDraftMatchesLocalRefs("leetcode_pandas_30_days", 5, 5, 33, 5);
  }

  @org.junit.jupiter.api.Test
  void invalidRhythmIsRejected() {
    templateRepository.saveTemplate(generatedTemplate("neetcode_150_systematic_interview", 12, 12, 150, 1));

    assertThatThrownBy(() -> templateDraftService.createDraft(
        7L,
        new LearningPlanTemplateDraftCommand("neetcode_150_systematic_interview", null, 11, 5)))
        .isInstanceOf(LearningPlanException.class)
        .hasMessage("每天题目数必须在 1-10 之间。");
  }

  @org.junit.jupiter.api.Test
  void rejectsTemplateDraftWhenDailyCreationLimitIsReached() {
    templateRepository.saveTemplate(blind75Template());
    LearningPlanTemplateDraftService limitedService = new LearningPlanTemplateDraftService(
        templateRepository,
        draftRepository,
        problemCatalog,
        validator,
        loadService,
        new LearningPlanCreationPolicyService(
            ignored -> new LearningPlanCreationPolicy(30, 0, 14),
            ZoneOffset.UTC),
        clock);

    assertThatThrownBy(() -> limitedService.createDraft(
        7L,
        new LearningPlanTemplateDraftCommand("neetcode_blind_75_interview_core", "Java", 2, 5)))
        .isInstanceOfSatisfying(LearningPlanException.class, exception ->
            assertThat(exception.code()).isEqualTo(
                LearningPlanCreationPolicyConstants.DRAFT_DAILY_LIMIT_EXCEEDED_CODE));
  }

  @org.junit.jupiter.api.Test
  void customRhythmWritesDailyProblemCountAndTrainingDaysMetadata() {
    templateRepository.saveTemplate(blind75Template());

    LearningPlanDraftResult result = templateDraftService.createDraft(
        7L,
        new LearningPlanTemplateDraftCommand(
            "neetcode_blind_75_interview_core",
            "Java",
            3,
            4));

    assertThat(result.draftPlan().durationWeeks()).isEqualTo(4);
    assertThat(result.draftPlan().weeklyHours()).isEqualTo(8);
    assertThat(result.draftPlan().metadata())
        .containsEntry("dailyProblemCount", 3)
        .containsEntry("trainingDaysPerWeek", 4);
    assertThat(result.draftPlan().phases())
        .flatExtracting(LearningPlanPhaseDraft::problems)
        .hasSize(9);
  }

  @org.junit.jupiter.api.Test
  void templateDraftSnapshotsProblemRecommendationReasonsInTheRequestedLanguage() {
    templateRepository.saveTemplate(withEnglishContent(blind75Template()));

    LearningPlanDraftResult chineseDraft = templateDraftService.createDraft(
        7L,
        new LearningPlanTemplateDraftCommand("neetcode_blind_75_interview_core", null, null, null));
    LearningPlanDraftResult englishDraft = templateDraftService.createDraft(
        7L,
        new LearningPlanTemplateDraftCommand(
            "neetcode_blind_75_interview_core",
            null,
            null,
            null,
            LearningPlanContentLocale.EN_US));

    assertThat(problemReasons(chineseDraft)).contains("中文推荐：two-sum");
    assertThat(problemReasons(englishDraft)).contains("English recommendation: two-sum");
    assertThat(englishDraft.draftPlan().contentLocale()).isEqualTo(LearningPlanContentLocale.EN_US);
    assertThat(problemReasons(chineseDraft))
        .noneMatch(reason -> reason.contains("来自模板") || reason.contains("围绕"));
    assertThat(problemReasons(englishDraft))
        .noneMatch(reason -> reason.contains("来自模板") || reason.contains("围绕"));
  }

  @org.junit.jupiter.api.Test
  void templateDraftRejectsMatchedProblemWithoutRecommendationReason() {
    templateRepository.saveTemplate(blind75Template());
    problemCatalog.removeRecommendationReason("two-sum");

    assertThatThrownBy(() -> templateDraftService.createDraft(
        7L,
        new LearningPlanTemplateDraftCommand("neetcode_blind_75_interview_core", null, null, null)))
        .isInstanceOfSatisfying(LearningPlanException.class, exception -> {
          assertThat(exception.code()).isEqualTo("LEARNING_PLAN_TEMPLATE_PROBLEM_REASON_MISSING");
          assertThat(exception).hasMessage("题库数据不完整：模板题目缺少推荐理由：two-sum。");
        });
  }

  @org.junit.jupiter.api.Test
  void templateDraftSkipsUnmatchedLegacyProblemWithoutRecommendationReason() {
    problemCatalog.add("legacy-premium-problem", 271, "Legacy Premium Problem", "MEDIUM", "Array");
    problemCatalog.removeRecommendationReason("legacy-premium-problem");
    LearningPlanTemplatePhase templatePhase = phase(
        1,
        "数组",
        List.of("two-sum", "legacy-premium-problem"));
    templatePhase = templatePhase.withProblemRefs(templatePhase.problemRefs().stream()
        .map(ref -> "legacy-premium-problem".equals(ref.problemSlug())
            ? ref.withMatchedProblem(false)
            : ref)
        .toList());
    templateRepository.saveTemplate(template(
        "legacy_premium_template",
        1,
        2,
        1,
        1,
        List.of(templatePhase)));

    LearningPlanDraftResult result = templateDraftService.createDraft(
        7L,
        new LearningPlanTemplateDraftCommand("legacy_premium_template", null, null, null));

    assertThat(result.draftPlan().phases())
        .flatExtracting(LearningPlanPhaseDraft::problems)
        .extracting(LearningPlanProblemDraft::slug)
        .containsExactly("two-sum");
  }

  private List<String> problemReasons(LearningPlanDraftResult result) {
    return result.draftPlan().phases().stream()
        .flatMap(phase -> phase.problems().stream())
        .map(LearningPlanProblemDraft::reason)
        .toList();
  }

  private void assertTemplateDraftMatchesLocalRefs(
      String templateId,
      int durationWeeks,
      int phaseCount,
      int problemCount,
      int missingProblemCount
  ) {
    templateRepository.saveTemplate(generatedTemplate(templateId, durationWeeks, phaseCount, problemCount, missingProblemCount));

    LearningPlanDraftResult result = templateDraftService.createDraft(
        7L,
        new LearningPlanTemplateDraftCommand(templateId, null, null, null));

    assertThat(result.status()).isEqualTo(LearningPlanDraftStatus.GENERATED);
    assertThat(result.draftPlan().durationWeeks()).isEqualTo(durationWeeks);
    assertThat(result.draftPlan().phases()).hasSize(phaseCount);
    assertThat(result.draftPlan().phases().stream().mapToInt(LearningPlanPhaseDraft::durationWeeks).sum())
        .isEqualTo(durationWeeks);
    assertThat(result.draftPlan().phases())
        .flatExtracting(LearningPlanPhaseDraft::problems)
        .extracting(LearningPlanProblemDraft::slug)
        .allSatisfy(slug -> assertThat((String) slug).doesNotStartWith("missing-problem"));
    assertThat(result.draftPlan().phases())
        .flatExtracting(LearningPlanPhaseDraft::problems)
        .hasSize(problemCount - missingProblemCount);
    Map<?, ?> metadata = (Map<?, ?>) result.draftPlan().metadata().get("template");
    assertThat(metadata.get("templateId")).isEqualTo(templateId);
    assertThat(metadata.get("matchedProblemCount")).isEqualTo(problemCount - missingProblemCount);
    assertThat(metadata.keySet().stream().map(String::valueOf).toList())
        .containsExactlyInAnyOrder("templateId", "matchedProblemCount");
  }

  private LearningPlanTemplate blind75Template() {
    List<LearningPlanTemplatePhase> phases = List.of(
        phase(1, "数组", List.of("two-sum", "valid-anagram", "contains-duplicate", "group-anagrams")),
        phase(2, "树", List.of("binary-search", "invert-binary-tree")),
        phase(3, "图", List.of("number-of-islands", "missing-problem")),
        phase(4, "动态规划", List.of("climbing-stairs", "coin-change")));
    return template("neetcode_blind_75_interview_core", 4, 10, 9, 1, phases);
  }

  private LearningPlanTemplate withEnglishContent(LearningPlanTemplate template) {
    List<LearningPlanTemplatePhase> phases = template.phases().stream()
        .map(phase -> new LearningPlanTemplatePhase(
            phase.id(),
            phase.phaseIndex(),
            phase.title(),
            "English " + phase.title(),
            phase.durationWeeks(),
            phase.focus(),
            "English " + phase.focus(),
            phase.problemRefs()))
        .toList();
    return new LearningPlanTemplate(
        template.id(),
        template.templateId(),
        template.title(),
        "English template title",
        template.summary(),
        "English template summary",
        template.catalogCategory(),
        template.recommendedOrder(),
        template.intent(),
        template.goal(),
        "Prepare for algorithm interviews",
        template.defaultDurationWeeks(),
        template.level(),
        template.defaultWeeklyHours(),
        template.programmingLanguage(),
        template.difficultyPreference(),
        template.topicPreferences(),
        template.targetAudience(),
        "Algorithm interview candidates",
        template.prerequisites(),
        List.of("Basic data structures"),
        template.recommendedFor(),
        List.of("Interview preparation"),
        template.notRecommendedFor(),
        List.of("Complete beginners"),
        template.expectedOutcome(),
        "Review core problem patterns",
        true,
        template.sourceName(),
        template.sourceUrl(),
        template.sourceCommit(),
        template.sourceDataPath(),
        template.sourceDescription(),
        template.curationNotes(),
        template.licenseNotice(),
        template.problemCount(),
        template.matchedProblemCount(),
        template.missingProblemCount(),
        template.metadata(),
        phases);
  }

  private LearningPlanTemplate generatedTemplate(
      String templateId,
      int durationWeeks,
      int phaseCount,
      int problemCount,
      int missingProblemCount
  ) {
    List<LearningPlanTemplatePhase> phases = new ArrayList<>();
    int sourceOrder = 1;
    int matchedProblemCount = problemCount - missingProblemCount;
    for (int phaseIndex = 1; phaseIndex <= phaseCount; phaseIndex++) {
      int from = (phaseIndex - 1) * problemCount / phaseCount;
      int to = phaseIndex * problemCount / phaseCount;
      List<String> slugs = new ArrayList<>();
      for (int index = from; index < to; index++) {
        if (index >= matchedProblemCount) {
          slugs.add("missing-problem-" + (index - matchedProblemCount + 1));
        } else {
          slugs.add("template-problem-" + (index + 1));
        }
      }
      phases.add(phase(phaseIndex, "阶段 " + phaseIndex, slugs, sourceOrder));
      sourceOrder += slugs.size();
    }
    return template(templateId, durationWeeks, problemCount, matchedProblemCount, missingProblemCount, phases);
  }

  private LearningPlanTemplate template(
      String templateId,
      int durationWeeks,
      int problemCount,
      int matchedProblemCount,
      int missingProblemCount,
      List<LearningPlanTemplatePhase> phases
  ) {
    return new LearningPlanTemplate(
        null,
        templateId,
        templateId,null,
        "summary",null,
        LearningPlanTemplateCatalogCategory.INTERVIEW_PREP,
        1,
        LearningPlanIntent.INTERVIEW_SPRINT,
        "准备算法面试",null,
        durationWeeks,
        LearningPlanLevel.INTERMEDIATE,
        8,
        "Java",
        LearningPlanDifficultyPreference.MEDIUM,
        List.of("Array", "Tree", "Graph", "Dynamic Programming"),
        "准备算法面试的学习者",null,
        List.of("基础数据结构"),List.of(),
        List.of("面试备战"),List.of(),
        List.of("零基础"),List.of(),
        "能完成核心题型复盘",null,false,
        "neetcode-gh/leetcode",
        "https://github.com/neetcode-gh/leetcode",
        "9907b7fed441fa55083c0751e208b7197101dbba",
        ".problemSiteData.json",
        "source",
        "notes",
        "MIT metadata only",
        problemCount,
        matchedProblemCount,
        missingProblemCount,
        Map.of(),
        phases);
  }

  private LearningPlanTemplatePhase phase(int phaseIndex, String title, List<String> slugs) {
    return phase(phaseIndex, title, slugs, phaseIndex * 100);
  }

  private LearningPlanTemplatePhase phase(int phaseIndex, String title, List<String> slugs, int firstSourceOrder) {
    List<LearningPlanTemplateProblemRef> refs = new ArrayList<>();
    for (int index = 0; index < slugs.size(); index++) {
      String slug = slugs.get(index);
      refs.add(new LearningPlanTemplateProblemRef(
          null,
          phaseIndex,
          index + 1,
          firstSourceOrder + index,
          slug,
          slug,
          "Medium",
          title,
          "https://neetcode.io/problems/" + slug,
          !slug.startsWith("missing-problem"),
          Map.of()));
    }
    return new LearningPlanTemplatePhase(
        null,
        phaseIndex,
        title,null,
        1,
        title,null,
        refs);
  }

  private static class FakeProblemCatalog implements LearningPlanProblemCatalog {

    private final Map<String, LearningPlanProblemCandidate> problems = new HashMap<>();
    private final Set<String> missingRecommendationReasonSlugs = new HashSet<>();

    FakeProblemCatalog() {
      add("two-sum", 1, "Two Sum", "EASY", "Array");
      add("valid-anagram", 242, "Valid Anagram", "EASY", "Hash Table");
      add("contains-duplicate", 217, "Contains Duplicate", "EASY", "Array");
      add("group-anagrams", 49, "Group Anagrams", "MEDIUM", "Hash Table");
      add("binary-search", 704, "Binary Search", "EASY", "Binary Search");
      add("invert-binary-tree", 226, "Invert Binary Tree", "EASY", "Tree");
      add("number-of-islands", 200, "Number of Islands", "MEDIUM", "Graph");
      add("climbing-stairs", 70, "Climbing Stairs", "EASY", "Dynamic Programming");
      add("coin-change", 322, "Coin Change", "MEDIUM", "Dynamic Programming");
    }

    @Override
    public List<LearningPlanProblemCandidate> searchProblems(LearningPlanProblemSearch search) {
      return List.copyOf(problems.values());
    }

    @Override
    public Optional<LearningPlanProblemCandidate> findBySlug(String slug) {
      return findBySlug(slug, LearningPlanTemplateDraftCommand.DEFAULT_RECOMMENDATION_REASON_LOCALE);
    }

    @Override
    public Optional<LearningPlanProblemCandidate> findBySlug(String slug, String locale) {
      if (slug.startsWith("missing-problem")) {
        return Optional.empty();
      }
      LearningPlanProblemCandidate candidate;
      if (slug.startsWith("template-problem-")) {
        int frontendId = Integer.parseInt(slug.substring("template-problem-".length()));
        candidate = new LearningPlanProblemCandidate(
            slug,
            frontendId,
            "Template Problem " + frontendId,
            "模板题 " + frontendId,
            "MEDIUM",
            List.of("Template"));
      } else {
        candidate = problems.get(slug);
      }
      return Optional.ofNullable(candidate).map(problem -> withRecommendationReason(problem, locale));
    }

    void removeRecommendationReason(String slug) {
      missingRecommendationReasonSlugs.add(slug);
    }

    private LearningPlanProblemCandidate withRecommendationReason(
        LearningPlanProblemCandidate problem,
        String locale
    ) {
      String reason = missingRecommendationReasonSlugs.contains(problem.slug())
          ? null
          : LearningPlanTemplateDraftCommand.ENGLISH_RECOMMENDATION_REASON_LOCALE.equals(locale)
              ? "English recommendation: " + problem.slug()
              : "中文推荐：" + problem.slug();
      return new LearningPlanProblemCandidate(
          problem.slug(),
          problem.frontendId(),
          problem.title(),
          problem.titleCn(),
          problem.difficulty(),
          problem.tags(),
          reason);
    }

    private void add(String slug, int frontendId, String title, String difficulty, String tag) {
      problems.put(slug, new LearningPlanProblemCandidate(slug, frontendId, title, title, difficulty, List.of(tag)));
    }
  }

  private static class InMemoryTemplateRepository implements LearningPlanTemplateRepository {

    private final Map<String, LearningPlanTemplate> templates = new HashMap<>();

    @Override
    public List<LearningPlanTemplate> findAllTemplates() {
      return List.copyOf(templates.values());
    }

    @Override
    public Optional<LearningPlanTemplate> findByTemplateId(String templateId) {
      return Optional.ofNullable(templates.get(templateId));
    }

    @Override
    public LearningPlanTemplate saveTemplate(LearningPlanTemplate template) {
      templates.put(template.templateId(), template);
      return template;
    }

    @Override
    public void insertImportRun(LearningPlanTemplateImportRun importRun) {
    }
  }

  private static class InMemoryDraftRepository implements LearningPlanDraftRepository {

    private final Map<Long, LearningPlanDraft> drafts = new HashMap<>();
    private long sequence = 100;

    @Override
    public LearningPlanDraft save(LearningPlanDraft draft) {
      long id = draft.id() == null ? sequence++ : draft.id();
      LearningPlanDraft saved = draft.withId(id);
      drafts.put(id, saved);
      return saved;
    }

    @Override
    public Optional<LearningPlanDraft> findDraftByIdForUser(long draftId, long userId) {
      return Optional.ofNullable(drafts.get(draftId)).filter(draft -> draft.userId() == userId);
    }
  }

  private static class InMemoryPlanRepository implements LearningPlanRepository {

    private final Map<Long, LearningPlan> plans = new HashMap<>();
    private long sequence = 900;

    @Override
    public LearningPlan save(LearningPlan plan) {
      long id = plan.id() == null ? sequence++ : plan.id();
      LearningPlan saved = plan.withId(id);
      plans.put(id, saved);
      return saved;
    }

    @Override
    public List<LearningPlan> findByUserId(long userId) {
      return plans.values().stream().filter(plan -> plan.userId() == userId).toList();
    }

    @Override
    public Optional<LearningPlan> findPlanByIdForUser(long planId, long userId) {
      return Optional.ofNullable(plans.get(planId)).filter(plan -> plan.userId() == userId);
    }
  }
}
