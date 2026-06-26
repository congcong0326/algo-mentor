package org.congcong.algomentor.agent.core.permission;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.congcong.algomentor.agent.core.AgentCancellationToken;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InMemoryAgentToolPermissionCoordinator implements AgentToolPermissionCoordinator {

  private static final Logger log = LoggerFactory.getLogger(InMemoryAgentToolPermissionCoordinator.class);

  public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

  private static final long WAIT_POLL_MILLIS = 25L;
  private static final String REASON_MISSING_TRUSTED_USER_ID = "missing_trusted_user_id";
  private static final String DEFAULT_ALLOW_REASON = "user_confirmed";
  private static final String DEFAULT_DENY_REASON = AgentToolPermissionResultFactory.REASON_USER_REJECTED;

  private final AgentToolPermissionResultFactory resultFactory;
  private final Duration timeout;
  private final Clock clock;
  private final Supplier<String> requestIdSupplier;
  private final AgentToolPermissionMetrics metrics;
  private final ConcurrentHashMap<String, PendingPermissionRequest> pendingRequests = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, CompletedPermissionRequest> completedRequests = new ConcurrentHashMap<>();

  public InMemoryAgentToolPermissionCoordinator(AgentToolPermissionResultFactory resultFactory) {
    this(resultFactory, DEFAULT_TIMEOUT, Clock.systemUTC());
  }

  public InMemoryAgentToolPermissionCoordinator(
      AgentToolPermissionResultFactory resultFactory,
      Duration timeout,
      Clock clock
  ) {
    this(resultFactory, timeout, clock, NoopAgentToolPermissionMetrics.INSTANCE);
  }

  public InMemoryAgentToolPermissionCoordinator(
      AgentToolPermissionResultFactory resultFactory,
      Duration timeout,
      Clock clock,
      AgentToolPermissionMetrics metrics
  ) {
    this(
        resultFactory,
        timeout,
        clock,
        () -> "perm_" + UUID.randomUUID().toString().replace("-", ""),
        metrics);
  }

  InMemoryAgentToolPermissionCoordinator(
      AgentToolPermissionResultFactory resultFactory,
      Duration timeout,
      Clock clock,
      Supplier<String> requestIdSupplier
  ) {
    this(resultFactory, timeout, clock, requestIdSupplier, NoopAgentToolPermissionMetrics.INSTANCE);
  }

  InMemoryAgentToolPermissionCoordinator(
      AgentToolPermissionResultFactory resultFactory,
      Duration timeout,
      Clock clock,
      Supplier<String> requestIdSupplier,
      AgentToolPermissionMetrics metrics
  ) {
    this.resultFactory = Objects.requireNonNull(resultFactory, "resultFactory must not be null");
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("Agent tool permission timeout must be positive");
    }
    this.timeout = timeout;
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
    this.requestIdSupplier = Objects.requireNonNull(requestIdSupplier, "requestIdSupplier must not be null");
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
  }

  @Override
  public AgentToolPermissionAuthorization authorize(
      AgentToolPermissionCheck check,
      AgentToolPermissionDecisionPlan plan,
      AgentCancellationToken cancellationToken,
      EventPublisher eventPublisher
  ) {
    requireCheckAndPlan(check, plan);
    EventPublisher events = eventPublisher == null ? EventPublisher.noop() : eventPublisher;
    return switch (plan.behavior()) {
      case ALLOW -> new AgentToolPermissionAuthorization.Allowed(plan);
      case DENY -> {
        recordImmediateLatency(check, AgentToolPermissionMetrics.OUTCOME_DENY);
        yield syntheticPolicyDenied(check, plan);
      }
      case ASK -> authorizeWithUserDecision(check, plan, cancellationToken, events);
      case PASSTHROUGH -> new AgentToolPermissionAuthorization.Allowed(
          AgentToolPermissionDecisionPlan.allow(plan.policySource(), plan.metadata()));
    };
  }

  @Override
  public AgentToolPermissionDecisionResult decide(
      String permissionRequestId,
      AgentToolPermissionDecisionType decision,
      String reason,
      long userId
  ) {
    validateDecisionInput(permissionRequestId, decision, reason, userId);
    cleanupCompletedRequests();

    PendingPermissionRequest pending = pendingRequests.get(permissionRequestId);
    if (pending == null) {
      CompletedPermissionRequest completed = completedRequests.get(permissionRequestId);
      if (completed != null) {
        verifyOwner(completed.ownerUserId(), userId);
        throw terminalException(completed.status());
      }
      throw new AgentToolPermissionException(
          AgentToolPermissionException.Code.NOT_FOUND,
          "Agent tool permission request was not found");
    }
    verifyOwner(pending.ownerUserId(), userId);

    if (pending.completed().get()) {
      throw terminalException(pending.status().get());
    }
    if (!clock.instant().isBefore(pending.request().expiresAt())) {
      expirePending(permissionRequestId, pending);
      throw new AgentToolPermissionException(
          AgentToolPermissionException.Code.EXPIRED,
          "Agent tool permission request has expired");
    }
    if (!pending.completed().compareAndSet(false, true)) {
      throw terminalException(pending.status().get());
    }

    AgentToolPermissionDecision acceptedDecision = new AgentToolPermissionDecision(
        permissionRequestId,
        decision,
        normalizedDecisionReason(decision, reason),
        userId,
        clock.instant());
    pending.status().set(TerminalStatus.DECIDED);
    rememberCompleted(permissionRequestId, pending.ownerUserId(), TerminalStatus.DECIDED);
    pending.future().complete(acceptedDecision);
    metrics.recordUserDecision(pending.request().toolName(), acceptedDecision.decision());
    logUserDecision(pending.request(), acceptedDecision);
    return new AgentToolPermissionDecisionResult(pending.request(), acceptedDecision, true);
  }

  public int pendingRequestCount() {
    return pendingRequests.size();
  }

  private AgentToolPermissionAuthorization authorizeWithUserDecision(
      AgentToolPermissionCheck check,
      AgentToolPermissionDecisionPlan plan,
      AgentCancellationToken cancellationToken,
      EventPublisher events
  ) {
    OptionalLong ownerUserId = ownerUserId(check.trustedMetadata());
    if (ownerUserId.isEmpty()) {
      recordImmediateLatency(check, AgentToolPermissionMetrics.OUTCOME_DENY);
      logSyntheticDecision(
          check,
          AgentToolPermissionMetrics.OUTCOME_DENY,
          REASON_MISSING_TRUSTED_USER_ID,
          Duration.ZERO);
      return new AgentToolPermissionAuthorization.SyntheticResult(
          resultFactory.policyDenied(check.toolCall().name(), check.toolCall().id(), REASON_MISSING_TRUSTED_USER_ID),
          plan);
    }

    Instant createdAt = clock.instant();
    AgentToolPermissionRequest request = newPendingRequest(check, plan, createdAt);
    PendingPermissionRequest pending = new PendingPermissionRequest(
        request,
        new CompletableFuture<>(),
        ownerUserId.getAsLong(),
        new AtomicBoolean(false),
        new AtomicReference<>(TerminalStatus.PENDING));
    pendingRequests.put(request.permissionRequestId(), pending);
    metrics.recordPermissionRequest(request.toolName(), plan.policySource());
    logPermissionRequest(request);
    events.toolPermissionRequested(request, plan);

    try {
      return waitForDecision(check, plan, pending, cancellationToken, events);
    } finally {
      pendingRequests.remove(request.permissionRequestId(), pending);
    }
  }

  private AgentToolPermissionAuthorization waitForDecision(
      AgentToolPermissionCheck check,
      AgentToolPermissionDecisionPlan plan,
      PendingPermissionRequest pending,
      AgentCancellationToken cancellationToken,
      EventPublisher events
  ) {
    while (true) {
      AgentToolPermissionDecision completedDecision = completedDecision(pending);
      if (completedDecision != null) {
        return authorizationForDecision(check, plan, pending, completedDecision, events);
      }
      TerminalStatus status = pending.status().get();
      if (status == TerminalStatus.EXPIRED) {
        return timeoutAuthorization(check, plan, pending, events);
      }
      if (status == TerminalStatus.CANCELLED) {
        return cancelledAuthorization(check, plan, pending);
      }
      if (status == TerminalStatus.DECIDED) {
        Thread.yield();
        continue;
      }
      if (pending.completed().get()) {
        Thread.yield();
        continue;
      }
      if (isCancelled(cancellationToken)) {
        return completeAsCancelled(check, plan, pending);
      }
      if (!clock.instant().isBefore(pending.request().expiresAt())) {
        return completeAsTimeout(check, plan, pending, events);
      }
      try {
        AgentToolPermissionDecision decision = pending.future().get(waitMillis(pending), TimeUnit.MILLISECONDS);
        if (pending.status().get() == TerminalStatus.EXPIRED) {
          return timeoutAuthorization(check, plan, pending, events);
        }
        return authorizationForDecision(check, plan, pending, decision, events);
      } catch (TimeoutException ignored) {
        // Re-check cancellation and expiry with the injected clock.
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return completeAsCancelled(check, plan, pending);
      } catch (ExecutionException ex) {
        return completeAsTimeout(check, plan, pending, events);
      }
    }
  }

  private AgentToolPermissionAuthorization authorizationForDecision(
      AgentToolPermissionCheck check,
      AgentToolPermissionDecisionPlan plan,
      PendingPermissionRequest pending,
      AgentToolPermissionDecision decision,
      EventPublisher events
  ) {
    events.toolPermissionDecided(pending.request(), decision, plan);
    recordTerminalLatency(
        pending.request(),
        decision.decision() == AgentToolPermissionDecisionType.ALLOW
            ? AgentToolPermissionMetrics.OUTCOME_ALLOW
            : AgentToolPermissionMetrics.OUTCOME_DENY);
    if (decision.decision() == AgentToolPermissionDecisionType.ALLOW) {
      metrics.recordHighPermissionExecution(pending.request().toolName(), plan.policySource());
      return new AgentToolPermissionAuthorization.Allowed(plan);
    }
    JsonNode result = resultFactory.denied(
        check.toolCall().name(),
        check.toolCall().id(),
        pending.request().permissionRequestId(),
        decision.reason());
    return new AgentToolPermissionAuthorization.SyntheticResult(result, plan);
  }

  private AgentToolPermissionAuthorization completeAsTimeout(
      AgentToolPermissionCheck check,
      AgentToolPermissionDecisionPlan plan,
      PendingPermissionRequest pending,
      EventPublisher events
  ) {
    if (pending.completed().compareAndSet(false, true)) {
      pending.status().set(TerminalStatus.EXPIRED);
      rememberCompleted(pending.request().permissionRequestId(), pending.ownerUserId(), TerminalStatus.EXPIRED);
      recordTimeout(pending.request());
      return timeoutAuthorization(check, plan, pending, events);
    }
    return authorizationForTerminalState(check, plan, pending, events, TerminalStatus.EXPIRED);
  }

  private AgentToolPermissionAuthorization timeoutAuthorization(
      AgentToolPermissionCheck check,
      AgentToolPermissionDecisionPlan plan,
      PendingPermissionRequest pending,
      EventPublisher events
  ) {
    events.toolPermissionTimedOut(
        pending.request(),
        AgentToolPermissionResultFactory.REASON_TIMEOUT,
        pending.request().expiresAt(),
        plan);
    JsonNode result = resultFactory.timeout(
        check.toolCall().name(),
        check.toolCall().id(),
        pending.request().permissionRequestId());
    return new AgentToolPermissionAuthorization.SyntheticResult(result, plan);
  }

  private AgentToolPermissionAuthorization completeAsCancelled(
      AgentToolPermissionCheck check,
      AgentToolPermissionDecisionPlan plan,
      PendingPermissionRequest pending
  ) {
    if (pending.completed().compareAndSet(false, true)) {
      pending.status().set(TerminalStatus.CANCELLED);
      rememberCompleted(pending.request().permissionRequestId(), pending.ownerUserId(), TerminalStatus.CANCELLED);
      recordTerminalLatency(pending.request(), AgentToolPermissionMetrics.OUTCOME_CANCELLED);
      logPermissionCancelled(pending.request());
      return cancelledAuthorization(check, plan, pending);
    }
    return authorizationForTerminalState(
        check,
        plan,
        pending,
        AgentToolPermissionCoordinator.EventPublisher.noop(),
        TerminalStatus.CANCELLED);
  }

  private AgentToolPermissionAuthorization cancelledAuthorization(
      AgentToolPermissionCheck check,
      AgentToolPermissionDecisionPlan plan,
      PendingPermissionRequest pending
  ) {
    JsonNode result = resultFactory.cancelled(
        check.toolCall().name(),
        check.toolCall().id(),
        pending.request().permissionRequestId());
    return new AgentToolPermissionAuthorization.SyntheticResult(result, plan);
  }

  private AgentToolPermissionAuthorization authorizationForTerminalState(
      AgentToolPermissionCheck check,
      AgentToolPermissionDecisionPlan plan,
      PendingPermissionRequest pending,
      EventPublisher events,
      TerminalStatus fallbackStatus
  ) {
    for (int attempt = 0; attempt < 10; attempt++) {
      AgentToolPermissionDecision decision = completedDecision(pending);
      if (decision != null) {
        return authorizationForDecision(check, plan, pending, decision, events);
      }
      TerminalStatus status = pending.status().get();
      if (status == TerminalStatus.EXPIRED) {
        return timeoutAuthorization(check, plan, pending, events);
      }
      if (status == TerminalStatus.CANCELLED) {
        return cancelledAuthorization(check, plan, pending);
      }
      if (status == TerminalStatus.DECIDED) {
        Thread.yield();
        continue;
      }
      if (pending.completed().get()) {
        Thread.yield();
        continue;
      }
      break;
    }
    if (fallbackStatus == TerminalStatus.EXPIRED) {
      return timeoutAuthorization(check, plan, pending, events);
    }
    return cancelledAuthorization(check, plan, pending);
  }

  private AgentToolPermissionDecision completedDecision(PendingPermissionRequest pending) {
    if (!pending.future().isDone()) {
      return null;
    }
    return pending.future().getNow(null);
  }

  private AgentToolPermissionAuthorization syntheticPolicyDenied(
      AgentToolPermissionCheck check,
      AgentToolPermissionDecisionPlan plan
  ) {
    logSyntheticDecision(check, AgentToolPermissionMetrics.OUTCOME_DENY, plan.reason(), Duration.ZERO);
    return new AgentToolPermissionAuthorization.SyntheticResult(
        resultFactory.policyDenied(check.toolCall().name(), check.toolCall().id(), plan.reason()),
        plan);
  }

  private AgentToolPermissionRequest newPendingRequest(
      AgentToolPermissionCheck check,
      AgentToolPermissionDecisionPlan plan,
      Instant createdAt
  ) {
    return new AgentToolPermissionRequest(
        nextRequestId(),
        check.context().runId(),
        check.stepIndex(),
        check.toolCall().id(),
        check.toolCall().name(),
        plan.displayName(),
        plan.reason(),
        plan.preview(),
        createdAt,
        createdAt.plus(timeout));
  }

  private String nextRequestId() {
    for (int attempt = 0; attempt < 10; attempt++) {
      String requestId = requestIdSupplier.get();
      if (requestId != null
          && !requestId.isBlank()
          && !pendingRequests.containsKey(requestId)
          && !completedRequests.containsKey(requestId)) {
        return requestId;
      }
    }
    throw new IllegalStateException("Unable to allocate agent tool permission request id");
  }

  private void expirePending(String permissionRequestId, PendingPermissionRequest pending) {
    if (pending.completed().compareAndSet(false, true)) {
      pending.status().set(TerminalStatus.EXPIRED);
      rememberCompleted(permissionRequestId, pending.ownerUserId(), TerminalStatus.EXPIRED);
      recordTimeout(pending.request());
    }
    pendingRequests.remove(permissionRequestId, pending);
  }

  private OptionalLong ownerUserId(Map<String, Object> trustedMetadata) {
    Object value = trustedMetadata.get(AgentRuntimeMetadataKeys.USER_ID);
    if (value instanceof Number number && number.longValue() > 0) {
      return OptionalLong.of(number.longValue());
    }
    if (value instanceof String text && !text.isBlank()) {
      try {
        long parsed = Long.parseLong(text);
        if (parsed > 0) {
          return OptionalLong.of(parsed);
        }
      } catch (NumberFormatException ignored) {
        return OptionalLong.empty();
      }
    }
    return OptionalLong.empty();
  }

  private void validateDecisionInput(
      String permissionRequestId,
      AgentToolPermissionDecisionType decision,
      String reason,
      long userId
  ) {
    if (permissionRequestId == null || permissionRequestId.isBlank()) {
      throw new AgentToolPermissionException(
          AgentToolPermissionException.Code.INVALID_DECISION,
          "Agent tool permission request id must not be blank");
    }
    if (decision == null) {
      throw new AgentToolPermissionException(
          AgentToolPermissionException.Code.INVALID_DECISION,
          "Agent tool permission decision type must not be null");
    }
    if (reason == null || reason.isBlank()) {
      throw new AgentToolPermissionException(
          AgentToolPermissionException.Code.INVALID_DECISION,
          "Agent tool permission decision reason must not be blank");
    }
    if (userId < 1) {
      throw new AgentToolPermissionException(
          AgentToolPermissionException.Code.INVALID_DECISION,
          "Agent tool permission user id must be positive");
    }
  }

  private void verifyOwner(long ownerUserId, long userId) {
    if (ownerUserId != userId) {
      throw new AgentToolPermissionException(
          AgentToolPermissionException.Code.FORBIDDEN,
          "Agent tool permission request belongs to another user");
    }
  }

  private AgentToolPermissionException terminalException(TerminalStatus status) {
    if (status == TerminalStatus.EXPIRED) {
      return new AgentToolPermissionException(
          AgentToolPermissionException.Code.EXPIRED,
          "Agent tool permission request has expired");
    }
    return new AgentToolPermissionException(
        AgentToolPermissionException.Code.ALREADY_DECIDED,
        "Agent tool permission request is no longer pending");
  }

  private String normalizedDecisionReason(AgentToolPermissionDecisionType decision, String reason) {
    if (reason != null && !reason.isBlank()) {
      return reason;
    }
    return decision == AgentToolPermissionDecisionType.ALLOW ? DEFAULT_ALLOW_REASON : DEFAULT_DENY_REASON;
  }

  private boolean isCancelled(AgentCancellationToken cancellationToken) {
    return cancellationToken != null && cancellationToken.isCancelled() || Thread.currentThread().isInterrupted();
  }

  private long waitMillis(PendingPermissionRequest pending) {
    long remainingMillis = Duration.between(clock.instant(), pending.request().expiresAt()).toMillis();
    return Math.max(1L, Math.min(WAIT_POLL_MILLIS, remainingMillis));
  }

  private void rememberCompleted(String permissionRequestId, long ownerUserId, TerminalStatus status) {
    completedRequests.put(permissionRequestId, new CompletedPermissionRequest(ownerUserId, status, clock.instant()));
  }

  private void cleanupCompletedRequests() {
    Instant threshold = clock.instant().minus(timeout.multipliedBy(2));
    completedRequests.entrySet().removeIf(entry -> entry.getValue().completedAt().isBefore(threshold));
  }

  private void recordImmediateLatency(
      AgentToolPermissionCheck check,
      String outcome
  ) {
    metrics.recordLatency(check.toolCall().name(), outcome, Duration.ZERO);
  }

  private void recordTimeout(AgentToolPermissionRequest request) {
    metrics.recordTimeout(request.toolName());
    recordTerminalLatency(request, AgentToolPermissionMetrics.OUTCOME_TIMEOUT);
    logPermissionTimedOut(request);
  }

  private void recordTerminalLatency(
      AgentToolPermissionRequest request,
      String outcome
  ) {
    metrics.recordLatency(request.toolName(), outcome, Duration.between(request.createdAt(), clock.instant()));
  }

  private void logPermissionRequest(AgentToolPermissionRequest request) {
    if (!log.isInfoEnabled()) {
      return;
    }
    log.info(
        "Agent tool permission requested runId={} toolName={} toolCallId={} permissionRequestId={} reason={} "
            + "expiresAt={}",
        request.runId(),
        request.toolName(),
        request.toolCallId(),
        request.permissionRequestId(),
        request.reason(),
        request.expiresAt());
  }

  private void logUserDecision(
      AgentToolPermissionRequest request,
      AgentToolPermissionDecision decision
  ) {
    if (!log.isInfoEnabled()) {
      return;
    }
    log.info(
        "Agent tool permission decided runId={} toolName={} toolCallId={} permissionRequestId={} decision={} "
            + "reason={} latency={}",
        request.runId(),
        request.toolName(),
        request.toolCallId(),
        request.permissionRequestId(),
        decision.decision(),
        decision.reason(),
        Duration.between(request.createdAt(), decision.decidedAt()));
  }

  private void logPermissionTimedOut(AgentToolPermissionRequest request) {
    if (!log.isInfoEnabled()) {
      return;
    }
    log.info(
        "Agent tool permission timed out runId={} toolName={} toolCallId={} permissionRequestId={} decision={} "
            + "reason={} latency={} expiresAt={}",
        request.runId(),
        request.toolName(),
        request.toolCallId(),
        request.permissionRequestId(),
        AgentToolPermissionMetrics.OUTCOME_TIMEOUT,
        AgentToolPermissionResultFactory.REASON_TIMEOUT,
        Duration.between(request.createdAt(), clock.instant()),
        request.expiresAt());
  }

  private void logPermissionCancelled(AgentToolPermissionRequest request) {
    if (!log.isInfoEnabled()) {
      return;
    }
    log.info(
        "Agent tool permission cancelled runId={} toolName={} toolCallId={} permissionRequestId={} decision={} "
            + "reason={} latency={}",
        request.runId(),
        request.toolName(),
        request.toolCallId(),
        request.permissionRequestId(),
        AgentToolPermissionMetrics.OUTCOME_CANCELLED,
        AgentToolPermissionResultFactory.REASON_RUN_CANCELLED,
        Duration.between(request.createdAt(), clock.instant()));
  }

  private void logSyntheticDecision(
      AgentToolPermissionCheck check,
      String decision,
      String reason,
      Duration latency
  ) {
    if (!log.isInfoEnabled()) {
      return;
    }
    log.info(
        "Agent tool permission synthetic decision runId={} toolName={} toolCallId={} decision={} reason={} "
            + "latency={}",
        check.context().runId(),
        check.toolCall().name(),
        check.toolCall().id(),
        decision,
        reason,
        latency);
  }

  private void requireCheckAndPlan(AgentToolPermissionCheck check, AgentToolPermissionDecisionPlan plan) {
    if (check == null) {
      throw new IllegalArgumentException("Agent tool permission check must not be null");
    }
    if (plan == null) {
      throw new IllegalArgumentException("Agent tool permission plan must not be null");
    }
  }

  private enum TerminalStatus {
    PENDING,
    DECIDED,
    EXPIRED,
    CANCELLED
  }

  private record PendingPermissionRequest(
      AgentToolPermissionRequest request,
      CompletableFuture<AgentToolPermissionDecision> future,
      long ownerUserId,
      AtomicBoolean completed,
      AtomicReference<TerminalStatus> status
  ) {
  }

  private record CompletedPermissionRequest(
      long ownerUserId,
      TerminalStatus status,
      Instant completedAt
  ) {
  }
}
