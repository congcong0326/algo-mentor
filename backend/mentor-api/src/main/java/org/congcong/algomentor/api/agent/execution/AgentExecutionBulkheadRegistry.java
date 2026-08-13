package org.congcong.algomentor.api.agent.execution;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.congcong.algomentor.agent.core.execution.AgentExecutionGroup;
import org.congcong.algomentor.agent.core.execution.AgentExecutionPermit;
import org.congcong.algomentor.agent.runtime.definition.AgentDefinitionRegistry;
import org.congcong.algomentor.api.config.AgentExecutorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 当前生效 Agent Definition 的严格执行组舱壁。 */
public final class AgentExecutionBulkheadRegistry {

  public static final int MAX_TOTAL_CAPACITY = 100;
  private static final Logger log = LoggerFactory.getLogger(AgentExecutionBulkheadRegistry.class);
  private static final String GROUP_TAG = "group";
  private static final String REASON_TAG = "reason";
  private static final String GROUP_SATURATED_REASON = "group_saturated";

  private final Map<AgentExecutionGroup, GroupBulkhead> bulkheads;
  private final int totalCapacity;

  public AgentExecutionBulkheadRegistry(
      AgentDefinitionRegistry definitionRegistry,
      AgentExecutorProperties properties,
      MeterRegistry meterRegistry
  ) {
    Objects.requireNonNull(definitionRegistry, "Agent definition registry must not be null");
    Objects.requireNonNull(properties, "Agent executor properties must not be null");
    Set<AgentExecutionGroup> activeGroups = definitionRegistry.executionGroups();
    if (activeGroups.isEmpty()) {
      throw new IllegalStateException("Agent runtime is enabled but no Agent Definition is active");
    }

    Map<AgentExecutionGroup, Integer> configured = parseAndValidate(properties.getGroups());
    EnumMap<AgentExecutionGroup, GroupBulkhead> registered = new EnumMap<>(AgentExecutionGroup.class);
    int capacity = 0;
    for (AgentExecutionGroup group : activeGroups) {
      Integer groupCapacity = configured.get(group);
      if (groupCapacity == null) {
        throw new IllegalStateException("Agent executor capacity is missing for execution group: " + group.code());
      }
      registered.put(group, new GroupBulkhead(group, groupCapacity));
      capacity = Math.addExact(capacity, groupCapacity);
    }
    if (capacity > MAX_TOTAL_CAPACITY) {
      throw new IllegalStateException(
          "Agent executor total execution group capacity must not exceed " + MAX_TOTAL_CAPACITY + ": " + capacity);
    }
    for (AgentExecutionGroup group : configured.keySet()) {
      if (!activeGroups.contains(group)) {
        log.info("Agent execution group is configured but currently inactive. group={}", group.code());
      }
    }
    this.bulkheads = Map.copyOf(registered);
    this.totalCapacity = capacity;
    if (meterRegistry != null) {
      bulkheads.values().forEach(bulkhead -> bulkhead.registerMetrics(meterRegistry));
    }
  }

  /** 非阻塞获取当前执行组的一个许可。 */
  public Optional<AgentExecutionPermit> tryAcquire(AgentExecutionGroup group) {
    GroupBulkhead bulkhead = requireGroup(group);
    return bulkhead.tryAcquire();
  }

  public int totalCapacity() {
    return totalCapacity;
  }

  public int capacity(AgentExecutionGroup group) {
    return requireGroup(group).capacity;
  }

  public int available(AgentExecutionGroup group) {
    return requireGroup(group).semaphore.availablePermits();
  }

  public int active(AgentExecutionGroup group) {
    GroupBulkhead bulkhead = requireGroup(group);
    return bulkhead.capacity - bulkhead.semaphore.availablePermits();
  }

  /** 记录已实际进入工作线程并结束的 Agent 任务。 */
  public void recordCompletion(AgentExecutionGroup group) {
    requireGroup(group).completed.incrementAndGet();
  }

  private GroupBulkhead requireGroup(AgentExecutionGroup group) {
    AgentExecutionGroup requested = Objects.requireNonNull(group, "Agent execution group must not be null");
    GroupBulkhead bulkhead = bulkheads.get(requested);
    if (bulkhead == null) {
      throw new IllegalArgumentException("Agent execution group is not active: " + requested.code());
    }
    return bulkhead;
  }

  private static Map<AgentExecutionGroup, Integer> parseAndValidate(Map<String, Integer> capacities) {
    Map<String, Integer> source = capacities == null ? Map.of() : capacities;
    Map<AgentExecutionGroup, Integer> parsed = new LinkedHashMap<>();
    for (Map.Entry<String, Integer> entry : source.entrySet()) {
      String code = entry.getKey();
      AgentExecutionGroup group = AgentExecutionGroup.fromCode(code)
          .orElseThrow(() -> new IllegalStateException("Unknown Agent execution group configuration: " + code));
      Integer capacity = entry.getValue();
      if (capacity == null || capacity < 1) {
        throw new IllegalStateException("Agent executor group capacity must be positive: " + group.code());
      }
      if (parsed.put(group, capacity) != null) {
        throw new IllegalStateException("Duplicate Agent execution group configuration: " + group.code());
      }
    }
    return Map.copyOf(parsed);
  }

  private static final class GroupBulkhead {

    private final AgentExecutionGroup group;
    private final int capacity;
    private final Semaphore semaphore;
    private final AtomicLong completed = new AtomicLong();
    private Counter rejected;

    private GroupBulkhead(AgentExecutionGroup group, int capacity) {
      this.group = group;
      this.capacity = capacity;
      this.semaphore = new Semaphore(capacity);
    }

    private Optional<AgentExecutionPermit> tryAcquire() {
      if (!semaphore.tryAcquire()) {
        if (rejected != null) {
          rejected.increment();
        }
        return Optional.empty();
      }
      return Optional.of(new Permit(this));
    }

    private void registerMetrics(MeterRegistry meterRegistry) {
      String code = group.code();
      Gauge.builder(AgentExecutorMetrics.GROUP_LIMIT, this, bulkhead -> bulkhead.capacity)
          .tag(GROUP_TAG, code)
          .description("Configured Agent execution group capacity")
          .register(meterRegistry);
      Gauge.builder(AgentExecutorMetrics.GROUP_ACTIVE, this,
              bulkhead -> bulkhead.capacity - bulkhead.semaphore.availablePermits())
          .tag(GROUP_TAG, code)
          .description("Active Agent execution group tasks")
          .register(meterRegistry);
      Gauge.builder(AgentExecutorMetrics.GROUP_AVAILABLE, this, bulkhead -> bulkhead.semaphore.availablePermits())
          .tag(GROUP_TAG, code)
          .description("Available Agent execution group permits")
          .register(meterRegistry);
      FunctionCounter.builder(AgentExecutorMetrics.GROUP_COMPLETED, completed, AtomicLong::get)
          .tag(GROUP_TAG, code)
          .description("Completed Agent execution group tasks")
          .register(meterRegistry);
      rejected = Counter.builder(AgentExecutorMetrics.GROUP_REJECTED)
          .tag(GROUP_TAG, code)
          .tag(REASON_TAG, GROUP_SATURATED_REASON)
          .description("Rejected Agent execution group tasks")
          .register(meterRegistry);
    }
  }

  private static final class Permit implements AgentExecutionPermit {

    private final GroupBulkhead bulkhead;
    private final AtomicBoolean closed = new AtomicBoolean();

    private Permit(GroupBulkhead bulkhead) {
      this.bulkhead = bulkhead;
    }

    @Override
    public AgentExecutionGroup group() {
      return bulkhead.group;
    }

    @Override
    public void close() {
      if (closed.compareAndSet(false, true)) {
        bulkhead.semaphore.release();
      }
    }
  }
}
