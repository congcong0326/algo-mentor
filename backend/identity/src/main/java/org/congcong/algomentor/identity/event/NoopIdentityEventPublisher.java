package org.congcong.algomentor.identity.event;

public class NoopIdentityEventPublisher implements IdentityEventPublisher {

  @Override
  public void publish(IdentityUserStatusChangedEvent event) {
  }
}
