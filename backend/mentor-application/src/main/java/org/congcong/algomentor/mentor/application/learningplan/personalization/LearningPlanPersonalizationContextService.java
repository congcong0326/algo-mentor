package org.congcong.algomentor.mentor.application.learningplan.personalization;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract.Dimension;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract.Kind;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract.RevisionStatus;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;

/** 按固定优先级组装并裁剪单次学习计划个性化上下文。 */
public final class LearningPlanPersonalizationContextService {

  private final LearningPlanPersonalizationDataProvider provider;
  private final LearningPlanPersonalizationPromptRenderer renderer;
  private final Clock clock;
  private final LearningPlanPersonalizationMetrics metrics;

  public LearningPlanPersonalizationContextService(LearningPlanPersonalizationDataProvider provider) {
    this(provider, new LearningPlanPersonalizationPromptRenderer(), Clock.systemUTC());
  }

  public LearningPlanPersonalizationContextService(
      LearningPlanPersonalizationDataProvider provider,
      LearningPlanPersonalizationPromptRenderer renderer,
      Clock clock
  ) {
    this(provider, renderer, clock, LearningPlanPersonalizationMetrics.NOOP);
  }

  public LearningPlanPersonalizationContextService(
      LearningPlanPersonalizationDataProvider provider,
      LearningPlanPersonalizationPromptRenderer renderer,
      Clock clock,
      LearningPlanPersonalizationMetrics metrics
  ) {
    this.provider = provider == null ? LearningPlanPersonalizationDataProvider.empty() : provider;
    this.renderer = renderer == null ? new LearningPlanPersonalizationPromptRenderer() : renderer;
    this.clock = clock == null ? Clock.systemUTC() : clock;
    this.metrics = metrics == null ? LearningPlanPersonalizationMetrics.NOOP : metrics;
  }

  public LearningPlanPersonalizationSnapshot snapshot(long userId, boolean enabled) {
    return snapshot(userId, enabled, LearningPlanPersonalizationScenario.DRAFT);
  }

  public LearningPlanPersonalizationSnapshot snapshot(
      long userId,
      boolean enabled,
      LearningPlanPersonalizationScenario scenario
  ) {
    LearningPlanPersonalizationScenario effectiveScenario = scenario == null
        ? LearningPlanPersonalizationScenario.DRAFT
        : scenario;
    long startedAt = System.nanoTime();
    Instant generatedAt = clock.instant();
    if (!enabled) {
      LearningPlanPersonalizationSnapshot snapshot = disabledSnapshot(generatedAt);
      recordMetrics(effectiveScenario, snapshot, elapsedSince(startedAt));
      return snapshot;
    }
    requireUserId(userId);

    EnumMap<LearningPlanPersonalizationSource, LearningPlanPersonalizationSourceOutcome> outcomes =
        new EnumMap<>(LearningPlanPersonalizationSource.class);
    List<LearnerMemoryClaimRevision> claims = loadClaims(userId, outcomes);
    List<LearningPlanAbilityTagSummary> abilityTags = loadAbilityTags(userId, outcomes);
    LearningPlanActiveProgressSummary activePlan = loadActivePlan(userId, outcomes);
    LearningPlanReviewLoadSummary reviewLoad = loadReviewLoad(userId, outcomes);

    List<String> declaredFacts = selectDeclaredFacts(claims);
    List<String> generalObservations = selectGeneralObservations(claims);
    List<LearningPlanAbilityTagSummary> weakTags = selectWeakTags(abilityTags);
    List<LearningPlanAbilityTagSummary> strongTags = selectStrongTags(abilityTags, weakTags);

    Selection selection = new Selection(generatedAt);
    boolean trimmed = appendInPriorityOrder(
        selection,
        declaredFacts,
        weakTags,
        activePlan,
        reviewLoad,
        generalObservations,
        strongTags);
    LearningPlanPersonalizationContext context = selection.context();
    String promptText = renderer.render(context);
    LearningPlanPersonalizationSnapshot snapshot = new LearningPlanPersonalizationSnapshot(
        true,
        context,
        promptText,
        renderer.estimateTokens(promptText),
        trimmed,
        outcomes);
    recordMetrics(effectiveScenario, snapshot, elapsedSince(startedAt));
    return snapshot;
  }

  private void recordMetrics(
      LearningPlanPersonalizationScenario scenario,
      LearningPlanPersonalizationSnapshot snapshot,
      Duration duration
  ) {
    snapshot.sourceOutcomes().forEach(metrics::recordSourceLoad);
    metrics.recordContextBuild(
        scenario,
        snapshot.enabled(),
        buildOutcome(snapshot),
        snapshot.trimmed(),
        duration);
    metrics.recordEntryCount(scenario, entryCount(snapshot.context()));
    metrics.recordTokenEstimate(scenario, snapshot.tokenEstimate());
  }

  private LearningPlanPersonalizationBuildOutcome buildOutcome(
      LearningPlanPersonalizationSnapshot snapshot
  ) {
    if (!snapshot.enabled()) {
      return LearningPlanPersonalizationBuildOutcome.DISABLED;
    }
    if (snapshot.sourceOutcomes().values().stream()
        .anyMatch(outcome -> outcome == LearningPlanPersonalizationSourceOutcome.ERROR)) {
      return LearningPlanPersonalizationBuildOutcome.PARTIAL_FAILURE;
    }
    if (snapshot.context().isEmpty()) {
      return LearningPlanPersonalizationBuildOutcome.EMPTY;
    }
    return LearningPlanPersonalizationBuildOutcome.SUCCESS;
  }

  private int entryCount(LearningPlanPersonalizationContext context) {
    return context.declaredFacts().size()
        + context.generalObservations().size()
        + context.weakTags().size()
        + context.strongTags().size()
        + (context.activePlan() == null ? 0 : 1)
        + (context.reviewLoad() == null ? 0 : 1);
  }

  private Duration elapsedSince(long startedAt) {
    return Duration.ofNanos(Math.max(0L, System.nanoTime() - startedAt));
  }

  private LearningPlanPersonalizationSnapshot disabledSnapshot(Instant generatedAt) {
    EnumMap<LearningPlanPersonalizationSource, LearningPlanPersonalizationSourceOutcome> outcomes =
        new EnumMap<>(LearningPlanPersonalizationSource.class);
    for (LearningPlanPersonalizationSource source : LearningPlanPersonalizationSource.values()) {
      outcomes.put(source, LearningPlanPersonalizationSourceOutcome.DISABLED);
    }
    LearningPlanPersonalizationContext context = emptyContext(generatedAt);
    return new LearningPlanPersonalizationSnapshot(false, context, "", 0, false, outcomes);
  }

  private List<LearnerMemoryClaimRevision> loadClaims(
      long userId,
      EnumMap<LearningPlanPersonalizationSource, LearningPlanPersonalizationSourceOutcome> outcomes
  ) {
    try {
      List<LearnerMemoryClaimRevision> claims = copyNonNull(provider.findActiveClaims(userId));
      outcomes.put(
          LearningPlanPersonalizationSource.ACTIVE_CLAIMS,
          claims.isEmpty() ? LearningPlanPersonalizationSourceOutcome.EMPTY
              : LearningPlanPersonalizationSourceOutcome.SUCCESS);
      return claims;
    } catch (RuntimeException exception) {
      outcomes.put(LearningPlanPersonalizationSource.ACTIVE_CLAIMS, LearningPlanPersonalizationSourceOutcome.ERROR);
      return List.of();
    }
  }

  private List<LearningPlanAbilityTagSummary> loadAbilityTags(
      long userId,
      EnumMap<LearningPlanPersonalizationSource, LearningPlanPersonalizationSourceOutcome> outcomes
  ) {
    try {
      List<LearningPlanAbilityTagSummary> tags = copyNonNull(provider.findAbilityTagSummaries(userId));
      outcomes.put(
          LearningPlanPersonalizationSource.ABILITY_TAGS,
          tags.isEmpty() ? LearningPlanPersonalizationSourceOutcome.EMPTY
              : LearningPlanPersonalizationSourceOutcome.SUCCESS);
      return tags;
    } catch (RuntimeException exception) {
      outcomes.put(LearningPlanPersonalizationSource.ABILITY_TAGS, LearningPlanPersonalizationSourceOutcome.ERROR);
      return List.of();
    }
  }

  private LearningPlanActiveProgressSummary loadActivePlan(
      long userId,
      EnumMap<LearningPlanPersonalizationSource, LearningPlanPersonalizationSourceOutcome> outcomes
  ) {
    try {
      LearningPlanActiveProgressSummary activePlan = optional(provider.findActivePlanProgress(userId)).orElse(null);
      outcomes.put(
          LearningPlanPersonalizationSource.ACTIVE_PLAN,
          activePlan == null ? LearningPlanPersonalizationSourceOutcome.EMPTY
              : LearningPlanPersonalizationSourceOutcome.SUCCESS);
      return activePlan;
    } catch (RuntimeException exception) {
      outcomes.put(LearningPlanPersonalizationSource.ACTIVE_PLAN, LearningPlanPersonalizationSourceOutcome.ERROR);
      return null;
    }
  }

  private LearningPlanReviewLoadSummary loadReviewLoad(
      long userId,
      EnumMap<LearningPlanPersonalizationSource, LearningPlanPersonalizationSourceOutcome> outcomes
  ) {
    try {
      LearningPlanReviewLoadSummary reviewLoad = optional(provider.findReviewLoad(userId)).orElse(null);
      outcomes.put(
          LearningPlanPersonalizationSource.REVIEW_LOAD,
          reviewLoad == null ? LearningPlanPersonalizationSourceOutcome.EMPTY
              : LearningPlanPersonalizationSourceOutcome.SUCCESS);
      return reviewLoad;
    } catch (RuntimeException exception) {
      outcomes.put(LearningPlanPersonalizationSource.REVIEW_LOAD, LearningPlanPersonalizationSourceOutcome.ERROR);
      return null;
    }
  }

  private boolean appendInPriorityOrder(
      Selection selection,
      List<String> declaredFacts,
      List<LearningPlanAbilityTagSummary> weakTags,
      LearningPlanActiveProgressSummary activePlan,
      LearningPlanReviewLoadSummary reviewLoad,
      List<String> generalObservations,
      List<LearningPlanAbilityTagSummary> strongTags
  ) {
    List<Candidate> candidates = new ArrayList<>();
    declaredFacts.forEach(value -> candidates.add(new Candidate(CandidateType.DECLARED_FACT, value)));
    weakTags.forEach(value -> candidates.add(new Candidate(CandidateType.WEAK_TAG, value)));
    if (activePlan != null) {
      candidates.add(new Candidate(CandidateType.ACTIVE_PLAN, activePlan));
    }
    if (reviewLoad != null) {
      candidates.add(new Candidate(CandidateType.REVIEW_LOAD, reviewLoad));
    }
    generalObservations.forEach(value -> candidates.add(new Candidate(CandidateType.GENERAL_OBSERVATION, value)));
    strongTags.forEach(value -> candidates.add(new Candidate(CandidateType.STRONG_TAG, value)));

    for (Candidate candidate : candidates) {
      selection.add(candidate);
      String promptText = renderer.render(selection.context());
      if (renderer.estimateTokens(promptText) <= LearningPlanPersonalizationConstants.TOKEN_BUDGET) {
        continue;
      }
      selection.removeLast(candidate.type());
      return true;
    }
    return false;
  }

  private List<String> selectDeclaredFacts(List<LearnerMemoryClaimRevision> claims) {
    return claims.stream()
        .filter(this::isActiveDeclaredClaim)
        .sorted(Comparator
            .comparingInt((LearnerMemoryClaimRevision claim) -> declaredDimensionPriority(claim.scope().dimension()))
            .thenComparing(LearnerMemoryClaimRevision::updatedAt, Comparator.reverseOrder())
            .thenComparingLong(LearnerMemoryClaimRevision::id))
        .limit(LearningPlanPersonalizationConstants.DECLARED_FACT_LIMIT)
        .map(LearnerMemoryClaimRevision::claimText)
        .toList();
  }

  private List<String> selectGeneralObservations(List<LearnerMemoryClaimRevision> claims) {
    return claims.stream()
        .filter(this::isActiveGeneralClaim)
        .sorted(Comparator
            .comparingInt((LearnerMemoryClaimRevision claim) -> claim.evidenceGrade().ordinal()).reversed()
            .thenComparing(LearnerMemoryClaimRevision::updatedAt, Comparator.reverseOrder())
            .thenComparingLong(LearnerMemoryClaimRevision::id))
        .limit(LearningPlanPersonalizationConstants.GENERAL_OBSERVATION_LIMIT)
        .map(LearnerMemoryClaimRevision::claimText)
        .toList();
  }

  private List<LearningPlanAbilityTagSummary> selectWeakTags(List<LearningPlanAbilityTagSummary> tags) {
    return uniqueTags(tags.stream()
        .filter(tag -> tag.reviewedProblemCount() > 0)
        .sorted(Comparator
            .comparing(LearningPlanAbilityTagSummary::abilityScore)
            .thenComparing(LearningPlanAbilityTagSummary::reviewedProblemCount, Comparator.reverseOrder())
            .thenComparing(LearningPlanAbilityTagSummary::tag))
        .toList(), Set.of());
  }

  private List<LearningPlanAbilityTagSummary> selectStrongTags(
      List<LearningPlanAbilityTagSummary> tags,
      List<LearningPlanAbilityTagSummary> weakTags
  ) {
    Set<String> excludedTags = weakTags.stream().map(LearningPlanAbilityTagSummary::tag).collect(java.util.stream.Collectors.toSet());
    return uniqueTags(tags.stream()
        .filter(tag -> tag.reviewedProblemCount() > 0)
        .filter(tag -> !excludedTags.contains(tag.tag()))
        .sorted(Comparator
            .comparing(LearningPlanAbilityTagSummary::abilityScore, Comparator.reverseOrder())
            .thenComparing(LearningPlanAbilityTagSummary::reviewedProblemCount, Comparator.reverseOrder())
            .thenComparing(LearningPlanAbilityTagSummary::tag))
        .toList(), excludedTags);
  }

  private List<LearningPlanAbilityTagSummary> uniqueTags(
      List<LearningPlanAbilityTagSummary> tags,
      Set<String> initiallySeenTags
  ) {
    Set<String> seenTags = new HashSet<>(initiallySeenTags);
    List<LearningPlanAbilityTagSummary> selected = new ArrayList<>();
    for (LearningPlanAbilityTagSummary tag : tags) {
      if (seenTags.add(tag.tag())) {
        selected.add(tag);
      }
      if (selected.size() == LearningPlanPersonalizationConstants.ABILITY_TAG_LIMIT) {
        break;
      }
    }
    return List.copyOf(selected);
  }

  private boolean isActiveDeclaredClaim(LearnerMemoryClaimRevision claim) {
    return isActiveClaimOfKind(claim, Kind.DECLARED_FACT)
        && declaredDimensionPriority(claim.scope().dimension()) < Integer.MAX_VALUE;
  }

  private boolean isActiveGeneralClaim(LearnerMemoryClaimRevision claim) {
    if (!isActiveClaimOfKind(claim, Kind.GENERAL_OBSERVATION)) {
      return false;
    }
    return switch (claim.scope().dimension()) {
      case PROBLEM_SOLVING_APPROACH,
          IMPLEMENTATION_AND_ERROR_PATTERN,
          REVIEW_AND_GROWTH_PERFORMANCE -> true;
      default -> false;
    };
  }

  private boolean isActiveClaimOfKind(LearnerMemoryClaimRevision claim, Kind kind) {
    return claim != null
        && claim.status() == RevisionStatus.ACTIVE
        && claim.scope() != null
        && claim.scope().kind() == kind;
  }

  private int declaredDimensionPriority(Dimension dimension) {
    if (dimension == null) {
      return Integer.MAX_VALUE;
    }
    return switch (dimension) {
      case GOALS_AND_INTENTS -> 0;
      case TIME_AND_RESOURCE_CONSTRAINTS -> 1;
      case LEARNING_AND_INTERACTION_PREFERENCES -> 2;
      case SELF_ABILITY_ASSESSMENT -> 3;
      default -> Integer.MAX_VALUE;
    };
  }

  private static <T> List<T> copyNonNull(List<T> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    return values.stream().filter(java.util.Objects::nonNull).toList();
  }

  private static <T> Optional<T> optional(Optional<T> value) {
    return value == null ? Optional.empty() : value;
  }

  private static LearningPlanPersonalizationContext emptyContext(Instant generatedAt) {
    return new LearningPlanPersonalizationContext(List.of(), List.of(), List.of(), List.of(), null, null, generatedAt);
  }

  private static void requireUserId(long userId) {
    if (userId <= 0) {
      throw new IllegalArgumentException("user id must be positive");
    }
  }

  private enum CandidateType {
    DECLARED_FACT,
    WEAK_TAG,
    ACTIVE_PLAN,
    REVIEW_LOAD,
    GENERAL_OBSERVATION,
    STRONG_TAG
  }

  private record Candidate(CandidateType type, Object value) {
  }

  private static final class Selection {

    private final Instant generatedAt;
    private final List<String> declaredFacts = new ArrayList<>();
    private final List<String> generalObservations = new ArrayList<>();
    private final List<LearningPlanAbilityTagSummary> weakTags = new ArrayList<>();
    private final List<LearningPlanAbilityTagSummary> strongTags = new ArrayList<>();
    private LearningPlanActiveProgressSummary activePlan;
    private LearningPlanReviewLoadSummary reviewLoad;

    private Selection(Instant generatedAt) {
      this.generatedAt = generatedAt;
    }

    private void add(Candidate candidate) {
      switch (candidate.type()) {
        case DECLARED_FACT -> declaredFacts.add((String) candidate.value());
        case WEAK_TAG -> weakTags.add((LearningPlanAbilityTagSummary) candidate.value());
        case ACTIVE_PLAN -> activePlan = (LearningPlanActiveProgressSummary) candidate.value();
        case REVIEW_LOAD -> reviewLoad = (LearningPlanReviewLoadSummary) candidate.value();
        case GENERAL_OBSERVATION -> generalObservations.add((String) candidate.value());
        case STRONG_TAG -> strongTags.add((LearningPlanAbilityTagSummary) candidate.value());
      }
    }

    private void removeLast(CandidateType type) {
      switch (type) {
        case DECLARED_FACT -> declaredFacts.remove(declaredFacts.size() - 1);
        case WEAK_TAG -> weakTags.remove(weakTags.size() - 1);
        case ACTIVE_PLAN -> activePlan = null;
        case REVIEW_LOAD -> reviewLoad = null;
        case GENERAL_OBSERVATION -> generalObservations.remove(generalObservations.size() - 1);
        case STRONG_TAG -> strongTags.remove(strongTags.size() - 1);
      }
    }

    private LearningPlanPersonalizationContext context() {
      return new LearningPlanPersonalizationContext(
          declaredFacts,
          generalObservations,
          weakTags,
          strongTags,
          activePlan,
          reviewLoad,
          generatedAt);
    }
  }
}
