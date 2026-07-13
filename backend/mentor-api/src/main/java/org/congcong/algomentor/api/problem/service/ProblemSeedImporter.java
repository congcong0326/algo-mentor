package org.congcong.algomentor.api.problem.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.congcong.algomentor.api.problem.model.ProblemInsightSeedRecord;
import org.congcong.algomentor.api.problem.model.ProblemSeedRecord;
import org.congcong.algomentor.api.problem.repository.ProblemRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProblemSeedImporter {

  public static final String PROBLEMS_FILE = "problems.jsonl";
  public static final String PROBLEM_REASONS_FILE = "problem_reasons.json";

  private final ObjectProvider<ProblemRepository> repositoryProvider;
  private final ObjectMapper objectMapper;

  public ProblemSeedImporter(ObjectProvider<ProblemRepository> repositoryProvider, ObjectMapper objectMapper) {
    this.repositoryProvider = repositoryProvider;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public int importSeed(Path seedDirectory, Path insightSeedDirectory) throws IOException {
    Path problemsFile = seedDirectory.resolve(PROBLEMS_FILE);
    Map<String, ProblemInsightSeedRecord> reasonsBySlug = readReasons(
        insightSeedDirectory.resolve(PROBLEM_REASONS_FILE));
    List<ProblemSeedRecord> problems = readProblems(problemsFile);
    validateMatchingSlugs(problems, reasonsBySlug);
    ProblemRepository repository = repository();

    for (ProblemSeedRecord problem : problems) {
      ProblemInsightSeedRecord reason = reasonsBySlug.get(problem.slug());
      repository.upsertProblem(problem.withRecommendationReasons(reason.reasonEn(), reason.reasonZh()));
    }

    return problems.size();
  }

  private List<ProblemSeedRecord> readProblems(Path problemsFile) throws IOException {
    List<ProblemSeedRecord> problems = new ArrayList<>();

    try (BufferedReader reader = Files.newBufferedReader(problemsFile)) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        problems.add(objectMapper.readValue(line, ProblemSeedRecord.class));
      }
    }

    return List.copyOf(problems);
  }

  private Map<String, ProblemInsightSeedRecord> readReasons(Path reasonsFile) throws IOException {
    ProblemInsightSeedRecord[] records = objectMapper.readValue(
        reasonsFile.toFile(), ProblemInsightSeedRecord[].class);
    Map<String, ProblemInsightSeedRecord> reasonsBySlug = new LinkedHashMap<>();
    for (ProblemInsightSeedRecord record : records) {
      validateReason(record);
      if (reasonsBySlug.putIfAbsent(record.slug(), record) != null) {
        throw new IllegalArgumentException("Duplicate problem recommendation reason slug: " + record.slug());
      }
    }
    return Map.copyOf(reasonsBySlug);
  }

  private void validateReason(ProblemInsightSeedRecord record) {
    if (record.slug() == null || record.slug().isBlank()) {
      throw new IllegalArgumentException("Problem recommendation reason slug must not be blank");
    }
    if (record.reasonEn() == null || record.reasonEn().isBlank()) {
      throw new IllegalArgumentException("English recommendation reason must not be blank: " + record.slug());
    }
    if (record.reasonZh() == null || record.reasonZh().isBlank()) {
      throw new IllegalArgumentException("Chinese recommendation reason must not be blank: " + record.slug());
    }
  }

  private void validateMatchingSlugs(
      List<ProblemSeedRecord> problems,
      Map<String, ProblemInsightSeedRecord> reasonsBySlug
  ) {
    Set<String> problemSlugs = new HashSet<>();
    for (ProblemSeedRecord problem : problems) {
      problemSlugs.add(problem.slug());
      if (!reasonsBySlug.containsKey(problem.slug())) {
        throw new IllegalArgumentException("Missing problem recommendation reason: " + problem.slug());
      }
    }
    Set<String> unmatchedReasonSlugs = new HashSet<>(reasonsBySlug.keySet());
    unmatchedReasonSlugs.removeAll(problemSlugs);
    if (!unmatchedReasonSlugs.isEmpty()) {
      throw new IllegalArgumentException(
          "Problem recommendation reasons do not match problem seed: " + unmatchedReasonSlugs);
    }
  }

  private ProblemRepository repository() {
    ProblemRepository repository = repositoryProvider.getIfAvailable();
    if (repository == null) {
      throw new ProblemService.ProblemRepositoryUnavailableException();
    }
    return repository;
  }
}
