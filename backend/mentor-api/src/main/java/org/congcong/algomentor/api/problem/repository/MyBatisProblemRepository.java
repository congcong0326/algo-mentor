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
import org.congcong.algomentor.api.problem.model.ProblemLocale;
import org.congcong.algomentor.api.problem.model.ProblemListItem;
import org.congcong.algomentor.api.problem.model.ProblemListRequest;
import org.congcong.algomentor.api.problem.model.ProblemPage;
import org.congcong.algomentor.api.problem.model.ProblemSeedRecord;
import org.congcong.algomentor.api.problem.model.ProblemTag;
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
            request.locale().value(),
            request.pageSize(),
            request.offset())
        .stream()
        .map(row -> toListItem(row, request.locale()))
        .toList();
    return new ProblemPage<>(items, total, request.page(), request.pageSize());
  }

  @Override
  public Optional<ProblemDetail> findProblemBySlug(String slug) {
    return findProblemBySlug(slug, ProblemLocale.DEFAULT);
  }

  @Override
  public ProblemFilters findProblemFilters() {
    return findProblemFilters(ProblemLocale.DEFAULT);
  }

  @Override
  public Optional<ProblemDetail> findProblemBySlug(String slug, ProblemLocale locale) {
    return Optional.ofNullable(mapper.findProblemBySlug(slug)).map(row -> toDetail(row, locale));
  }

  @Override
  public ProblemFilters findProblemFilters(ProblemLocale locale) {
    Map<String, Long> difficultyCounts = mapper.countProblemsByDifficulty().stream()
        .collect(Collectors.toMap(ProblemFilterCountRow::value, row -> count(row.problemCount())));
    List<ProblemFilterOption> difficulties = Arrays.stream(ProblemDifficulty.values())
        .map(difficulty -> new ProblemFilterOption(
            difficulty.name(),
            difficulty.name(),
            difficultyCounts.getOrDefault(difficulty.name(), 0L)))
        .toList();
    List<ProblemFilterOption> tags = mapper.countProblemsByTag(locale.value()).stream()
        .map(row -> new ProblemFilterOption(row.value(), fallback(row.label(), row.value()), count(row.problemCount())))
        .toList();
    List<ProblemCategoryFilterOption> categories = mapper.countProblemCategories().stream()
        .map(this::toCategoryFilterOption)
        .toList();
    return new ProblemFilters(mapper.countAllProblems(), difficulties, tags, categories);
  }

  @Override
  public void upsertProblem(ProblemSeedRecord problem) {
    Connection connection = DataSourceUtils.getConnection(dataSource);
    List<String> tagValues = normalizedValues(problem.tagValues());
    List<String> tagLabelsEn = normalizedLabels(problem.tagLabelsEn(), tagValues);
    List<String> tagLabelsZh = normalizedLabels(problem.tagLabelsZh(), tagValues);
    Array tagValuesArray = null;
    Array tagLabelsEnArray = null;
    Array tagLabelsZhArray = null;
    try {
      tagValuesArray = connection.createArrayOf("text", tagValues.toArray(String[]::new));
      tagLabelsEnArray = connection.createArrayOf("text", tagLabelsEn.toArray(String[]::new));
      tagLabelsZhArray = connection.createArrayOf("text", tagLabelsZh.toArray(String[]::new));
      mapper.upsertProblem(new ProblemUpsertRow(
          problem.slug(),
          problem.frontendId(),
          problem.titleEn(),
          problem.titleZh(),
          problem.difficulty() == null ? null : problem.difficulty().name(),
          tagValuesArray,
          tagLabelsEnArray,
          tagLabelsZhArray,
          problem.contentMarkdownEn(),
          problem.contentMarkdownZh(),
          problem.leetcodeUrl(),
          problem.sampleTestCase(),
          problem.python3Template(),
          problem.sourceCommit()));
    } catch (SQLException exception) {
      throw new IllegalStateException("Failed to create PostgreSQL array for problem tags.", exception);
    } finally {
      freeArray(tagValuesArray);
      freeArray(tagLabelsEnArray);
      freeArray(tagLabelsZhArray);
      DataSourceUtils.releaseConnection(connection, dataSource);
    }
  }

  private ProblemListItem toListItem(ProblemRow row, ProblemLocale locale) {
    return new ProblemListItem(
        row.slug(),
        row.frontendId(),
        title(row, locale),
        parseDifficulty(row.difficulty()),
        tags(row, locale));
  }

  private ProblemDetail toDetail(ProblemRow row, ProblemLocale locale) {
    return new ProblemDetail(
        row.slug(),
        row.frontendId(),
        title(row, locale),
        parseDifficulty(row.difficulty()),
        tags(row, locale),
        contentMarkdown(row, locale),
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

  private String title(ProblemRow row, ProblemLocale locale) {
    return locale.isEnglish() ? fallback(row.titleEn(), row.titleZh()) : fallback(row.titleZh(), row.titleEn());
  }

  private String contentMarkdown(ProblemRow row, ProblemLocale locale) {
    return locale.isEnglish()
        ? fallback(row.contentMarkdownEn(), row.contentMarkdownZh())
        : fallback(row.contentMarkdownZh(), row.contentMarkdownEn());
  }

  private List<ProblemTag> tags(ProblemRow row, ProblemLocale locale) {
    List<String> values = splitArrayText(row.tagValuesText());
    List<String> labels = splitArrayText(locale.isEnglish() ? row.tagLabelsEnText() : row.tagLabelsZhText());
    return java.util.stream.IntStream.range(0, values.size())
        .mapToObj(index -> new ProblemTag(values.get(index), fallback(valueAt(labels, index), values.get(index))))
        .toList();
  }

  private List<String> splitArrayText(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return Arrays.stream(value.split("\\R", -1))
        .map(this::blankToNull)
        .map(valueOrNull -> valueOrNull == null ? "" : valueOrNull)
        .toList();
  }

  private List<String> normalizedValues(List<String> values) {
    if (values == null) {
      return List.of();
    }
    return values.stream()
        .map(this::blankToNull)
        .filter(value -> value != null)
        .distinct()
        .toList();
  }

  private List<String> normalizedLabels(List<String> labels, List<String> tagValues) {
    return java.util.stream.IntStream.range(0, tagValues.size())
        .mapToObj(index -> fallback(valueAt(labels, index), tagValues.get(index)))
        .toList();
  }

  private String valueAt(List<String> values, int index) {
    if (values == null || index < 0 || index >= values.size()) {
      return null;
    }
    return blankToNull(values.get(index));
  }

  private String fallback(String value, String fallback) {
    String normalized = blankToNull(value);
    return normalized == null ? fallback : normalized;
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private void freeArray(Array array) {
    if (array != null) {
      try {
        array.free();
      } catch (SQLException ignored) {
        // PostgreSQL array cleanup failure should not mask the import result.
      }
    }
  }
}
