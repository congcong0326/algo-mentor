package org.congcong.algomentor.api.problem.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.congcong.algomentor.api.problem.model.ProblemLearningMetadataImportResult;
import org.congcong.algomentor.api.problem.model.ProblemLearningMetadataContract;
import org.congcong.algomentor.api.problem.model.ProblemMetadataSourceProblemRecord;
import org.congcong.algomentor.api.problem.repository.ProblemLearningMetadataRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 将已审核的学习元数据 seed 以来源隔离的精确刷新语义写入 PostgreSQL。 */
@Service
public class ProblemLearningMetadataSeedImportService {

  private final ObjectProvider<ProblemLearningMetadataRepository> repositoryProvider;
  private final ProblemLearningMetadataSeedReader reader;
  private final ProblemLearningMetadataSeedValidator validator;
  private final ObjectMapper objectMapper;

  public ProblemLearningMetadataSeedImportService(
      ObjectProvider<ProblemLearningMetadataRepository> repositoryProvider,
      ProblemLearningMetadataSeedReader reader,
      ProblemLearningMetadataSeedValidator validator,
      ObjectMapper objectMapper
  ) {
    this.repositoryProvider = repositoryProvider;
    this.reader = reader;
    this.validator = validator;
    this.objectMapper = objectMapper;
  }

  @Transactional(rollbackFor = Exception.class)
  public ProblemLearningMetadataImportResult importSeed(Path seedDirectory) throws IOException {
    ProblemLearningMetadataSeed seed = reader.read(seedDirectory);
    validator.validate(seed);
    ProblemLearningMetadataRepository repository = repository();
    List<String> sourceSlugs = seed.sourceProblems().stream()
        .map(ProblemMetadataSourceProblemRecord::problemSlug)
        .sorted()
        .toList();
    Set<String> existing = repository.findExistingProblemSlugs(sourceSlugs);
    List<String> matchedSlugs = sourceSlugs.stream().filter(existing::contains).toList();
    Map<String, List<String>> sourceSites = sourceSites(seed.sourceProblems(), existing);

    repository.replaceLeetCodeRelations(matchedSlugs, seed.relations().stream()
        .filter(record -> existing.contains(record.sourceSlug())).toList());
    repository.replaceHints(matchedSlugs, seed.hints().stream()
        .filter(record -> existing.contains(record.problemSlug())).toList(), sourceSites);
    repository.replaceCodeTemplates(matchedSlugs, seed.codeTemplates().stream()
        .filter(record -> existing.contains(record.problemSlug())).toList());
    repository.replaceLeetCodeCategoryItems(matchedSlugs, seed.categories(), seed.categoryItems().stream()
        .filter(record -> existing.contains(record.problemSlug())).toList());

    int readCount = seed.relations().size() + seed.hints().size() + seed.codeTemplates().size()
        + seed.categories().size() + seed.categoryItems().size();
    String sourceSnapshot = seed.manifest()
        .path(ProblemLearningMetadataContract.JSON_FIELD_SOURCE_SNAPSHOT).asText();
    repository.insertImportRun(
        seed.manifestPath().toString(),
        checksum(seed.manifestPath()),
        sourceSnapshot,
        readCount,
        matchedSlugs.size(),
        sourceSlugs.size() - matchedSlugs.size(),
        json(seed.auditReport()));
    return new ProblemLearningMetadataImportResult(
        readCount, matchedSlugs.size(), sourceSlugs.size() - matchedSlugs.size(), sourceSnapshot);
  }

  private Map<String, List<String>> sourceSites(
      List<ProblemMetadataSourceProblemRecord> records,
      Set<String> existing
  ) {
    Map<String, List<String>> byProblem = new LinkedHashMap<>();
    for (ProblemMetadataSourceProblemRecord record : records) {
      if (existing.contains(record.problemSlug())) {
        byProblem.put(record.problemSlug(), List.copyOf(record.sourceSites()));
      }
    }
    return Map.copyOf(byProblem);
  }

  private ProblemLearningMetadataRepository repository() {
    ProblemLearningMetadataRepository repository = repositoryProvider.getIfAvailable();
    if (repository == null) {
      throw new ProblemService.ProblemRepositoryUnavailableException();
    }
    return repository;
  }

  private String checksum(Path path) throws IOException {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(path)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable.", exception);
    }
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Unable to serialize metadata audit report.", exception);
    }
  }
}
