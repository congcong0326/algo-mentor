package org.congcong.algomentor.mentor.application.learningplan.proposal.revision;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanDraftRevision;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRepository;

/** 从当前受信修订上下文解析来源无关的冻结计划基线。 */
public final class LearningPlanRevisionBaselineResolver {

  private final LearningPlanProposalRepository proposalRepository;

  public LearningPlanRevisionBaselineResolver(LearningPlanProposalRepository proposalRepository) {
    this.proposalRepository = Objects.requireNonNull(proposalRepository, "Proposal repository must not be null");
  }

  public ResolvedBaseline resolve(
      LearningPlanDraftRevision revision,
      long userId,
      String requestedBaseline
  ) {
    Objects.requireNonNull(revision, "Learning plan revision must not be null");
    String baseline = normalize(requestedBaseline);
    Optional<LearningPlanRevisionBaseSnapshot> snapshot = switch (baseline) {
      case LearningPlanRevisionToolContracts.BASELINE_CURRENT_REVISION -> Optional.of(
          new LearningPlanRevisionBaseSnapshot(revision.baseBrief(), revision.basePlan()));
      case LearningPlanRevisionToolContracts.BASELINE_ORIGINAL_DRAFT -> proposalRepository.findDraftOriginForUser(
          revision.draftId(), userId);
      case LearningPlanRevisionToolContracts.BASELINE_PREVIOUS_REVISION ->
          proposalRepository.findPreviousDraftRevisionBaseForUser(
              revision.proposalGroupId(), revision.revisionNo(), userId);
      default -> throw new BaselineResolutionException(
          "INVALID_BASELINE",
          "baseline",
          "不支持的学习计划修订基线：" + baseline);
    };
    return new ResolvedBaseline(
        baseline,
        snapshot.orElseThrow(() -> unavailable(baseline)));
  }

  public List<BaselineOption> options(LearningPlanDraftRevision revision, long userId) {
    Objects.requireNonNull(revision, "Learning plan revision must not be null");
    return List.of(
        new BaselineOption(
            LearningPlanRevisionToolContracts.BASELINE_CURRENT_REVISION,
            true,
            "本次修订开始时冻结的当前草稿。"),
        new BaselineOption(
            LearningPlanRevisionToolContracts.BASELINE_ORIGINAL_DRAFT,
            proposalRepository.findDraftOriginForUser(revision.draftId(), userId).isPresent(),
            "草稿第一次形成完整计划时冻结的原始版本，与模板或 AI 来源无关。"),
        new BaselineOption(
            LearningPlanRevisionToolContracts.BASELINE_PREVIOUS_REVISION,
            proposalRepository.findPreviousDraftRevisionBaseForUser(
                revision.proposalGroupId(), revision.revisionNo(), userId).isPresent(),
            "上一次修订开始前的草稿，用于撤销上一轮修订。"));
  }

  private String normalize(String requestedBaseline) {
    if (requestedBaseline == null || requestedBaseline.isBlank()) {
      return LearningPlanRevisionToolContracts.BASELINE_CURRENT_REVISION;
    }
    return requestedBaseline.trim().toUpperCase(Locale.ROOT);
  }

  private BaselineResolutionException unavailable(String baseline) {
    return new BaselineResolutionException(
        "BASELINE_NOT_AVAILABLE",
        "baseline",
        "当前草稿没有可用的学习计划修订基线：" + baseline);
  }

  public record ResolvedBaseline(
      String baseline,
      LearningPlanRevisionBaseSnapshot snapshot
  ) {

    public ResolvedBaseline {
      if (baseline == null || baseline.isBlank() || snapshot == null) {
        throw new IllegalArgumentException("Resolved learning plan revision baseline must be complete");
      }
    }
  }

  public record BaselineOption(
      String baseline,
      boolean available,
      String description
  ) {
  }

  public static final class BaselineResolutionException extends RuntimeException {

    private final String code;
    private final String path;

    private BaselineResolutionException(String code, String path, String message) {
      super(message);
      this.code = code;
      this.path = path;
    }

    public String code() {
      return code;
    }

    public String path() {
      return path;
    }
  }
}
