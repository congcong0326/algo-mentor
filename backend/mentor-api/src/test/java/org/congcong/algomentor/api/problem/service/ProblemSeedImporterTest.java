package org.congcong.algomentor.api.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.api.problem.model.NormalizedProblemSeed;
import org.congcong.algomentor.api.problem.model.ProblemDetail;
import org.congcong.algomentor.api.problem.model.ProblemFilters;
import org.congcong.algomentor.api.problem.model.ProblemListItem;
import org.congcong.algomentor.api.problem.model.ProblemListRequest;
import org.congcong.algomentor.api.problem.model.ProblemPage;
import org.congcong.algomentor.api.problem.model.ProblemSeedRecord;
import org.congcong.algomentor.api.problem.repository.ProblemRepository;
import org.congcong.algomentor.api.problem.repository.ProblemTagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

class ProblemSeedImporterTest {

  @TempDir
  private Path tempDir;

  @Test
  void importSeedMergesRecommendationReasonsAndCanBeRepeated() throws Exception {
    Path problemSeedDirectory = tempDir.resolve("problem-seed");
    Path insightSeedDirectory = tempDir.resolve("insight-seed");
    Files.createDirectories(problemSeedDirectory);
    Files.createDirectories(insightSeedDirectory);
    Files.writeString(problemSeedDirectory.resolve("problems.jsonl"), """
        {"slug":"two-sum","frontendId":1,"titleEn":"Two Sum","titleZh":"两数之和","difficulty":"EASY","tagValues":["array"],"tagLabelsEn":["Array"],"tagLabelsZh":["数组"],"contentMarkdownEn":"old","contentMarkdownZh":"旧"}
        {"slug":"two-sum","frontendId":1,"titleEn":"Two Sum Updated","titleZh":"两数之和更新","difficulty":"EASY","tagValues":["array","hash-table"],"tagLabelsEn":["Array","Hash Table"],"tagLabelsZh":["数组","哈希表"],"contentMarkdownEn":"new","contentMarkdownZh":"新"}
        """);
    Files.writeString(insightSeedDirectory.resolve("problem_reasons.json"), """
        [{"slug":"two-sum","reasonEN":"Practice hash lookup.","reasonZH":"练习哈希查找。"}]
        """);
    InMemoryProblemRepository repository = new InMemoryProblemRepository();
    InMemoryProblemTagRepository tagRepository = new InMemoryProblemTagRepository();
    ProblemSeedImporter importer = importer(repository, tagRepository);

    int firstImported = importer.importSeed(problemSeedDirectory, insightSeedDirectory);
    int secondImported = importer.importSeed(problemSeedDirectory, insightSeedDirectory);

    assertThat(firstImported).isEqualTo(2);
    assertThat(secondImported).isEqualTo(2);
    assertThat(repository.upsertCalls).isEqualTo(4);
    assertThat(repository.records).hasSize(1);
    assertThat(repository.records.get("two-sum").problem().titleEn()).isEqualTo("Two Sum Updated");
    assertThat(repository.records.get("two-sum").tags())
        .extracting(tag -> tag.value())
        .containsExactly("array", "hash-table");
    assertThat(repository.records.get("two-sum").problem().recommendationReasonEn())
        .isEqualTo("Practice hash lookup.");
    assertThat(repository.records.get("two-sum").problem().recommendationReasonZh())
        .isEqualTo("练习哈希查找。");
    assertThat(tagRepository.catalogByValue).containsOnlyKeys("array", "hash-table");
  }

  @Test
  void importSeedRejectsMissingRecommendationReasonBeforeWriting() throws Exception {
    Path problemSeedDirectory = tempDir.resolve("problem-seed");
    Path insightSeedDirectory = tempDir.resolve("insight-seed");
    Files.createDirectories(problemSeedDirectory);
    Files.createDirectories(insightSeedDirectory);
    Files.writeString(problemSeedDirectory.resolve("problems.jsonl"), """
        {"slug":"two-sum","frontendId":1,"titleEn":"Two Sum","titleZh":"两数之和","difficulty":"EASY"}
        """);
    Files.writeString(insightSeedDirectory.resolve("problem_reasons.json"), "[]");
    InMemoryProblemRepository repository = new InMemoryProblemRepository();
    ProblemSeedImporter importer = importer(repository, new InMemoryProblemTagRepository());

    assertThatThrownBy(() -> importer.importSeed(problemSeedDirectory, insightSeedDirectory))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing problem recommendation reason: two-sum");
    assertThat(repository.upsertCalls).isZero();
  }

  @Test
  void repositoryProblemAndRecommendationSeedsMatchExactly() throws Exception {
    Path repoRoot = repoRoot();
    InMemoryProblemRepository repository = new InMemoryProblemRepository();
    ProblemSeedImporter importer = importer(repository, new InMemoryProblemTagRepository());

    int imported = importer.importSeed(
        repoRoot.resolve("data/seed"),
        repoRoot.resolve("data/problem-insight-seed"));

    assertThat(imported).isEqualTo(3591);
    assertThat(repository.records).hasSize(3591);
    assertThat(repository.records.values())
        .allSatisfy(normalizedProblem -> {
          assertThat(normalizedProblem.problem().recommendationReasonEn()).isNotBlank();
          assertThat(normalizedProblem.problem().recommendationReasonZh()).isNotBlank();
        });
  }

  private ProblemSeedImporter importer(
      InMemoryProblemRepository repository,
      InMemoryProblemTagRepository tagRepository
  ) {
    ProblemTagConsistencyValidator validator = org.mockito.Mockito.mock(ProblemTagConsistencyValidator.class);
    return new ProblemSeedImporter(
        new StaticObjectProvider<>(repository),
        new StaticObjectProvider<>(tagRepository),
        new ProblemSeedTagNormalizer(),
        validator,
        new ObjectMapper());
  }

  private Path repoRoot() {
    Path current = Path.of("").toAbsolutePath();
    while (current != null) {
      if (Files.isRegularFile(current.resolve("data/seed/problems.jsonl"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("Cannot locate repository root from test working directory.");
  }

  static class InMemoryProblemRepository implements ProblemRepository {
    private final Map<String, NormalizedProblemSeed> records = new LinkedHashMap<>();
    private int upsertCalls;

    @Override
    public ProblemPage<ProblemListItem> findProblems(ProblemListRequest request) {
      return new ProblemPage<>(java.util.List.of(), 0, request.page(), request.pageSize());
    }

    @Override
    public Optional<ProblemDetail> findProblemBySlug(String slug) {
      return Optional.empty();
    }

    @Override
    public ProblemFilters findProblemFilters() {
      return new ProblemFilters(
          0,
          java.util.List.of(),
          java.util.List.of(),
          java.util.List.of(),
          java.util.List.of(),
          java.util.List.of(),
          java.util.List.of());
    }

    @Override
    public void upsertProblem(NormalizedProblemSeed problem) {
      upsertCalls += 1;
      records.put(problem.problem().slug(), problem);
    }
  }

  static class InMemoryProblemTagRepository implements ProblemTagRepository {
    private final Map<String, org.congcong.algomentor.api.problem.model.ProblemTagDefinition> catalogByValue =
        new LinkedHashMap<>();
    private final Map<String, List<org.congcong.algomentor.api.problem.model.ProblemSeedTag>> assignmentsBySlug =
        new LinkedHashMap<>();

    @Override
    public void upsertCatalog(List<org.congcong.algomentor.api.problem.model.ProblemTagDefinition> catalog) {
      for (var tag : catalog) {
        catalogByValue.put(tag.value(), tag);
      }
    }

    @Override
    public void replaceAssignments(
        String problemSlug,
        List<org.congcong.algomentor.api.problem.model.ProblemSeedTag> tags
    ) {
      assignmentsBySlug.put(problemSlug, List.copyOf(tags));
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
