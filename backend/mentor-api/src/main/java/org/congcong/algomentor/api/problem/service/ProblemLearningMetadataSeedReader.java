package org.congcong.algomentor.api.problem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.congcong.algomentor.api.problem.model.ProblemCategoryItemSeedRecord;
import org.congcong.algomentor.api.problem.model.ProblemCategorySeedRecord;
import org.congcong.algomentor.api.problem.model.ProblemCodeTemplateSeedRecord;
import org.congcong.algomentor.api.problem.model.ProblemHintSeedRecord;
import org.congcong.algomentor.api.problem.model.ProblemLearningMetadataContract;
import org.congcong.algomentor.api.problem.model.ProblemMetadataSourceProblemRecord;
import org.congcong.algomentor.api.problem.model.ProblemRelationSeedRecord;
import org.springframework.stereotype.Component;

/** 只负责读取 seed，不在此层接受未审核数据或执行数据库写入。 */
@Component
public class ProblemLearningMetadataSeedReader {

  private final ObjectMapper objectMapper;

  public ProblemLearningMetadataSeedReader(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public ProblemLearningMetadataSeed read(Path seedDirectory) throws IOException {
    Path manifestPath = seedDirectory.resolve(ProblemLearningMetadataContract.MANIFEST_FILE);
    Path auditReportPath = seedDirectory.resolve(ProblemLearningMetadataContract.AUDIT_REPORT_FILE);
    JsonNode manifest = objectMapper.readTree(manifestPath.toFile());
    JsonNode auditReport = objectMapper.readTree(auditReportPath.toFile());
    return new ProblemLearningMetadataSeed(
        seedDirectory,
        manifestPath,
        auditReportPath,
        manifest,
        auditReport,
        readManifestSourceProblems(manifest),
        readJsonl(seedDirectory.resolve(ProblemLearningMetadataContract.RELATIONS_FILE), ProblemRelationSeedRecord.class),
        readJsonl(seedDirectory.resolve(ProblemLearningMetadataContract.HINTS_FILE), ProblemHintSeedRecord.class),
        readJsonl(seedDirectory.resolve(ProblemLearningMetadataContract.CODE_TEMPLATES_FILE), ProblemCodeTemplateSeedRecord.class),
        readJsonl(seedDirectory.resolve(ProblemLearningMetadataContract.CATEGORIES_FILE), ProblemCategorySeedRecord.class),
        readJsonl(seedDirectory.resolve(ProblemLearningMetadataContract.CATEGORY_ITEMS_FILE), ProblemCategoryItemSeedRecord.class));
  }

  private List<ProblemMetadataSourceProblemRecord> readManifestSourceProblems(JsonNode manifest)
      throws IOException {
    JsonNode sourceProblems = manifest.path("sourceProblemSites");
    if (!sourceProblems.isArray()) {
      throw new IllegalArgumentException("Metadata seed manifest sourceProblemSites must be an array.");
    }
    List<ProblemMetadataSourceProblemRecord> records = new ArrayList<>();
    for (JsonNode node : sourceProblems) {
      records.add(objectMapper.treeToValue(node, ProblemMetadataSourceProblemRecord.class));
    }
    return List.copyOf(records);
  }

  private <T> List<T> readJsonl(Path path, Class<T> type) throws IOException {
    List<T> records = new ArrayList<>();
    try (BufferedReader reader = Files.newBufferedReader(path)) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.isBlank()) {
          records.add(objectMapper.readValue(line, type));
        }
      }
    }
    return List.copyOf(records);
  }
}
