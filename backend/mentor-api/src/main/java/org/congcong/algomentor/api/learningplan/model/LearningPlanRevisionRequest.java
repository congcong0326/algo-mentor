package org.congcong.algomentor.api.learningplan.model;

public record LearningPlanRevisionRequest(String instruction) {

  public String normalizedInstruction() {
    return instruction == null ? "" : instruction.trim();
  }
}
