package org.congcong.algomentor.api.problem.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.congcong.algomentor.api.problem.model.ProblemCompanySeedRecord;
import org.congcong.algomentor.api.problem.repository.ProblemCompanyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

class ProblemCompanySeedImportServiceTest {

  @TempDir
  private Path tempDir;

  @Test
  void importSeedUpsertsMatchedSignalsAndWritesAuditRun() throws Exception {
    Path companySeed = tempDir.resolve("company-seed");
    Path problemSeed = tempDir.resolve("problem-seed");
    Files.createDirectories(companySeed);
    Files.createDirectories(problemSeed);
    Files.writeString(companySeed.resolve("problem_company_signals.jsonl"), """
        {"companySlug":"tencent","companyName":"腾讯","companyMarket":"CHINA","role":"BACKEND","problemSlug":"two-sum","recencyBucket":"ALL_TIME","frequencyScore":10,"rank":1,"acceptanceRate":0.5,"sourceName":"src","sourceUrl":"url","sourceCommit":"abc","sourceProblemUrl":"problem-url"}
        {"companySlug":"tencent","companyName":"腾讯","companyMarket":"CHINA","role":"BACKEND","problemSlug":"missing","recencyBucket":"ALL_TIME","frequencyScore":1,"rank":2,"sourceName":"src","sourceUrl":"url","sourceProblemUrl":"problem-url"}
        """);
    Files.writeString(companySeed.resolve("problem_company_seed_manifest.json"), "{\"signalCount\":2}");
    Files.writeString(problemSeed.resolve("manifest.json"), "{\"problemCount\":1}");
    InMemoryProblemCompanyRepository repository = new InMemoryProblemCompanyRepository(Set.of("two-sum"));
    ObjectMapper objectMapper = new ObjectMapper();
    ProblemCompanySeedImportService service = new ProblemCompanySeedImportService(
        new StaticObjectProvider<>(repository),
        new ProblemCompanySeedReader(objectMapper),
        objectMapper);

    ProblemCompanySeedImportResult result = service.importSeed(companySeed, problemSeed);

    assertThat(result.readSignalCount()).isEqualTo(2);
    assertThat(result.matchedSignalCount()).isEqualTo(1);
    assertThat(result.skippedSignalCount()).isEqualTo(1);
    assertThat(repository.upserted).containsExactly("two-sum");
    assertThat(repository.auditMatched).isEqualTo(1);
    assertThat(repository.auditSkipped).isEqualTo(1);
    assertThat(repository.auditMetadataJson).contains("seedChecksum");
  }

  static class InMemoryProblemCompanyRepository implements ProblemCompanyRepository {
    private final Set<String> knownProblems;
    private final Set<String> upserted = new HashSet<>();
    private int auditMatched;
    private int auditSkipped;
    private String auditMetadataJson;

    InMemoryProblemCompanyRepository(Set<String> knownProblems) {
      this.knownProblems = knownProblems;
    }

    @Override
    public boolean upsertSignal(ProblemCompanySeedRecord record) {
      if (!knownProblems.contains(record.problemSlug())) {
        return false;
      }
      upserted.add(record.problemSlug());
      return true;
    }

    @Override
    public void insertImportRun(
        String sourceName,
        String sourceCommit,
        String manifestPath,
        int matchedSignalCount,
        int skippedSignalCount,
        int errorCount,
        String metadataJson
    ) {
      auditMatched = matchedSignalCount;
      auditSkipped = skippedSignalCount;
      auditMetadataJson = metadataJson;
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
