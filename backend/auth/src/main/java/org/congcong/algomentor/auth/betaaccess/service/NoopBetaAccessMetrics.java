package org.congcong.algomentor.auth.betaaccess.service;

public class NoopBetaAccessMetrics implements BetaAccessMetrics {

  @Override
  public void recordSessionRevocationFailure() {
  }
}
