package org.congcong.algomentor.mentor.application.profile.review.snapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewFact;

/** 从全量正式 Review 构建有界的画像事实快照。 */
public final class LearnerReviewFactSnapshotBuilder {

  private static final int MAX_FINDING_ITEMS_PER_ATTEMPT = 4;

  private final LearnerReviewFactSnapshotPolicy policy;

  public LearnerReviewFactSnapshotBuilder() {
    this(LearnerReviewFactSnapshotPolicy.defaults());
  }

  public LearnerReviewFactSnapshotBuilder(LearnerReviewFactSnapshotPolicy policy) {
    this.policy = Objects.requireNonNull(policy, "policy");
  }

  public LearnerReviewFactSnapshot build(List<LearnerMemoryCodeReviewFact> facts) {
    List<LearnerMemoryCodeReviewFact> ordered = normalizedFacts(facts);
    Map<String, List<LearnerMemoryCodeReviewFact>> byProblem = new LinkedHashMap<>();
    for (LearnerMemoryCodeReviewFact fact : ordered) {
      byProblem.computeIfAbsent(fact.problemSlug(), ignored -> new ArrayList<>()).add(fact);
    }

    List<ProblemReviewTrajectory> trajectories = byProblem.values().stream().map(this::trajectory).toList();
    int passed = (int) ordered.stream().filter(LearnerMemoryCodeReviewFact::passed).count();
    int failed = ordered.size() - passed;
    int recovered = trajectories.stream().mapToInt(ProblemReviewTrajectory::recoveredFailureCount).sum();
    int unresolved = trajectories.stream().mapToInt(ProblemReviewTrajectory::unresolvedFailureCount).sum();
    List<ReviewFactSnapshotAttempt> latestAttempts = trajectories.stream()
        .map(ProblemReviewTrajectory::latestAttempt).toList();
    int latestPassed = (int) latestAttempts.stream().filter(ReviewFactSnapshotAttempt::passed).count();
    int firstPassed = (int) byProblem.values().stream().filter(attempts -> attempts.get(0).passed()).count();
    Instant earliest = ordered.get(0).createdAt();
    Instant latest = ordered.get(ordered.size() - 1).createdAt();

    return new LearnerReviewFactSnapshot(
        new LearnerReviewFactSnapshot.Coverage(
            ordered.size(), trajectories.size(), earliest, latest, historyDepth(trajectories.size(), earliest, latest)),
        new LearnerReviewFactSnapshot.Overall(
            passed,
            failed,
            new LearnerReviewFactSnapshot.PassCount(latestPassed, trajectories.size()),
            new LearnerReviewFactSnapshot.PassCount(firstPassed, trajectories.size()),
            failed,
            recovered,
            unresolved,
            average(latestAttempts.stream().map(ReviewFactSnapshotAttempt::score).toList())),
        trajectories,
        tagFacts(ordered, trajectories, byProblem));
  }

  private ProblemReviewTrajectory trajectory(List<LearnerMemoryCodeReviewFact> orderedAttempts) {
    int failed = (int) orderedAttempts.stream().filter(fact -> !fact.passed()).count();
    int recovered = recoveredFailures(orderedAttempts);
    List<Long> tagIds = orderedAttempts.stream().flatMap(fact -> fact.affectedTagIds().stream())
        .distinct().sorted().toList();
    int retainedStart = Math.max(0, orderedAttempts.size() - policy.retainedAttemptsPerProblem());
    List<ReviewFactSnapshotAttempt> attempts = orderedAttempts.subList(retainedStart, orderedAttempts.size()).stream()
        .map(this::attempt).toList();
    return new ProblemReviewTrajectory(
        orderedAttempts.get(0).problemSlug(),
        tagIds,
        orderedAttempts.size(),
        orderedAttempts.size() - failed,
        failed,
        failed,
        recovered,
        failed - recovered,
        retainedStart,
        attempts,
        currentStatus(orderedAttempts));
  }

  private List<TagReviewFacts> tagFacts(
      List<LearnerMemoryCodeReviewFact> facts,
      List<ProblemReviewTrajectory> trajectories,
      Map<String, List<LearnerMemoryCodeReviewFact>> byProblem
  ) {
    Set<Long> tagIds = facts.stream().flatMap(fact -> fact.affectedTagIds().stream())
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    List<TagReviewFacts> result = new ArrayList<>();
    for (Long tagId : tagIds.stream().sorted().toList()) {
      List<ProblemReviewTrajectory> tagged = trajectories.stream().filter(trajectory -> trajectory.tagIds().contains(tagId))
          .toList();
      int latestPassed = (int) tagged.stream().filter(trajectory -> trajectory.latestAttempt().passed()).count();
      int firstPassed = (int) tagged.stream().filter(trajectory -> byProblem.get(trajectory.problemSlug()).get(0).passed())
          .count();
      int reviewCount = (int) facts.stream().filter(fact -> fact.affectedTagIds().contains(tagId)).count();
      result.add(new TagReviewFacts(
          tagId,
          tagged.size(),
          reviewCount,
          new LearnerReviewFactSnapshot.PassCount(latestPassed, tagged.size()),
          new LearnerReviewFactSnapshot.PassCount(firstPassed, tagged.size()),
          tagged.stream().mapToInt(ProblemReviewTrajectory::functionalFailureCount).sum(),
          tagged.stream().mapToInt(ProblemReviewTrajectory::recoveredFailureCount).sum(),
          tagged.stream().mapToInt(ProblemReviewTrajectory::unresolvedFailureCount).sum()));
    }
    return List.copyOf(result);
  }

  private ReviewFactSnapshotAttempt attempt(LearnerMemoryCodeReviewFact fact) {
    return new ReviewFactSnapshotAttempt(
        fact.reviewId(), fact.versionNo(), fact.passed(), fact.totalScore(), normalizedFindingSummary(fact), fact.createdAt());
  }

  private String normalizedFindingSummary(LearnerMemoryCodeReviewFact fact) {
    List<String> findings = new ArrayList<>();
    appendFindings(findings, "finding", fact.deductionReasons());
    appendFindings(findings, "next", fact.improvementSuggestions());
    String summary = String.join("; ", findings);
    return summary.length() <= policy.findingSummaryMaxChars()
        ? summary
        : summary.substring(0, policy.findingSummaryMaxChars() - 3).trim() + "...";
  }

  private void appendFindings(List<String> target, String label, List<String> values) {
    if (values == null) {
      return;
    }
    for (String value : values) {
      String normalized = normalizeFinding(value);
      if (!normalized.isEmpty()) {
        target.add(label + ": " + normalized);
      }
      if (target.size() == MAX_FINDING_ITEMS_PER_ATTEMPT) {
        return;
      }
    }
  }

  private static String normalizeFinding(String value) {
    if (value == null) {
      return "";
    }
    return value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "")
        .replaceAll("\\s+", " ")
        .trim();
  }

  private static int recoveredFailures(List<LearnerMemoryCodeReviewFact> attempts) {
    int recovered = 0;
    boolean laterPassed = false;
    for (int index = attempts.size() - 1; index >= 0; index--) {
      LearnerMemoryCodeReviewFact attempt = attempts.get(index);
      if (!attempt.passed() && laterPassed) {
        recovered++;
      }
      laterPassed |= attempt.passed();
    }
    return recovered;
  }

  private static ProblemReviewTrajectory.CurrentStatus currentStatus(List<LearnerMemoryCodeReviewFact> attempts) {
    LearnerMemoryCodeReviewFact latest = attempts.get(attempts.size() - 1);
    if (!latest.passed()) {
      return ProblemReviewTrajectory.CurrentStatus.ACTIVE_RISK;
    }
    boolean failed = attempts.stream().anyMatch(attempt -> !attempt.passed());
    if (!failed) {
      return ProblemReviewTrajectory.CurrentStatus.PASSED_FIRST_ATTEMPT;
    }
    return attempts.get(0).passed()
        ? ProblemReviewTrajectory.CurrentStatus.RECOVERED_AFTER_REGRESSION
        : ProblemReviewTrajectory.CurrentStatus.RECOVERED;
  }

  private LearnerReviewFactSnapshot.HistoryDepth historyDepth(
      int problemCount, Instant earliest, Instant latest) {
    Duration observed = Duration.between(earliest, latest);
    return problemCount >= policy.establishedMinimumProblemCount()
        && observed.compareTo(policy.establishedMinimumObservationPeriod()) >= 0
        ? LearnerReviewFactSnapshot.HistoryDepth.ESTABLISHED
        : LearnerReviewFactSnapshot.HistoryDepth.EARLY_SAMPLE;
  }

  private static BigDecimal average(List<BigDecimal> scores) {
    BigDecimal total = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    return total.divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP).stripTrailingZeros();
  }

  private static List<LearnerMemoryCodeReviewFact> normalizedFacts(List<LearnerMemoryCodeReviewFact> facts) {
    if (facts == null || facts.isEmpty() || facts.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("Learner review fact snapshot requires formal reviews");
    }
    Set<Long> ids = new LinkedHashSet<>();
    for (LearnerMemoryCodeReviewFact fact : facts) {
      if (!ids.add(fact.reviewId())) {
        throw new IllegalArgumentException("Learner review fact snapshot review ids must be distinct");
      }
    }
    return facts.stream().sorted(Comparator.comparing(LearnerMemoryCodeReviewFact::createdAt)
        .thenComparingInt(LearnerMemoryCodeReviewFact::versionNo)
        .thenComparingLong(LearnerMemoryCodeReviewFact::reviewId)).toList();
  }
}
