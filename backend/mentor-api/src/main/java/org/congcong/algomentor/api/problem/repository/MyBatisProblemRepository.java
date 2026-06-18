package org.congcong.algomentor.api.problem.repository;

import java.sql.Array;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.congcong.algomentor.api.problem.mapper.model.ProblemCategoryFilterRow;
import org.congcong.algomentor.api.problem.mapper.model.ProblemFilterCountRow;
import org.congcong.algomentor.api.problem.mapper.ProblemMapper;
import org.congcong.algomentor.api.problem.mapper.model.ProblemRow;
import org.congcong.algomentor.api.problem.mapper.model.ProblemUpsertRow;
import org.congcong.algomentor.api.problem.model.ProblemCategoryFilterOption;
import org.congcong.algomentor.api.problem.model.ProblemDetail;
import org.congcong.algomentor.api.problem.model.ProblemDifficulty;
import org.congcong.algomentor.api.problem.model.ProblemFilterOption;
import org.congcong.algomentor.api.problem.model.ProblemFilters;
import org.congcong.algomentor.api.problem.model.ProblemListItem;
import org.congcong.algomentor.api.problem.model.ProblemListRequest;
import org.congcong.algomentor.api.problem.model.ProblemPage;
import org.congcong.algomentor.api.problem.model.ProblemSeedRecord;
import org.springframework.jdbc.datasource.DataSourceUtils;

public class MyBatisProblemRepository implements ProblemRepository {

  private final ProblemMapper mapper;
  private final DataSource dataSource;

  public MyBatisProblemRepository(ProblemMapper mapper, DataSource dataSource) {
    this.mapper = mapper;
    this.dataSource = dataSource;
  }

  @Override
  public ProblemPage<ProblemListItem> findProblems(ProblemListRequest request) {
    String difficulty = request.difficulty() == null ? null : request.difficulty().name();
    long total = mapper.countProblems(request.keyword(), difficulty, request.tag(), request.category());
    List<ProblemListItem> items = mapper.findProblems(
            request.keyword(),
            difficulty,
            request.tag(),
            request.category(),
            request.sort().name(),
            request.pageSize(),
            request.offset())
        .stream()
        .map(this::toListItem)
        .toList();
    return new ProblemPage<>(items, total, request.page(), request.pageSize());
  }

  @Override
  public Optional<ProblemDetail> findProblemBySlug(String slug) {
    return Optional.ofNullable(mapper.findProblemBySlug(slug)).map(this::toDetail);
  }

  @Override
  public ProblemFilters findProblemFilters() {
    Map<String, Long> difficultyCounts = mapper.countProblemsByDifficulty().stream()
        .collect(Collectors.toMap(ProblemFilterCountRow::value, row -> count(row.problemCount())));
    List<ProblemFilterOption> difficulties = Arrays.stream(ProblemDifficulty.values())
        .map(difficulty -> new ProblemFilterOption(
            difficulty.name(),
            difficultyCounts.getOrDefault(difficulty.name(), 0L)))
        .toList();
    List<ProblemFilterOption> tags = mapper.countProblemsByTag().stream()
        .map(row -> new ProblemFilterOption(row.value(), count(row.problemCount())))
        .toList();
    List<ProblemCategoryFilterOption> categories = mapper.countProblemCategories().stream()
        .map(this::toCategoryFilterOption)
        .toList();
    return new ProblemFilters(mapper.countAllProblems(), difficulties, tags, categories);
  }

  @Override
  public void upsertProblem(ProblemSeedRecord problem) {
    Connection connection = DataSourceUtils.getConnection(dataSource);
    Array tags = null;
    try {
      tags = connection.createArrayOf("text", normalizedTags(problem.tags()).toArray(String[]::new));
      mapper.upsertProblem(new ProblemUpsertRow(
          problem.slug(),
          problem.frontendId(),
          problem.title(),
          problem.titleCn(),
          problem.difficulty() == null ? null : problem.difficulty().name(),
          tags,
          problem.contentMarkdown(),
          problem.leetcodeUrl(),
          problem.sampleTestCase(),
          problem.python3Template(),
          problem.sourceCommit()));
    } catch (SQLException exception) {
      throw new IllegalStateException("Failed to create PostgreSQL array for problem tags.", exception);
    } finally {
      if (tags != null) {
        try {
          tags.free();
        } catch (SQLException ignored) {
          // PostgreSQL array cleanup failure should not mask the import result.
        }
      }
      DataSourceUtils.releaseConnection(connection, dataSource);
    }
  }

  private ProblemListItem toListItem(ProblemRow row) {
    return new ProblemListItem(
        row.slug(),
        row.frontendId(),
        row.title(),
        row.titleCn(),
        parseDifficulty(row.difficulty()),
        tags(row));
  }

  private ProblemDetail toDetail(ProblemRow row) {
    return new ProblemDetail(
        row.slug(),
        row.frontendId(),
        row.title(),
        row.titleCn(),
        parseDifficulty(row.difficulty()),
        tags(row),
        row.contentMarkdown(),
        row.leetcodeUrl(),
        row.sampleTestCase(),
        row.python3Template(),
        row.sourceCommit());
  }

  private ProblemCategoryFilterOption toCategoryFilterOption(ProblemCategoryFilterRow row) {
    return new ProblemCategoryFilterOption(row.slug(), row.name(), count(row.problemCount()));
  }

  private long count(Long value) {
    return value == null ? 0L : value;
  }

  private ProblemDifficulty parseDifficulty(String difficulty) {
    return difficulty == null ? null : ProblemDifficulty.valueOf(difficulty);
  }

  private List<String> tags(ProblemRow row) {
    if (row.tagsText() == null || row.tagsText().isBlank()) {
      return List.of();
    }
    return Arrays.stream(row.tagsText().split("\\R"))
        .filter(tag -> !tag.isBlank())
        .toList();
  }

  private List<String> normalizedTags(List<String> tags) {
    if (tags == null) {
      return List.of();
    }
    return tags.stream()
        .filter(tag -> tag != null && !tag.isBlank())
        .map(String::trim)
        .distinct()
        .toList();
  }
}
