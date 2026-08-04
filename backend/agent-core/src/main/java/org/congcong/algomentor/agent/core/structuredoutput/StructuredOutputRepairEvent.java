package org.congcong.algomentor.agent.core.structuredoutput;

/** structured output 有限 repair 的低敏生命周期事件。 */
public record StructuredOutputRepairEvent(
    int stepIndex,
    int repairAttempt,
    int maxRepairAttempts,
    StructuredOutputValidationError.Type failureType,
    Outcome outcome
) {

  public StructuredOutputRepairEvent {
    if (stepIndex < 1) {
      throw new IllegalArgumentException("Structured output repair step index must be positive");
    }
    if (repairAttempt < 1 || repairAttempt > maxRepairAttempts) {
      throw new IllegalArgumentException(
          "Structured output repair attempt must be between 1 and max repair attempts");
    }
    if (failureType == null) {
      throw new IllegalArgumentException("Structured output repair failure type must not be null");
    }
    if (outcome == null) {
      throw new IllegalArgumentException("Structured output repair outcome must not be null");
    }
  }

  public enum Outcome {
    TRIGGERED,
    SUCCEEDED,
    FAILED
  }
}
