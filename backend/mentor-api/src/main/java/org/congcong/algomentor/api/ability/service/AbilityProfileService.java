package org.congcong.algomentor.api.ability.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import org.congcong.algomentor.api.ability.mapper.AbilityProfileMapper;
import org.congcong.algomentor.api.ability.mapper.model.AbilityTagScoreRow;
import org.congcong.algomentor.api.ability.model.AbilityHomeSummaryResponse;
import org.congcong.algomentor.api.ability.model.AbilityHomeSummaryTagResponse;
import org.congcong.algomentor.api.ability.model.AbilityProfileResponse;
import org.congcong.algomentor.api.ability.model.AbilityProfileScopeResponse;
import org.congcong.algomentor.api.ability.model.AbilityTagScoreResponse;
import org.congcong.algomentor.api.problem.model.ProblemLocale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AbilityProfileService {

  private final Supplier<AbilityProfileMapper> mapperSupplier;

  @Autowired
  public AbilityProfileService(ObjectProvider<AbilityProfileMapper> mapperProvider) {
    this(mapperProvider::getIfAvailable);
  }

  AbilityProfileService(AbilityProfileMapper mapper) {
    this(() -> mapper);
  }

  private AbilityProfileService(Supplier<AbilityProfileMapper> mapperSupplier) {
    this.mapperSupplier = mapperSupplier;
  }

  public AbilityProfileResponse getProfile(long userId) {
    return getProfile(userId, ProblemLocale.DEFAULT);
  }

  public AbilityProfileResponse getProfile(long userId, ProblemLocale locale) {
    List<AbilityTagScoreResponse> tags = tagScores(userId, locale);
    return new AbilityProfileResponse(tags, scope());
  }

  public AbilityHomeSummaryResponse getHomeSummary(long userId, ProblemLocale locale) {
    List<AbilityTagScoreResponse> tags = tagScores(userId, locale);
    if (tags.isEmpty()) {
      return new AbilityHomeSummaryResponse(zeroScore(), null, null);
    }

    AbilityTagScoreResponse currentStrength = tags.stream()
        .sorted(Comparator.comparing(AbilityTagScoreResponse::abilityScore).reversed()
            .thenComparing(AbilityTagScoreResponse::reviewedProblemCount, Comparator.reverseOrder()))
        .findFirst()
        .orElseThrow();
    AbilityTagScoreResponse nextBreakthrough = tags.stream()
        .filter(tag -> tag.reviewedProblemCount() == 0)
        .findFirst()
        .orElseGet(() -> tags.stream()
            .filter(tag -> !tag.tag().equals(currentStrength.tag()))
            .findFirst()
            .orElse(currentStrength));

    return new AbilityHomeSummaryResponse(
        averageAbilityScore(tags),
        toHomeSummaryTag(currentStrength),
        toHomeSummaryTag(nextBreakthrough));
  }

  private List<AbilityTagScoreResponse> tagScores(long userId, ProblemLocale locale) {
    return mapper()
        .findCommonTagScores(userId, AbilityProfileConstants.MIN_PROBLEM_COUNT, locale.value())
        .stream()
        .map(this::toResponse)
        .toList();
  }

  private BigDecimal averageAbilityScore(List<AbilityTagScoreResponse> tags) {
    return tags.stream()
        .map(AbilityTagScoreResponse::abilityScore)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .divide(BigDecimal.valueOf(tags.size()), AbilityProfileConstants.SCORE_SCALE, RoundingMode.HALF_UP);
  }

  private AbilityHomeSummaryTagResponse toHomeSummaryTag(AbilityTagScoreResponse tag) {
    return new AbilityHomeSummaryTagResponse(tag.label(), tag.reviewedProblemCount());
  }

  private AbilityTagScoreResponse toResponse(AbilityTagScoreRow row) {
    BigDecimal rawAverageScore = row.rawAverageScore() == null ? BigDecimal.ZERO : row.rawAverageScore();
    return new AbilityTagScoreResponse(
        row.tag(),
        row.label(),
        row.problemCount(),
        row.reviewedProblemCount(),
        score(rawAverageScore),
        abilityScore(rawAverageScore, row.reviewedProblemCount()));
  }

  private BigDecimal abilityScore(BigDecimal rawAverageScore, long reviewedProblemCount) {
    if (reviewedProblemCount <= 0) {
      return zeroScore();
    }
    BigDecimal confidence = BigDecimal.valueOf(reviewedProblemCount)
        .divide(
            BigDecimal.valueOf(reviewedProblemCount + AbilityProfileConstants.CONSERVATIVE_WEIGHT),
            8,
            RoundingMode.HALF_UP);
    return rawAverageScore.multiply(confidence)
        .setScale(AbilityProfileConstants.SCORE_SCALE, RoundingMode.HALF_UP);
  }

  private BigDecimal score(BigDecimal value) {
    if (value == null) {
      return zeroScore();
    }
    return value.setScale(AbilityProfileConstants.SCORE_SCALE, RoundingMode.HALF_UP);
  }

  private BigDecimal zeroScore() {
    return BigDecimal.ZERO.setScale(AbilityProfileConstants.SCORE_SCALE, RoundingMode.HALF_UP);
  }

  private AbilityProfileScopeResponse scope() {
    return new AbilityProfileScopeResponse(
        AbilityProfileConstants.MIN_PROBLEM_COUNT,
        AbilityProfileConstants.SCORE_SCALE,
        AbilityProfileConstants.LATEST_REVIEW_ONLY,
        AbilityProfileConstants.CONSERVATIVE_WEIGHT);
  }

  private AbilityProfileMapper mapper() {
    AbilityProfileMapper mapper = mapperSupplier.get();
    if (mapper == null) {
      throw new AbilityProfileMapperUnavailableException();
    }
    return mapper;
  }

  public static class AbilityProfileMapperUnavailableException extends RuntimeException {
    public AbilityProfileMapperUnavailableException() {
      super("Ability profile mapper is unavailable. Enable the local datasource profile before using ability APIs.");
    }
  }
}
