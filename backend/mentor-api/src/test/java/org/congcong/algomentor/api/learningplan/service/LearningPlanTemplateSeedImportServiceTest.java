package org.congcong.algomentor.api.learningplan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.congcong.algomentor.api.problem.model.ProblemDetail;
import org.congcong.algomentor.api.problem.model.ProblemFilters;
import org.congcong.algomentor.api.problem.model.ProblemListItem;
import org.congcong.algomentor.api.problem.model.ProblemListRequest;
import org.congcong.algomentor.api.problem.model.ProblemPage;
import org.congcong.algomentor.api.problem.model.ProblemSeedRecord;
import org.congcong.algomentor.api.problem.repository.ProblemRepository;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplate;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateImportRun;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

class LearningPlanTemplateSeedImportServiceTest {

  @TempDir
  private Path tempDir;

  @Test
  void importGeneratedSeedMatchesManifestCounts() throws Exception {
    Path repoRoot = repoRoot();
    ObjectMapper objectMapper = new ObjectMapper();
    InMemoryTemplateRepository templateRepository = new InMemoryTemplateRepository();
    LearningPlanTemplateSeedImportService service = new LearningPlanTemplateSeedImportService(
        new StaticObjectProvider<>(templateRepository),
        new StaticObjectProvider<>(new InMemoryProblemRepository(localProblemSlugs(repoRoot, objectMapper))),
        new LearningPlanTemplateSeedReader(objectMapper),
        objectMapper);

    LearningPlanTemplateSeedImportResult result = service.importSeed(repoRoot.resolve("data/learning-plan-template-seed"));

    assertThat(result.templateCount()).isEqualTo(16);
    assertThat(result.problemRefCount()).isEqualTo(631);
    assertThat(result.matchedProblemCount()).isEqualTo(611);
    assertThat(result.missingProblemCount()).isEqualTo(20);
    assertThat(templateRepository.templates).hasSize(16);
    assertThat(templateRepository.templates)
        .extracting(LearningPlanTemplate::templateId)
        .contains(
            "tih_best_practice_50_5weeks",
            "topic_binary_search_boundaries",
            "topic_tree_binary_tree_foundation",
            "topic_bit_manipulation");
    Map<?, ?> manifest = (Map<?, ?>) templateRepository.importRuns.get(0).metadata().get("manifest");
    assertThat((List<?>) manifest.get("sources")).hasSizeGreaterThanOrEqualTo(5);
  }

  @Test
  void importSeedWritesTemplatesAndAuditRunWithMissingProblemCount() throws Exception {
    writeValidSeed(tempDir);
    InMemoryTemplateRepository templateRepository = new InMemoryTemplateRepository();
    LearningPlanTemplateSeedImportService service = new LearningPlanTemplateSeedImportService(
        new StaticObjectProvider<>(templateRepository),
        new StaticObjectProvider<>(new InMemoryProblemRepository(Set.of("two-sum"))),
        new LearningPlanTemplateSeedReader(new ObjectMapper()),
        new ObjectMapper());

    LearningPlanTemplateSeedImportResult result = service.importSeed(tempDir);

    assertThat(result.templateCount()).isEqualTo(1);
    assertThat(result.problemRefCount()).isEqualTo(2);
    assertThat(result.matchedProblemCount()).isEqualTo(1);
    assertThat(result.missingProblemCount()).isEqualTo(1);
    LearningPlanTemplate saved = templateRepository.templates.get(0);
    assertThat(saved.templateId()).isEqualTo("neetcode_blind_75_interview_core");
    assertThat(saved.matchedProblemCount()).isEqualTo(1);
    assertThat(saved.missingProblemCount()).isEqualTo(1);
    assertThat(saved.phases().get(0).problemRefs())
        .extracting(ref -> ref.problemSlug() + ":" + ref.matchedProblem())
        .containsExactly("two-sum:true", "missing-problem:false");
    assertThat(templateRepository.importRuns).hasSize(1);
    assertThat(templateRepository.importRuns.get(0).checksum()).isNotBlank();
  }

  @Test
  void importSeedKeepsMultiSourceManifestInAuditAndUsesRootSourceCommit() throws Exception {
    writeValidSeed(tempDir);
    Files.writeString(tempDir.resolve(LearningPlanTemplateSeedConstants.MANIFEST_FILE), """
        {
          "source": {
            "name": "algo-mentor learning-plan-template-seed",
            "commit": "p0-templates-2026-07-06"
          },
          "sources": [
            {
              "name": "neetcode-gh/leetcode",
              "commitOrVersion": "9907b7fed441fa55083c0751e208b7197101dbba",
              "templateIds": ["neetcode_blind_75_interview_core"]
            },
            {
              "name": "yangshun/tech-interview-handbook",
              "commitOrVersion": "8ee2acb54a05c4add123a824d15e7dfc4e703b2f",
              "templateIds": ["tih_best_practice_50_5weeks"]
            }
          ]
        }
        """);
    InMemoryTemplateRepository templateRepository = new InMemoryTemplateRepository();
    LearningPlanTemplateSeedImportService service = new LearningPlanTemplateSeedImportService(
        new StaticObjectProvider<>(templateRepository),
        new StaticObjectProvider<>(new InMemoryProblemRepository(Set.of("two-sum"))),
        new LearningPlanTemplateSeedReader(new ObjectMapper()),
        new ObjectMapper());

    service.importSeed(tempDir);

    LearningPlanTemplateImportRun importRun = templateRepository.importRuns.get(0);
    assertThat(importRun.sourceCommit()).isEqualTo("p0-templates-2026-07-06");
    Map<?, ?> manifest = (Map<?, ?>) importRun.metadata().get("manifest");
    assertThat((List<?>) manifest.get("sources")).hasSize(2);
  }

  @Test
  void importSeedIsIdempotentForTemplatesAndKeepsRunAudit() throws Exception {
    writeValidSeed(tempDir);
    InMemoryTemplateRepository templateRepository = new InMemoryTemplateRepository();
    LearningPlanTemplateSeedImportService service = new LearningPlanTemplateSeedImportService(
        new StaticObjectProvider<>(templateRepository),
        new StaticObjectProvider<>(new InMemoryProblemRepository(Set.of("two-sum"))),
        new LearningPlanTemplateSeedReader(new ObjectMapper()),
        new ObjectMapper());

    service.importSeed(tempDir);
    service.importSeed(tempDir);

    assertThat(templateRepository.templates).hasSize(1);
    LearningPlanTemplate saved = templateRepository.templates.get(0);
    assertThat(saved.templateId()).isEqualTo("neetcode_blind_75_interview_core");
    assertThat(saved.phases()).hasSize(1);
    assertThat(saved.phases().get(0).problemRefs()).hasSize(2);
    assertThat(templateRepository.importRuns).hasSize(2);
    assertThat(templateRepository.importRuns)
        .extracting(LearningPlanTemplateImportRun::checksum)
        .containsOnly(templateRepository.importRuns.get(0).checksum());
  }

  @Test
  void importSeedRequiresMetadataMarkdown() throws Exception {
    writeValidSeed(tempDir);
    Files.delete(tempDir.resolve(LearningPlanTemplateSeedConstants.METADATA_FILE));
    LearningPlanTemplateSeedImportService service = new LearningPlanTemplateSeedImportService(
        new StaticObjectProvider<>(new InMemoryTemplateRepository()),
        new StaticObjectProvider<>(new InMemoryProblemRepository(Set.of())),
        new LearningPlanTemplateSeedReader(new ObjectMapper()),
        new ObjectMapper());

    assertThatThrownBy(() -> service.importSeed(tempDir))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(LearningPlanTemplateSeedConstants.METADATA_FILE);
  }

  @Test
  void importSeedRequiresPhaseDurationSumToMatchTemplateDuration() throws Exception {
    writeValidSeed(tempDir);
    Files.writeString(tempDir.resolve(LearningPlanTemplateSeedConstants.TEMPLATES_FILE), """
        {"templateId":"neetcode_blind_75_interview_core","title":"Blind 75","summary":"summary","intent":"INTERVIEW_SPRINT","goal":"goal","defaultDurationWeeks":4,"level":"INTERMEDIATE","defaultWeeklyHours":8,"programmingLanguage":"Java","difficultyPreference":"MEDIUM","interviewOriented":true,"topicPreferences":["Array"],"targetAudience":"audience","difficultyMix":{"Easy":{"count":1}},"prerequisites":["basic"],"recommendedFor":["interview"],"notRecommendedFor":["zero"],"expectedOutcome":"outcome","sourceName":"neetcode-gh/leetcode","sourceUrl":"https://github.com/neetcode-gh/leetcode","sourceCommit":"9907b7fed441fa55083c0751e208b7197101dbba","sourceDataPath":".problemSiteData.json","sourceDescription":"source","curationNotes":"notes","licenseNotice":"MIT metadata only","metadata":{},"phases":[{"phaseIndex":1,"title":"phase","durationWeeks":3,"focus":"focus","objectives":["objective"],"recommendedTags":["Array"],"acceptanceCriteria":["done"],"reviewAdvice":"review"}]}
        """);
    LearningPlanTemplateSeedImportService service = new LearningPlanTemplateSeedImportService(
        new StaticObjectProvider<>(new InMemoryTemplateRepository()),
        new StaticObjectProvider<>(new InMemoryProblemRepository(Set.of())),
        new LearningPlanTemplateSeedReader(new ObjectMapper()),
        new ObjectMapper());

    assertThatThrownBy(() -> service.importSeed(tempDir))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("phase duration sum");
  }

  @Test
  void importSeedRequiresProblemRefsToPointToExistingPhase() throws Exception {
    writeValidSeed(tempDir);
    Files.writeString(tempDir.resolve(LearningPlanTemplateSeedConstants.PROBLEM_REFS_FILE), """
        {"templateId":"neetcode_blind_75_interview_core","phaseIndex":2,"sortOrder":1,"sourceOrder":1,"problemSlug":"two-sum","sourceTitle":"Two Sum","sourceDifficulty":"Easy","pattern":"Arrays & Hashing","sourceUrl":"https://neetcode.io/problems/two-sum","metadata":{}}
        """);
    LearningPlanTemplateSeedImportService service = new LearningPlanTemplateSeedImportService(
        new StaticObjectProvider<>(new InMemoryTemplateRepository()),
        new StaticObjectProvider<>(new InMemoryProblemRepository(Set.of())),
        new LearningPlanTemplateSeedReader(new ObjectMapper()),
        new ObjectMapper());

    assertThatThrownBy(() -> service.importSeed(tempDir))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown template phase");
  }

  private void writeValidSeed(Path dir) throws Exception {
    Files.writeString(dir.resolve(LearningPlanTemplateSeedConstants.TEMPLATES_FILE), """
        {"templateId":"neetcode_blind_75_interview_core","title":"Blind 75","summary":"summary","intent":"INTERVIEW_SPRINT","goal":"goal","defaultDurationWeeks":4,"level":"INTERMEDIATE","defaultWeeklyHours":8,"programmingLanguage":"Java","difficultyPreference":"MEDIUM","interviewOriented":true,"topicPreferences":["Array"],"targetAudience":"audience","difficultyMix":{"Easy":{"count":1}},"prerequisites":["basic"],"recommendedFor":["interview"],"notRecommendedFor":["zero"],"expectedOutcome":"outcome","sourceName":"neetcode-gh/leetcode","sourceUrl":"https://github.com/neetcode-gh/leetcode","sourceCommit":"9907b7fed441fa55083c0751e208b7197101dbba","sourceDataPath":".problemSiteData.json","sourceDescription":"source","curationNotes":"notes","licenseNotice":"MIT metadata only","metadata":{},"phases":[{"phaseIndex":1,"title":"phase","durationWeeks":4,"focus":"focus","objectives":["objective"],"recommendedTags":["Array"],"acceptanceCriteria":["done"],"reviewAdvice":"review"}]}
        """);
    Files.writeString(dir.resolve(LearningPlanTemplateSeedConstants.PROBLEM_REFS_FILE), """
        {"templateId":"neetcode_blind_75_interview_core","phaseIndex":1,"sortOrder":1,"sourceOrder":1,"problemSlug":"two-sum","sourceTitle":"Two Sum","sourceDifficulty":"Easy","pattern":"Arrays & Hashing","sourceUrl":"https://neetcode.io/problems/two-sum","metadata":{}}
        {"templateId":"neetcode_blind_75_interview_core","phaseIndex":1,"sortOrder":2,"sourceOrder":2,"problemSlug":"missing-problem","sourceTitle":"Missing","sourceDifficulty":"Medium","pattern":"Graphs","sourceUrl":"https://neetcode.io/problems/missing","metadata":{}}
        """);
    Files.writeString(dir.resolve(LearningPlanTemplateSeedConstants.MANIFEST_FILE), """
        {"source":{"commit":"9907b7fed441fa55083c0751e208b7197101dbba"}}
        """);
    Files.writeString(dir.resolve(LearningPlanTemplateSeedConstants.METADATA_FILE), "# metadata\n");
  }

  private Path repoRoot() {
    Path current = Path.of("").toAbsolutePath();
    while (current != null) {
      if (Files.isRegularFile(current.resolve("data/learning-plan-template-seed/"
          + LearningPlanTemplateSeedConstants.TEMPLATES_FILE))) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("Cannot locate repository root from test working directory.");
  }

  private Set<String> localProblemSlugs(Path repoRoot, ObjectMapper objectMapper) throws Exception {
    Set<String> slugs = new HashSet<>();
    for (String line : Files.readAllLines(repoRoot.resolve("data/seed/problems.jsonl"))) {
      if (line.isBlank()) {
        continue;
      }
      JsonNode node = objectMapper.readTree(line);
      String slug = node.path("slug").asText("");
      if (!slug.isBlank()) {
        slugs.add(slug);
      }
    }
    return slugs;
  }

  private static class InMemoryTemplateRepository implements LearningPlanTemplateRepository {

    private final List<LearningPlanTemplate> templates = new ArrayList<>();
    private final List<LearningPlanTemplateImportRun> importRuns = new ArrayList<>();

    @Override
    public List<LearningPlanTemplate> findAllTemplates() {
      return templates;
    }

    @Override
    public Optional<LearningPlanTemplate> findByTemplateId(String templateId) {
      return templates.stream().filter(template -> template.templateId().equals(templateId)).findFirst();
    }

    @Override
    public LearningPlanTemplate saveTemplate(LearningPlanTemplate template) {
      templates.removeIf(existing -> existing.templateId().equals(template.templateId()));
      templates.add(template);
      return template;
    }

    @Override
    public void insertImportRun(LearningPlanTemplateImportRun importRun) {
      importRuns.add(importRun);
    }
  }

  private static class InMemoryProblemRepository implements ProblemRepository {

    private final Set<String> knownSlugs;

    InMemoryProblemRepository(Set<String> knownSlugs) {
      this.knownSlugs = knownSlugs;
    }

    @Override
    public ProblemPage<ProblemListItem> findProblems(ProblemListRequest request) {
      return new ProblemPage<>(List.of(), 0, request.page(), request.pageSize());
    }

    @Override
    public Optional<ProblemDetail> findProblemBySlug(String slug) {
      if (!knownSlugs.contains(slug)) {
        return Optional.empty();
      }
      return Optional.of(new ProblemDetail(slug, 1, "1", slug, null, List.of(), null, null, null, null, null, null, null));
    }

    @Override
    public ProblemFilters findProblemFilters() {
      return new ProblemFilters(0, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    @Override
    public void upsertProblem(ProblemSeedRecord problem) {
    }
  }

  record StaticObjectProvider<T>(T value) implements ObjectProvider<T> {
    @Override
    public T getObject(Object... args) {
      return value;
    }

    @Override
    public T getIfAvailable() {
      return value;
    }

    @Override
    public T getIfUnique() {
      return value;
    }

    @Override
    public T getObject() {
      return value;
    }
  }
}
