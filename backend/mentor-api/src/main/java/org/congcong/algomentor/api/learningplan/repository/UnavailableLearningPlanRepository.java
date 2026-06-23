package org.congcong.algomentor.api.learningplan.repository;

import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPage;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepositoryUnavailableException;

public class UnavailableLearningPlanRepository implements LearningPlanDraftRepository, LearningPlanRepository {

  @Override
  public LearningPlanDraft save(LearningPlanDraft draft) {
    throw unavailable();
  }

  @Override
  public Optional<LearningPlanDraft> findDraftByIdForUser(long draftId, long userId) {
    throw unavailable();
  }

  @Override
  public LearningPlan save(LearningPlan plan) {
    throw unavailable();
  }

  @Override
  public List<LearningPlan> findByUserId(long userId) {
    throw unavailable();
  }

  @Override
  public LearningPlanPage findPageByUserId(long userId, int page, int pageSize) {
    throw unavailable();
  }

  @Override
  public Optional<LearningPlan> findPlanByIdForUser(long planId, long userId) {
    throw unavailable();
  }

  @Override
  public void clearConfirmedPlanReferences(long userId, long planId) {
    throw unavailable();
  }

  @Override
  public boolean deletePlanByIdForUser(long planId, long userId) {
    throw unavailable();
  }

  private LearningPlanRepositoryUnavailableException unavailable() {
    return new LearningPlanRepositoryUnavailableException();
  }
}
