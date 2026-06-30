package org.congcong.algomentor.identity.event;

import org.springframework.context.ApplicationEventPublisher;

public class SpringIdentityEventPublisher implements IdentityEventPublisher {

  private final ApplicationEventPublisher publisher;

  public SpringIdentityEventPublisher(ApplicationEventPublisher publisher) {
    this.publisher = publisher;
  }

  @Override
  public void publish(IdentityUserStatusChangedEvent event) {
    publisher.publishEvent(event);
  }
}
