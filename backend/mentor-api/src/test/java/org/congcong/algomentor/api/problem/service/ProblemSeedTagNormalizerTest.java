package org.congcong.algomentor.api.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.congcong.algomentor.api.problem.model.ProblemSeedRecord;
import org.congcong.algomentor.api.problem.model.ProblemTagDefinition;
import org.junit.jupiter.api.Test;

class ProblemSeedTagNormalizerTest {

  private final ProblemSeedTagNormalizer normalizer = new ProblemSeedTagNormalizer();

  @Test
  void normalizesTagsWithFallbackAndZeroBasedOrdinals() {
    var result = normalizer.normalize(List.of(problem(
        "two-sum",
        List.of(" array ", "hash-table"),
        List.of(" Array ", ""),
        List.of(" 数组 ", ""))));

    assertThat(result.catalog()).containsExactly(
        new ProblemTagDefinition("array", "Array", "数组"),
        new ProblemTagDefinition("hash-table", "hash-table", "hash-table"));
    assertThat(result.problems().get(0).tags())
        .extracting(tag -> List.of(tag.value(), tag.labelEn(), tag.labelZh(), tag.ordinal()))
        .containsExactly(
            List.of("array", "Array", "数组", 0),
            List.of("hash-table", "hash-table", "hash-table", 1));
  }

  @Test
  void rejectsBlankValuesAndMismatchedTagArrays() {
    assertThatThrownBy(() -> normalizer.normalize(List.of(problem(
        "broken-values", List.of(" "), List.of("Array"), List.of("数组")))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("slug=broken-values");
    assertThatThrownBy(() -> normalizer.normalize(List.of(problem(
        "broken-lengths", List.of("array"), List.of(), List.of("数组")))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("equal lengths: broken-lengths");
  }

  @Test
  void removesExactDuplicatesAndRejectsConflictingDuplicates() {
    var deduplicated = normalizer.normalize(List.of(problem(
        "deduplicated",
        List.of("array", "array", "hash-table"),
        List.of("Array", "Array", "Hash Table"),
        List.of("数组", "数组", "哈希表"))));

    assertThat(deduplicated.problems().get(0).tags())
        .extracting(tag -> List.of(tag.value(), tag.ordinal()))
        .containsExactly(List.of("array", 0), List.of("hash-table", 1));
    assertThatThrownBy(() -> normalizer.normalize(List.of(problem(
        "conflicting",
        List.of("graph", "graph"),
        List.of("Graph", "Graph Theory"),
        List.of("图", "图")))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("slug=conflicting")
        .hasMessageContaining("value=graph");
  }

  @Test
  void selectsCatalogNamesByFrequencyThenStableStringOrderIndependentOfProblemOrder() {
    ProblemSeedRecord graphTheory = problem(
        "graph-theory", List.of("graph"), List.of("Graph Theory"), List.of("Graph Theory"));
    ProblemSeedRecord graph = problem(
        "graph", List.of("graph"), List.of("Graph"), List.of("图"));
    ProblemSeedRecord graphAgain = problem(
        "graph-again", List.of("graph"), List.of("Graph"), List.of("图"));
    ProblemSeedRecord unionFind = problem(
        "union-find", List.of("union-find"), List.of("Union Find"), List.of("并查集"));
    ProblemSeedRecord unionHyphen = problem(
        "union-hyphen", List.of("union-find"), List.of("Union-Find"), List.of("并查集"));

    var forward = normalizer.normalize(List.of(graphTheory, graph, graphAgain, unionFind, unionHyphen));
    var reverse = normalizer.normalize(List.of(unionHyphen, unionFind, graphAgain, graph, graphTheory));

    assertThat(forward.catalog()).containsExactly(
        new ProblemTagDefinition("graph", "Graph", "图"),
        new ProblemTagDefinition("union-find", "Union Find", "并查集"));
    assertThat(reverse.catalog()).isEqualTo(forward.catalog());
    assertThat(reverse.problems())
        .allSatisfy(problem -> assertThat(problem.tags()).allSatisfy(tag -> {
          if (tag.value().equals("graph")) {
            assertThat(tag.labelEn()).isEqualTo("Graph");
            assertThat(tag.labelZh()).isEqualTo("图");
          }
        }));
  }

  @Test
  void prefersARealChineseTranslationOverFallbackText() {
    var result = normalizer.normalize(List.of(
        problem("english-fallback", List.of("array"), List.of("Array"), List.of("Array")),
        problem("translation", List.of("array"), List.of("Array"), List.of("数组"))));

    assertThat(result.catalog()).containsExactly(new ProblemTagDefinition("array", "Array", "数组"));
  }

  @Test
  void normalizesTheCompleteRepositorySeed() throws Exception {
    Path problemsFile = repoRoot().resolve("data/seed/problems.jsonl");
    ObjectMapper objectMapper = new ObjectMapper();
    List<ProblemSeedRecord> problems;
    try (BufferedReader reader = Files.newBufferedReader(problemsFile)) {
      problems = reader.lines()
          .filter(line -> !line.isBlank())
          .map(line -> readProblem(objectMapper, line))
          .toList();
    }

    var result = normalizer.normalize(problems);

    assertThat(result.problems()).hasSize(3591);
    assertThat(result.catalog()).hasSize(72);
  }

  private ProblemSeedRecord readProblem(ObjectMapper objectMapper, String line) {
    try {
      return objectMapper.readValue(line, ProblemSeedRecord.class);
    } catch (Exception exception) {
      throw new IllegalStateException("Could not read problem seed test fixture", exception);
    }
  }

  private ProblemSeedRecord problem(
      String slug,
      List<String> values,
      List<String> labelsEn,
      List<String> labelsZh
  ) {
    return new ProblemSeedRecord(
        slug,
        null,
        null,
        null,
        "中文标题",
        null,
        values,
        labelsEn,
        labelsZh,
        null,
        "中文题面",
        "CN_ONLY",
        "LEETCODE_CN",
        null,
        null,
        null,
        null,
        null,
        null);
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
}
