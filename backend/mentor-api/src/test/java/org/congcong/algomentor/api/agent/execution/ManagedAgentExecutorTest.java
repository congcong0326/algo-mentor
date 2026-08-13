package org.congcong.algomentor.api.agent.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.congcong.algomentor.agent.core.execution.AgentExecutionPermit;
import org.congcong.algomentor.agent.core.execution.AgentExecutionGroup;
import org.congcong.algomentor.agent.core.execution.AgentExecutionRejectedException;
import org.congcong.algomentor.agent.core.execution.AgentExecutionRejectionReason;
import org.congcong.algomentor.agent.core.runtime.definition.AgentDefinition;
import org.congcong.algomentor.agent.core.runtime.definition.AgentKey;
import org.congcong.algomentor.agent.core.runtime.definition.AgentLoopPolicy;
import org.congcong.algomentor.agent.core.runtime.definition.AgentOutputContract;
import org.congcong.algomentor.agent.core.runtime.definition.AgentPreparedRequest;
import org.congcong.algomentor.agent.runtime.definition.AgentDefinitionRegistry;
import org.congcong.algomentor.api.config.AgentExecutorProperties;
import org.congcong.algomentor.common.trace.RequestTraceContext;
import org.junit.jupiter.api.Test;

class ManagedAgentExecutorTest {

  @Test
  void derivesPhysicalPoolShapeAndPropagatesExecutionGroupContext() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    ManagedAgentExecutor executor = executor(Map.of(
        "practice", 27,
        "learning-plan", 2,
        "learner-profile-background", 1), meterRegistry);
    CountDownLatch completed = new CountDownLatch(1);
    AtomicReference<String> requestId = new AtomicReference<>();
    AtomicReference<AgentExecutionGroup> currentGroup = new AtomicReference<>();

    try (RequestTraceContext.RequestTraceScope ignored = RequestTraceContext.withRequestId("request-agent-pool")) {
      executor.execute(AgentExecutionGroup.PRACTICE, () -> {
        requestId.set(RequestTraceContext.currentRequestId().orElse(null));
        currentGroup.set(executor.currentExecutionGroup().orElse(null));
        completed.countDown();
      });
    }

    await(completed);
    ThreadPoolExecutor pool = executor.threadPoolExecutor();
    assertThat(pool.getCorePoolSize()).isEqualTo(10);
    assertThat(pool.getMaximumPoolSize()).isEqualTo(30);
    assertThat(pool.allowsCoreThreadTimeOut()).isTrue();
    assertThat(requestId).hasValue("request-agent-pool");
    assertThat(currentGroup).hasValue(AgentExecutionGroup.PRACTICE);
    assertThat(meterRegistry.get(AgentExecutorMetrics.GROUP_LIMIT).tag("group", "practice").gauge().value()).isEqualTo(27);
    executor.shutdown();
  }

  @Test
  void appliesStrictGroupBulkheadsAndReleasesPermitAfterTaskCompletion() {
    ManagedAgentExecutor executor = executor(Map.of(
        "practice", 1,
        "learning-plan", 1,
        "learner-profile-background", 1), new SimpleMeterRegistry());
    CountDownLatch practiceStarted = new CountDownLatch(1);
    CountDownLatch releasePractice = new CountDownLatch(1);
    CountDownLatch otherGroupsCompleted = new CountDownLatch(2);
    try {
      executor.execute(AgentExecutionGroup.PRACTICE, () -> {
        practiceStarted.countDown();
        await(releasePractice);
      });
      await(practiceStarted);

      assertThatThrownBy(() -> executor.execute(AgentExecutionGroup.PRACTICE, () -> { }))
          .isInstanceOf(AgentExecutionRejectedException.class)
          .extracting(error -> ((AgentExecutionRejectedException) error).reason())
          .isEqualTo(AgentExecutionRejectionReason.GROUP_SATURATED);
      executor.execute(AgentExecutionGroup.LEARNING_PLAN, otherGroupsCompleted::countDown);
      executor.execute(AgentExecutionGroup.LEARNER_PROFILE_BACKGROUND, otherGroupsCompleted::countDown);
      await(otherGroupsCompleted);
      assertThat(executor.threadPoolExecutor().getMaximumPoolSize()).isEqualTo(3);
    } finally {
      releasePractice.countDown();
      executor.shutdown();
    }
  }

  @Test
  void rejectsDuringShutdownWithoutLeakingGroupPermit() {
    AgentExecutionBulkheadRegistry bulkheads = bulkheads(Map.of(
        "practice", 1,
        "learning-plan", 1,
        "learner-profile-background", 1));
    AgentExecutorProperties properties = properties(Map.of(
        "practice", 1,
        "learning-plan", 1,
        "learner-profile-background", 1));
    ManagedAgentExecutor executor = new ManagedAgentExecutor(properties, bulkheads, new SimpleMeterRegistry());
    executor.shutdown();

    assertThatThrownBy(() -> executor.execute(AgentExecutionGroup.PRACTICE, () -> { }))
        .isInstanceOf(AgentExecutionRejectedException.class)
        .extracting(error -> ((AgentExecutionRejectedException) error).reason())
        .isEqualTo(AgentExecutionRejectionReason.SHUTDOWN);
    assertThat(bulkheads.available(AgentExecutionGroup.PRACTICE)).isEqualTo(1);
  }

  @Test
  void validatesConfigurationAndKeepsInactiveGroupOutsideThePhysicalCapacity() {
    AgentDefinitionRegistry registry = new AgentDefinitionRegistry(List.of(
        definition("practice", AgentExecutionGroup.PRACTICE)));
    AgentExecutionBulkheadRegistry bulkheads = new AgentExecutionBulkheadRegistry(
        registry,
        properties(Map.of(
            "practice", 2,
            "learning-plan", 2,
            "learner-profile-background", 1)),
        null);

    assertThat(bulkheads.totalCapacity()).isEqualTo(2);
    assertThatThrownBy(() -> new AgentExecutionBulkheadRegistry(registry, properties(Map.of(
        "unknown", 1)), null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unknown Agent execution group configuration: unknown");
    assertThatThrownBy(() -> new AgentExecutionBulkheadRegistry(registry, properties(Map.of(
        "practice", 0)), null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Agent executor group capacity must be positive: practice");
  }

  @Test
  void permitsAreIdempotentAndOnlyEnteredTasksCountAsCompleted() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    AgentExecutionBulkheadRegistry bulkheads = bulkheads(Map.of(
        "practice", 1,
        "learning-plan", 1,
        "learner-profile-background", 1), meterRegistry);

    AgentExecutionPermit permit = bulkheads.tryAcquire(AgentExecutionGroup.PRACTICE).orElseThrow();
    assertThat(bulkheads.available(AgentExecutionGroup.PRACTICE)).isZero();
    permit.close();
    permit.close();

    assertThat(bulkheads.available(AgentExecutionGroup.PRACTICE)).isEqualTo(1);
    assertThat(meterRegistry.get(AgentExecutorMetrics.GROUP_COMPLETED)
        .tag("group", "practice").functionCounter().count()).isZero();
  }

  private static ManagedAgentExecutor executor(Map<String, Integer> capacities, SimpleMeterRegistry meterRegistry) {
    return new ManagedAgentExecutor(properties(capacities), bulkheads(capacities, meterRegistry), meterRegistry);
  }

  private static AgentExecutorProperties properties(Map<String, Integer> capacities) {
    AgentExecutorProperties properties = new AgentExecutorProperties();
    properties.setGroups(capacities);
    properties.setShutdownTimeout(java.time.Duration.ofSeconds(1));
    return properties;
  }

  private static AgentExecutionBulkheadRegistry bulkheads(Map<String, Integer> capacities) {
    return bulkheads(capacities, null);
  }

  private static AgentExecutionBulkheadRegistry bulkheads(
      Map<String, Integer> capacities,
      SimpleMeterRegistry meterRegistry
  ) {
    return new AgentExecutionBulkheadRegistry(
        new AgentDefinitionRegistry(List.of(
            definition("practice", AgentExecutionGroup.PRACTICE),
            definition("plan", AgentExecutionGroup.LEARNING_PLAN),
            definition("profile", AgentExecutionGroup.LEARNER_PROFILE_BACKGROUND))),
        properties(capacities),
        meterRegistry);
  }

  private static AgentDefinition<String> definition(String key, AgentExecutionGroup group) {
    return new AgentDefinition<>() {
      @Override
      public AgentKey<String> key() {
        return new AgentKey<>(key, String.class);
      }

      @Override
      public AgentExecutionGroup executionGroup() {
        return group;
      }

      @Override
      public AgentLoopPolicy loopPolicy() {
        return new AgentLoopPolicy(1);
      }

      @Override
      public AgentOutputContract outputContract() {
        return AgentOutputContract.defaults();
      }

      @Override
      public AgentPreparedRequest prepare(String input, org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext context) {
        throw new UnsupportedOperationException();
      }
    };
  }

  private static void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new AssertionError(interrupted);
    }
  }
}
