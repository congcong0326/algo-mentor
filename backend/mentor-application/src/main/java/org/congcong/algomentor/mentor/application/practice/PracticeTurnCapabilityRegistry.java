package org.congcong.algomentor.mentor.application.practice;

import java.util.List;

public class PracticeTurnCapabilityRegistry {

  private final List<PracticeTurnCapability> capabilities;

  public PracticeTurnCapabilityRegistry(List<PracticeTurnCapability> capabilities) {
    this.capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
  }

  public List<PracticeTurnCapability> capabilities() {
    return capabilities;
  }
}
