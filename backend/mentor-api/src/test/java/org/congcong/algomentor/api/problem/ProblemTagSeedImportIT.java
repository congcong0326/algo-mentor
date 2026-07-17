package org.congcong.algomentor.api.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.congcong.algomentor.api.problem.mapper.ProblemMapper;
import org.congcong.algomentor.api.problem.mapper.ProblemTagMapper;
import org.congcong.algomentor.api.problem.repository.MyBatisProblemRepository;
import org.congcong.algomentor.api.problem.repository.MyBatisProblemTagRepository;
import org.congcong.algomentor.api.problem.repository.ProblemRepository;
import org.congcong.algomentor.api.problem.repository.ProblemTagRepository;
import org.congcong.algomentor.api.problem.service.ProblemSeedImporter;
import org.congcong.algomentor.api.problem.service.ProblemSeedTagNormalizer;
import org.congcong.algomentor.api.problem.service.ProblemTagConsistencyValidator;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mybatis.spring.SqlSessionTemplate;

class ProblemTagSeedImportIT extends PostgresIntegrationTestSupport {

  @TempDir
  private Path tempDir;

  @Test
  void importsNormalizedCatalogAssignmentsAndCompatibilityArraysIdempotently() throws Exception {
    migrateLatest();
    Path problemSeedDirectory = tempDir.resolve("problem-seed");
    Path insightSeedDirectory = tempDir.resolve("insight-seed");
    writeSeed(problemSeedDirectory, insightSeedDirectory, false, true);
    ProblemSeedImporter importer = importer();

    assertThat(importSeed(importer, problemSeedDirectory, insightSeedDirectory)).isEqualTo(1);
    assertThat(count("problem_tag")).isEqualTo(2L);
    assertThat(count("problem_tag_assignment")).isEqualTo(2L);
    assertThat(queryString("""
        SELECT array_to_string(tag_labels_zh, E'\\n')
        FROM problem
        WHERE slug = 'two-sum'
        """)).isEqualTo("数组\n哈希表");
    String problemUpdatedAt = queryString("SELECT updated_at::TEXT FROM problem WHERE slug = 'two-sum'");
    String tagUpdatedAt = queryString("SELECT updated_at::TEXT FROM problem_tag WHERE value = 'array'");

    Thread.sleep(5L);
    assertThat(importSeed(importer, problemSeedDirectory, insightSeedDirectory)).isEqualTo(1);
    assertThat(count("problem_tag")).isEqualTo(2L);
    assertThat(count("problem_tag_assignment")).isEqualTo(2L);
    assertThat(queryString("SELECT updated_at::TEXT FROM problem WHERE slug = 'two-sum'"))
        .isEqualTo(problemUpdatedAt);
    assertThat(queryString("SELECT updated_at::TEXT FROM problem_tag WHERE value = 'array'"))
        .isEqualTo(tagUpdatedAt);

    try (var connection = dataSource().getConnection();
        var statement = connection.prepareStatement("UPDATE problem_tag SET active = FALSE WHERE value = 'array'")) {
      statement.executeUpdate();
    }
    assertThat(importSeed(importer, problemSeedDirectory, insightSeedDirectory)).isEqualTo(1);
    assertThat(queryLong("SELECT COUNT(*) FROM problem_tag WHERE value = 'array' AND active = TRUE"))
        .isEqualTo(1L);

    writeSeed(problemSeedDirectory, insightSeedDirectory, false, false);
    assertThat(importSeed(importer, problemSeedDirectory, insightSeedDirectory)).isEqualTo(1);
    assertThat(count("problem_tag_assignment")).isEqualTo(1L);
    assertThat(queryString("""
        SELECT array_to_string(tag_values, E'\\n')
        FROM problem
        WHERE slug = 'two-sum'
        """)).isEqualTo("hash-table");
    assertThat(queryLong(arrayConsistencySql())).isZero();
  }

  @Test
  void rejectsConflictingTagsBeforeTheTransactionWritesAnyRows() throws Exception {
    migrateLatest();
    Path problemSeedDirectory = tempDir.resolve("problem-seed");
    Path insightSeedDirectory = tempDir.resolve("insight-seed");
    writeSeed(problemSeedDirectory, insightSeedDirectory, true, true);
    ProblemSeedImporter importer = importer();

    assertThatThrownBy(() -> importSeed(importer, problemSeedDirectory, insightSeedDirectory))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Conflicting duplicate problem tag labels");
    assertThat(count("problem")).isZero();
    assertThat(count("problem_tag")).isZero();
    assertThat(count("problem_tag_assignment")).isZero();
  }

  private ProblemSeedImporter importer() throws Exception {
    SqlSessionTemplate sqlSessionTemplate = sqlSessionTemplate(
        "mapper/problem/ProblemMapper.xml",
        "mapper/problem/ProblemTagMapper.xml");
    ProblemMapper problemMapper = sqlSessionTemplate.getMapper(ProblemMapper.class);
    ProblemTagMapper problemTagMapper = sqlSessionTemplate.getMapper(ProblemTagMapper.class);
    ProblemRepository problemRepository = new MyBatisProblemRepository(problemMapper, dataSource());
    ProblemTagRepository tagRepository = new MyBatisProblemTagRepository(problemTagMapper);
    ProblemTagConsistencyValidator validator = new ProblemTagConsistencyValidator(objectProvider(problemTagMapper));
    return new ProblemSeedImporter(
        objectProvider(problemRepository),
        objectProvider(tagRepository),
        new ProblemSeedTagNormalizer(),
        validator,
        new ObjectMapper());
  }

  private int importSeed(ProblemSeedImporter importer, Path problemSeedDirectory, Path insightSeedDirectory) {
    return transactionTemplate().execute(status -> {
      try {
        return importer.importSeed(problemSeedDirectory, insightSeedDirectory);
      } catch (IOException exception) {
        throw new UncheckedIOException(exception);
      }
    });
  }

  private void writeSeed(
      Path problemSeedDirectory,
      Path insightSeedDirectory,
      boolean conflictingTags,
      boolean includeArray
  ) throws IOException {
    Files.createDirectories(problemSeedDirectory);
    Files.createDirectories(insightSeedDirectory);
    String tags = conflictingTags
        ? """
            "tagValues":["array","array"],
            "tagLabelsEn":["Array","Array Theory"],
            "tagLabelsZh":["数组","数组"]
            """
        : includeArray
            ? """
                "tagValues":["array","hash-table"],
                "tagLabelsEn":["Array","Hash Table"],
                "tagLabelsZh":["数组","哈希表"]
                """
            : """
                "tagValues":["hash-table"],
                "tagLabelsEn":["Hash Table"],
                "tagLabelsZh":["哈希表"]
                """;
    Files.writeString(problemSeedDirectory.resolve("problems.jsonl"), """
        {
          "slug":"two-sum",
          "frontendId":1,
          "frontendDisplayId":"1",
          "titleEn":"Two Sum",
          "titleZh":"两数之和",
          "difficulty":"EASY",
          %s,
          "contentMarkdownEn":"Two Sum statement",
          "contentMarkdownZh":"两数之和题面",
          "contentStatus":"BILINGUAL",
          "sourceSite":"LEETCODE_COM_CN"
        }
        """.formatted(tags).replaceAll("\\n\\s*", ""));
    Files.writeString(insightSeedDirectory.resolve("problem_reasons.json"), """
        [{"slug":"two-sum","reasonEN":"Practice lookup.","reasonZH":"练习查找。"}]
        """);
  }

  private String arrayConsistencySql() {
    return """
        SELECT COUNT(*)
        FROM problem p
        LEFT JOIN LATERAL (
          SELECT
            array_agg(tag.value::TEXT ORDER BY assignment.ordinal) AS tag_values,
            array_agg(tag.label_en::TEXT ORDER BY assignment.ordinal) AS tag_labels_en,
            array_agg(tag.label_zh::TEXT ORDER BY assignment.ordinal) AS tag_labels_zh
          FROM problem_tag_assignment assignment
          JOIN problem_tag tag ON tag.id = assignment.tag_id
          WHERE assignment.problem_id = p.id
        ) normalized_tags ON TRUE
        WHERE p.tag_values IS DISTINCT FROM COALESCE(normalized_tags.tag_values, ARRAY[]::TEXT[])
           OR p.tag_labels_en IS DISTINCT FROM COALESCE(normalized_tags.tag_labels_en, ARRAY[]::TEXT[])
           OR p.tag_labels_zh IS DISTINCT FROM COALESCE(normalized_tags.tag_labels_zh, ARRAY[]::TEXT[])
        """;
  }
}
