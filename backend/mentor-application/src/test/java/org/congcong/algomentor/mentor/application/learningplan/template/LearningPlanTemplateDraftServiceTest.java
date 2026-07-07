package org.congcong.algomentor.mentor.application.learningplan.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanConfirmResult;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftCommand;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftResult;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftStatus;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftValidator;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCandidate;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCatalog;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemSearch;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanStatus;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanAgentService;

class LearningPlanTemplateDraftServiceTest {

  private final Clock clock = Clock.fixed(Instant.parse("2026-07-06T00:00:00Z"), ZoneOffset.UTC);
  private final InMemoryTemplateRepository templateRepository = new InMemoryTemplateRepository();
  private final InMemoryDraftRepository draftRepository = new InMemoryDraftRepository();
  private final InMemoryPlanRepository planRepository = new InMemoryPlanRepository();
  private final FakeProblemCatalog problemCatalog = new FakeProblemCatalog();
  private final LearningPlanDraftValidator validator = new LearningPlanDraftValidator();
  private final LearningPlanTemplateDraftService templateDraftService = new LearningPlanTemplateDraftService(
      templateRepository,
      draftRepository,
      problemCatalog,
      validator,
      clock);
  private final LearningPlanDraftService draftService = new LearningPlanDraftService(
      draftRepository,
      planRepository,
      new LearningPlanAgentService(problemCatalog),
      validator,
      clock);

  @org.junit.jupiter.api.Test
  void blind75TemplateCreatesFourWeekDraftWithAllMatchedProblemsAndCanBeConfirmed() {
    templateRepository.saveTemplate(blind75Template());

    LearningPlanDraftResult result = templateDraftService.createDraft(
        7L,
        new LearningPlanTemplateDraftCommand("neetcode_blind_75_interview_core", null, null, "Java"));

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
    assertThat((List<?>) metadata.get("problemRefs")).hasSize(150);

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
  void sixWeekRevisionTemplateCreatesFullMatchedDraft() {
    assertTemplateDraftMatchesLocalRefs("leetcode_top_100_liked_revision", 6, 6, 67, 0);
  }

  @org.junit.jupiter.api.Test
  void twelveWeekBeginnerTemplateCanUseMoreThanOneWeekPhases() {
    assertTemplateDraftMatchesLocalRefs("cn_algorithm_foundation_12weeks", 12, 9, 46, 0);
  }

  @org.junit.jupiter.api.Test
  void templateDurationCannotBeShorterThanPhaseCount() {
    templateRepository.saveTemplate(generatedTemplate("neetcode_150_systematic_interview", 12, 12, 150, 1));

    assertThatThrownBy(() -> templateDraftService.createDraft(
        7L,
        new LearningPlanTemplateDraftCommand("neetcode_150_systematic_interview", 4, null, null)))
        .isInstanceOf(LearningPlanException.class)
        .hasMessage("模板学习计划周期不能少于阶段数。");
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
    assertThat(metadata.get("problemCount")).isEqualTo(problemCount);
    assertThat(metadata.get("matchedProblemCount")).isEqualTo(problemCount - missingProblemCount);
    assertThat(metadata.get("missingProblemCount")).isEqualTo(missingProblemCount);
    assertThat((List<?>) metadata.get("problemRefs")).hasSize(problemCount);
  }

  private LearningPlanTemplate blind75Template() {
    List<LearningPlanTemplatePhase> phases = List.of(
        phase(1, "数组", List.of("two-sum", "valid-anagram", "contains-duplicate", "group-anagrams")),
        phase(2, "树", List.of("binary-search", "invert-binary-tree")),
        phase(3, "图", List.of("number-of-islands", "missing-problem")),
        phase(4, "动态规划", List.of("climbing-stairs", "coin-change")));
    return template("neetcode_blind_75_interview_core", 4, 10, 9, 1, phases);
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
        templateId,
        "summary",
        LearningPlanIntent.INTERVIEW_SPRINT,
        "准备算法面试",
        durationWeeks,
        LearningPlanLevel.INTERMEDIATE,
        8,
        "Java",
        LearningPlanDifficultyPreference.MEDIUM,
        true,
        List.of("Array", "Tree", "Graph", "Dynamic Programming"),
        "准备算法面试的学习者",
        Map.of("Easy", Map.of("count", 2), "Medium", Map.of("count", 6)),
        List.of("基础数据结构"),
        List.of("面试备战"),
        List.of("零基础"),
        "能完成核心题型复盘",
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
        title,
        1,
        title,
        List.of("完成 " + title),
        List.of(title),
        List.of("能复盘 " + title),
        "记录错题。",
        refs);
  }

  private static class FakeProblemCatalog implements LearningPlanProblemCatalog {

    private final Map<String, LearningPlanProblemCandidate> problems = new HashMap<>();

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
      if (slug.startsWith("missing-problem")) {
        return Optional.empty();
      }
      if (slug.startsWith("template-problem-")) {
        int frontendId = Integer.parseInt(slug.substring("template-problem-".length()));
        return Optional.of(new LearningPlanProblemCandidate(
            slug,
            frontendId,
            "Template Problem " + frontendId,
            "模板题 " + frontendId,
            "MEDIUM",
            List.of("Template")));
      }
      return Optional.ofNullable(problems.get(slug));
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
