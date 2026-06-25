package org.congcong.algomentor.mentor.application.practice;

public interface PracticeTurnCapability {

  String capabilityName();

  PracticeTurnCapabilityResult afterTurn(PracticeTurnContext context, PracticeTurnClassification classification);
}
