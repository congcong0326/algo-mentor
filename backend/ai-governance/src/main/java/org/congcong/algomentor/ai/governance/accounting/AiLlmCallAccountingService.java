package org.congcong.algomentor.ai.governance.accounting;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.congcong.algomentor.ai.governance.repository.mybatis.AiLlmCallUsageMapper;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiLlmCallUsageRow;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiLlmCallUsageUpdate;
import org.congcong.algomentor.ai.governance.usage.AiDailyUsageStore;
import org.congcong.algomentor.llm.core.exception.LlmErrorCode;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 负责把每次真实 provider dispatch 写入调用级台账，并在成功时累计 daily Token。 */
public class AiLlmCallAccountingService {

  public static final String PERSIST_FAILURES_TOTAL = "ai_accounting_persist_failures_total";
  public static final String MISSING_CONTEXT_TOTAL = "ai_accounting_missing_context_total";
  private static final Logger log = LoggerFactory.getLogger(AiLlmCallAccountingService.class);

  private final AiLlmCallUsageMapper mapper;
  private final AiDailyUsageStore dailyUsageStore;
  private final AiLlmCallContextResolver contextResolver;
  private final Clock clock;
  private final ZoneId quotaZone;
  private final MeterRegistry meterRegistry;

  public AiLlmCallAccountingService(
      AiLlmCallUsageMapper mapper,
      AiDailyUsageStore dailyUsageStore,
      AiLlmCallContextResolver contextResolver,
      Clock clock,
      ZoneId quotaZone,
      MeterRegistry meterRegistry
  ) {
    this.mapper = mapper;
    this.dailyUsageStore = dailyUsageStore;
    this.contextResolver = contextResolver;
    this.clock = clock;
    this.quotaZone = quotaZone == null ? java.time.ZoneOffset.UTC : quotaZone;
    this.meterRegistry = meterRegistry;
  }

  public AiLlmCallUsage start(LlmCompletionRequest request) {
    AiLlmCallContext context = contextResolver.resolve(request);
    if (context.missingTrustedContext()) {
      log.warn("AI accounting request metadata is incomplete. callId={}", context.callId());
      increment(MISSING_CONTEXT_TOTAL);
    }
    String provider = request.modelSelector().providerId().map(value -> value.value()).orElse(null);
    String model = request.modelSelector().modelId().map(value -> value.value()).orElse(null);
    AiLlmCallUsage usage = new AiLlmCallUsage(
        context,
        AiLlmCallStatus.RUNNING,
        provider,
        model,
        null,
        AiUsage.zero(),
        Instant.now(clock),
        null);
    try {
      mapper.insert(toRow(usage));
    } catch (RuntimeException exception) {
      persistFailure(context.callId(), "insert", exception);
    }
    return usage;
  }

  public void complete(AiLlmCallUsage call, LlmCompletionResult result) {
    finish(new AiLlmCallUsage(
        call.context(),
        AiLlmCallStatus.COMPLETED,
        result.provider().value(),
        result.model().value(),
        null,
        toAiUsage(result.usage()),
        call.startedAt(),
        Instant.now(clock)));
  }

  public void complete(AiLlmCallUsage call, String provider, String model, LlmUsage usage) {
    finish(new AiLlmCallUsage(
        call.context(),
        AiLlmCallStatus.COMPLETED,
        provider == null ? call.provider() : provider,
        model == null ? call.model() : model,
        null,
        toAiUsage(usage),
        call.startedAt(),
        Instant.now(clock)));
  }

  public void fail(AiLlmCallUsage call, Throwable throwable) {
    String provider = call.provider();
    String model = call.model();
    String errorCode = LlmErrorCode.UNKNOWN.name();
    if (throwable instanceof LlmException llmException) {
      provider = llmException.provider() == null ? provider : llmException.provider().value();
      model = llmException.model() == null ? model : llmException.model().value();
      errorCode = llmException.code().name();
    }
    finish(new AiLlmCallUsage(
        call.context(),
        AiLlmCallStatus.FAILED,
        provider,
        model,
        errorCode,
        AiUsage.zero(),
        call.startedAt(),
        Instant.now(clock)));
  }

  public void cancel(AiLlmCallUsage call, String provider, String model, LlmUsage usage) {
    finish(new AiLlmCallUsage(
        call.context(),
        AiLlmCallStatus.CANCELLED,
        provider == null ? call.provider() : provider,
        model == null ? call.model() : model,
        LlmErrorCode.CANCELLED.name(),
        toAiUsage(usage),
        call.startedAt(),
        Instant.now(clock)));
  }

  private void finish(AiLlmCallUsage call) {
    try {
      int updated = mapper.updateTerminal(toUpdate(call));
      if (updated != 1) {
        return;
      }
      if (call.context().userId() != null) {
        dailyUsageStore.addUsage(
            call.context().userId(),
            java.time.LocalDate.now(clock.withZone(quotaZone)),
            call.context().quotaScope(),
            call.usage());
      }
    } catch (RuntimeException exception) {
      persistFailure(call.context().callId(), "terminal", exception);
    }
  }

  private AiLlmCallUsageRow toRow(AiLlmCallUsage call) {
    return new AiLlmCallUsageRow(
        call.context().callId(),
        call.context().runId(),
        call.context().userId(),
        call.context().purpose(),
        call.context().source(),
        call.context().callKind(),
        call.context().stepIndex(),
        call.provider(),
        call.model(),
        call.status(),
        call.errorCode(),
        call.usage(),
        call.startedAt(),
        call.completedAt());
  }

  private AiLlmCallUsageUpdate toUpdate(AiLlmCallUsage call) {
    return new AiLlmCallUsageUpdate(
        call.context().callId(),
        call.status(),
        call.provider(),
        call.model(),
        call.errorCode(),
        call.usage(),
        call.completedAt());
  }

  private static AiUsage toAiUsage(LlmUsage usage) {
    if (usage == null) {
      return AiUsage.zero();
    }
    return new AiUsage(
        usage.inputTokens(),
        usage.outputTokens(),
        usage.cachedTokens(),
        usage.reasoningTokens(),
        usage.totalTokens());
  }

  private void persistFailure(String callId, String operation, RuntimeException exception) {
    log.error(
        "AI accounting persistence failed. operation={} callId={} exceptionType={}",
        operation,
        callId,
        exception.getClass().getSimpleName());
    increment(PERSIST_FAILURES_TOTAL);
  }

  private void increment(String metric) {
    if (meterRegistry != null) {
      Counter.builder(metric).register(meterRegistry).increment();
    }
  }
}
