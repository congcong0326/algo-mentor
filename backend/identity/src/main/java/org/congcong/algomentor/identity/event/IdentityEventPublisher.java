package org.congcong.algomentor.identity.event;

public interface IdentityEventPublisher {

  void publish(IdentityUserStatusChangedEvent event);
}
