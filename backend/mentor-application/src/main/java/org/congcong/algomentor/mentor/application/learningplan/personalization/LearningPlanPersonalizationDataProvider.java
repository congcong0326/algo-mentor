package org.congcong.algomentor.mentor.application.learningplan.personalization;

import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;

/** 学习计划个性化数据的唯一应用层聚合端口。 */
public interface LearningPlanPersonalizationDataProvider {

  List<LearnerMemoryClaimRevision> findActiveClaims(long userId);

  List<LearningPlanAbilityTagSummary> findAbilityTagSummaries(long userId);

  Optional<LearningPlanActiveProgressSummary> findActivePlanProgress(long userId);

  Optional<LearningPlanReviewLoadSummary> findReviewLoad(long userId);

  static LearningPlanPersonalizationDataProvider empty() {
    return EmptyProvider.INSTANCE;
  }

  enum EmptyProvider implements LearningPlanPersonalizationDataProvider {
    INSTANCE;

    @Override
    public List<LearnerMemoryClaimRevision> findActiveClaims(long userId) {
      return List.of();
    }

    @Override
    public List<LearningPlanAbilityTagSummary> findAbilityTagSummaries(long userId) {
      return List.of();
    }

    @Override
    public Optional<LearningPlanActiveProgressSummary> findActivePlanProgress(long userId) {
      return Optional.empty();
    }

    @Override
    public Optional<LearningPlanReviewLoadSummary> findReviewLoad(long userId) {
      return Optional.empty();
    }
  }
}
