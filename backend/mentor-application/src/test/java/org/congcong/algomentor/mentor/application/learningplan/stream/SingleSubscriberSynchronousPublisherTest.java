package org.congcong.algomentor.mentor.application.learningplan.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SingleSubscriberSynchronousPublisherTest {

  @Test
  void deliversEventsAndCompletionOnTheProducingThread() {
    SingleSubscriberSynchronousPublisher<String> publisher = new SingleSubscriberSynchronousPublisher<>();
    AtomicReference<Thread> eventThread = new AtomicReference<>();
    AtomicReference<Thread> completionThread = new AtomicReference<>();

    publisher.subscribe(new Flow.Subscriber<>() {
      @Override
      public void onSubscribe(Flow.Subscription subscription) {
        subscription.request(Long.MAX_VALUE);
      }

      @Override
      public void onNext(String event) {
        eventThread.set(Thread.currentThread());
      }

      @Override
      public void onError(Throwable throwable) {
      }

      @Override
      public void onComplete() {
        completionThread.set(Thread.currentThread());
      }
    });

    assertThat(publisher.emit("event")).isTrue();
    publisher.complete();

    assertThat(eventThread.get()).isSameAs(Thread.currentThread());
    assertThat(completionThread.get()).isSameAs(Thread.currentThread());
  }

  @Test
  void waitsForDemandBeforeDeliveringAnEvent() throws InterruptedException {
    SingleSubscriberSynchronousPublisher<String> publisher = new SingleSubscriberSynchronousPublisher<>();
    AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
    CountDownLatch producerStarted = new CountDownLatch(1);
    CountDownLatch eventDelivered = new CountDownLatch(1);
    AtomicBoolean emitted = new AtomicBoolean(false);

    publisher.subscribe(new Flow.Subscriber<>() {
      @Override
      public void onSubscribe(Flow.Subscription value) {
        subscription.set(value);
      }

      @Override
      public void onNext(String event) {
        eventDelivered.countDown();
      }

      @Override
      public void onError(Throwable throwable) {
      }

      @Override
      public void onComplete() {
      }
    });

    Thread producer = new Thread(() -> {
      producerStarted.countDown();
      emitted.set(publisher.emit("event"));
    }, "learning-plan-producer-test");
    producer.start();

    assertThat(producerStarted.await(1, TimeUnit.SECONDS)).isTrue();
    assertThat(eventDelivered.await(100, TimeUnit.MILLISECONDS)).isFalse();

    subscription.get().request(1);
    producer.join(TimeUnit.SECONDS.toMillis(1));

    assertThat(producer.isAlive()).isFalse();
    assertThat(emitted).isTrue();
    assertThat(eventDelivered.getCount()).isZero();
  }

  @Test
  void cancelsPendingDeliveryWithoutCallingTheSubscriber() throws InterruptedException {
    SingleSubscriberSynchronousPublisher<String> publisher = new SingleSubscriberSynchronousPublisher<>();
    AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
    CountDownLatch producerStarted = new CountDownLatch(1);
    AtomicBoolean eventDelivered = new AtomicBoolean(false);
    AtomicBoolean emitted = new AtomicBoolean(true);

    publisher.subscribe(new Flow.Subscriber<>() {
      @Override
      public void onSubscribe(Flow.Subscription value) {
        subscription.set(value);
      }

      @Override
      public void onNext(String event) {
        eventDelivered.set(true);
      }

      @Override
      public void onError(Throwable throwable) {
      }

      @Override
      public void onComplete() {
      }
    });

    Thread producer = new Thread(() -> {
      producerStarted.countDown();
      emitted.set(publisher.emit("event"));
    }, "learning-plan-cancel-test");
    producer.start();

    assertThat(producerStarted.await(1, TimeUnit.SECONDS)).isTrue();
    subscription.get().cancel();
    producer.join(TimeUnit.SECONDS.toMillis(1));

    assertThat(producer.isAlive()).isFalse();
    assertThat(emitted).isFalse();
    assertThat(eventDelivered).isFalse();
  }

  @Test
  void rejectsInvalidDemandAndDuplicateSubscribers() {
    SingleSubscriberSynchronousPublisher<String> publisher = new SingleSubscriberSynchronousPublisher<>();
    List<Throwable> firstErrors = new ArrayList<>();
    List<Throwable> duplicateErrors = new ArrayList<>();

    publisher.subscribe(new Flow.Subscriber<>() {
      @Override
      public void onSubscribe(Flow.Subscription subscription) {
        subscription.request(0);
      }

      @Override
      public void onNext(String event) {
      }

      @Override
      public void onError(Throwable throwable) {
        firstErrors.add(throwable);
      }

      @Override
      public void onComplete() {
      }
    });
    publisher.subscribe(new Flow.Subscriber<>() {
      @Override
      public void onSubscribe(Flow.Subscription subscription) {
      }

      @Override
      public void onNext(String event) {
      }

      @Override
      public void onError(Throwable throwable) {
        duplicateErrors.add(throwable);
      }

      @Override
      public void onComplete() {
      }
    });

    assertThat(firstErrors).singleElement().isInstanceOf(IllegalArgumentException.class);
    assertThat(firstErrors.get(0).getMessage()).isEqualTo("Flow request count must be positive: 0");
    assertThat(duplicateErrors).singleElement().isInstanceOf(IllegalStateException.class);
    assertThat(duplicateErrors.get(0).getMessage()).isEqualTo("Learning plan stream supports only one subscriber");
  }
}
