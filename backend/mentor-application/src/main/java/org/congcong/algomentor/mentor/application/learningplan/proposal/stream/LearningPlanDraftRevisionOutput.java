package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

/** 模型完成编译 Tool 后返回的有界终态引用。 */
public record LearningPlanDraftRevisionOutput(
    String status,
    String artifactRef
) {
}
