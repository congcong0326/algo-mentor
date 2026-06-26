package org.congcong.algomentor.common.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RequestTraceContextTest {

  @AfterEach
  void clearMdc() {
    org.slf4j.MDC.clear();
  }

  @Test
  void wrapPropagatesCapturedRequestIdAndRestoresPreviousContext() {
    RequestTraceContext.setRequestId("request-1");
    Runnable task = RequestTraceContext.wrap(() -> {
      assertThat(RequestTraceContext.currentRequestId()).contains("request-1");
      RequestTraceContext.setRequestId("nested");
    });

    RequestTraceContext.setRequestId("request-2");
    task.run();

    assertThat(RequestTraceContext.currentRequestId()).contains("request-2");
  }

  @Test
  void contextAwareExecutorWrapsSubmittedTasks() {
    AtomicReference<String> observedRequestId = new AtomicReference<>();
    Executor sameThreadExecutor = Runnable::run;
    Executor executor = RequestTraceContext.contextAwareExecutor(sameThreadExecutor);

    RequestTraceContext.setRequestId("request-3");
    executor.execute(() -> observedRequestId.set(RequestTraceContext.currentRequestId().orElseThrow()));

    assertThat(observedRequestId).hasValue("request-3");
  }
}
