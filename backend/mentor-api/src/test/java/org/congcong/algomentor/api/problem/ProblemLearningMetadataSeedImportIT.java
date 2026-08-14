package org.congcong.algomentor.api.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.mybatis.spring.SqlSessionTemplate;
import org.congcong.algomentor.api.problem.mapper.ProblemLearningMetadataMapper;
import org.congcong.algomentor.api.problem.model.ProblemLearningMetadataContract;
import org.congcong.algomentor.api.problem.repository.MyBatisProblemLearningMetadataRepository;
import org.congcong.algomentor.api.problem.repository.ProblemLearningMetadataRepository;
import org.congcong.algomentor.api.problem.service.ProblemLearningMetadataSeedImportService;
import org.congcong.algomentor.api.problem.service.ProblemLearningMetadataSeedReader;
import org.congcong.algomentor.api.problem.service.ProblemLearningMetadataSeedValidator;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProblemLearningMetadataSeedImportIT extends PostgresIntegrationTestSupport {

  private static final String SNAPSHOT = "leetcode-question-learning-metadata.v1@test";

  @TempDir
  private Path tempDir;

  @Test
  void importsIdempotentlyRefreshesLeetCodeOnlyAndRejectsTamperedSeedBeforeWrites() throws Exception {
    migrateLatest();
    insertProblem("two-sum", 1, List.of(), List.of(), List.of());
    insertProblem("three-sum", 15, List.of(), List.of(), List.of());
    Path seedDirectory = tempDir.resolve("metadata-seed");
    writeSeed(seedDirectory, true, true, true);
    ProblemLearningMetadataSeedImportService importer = importer();

    importSeed(importer, seedDirectory);
    assertThat(count("problem_relation")).isEqualTo(2L);
    assertThat(count("problem_hint")).isEqualTo(1L);
    assertThat(count("problem_code_template")).isEqualTo(1L);
    assertThat(count("problem_category_item")).isEqualTo(1L);
    assertThat(queryLong("SELECT COUNT(*) FROM problem_relation WHERE target_problem_id IS NULL"))
        .isEqualTo(1L);
    assertThat(queryLong("SELECT COUNT(*) FROM problem_relation WHERE target_problem_id IS NOT NULL"))
        .isEqualTo(1L);
    String beforeHash = queryString("""
        SELECT md5(string_agg(value, ',' ORDER BY value))
        FROM (
          SELECT target_problem_slug || ':' || source_snapshot AS value FROM problem_relation
          UNION ALL SELECT content_markdown || ':' || source_snapshot FROM problem_hint
          UNION ALL SELECT language_slug || ':' || code FROM problem_code_template
        ) values
        """);

    importSeed(importer, seedDirectory);
    assertThat(count("problem_metadata_import_run")).isEqualTo(2L);
    assertThat(queryString("""
        SELECT md5(string_agg(value, ',' ORDER BY value))
        FROM (
          SELECT target_problem_slug || ':' || source_snapshot AS value FROM problem_relation
          UNION ALL SELECT content_markdown || ':' || source_snapshot FROM problem_hint
          UNION ALL SELECT language_slug || ':' || code FROM problem_code_template
        ) values
        """)).isEqualTo(beforeHash);

    execute("""
        INSERT INTO problem_relation (source_problem_id, target_problem_slug, relation_type, source, source_snapshot)
        SELECT id, 'curated-target', 'CURATED_RELATION', 'CURATED', 'curated@1'
        FROM problem WHERE slug = 'two-sum'
        """);
    execute("""
        INSERT INTO problem_category (slug, name, name_en, name_zh, source, source_snapshot)
        VALUES ('legacy', 'Legacy', 'Legacy', '遗留', 'LEGACY', 'legacy')
        """);
    execute("""
        INSERT INTO problem_category_item (category_id, problem_id, source, source_snapshot)
        SELECT category.id, problem.id, 'LEGACY', 'legacy'
        FROM problem_category category JOIN problem ON problem.slug = 'two-sum'
        WHERE category.slug = 'legacy'
        """);

    writeSeed(seedDirectory, false, false, false);
    importSeed(importer, seedDirectory);
    assertThat(count("problem_relation")).isEqualTo(1L);
    assertThat(queryLong("SELECT COUNT(*) FROM problem_relation WHERE source = 'CURATED'"))
        .isEqualTo(1L);
    assertThat(count("problem_hint")).isZero();
    assertThat(count("problem_code_template")).isZero();
    assertThat(queryLong("SELECT COUNT(*) FROM problem_category_item WHERE source = 'LEETCODE'"))
        .isZero();
    assertThat(queryLong("SELECT COUNT(*) FROM problem_category_item WHERE source = 'LEGACY'"))
        .isEqualTo(1L);

    Files.writeString(seedDirectory.resolve(ProblemLearningMetadataContract.HINTS_FILE), "{}\n");
    assertThatThrownBy(() -> importSeed(importer, seedDirectory))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("checksum mismatch");
    assertThat(count("problem_metadata_import_run")).isEqualTo(3L);
    assertThat(queryLong("SELECT COUNT(*) FROM problem_relation WHERE source = 'CURATED'"))
        .isEqualTo(1L);
  }

  private ProblemLearningMetadataSeedImportService importer() throws Exception {
    SqlSessionTemplate sqlSessionTemplate = sqlSessionTemplate("mapper/problem/ProblemLearningMetadataMapper.xml");
    ProblemLearningMetadataMapper mapper = sqlSessionTemplate.getMapper(ProblemLearningMetadataMapper.class);
    ObjectMapper objectMapper = new ObjectMapper();
    ProblemLearningMetadataRepository repository = new MyBatisProblemLearningMetadataRepository(mapper, objectMapper);
    return new ProblemLearningMetadataSeedImportService(
        objectProvider(repository),
        new ProblemLearningMetadataSeedReader(objectMapper),
        new ProblemLearningMetadataSeedValidator(),
        objectMapper);
  }

  private void importSeed(ProblemLearningMetadataSeedImportService importer, Path seedDirectory) {
    transactionTemplate().executeWithoutResult(status -> {
      try {
        importer.importSeed(seedDirectory);
      } catch (IOException exception) {
        throw new UncheckedIOException(exception);
      }
    });
  }

  private void writeSeed(Path directory, boolean includeHints, boolean includeTemplate, boolean includeCategory)
      throws IOException {
    Files.createDirectories(directory);
    boolean includeRelations = includeHints || includeTemplate || includeCategory;
    write(directory, ProblemLearningMetadataContract.RELATIONS_FILE, includeRelations ? """
        {"sourceSlug":"two-sum","targetSlug":"three-sum","relationType":"LEETCODE_SIMILAR","source":"LEETCODE","sourceSnapshot":"%s","metadata":{"title":"3Sum"}}
        {"sourceSlug":"two-sum","targetSlug":"future-problem","relationType":"LEETCODE_SIMILAR","source":"LEETCODE","sourceSnapshot":"%s","metadata":{"title":"Future"}}
        """.formatted(SNAPSHOT, SNAPSHOT) : "");
    write(directory, ProblemLearningMetadataContract.HINTS_FILE, includeHints
        ? "{\"problemSlug\":\"two-sum\",\"sourceSite\":\"LEETCODE_COM\",\"ordinal\":1,\"contentMarkdown\":\"Use a hash map.\",\"sourceSnapshot\":\"%s\"}\n".formatted(SNAPSHOT)
        : "");
    write(directory, ProblemLearningMetadataContract.CODE_TEMPLATES_FILE, includeTemplate
        ? "{\"problemSlug\":\"two-sum\",\"languageSlug\":\"java\",\"languageLabel\":\"Java\",\"code\":\"class Solution {}\",\"sourceSite\":\"LEETCODE_COM\",\"sourceSnapshot\":\"%s\"}\n".formatted(SNAPSHOT)
        : "");
    write(directory, ProblemLearningMetadataContract.CATEGORIES_FILE, includeCategory
        ? "{\"slug\":\"algorithms\",\"nameEn\":\"Algorithms\",\"nameZh\":\"算法\",\"source\":\"LEETCODE\",\"sourceSnapshot\":\"%s\"}\n".formatted(SNAPSHOT)
        : "");
    write(directory, ProblemLearningMetadataContract.CATEGORY_ITEMS_FILE, includeCategory
        ? "{\"problemSlug\":\"two-sum\",\"categorySlug\":\"algorithms\",\"source\":\"LEETCODE\",\"sourceSnapshot\":\"%s\"}\n".formatted(SNAPSHOT)
        : "");
    String checksums = """
        {"%s":"%s","%s":"%s","%s":"%s","%s":"%s","%s":"%s"}
        """.formatted(
        ProblemLearningMetadataContract.RELATIONS_FILE, checksum(directory.resolve(ProblemLearningMetadataContract.RELATIONS_FILE)),
        ProblemLearningMetadataContract.HINTS_FILE, checksum(directory.resolve(ProblemLearningMetadataContract.HINTS_FILE)),
        ProblemLearningMetadataContract.CODE_TEMPLATES_FILE, checksum(directory.resolve(ProblemLearningMetadataContract.CODE_TEMPLATES_FILE)),
        ProblemLearningMetadataContract.CATEGORIES_FILE, checksum(directory.resolve(ProblemLearningMetadataContract.CATEGORIES_FILE)),
        ProblemLearningMetadataContract.CATEGORY_ITEMS_FILE, checksum(directory.resolve(ProblemLearningMetadataContract.CATEGORY_ITEMS_FILE)));
    Path fetchReport = directory.resolve("problem_metadata_fetch_report.json");
    Files.writeString(fetchReport, "{\"sourceSnapshot\":\"%s\"}\n".formatted(SNAPSHOT));
    write(directory, ProblemLearningMetadataContract.MANIFEST_FILE, """
        {"sourceSnapshot":"%s","fetchReportPath":"%s","fetchReportSha256":"%s","outputSha256":%s,"sourceProblemSites":[{"problemSlug":"two-sum","sourceSites":["LEETCODE_COM"]}]}
        """.formatted(SNAPSHOT, fetchReport, checksum(fetchReport), checksums));
    write(directory, ProblemLearningMetadataContract.AUDIT_REPORT_FILE,
        "{\"outcome\":\"PASSED\",\"sourceSnapshot\":\"%s\",\"manualSampling\":{\"status\":\"PASSED\"}}\n".formatted(SNAPSHOT));
  }

  private void write(Path directory, String filename, String value) throws IOException {
    Files.writeString(directory.resolve(filename), value, StandardCharsets.UTF_8);
  }

  private String checksum(Path path) throws IOException {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
