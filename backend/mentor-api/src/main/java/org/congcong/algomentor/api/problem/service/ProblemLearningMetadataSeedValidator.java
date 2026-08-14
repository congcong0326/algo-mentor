package org.congcong.algomentor.api.problem.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.congcong.algomentor.api.problem.model.ProblemLearningMetadataContract;
import org.springframework.stereotype.Component;

/** 导入前复核 manifest、审核结论、输出哈希和最小业务契约；失败时不允许触碰业务表。 */
@Component
public class ProblemLearningMetadataSeedValidator {

  public void validate(ProblemLearningMetadataSeed seed) throws IOException {
    JsonNode manifest = seed.manifest();
    JsonNode audit = seed.auditReport();
    String sourceSnapshot = requiredText(manifest, ProblemLearningMetadataContract.JSON_FIELD_SOURCE_SNAPSHOT);
    if (!ProblemLearningMetadataContract.AUDIT_OUTCOME_PASSED.equals(
        requiredText(audit, ProblemLearningMetadataContract.JSON_FIELD_OUTCOME))) {
      throw new IllegalArgumentException("Problem metadata audit report outcome must be PASSED.");
    }
    if (!sourceSnapshot.equals(requiredText(audit, ProblemLearningMetadataContract.JSON_FIELD_SOURCE_SNAPSHOT))) {
      throw new IllegalArgumentException("Problem metadata audit report source snapshot does not match manifest.");
    }
    if (!ProblemLearningMetadataContract.AUDIT_OUTCOME_PASSED.equals(requiredText(
        audit.path(ProblemLearningMetadataContract.JSON_FIELD_MANUAL_SAMPLING),
        ProblemLearningMetadataContract.JSON_FIELD_STATUS))) {
      throw new IllegalArgumentException("Problem metadata audit report is missing a passed manual sample.");
    }
    JsonNode outputSha256 = manifest.path(ProblemLearningMetadataContract.JSON_FIELD_OUTPUT_SHA256);
    for (String filename : outputFiles()) {
      String expected = requiredText(outputSha256, filename);
      String actual = checksum(seed.seedDirectory().resolve(filename));
      if (!expected.equals(actual)) {
        throw new IllegalArgumentException("Problem metadata seed checksum mismatch: " + filename);
      }
    }
    validateFetchReportChecksum(seed, manifest);
    validateContracts(seed, sourceSnapshot);
  }

  private void validateContracts(ProblemLearningMetadataSeed seed, String sourceSnapshot) {
    Set<String> sourceSlugs = seed.sourceProblems().stream()
        .map(record -> record.problemSlug())
        .collect(java.util.stream.Collectors.toSet());
    if (sourceSlugs.size() != seed.sourceProblems().size() || sourceSlugs.stream().anyMatch(this::blank)) {
      throw new IllegalArgumentException("Metadata manifest contains duplicate or blank source problem slugs.");
    }
    if (seed.relations().stream().anyMatch(record -> blank(record.sourceSlug())
        || blank(record.targetSlug()) || record.sourceSlug().equals(record.targetSlug())
        || !ProblemLearningMetadataContract.RELATION_TYPE_LEETCODE_SIMILAR.equals(record.relationType())
        || !ProblemLearningMetadataContract.SOURCE_LEETCODE.equals(record.source())
        || !sourceSnapshot.equals(record.sourceSnapshot()))) {
      throw new IllegalArgumentException("Metadata relation seed violates the fixed contract.");
    }
    if (seed.hints().stream().anyMatch(record -> blank(record.problemSlug()) || blank(record.contentMarkdown())
        || record.ordinal() <= 0 || !sourceSnapshot.equals(record.sourceSnapshot()))) {
      throw new IllegalArgumentException("Metadata hint seed violates the fixed contract.");
    }
    if (seed.codeTemplates().stream().anyMatch(record -> blank(record.problemSlug())
        || blank(record.languageSlug()) || blank(record.languageLabel()) || blank(record.code())
        || !sourceSnapshot.equals(record.sourceSnapshot()))) {
      throw new IllegalArgumentException("Metadata code template seed violates the fixed contract.");
    }
    if (seed.categories().stream().anyMatch(record -> blank(record.slug()) || blank(record.nameEn())
        || blank(record.nameZh()) || !ProblemLearningMetadataContract.SOURCE_LEETCODE.equals(record.source())
        || !sourceSnapshot.equals(record.sourceSnapshot()))
        || seed.categoryItems().stream().anyMatch(record -> blank(record.problemSlug())
        || blank(record.categorySlug()) || !ProblemLearningMetadataContract.SOURCE_LEETCODE.equals(record.source())
        || !sourceSnapshot.equals(record.sourceSnapshot()))) {
      throw new IllegalArgumentException("Metadata category seed violates the fixed contract.");
    }
  }

  private void validateFetchReportChecksum(ProblemLearningMetadataSeed seed, JsonNode manifest) throws IOException {
    String reportPath = requiredText(manifest, ProblemLearningMetadataContract.JSON_FIELD_FETCH_REPORT_PATH);
    String expected = requiredText(manifest, ProblemLearningMetadataContract.JSON_FIELD_FETCH_REPORT_SHA256);
    Path path = Path.of(reportPath);
    if (!path.isAbsolute()) {
      path = seed.seedDirectory().resolve(path).normalize();
    }
    if (!expected.equals(checksum(path))) {
      throw new IllegalArgumentException("Problem metadata fetch report checksum mismatch.");
    }
  }

  private List<String> outputFiles() {
    return List.of(
        ProblemLearningMetadataContract.RELATIONS_FILE,
        ProblemLearningMetadataContract.HINTS_FILE,
        ProblemLearningMetadataContract.CODE_TEMPLATES_FILE,
        ProblemLearningMetadataContract.CATEGORIES_FILE,
        ProblemLearningMetadataContract.CATEGORY_ITEMS_FILE);
  }

  private String requiredText(JsonNode node, String field) {
    JsonNode value = node.path(field);
    if (!value.isTextual() || value.asText().isBlank()) {
      throw new IllegalArgumentException("Required metadata seed field is missing: " + field);
    }
    return value.asText();
  }

  private String checksum(Path path) throws IOException {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable.", exception);
    }
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
