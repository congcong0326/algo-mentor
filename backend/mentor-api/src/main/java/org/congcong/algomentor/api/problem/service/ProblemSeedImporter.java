package org.congcong.algomentor.api.problem.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.congcong.algomentor.api.problem.model.ProblemSeedRecord;
import org.congcong.algomentor.api.problem.repository.ProblemRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProblemSeedImporter {

  public static final String PROBLEMS_FILE = "problems.jsonl";

  private final ObjectProvider<ProblemRepository> repositoryProvider;
  private final ObjectMapper objectMapper;

  public ProblemSeedImporter(ObjectProvider<ProblemRepository> repositoryProvider, ObjectMapper objectMapper) {
    this.repositoryProvider = repositoryProvider;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public int importSeed(Path seedDirectory) throws IOException {
    Path problemsFile = seedDirectory.resolve(PROBLEMS_FILE);
    ProblemRepository repository = repository();
    int imported = 0;

    try (BufferedReader reader = Files.newBufferedReader(problemsFile)) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        ProblemSeedRecord problem = objectMapper.readValue(line, ProblemSeedRecord.class);
        repository.upsertProblem(problem);
        imported += 1;
      }
    }

    return imported;
  }

  private ProblemRepository repository() {
    ProblemRepository repository = repositoryProvider.getIfAvailable();
    if (repository == null) {
      throw new ProblemService.ProblemRepositoryUnavailableException();
    }
    return repository;
  }
}
