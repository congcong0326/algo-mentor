package org.congcong.algomentor.mentor.application.profile.recall;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.congcong.algomentor.mentor.application.practice.TrustedProblemTag;
import org.congcong.algomentor.mentor.application.practice.TrustedProblemTagCatalog;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileEntry;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileEntryKind;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 在 Agent loop 外执行有界、只读的画像召回；任意读取失败均降级为空快照。 */
public class LearnerProfileRecallService {

  private static final Logger log = LoggerFactory.getLogger(LearnerProfileRecallService.class);

  private final LearnerProfilePolicyResolver policyResolver;
  private final LearnerProfileQueryService queryService;
  private final TrustedProblemTagCatalog trustedProblemTagCatalog;

  public LearnerProfileRecallService(
      LearnerProfilePolicyResolver policyResolver,
      LearnerProfileQueryService queryService,
      TrustedProblemTagCatalog trustedProblemTagCatalog
  ) {
    this.policyResolver = Objects.requireNonNull(policyResolver, "policyResolver must not be null");
    this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
    this.trustedProblemTagCatalog = Objects.requireNonNull(
        trustedProblemTagCatalog, "trustedProblemTagCatalog must not be null");
  }

  public LearnerProfileRecallSnapshot recall(long userId, String scenario, String problemSlug) {
    LearnerProfilePolicy policy = policyResolver.resolve(scenario);
    if (!policy.enabled() || userId < 1) {
      return LearnerProfileRecallSnapshot.empty();
    }
    try {
      List<LearnerProfileEntry> declared = orderedDimensions(
          userId, LearnerProfileEntryKind.DECLARED_FACT, policy.declaredDimensions());
      List<LearnerProfileEntry> general = orderedDimensions(
          userId, LearnerProfileEntryKind.GENERAL_OBSERVATION, policy.generalDimensions());
      List<LearnerProfileRecallSnapshot.TagAssessment> tagAssessments = policy.includeCurrentProblemTags()
          ? currentProblemTagAssessments(userId, problemSlug)
          : List.of();
      LearnerProfileRecallSnapshot snapshot = new LearnerProfileRecallSnapshot(declared, general, tagAssessments);
      log.info("Learner profile recall completed. scenario={} entryCount={} declaredCount={} tagCount={} generalCount={}",
          scenario, snapshot.entryCount(), declared.size(), tagAssessments.size(), general.size());
      return snapshot;
    } catch (RuntimeException exception) {
      log.warn("Learner profile recall failed and was omitted. scenario={} exceptionType={}",
          scenario, exception.getClass().getSimpleName());
      return LearnerProfileRecallSnapshot.empty();
    }
  }

  private List<LearnerProfileEntry> orderedDimensions(
      long userId,
      LearnerProfileEntryKind kind,
      List<org.congcong.algomentor.mentor.application.profile.LearnerProfileDimension> dimensions
  ) {
    Map<org.congcong.algomentor.mentor.application.profile.LearnerProfileDimension, Integer> order = order(dimensions);
    return queryService.findCurrentByDimensions(userId, kind, dimensions).stream()
        .filter(entry -> order.containsKey(entry.identity().dimension()))
        .sorted(Comparator.comparingInt(entry -> order.get(entry.identity().dimension())))
        .toList();
  }

  private List<LearnerProfileRecallSnapshot.TagAssessment> currentProblemTagAssessments(long userId, String problemSlug) {
    List<TrustedProblemTag> tags = trustedProblemTagCatalog.findByProblemSlug(problemSlug);
    if (tags.isEmpty()) {
      return List.of();
    }
    Map<Long, LearnerProfileEntry> entriesByTagId = queryService.findCurrentByTagIds(
        userId, tags.stream().map(TrustedProblemTag::tagId).toList()).stream()
        .collect(Collectors.toMap(entry -> entry.identity().tagId(), Function.identity(), (left, right) -> left));
    return tags.stream().map(tag -> {
      LearnerProfileEntry entry = entriesByTagId.get(tag.tagId());
      return entry == null ? null : new LearnerProfileRecallSnapshot.TagAssessment(tag, entry);
    }).filter(java.util.Objects::nonNull).toList();
  }

  private <T> Map<T, Integer> order(List<T> values) {
    return java.util.stream.IntStream.range(0, values.size()).boxed()
        .collect(Collectors.toMap(values::get, Function.identity()));
  }
}
