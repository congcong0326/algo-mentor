package org.congcong.algomentor.api.problem.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.api.problem.model.ProblemDetail;
import org.congcong.algomentor.api.problem.model.ProblemListItem;
import org.congcong.algomentor.api.problem.model.ProblemListRequest;
import org.congcong.algomentor.api.problem.model.ProblemPage;
import org.congcong.algomentor.api.problem.model.ProblemSeedRecord;
import org.congcong.algomentor.api.problem.repository.ProblemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

class ProblemSeedImporterTest {

  @TempDir
  private Path tempDir;

  @Test
  void importSeedUpsertsJsonlRows() throws Exception {
    Files.writeString(tempDir.resolve("problems.jsonl"), """
        {"slug":"two-sum","frontendId":1,"title":"Two Sum","difficulty":"EASY","tags":["Array"],"contentMarkdown":"old"}
        {"slug":"two-sum","frontendId":1,"title":"Two Sum Updated","difficulty":"EASY","tags":["Array","Hash Table"],"contentMarkdown":"new"}
        """);
    InMemoryProblemRepository repository = new InMemoryProblemRepository();
    ProblemSeedImporter importer = new ProblemSeedImporter(new StaticObjectProvider<>(repository), new ObjectMapper());

    int imported = importer.importSeed(tempDir);

    assertThat(imported).isEqualTo(2);
    assertThat(repository.upsertCalls).isEqualTo(2);
    assertThat(repository.records).hasSize(1);
    assertThat(repository.records.get("two-sum").title()).isEqualTo("Two Sum Updated");
    assertThat(repository.records.get("two-sum").tags()).containsExactly("Array", "Hash Table");
  }

  static class InMemoryProblemRepository implements ProblemRepository {
    private final Map<String, ProblemSeedRecord> records = new LinkedHashMap<>();
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
    public void upsertProblem(ProblemSeedRecord problem) {
      upsertCalls += 1;
      records.put(problem.slug(), problem);
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
