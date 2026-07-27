package org.congcong.algomentor.ai.governance.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.ai.governance.model.AiGovernanceMetadataKeys;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.congcong.algomentor.ai.governance.repository.mybatis.AiLlmCallUsageMapper;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiLlmCallUsageRow;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiLlmCallUsageUpdate;
import org.congcong.algomentor.ai.governance.usage.AiDailyUsageStore;
import org.congcong.algomentor.llm.core.exception.LlmErrorCode;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.model.LlmInvocationTarget;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.provider.LlmProviderClient;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.provider.LlmProviderType;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.junit.jupiter.api.Test;

class AiAccountingLlmGatewayTest {

  private static final LlmProviderId PROVIDER = LlmProviderId.of("openai");
  private static final LlmModelId MODEL = LlmModelId.of("gpt-test");

  @Test
  void completeWritesOneTerminalRecordAndAccumulatesUsage() {
    RecordingMapper mapper = new RecordingMapper();
    RecordingUsageStore usageStore = new RecordingUsageStore();
    AiAccountingLlmGateway gateway = new AiAccountingLlmGateway(
        new CompletionGateway(result()),
        accountingService(mapper, usageStore));

    LlmCompletionResult result = gateway.complete(request(Map.of(
        AiGovernanceMetadataKeys.RUN_ID, "run-1",
        AiGovernanceMetadataKeys.USER_ID, 7L,
        AiGovernanceMetadataKeys.PURPOSE, "LEARNING_CHAT",
        AiGovernanceMetadataKeys.SOURCE, "PRACTICE_CHAT",
        AiGovernanceMetadataKeys.QUOTA_SCOPE, "ALL",
        AiGovernanceMetadataKeys.CALL_KIND, "AGENT_STEP",
        AiGovernanceMetadataKeys.PROVIDER_INSTANCE_ID, 12L,
        AiGovernanceMetadataKeys.CONFIGURED_MODEL_ID, 42L,
        AgentRuntimeMetadataKeys.STEP_INDEX, 2)));

    assertThat(result.model()).isEqualTo(MODEL);
    assertThat(mapper.rows).hasSize(1);
    assertThat(mapper.rows.get(0))
        .extracting(
            AiLlmCallUsageRow::callKind,
            AiLlmCallUsageRow::stepIndex,
            AiLlmCallUsageRow::providerInstanceId,
            AiLlmCallUsageRow::aiModelId)
        .containsExactly(AiLlmCallKind.AGENT_STEP, 2, 12L, 42L);
    assertThat(mapper.updates).singleElement()
        .extracting(AiLlmCallUsageUpdate::status, AiLlmCallUsageUpdate::errorCode)
        .containsExactly(AiLlmCallStatus.COMPLETED, null);
    assertThat(usageStore.usages).singleElement()
        .extracting(AiUsage::inputTokens, AiUsage::cachedTokens, AiUsage::outputTokens, AiUsage::totalTokens)
        .containsExactly(10L, 3L, 4L, 14L);
  }

  @Test
  void dynamicTargetWritesProviderAndModelSnapshotsBeforeDispatch() {
    RecordingMapper mapper = new RecordingMapper();
    AiAccountingLlmGateway gateway = new AiAccountingLlmGateway(
        new CompletionGateway(result()),
        accountingService(mapper, new RecordingUsageStore()));

    gateway.complete(dynamicRequest(trustedMetadata()));

    assertThat(mapper.rows).singleElement()
        .extracting(
            AiLlmCallUsageRow::provider,
            AiLlmCallUsageRow::model,
            AiLlmCallUsageRow::providerInstanceId,
            AiLlmCallUsageRow::aiModelId)
        .containsExactly("openai", "gpt-test", 12L, 42L);
  }

  @Test
  void completeFailureKeepsOriginalExceptionAndRecordsFailedCall() {
    RecordingMapper mapper = new RecordingMapper();
    AiAccountingLlmGateway gateway = new AiAccountingLlmGateway(
        new CompletionGateway(new LlmException(
            LlmErrorCode.TIMEOUT,
            "timeout",
            PROVIDER,
            MODEL,
            true,
            Map.of(),
            null)),
        accountingService(mapper, new RecordingUsageStore()));

    assertThatThrownBy(() -> gateway.complete(request(trustedMetadata())))
        .isInstanceOfSatisfying(LlmException.class,
            exception -> assertThat(exception.code()).isEqualTo(LlmErrorCode.TIMEOUT));

    assertThat(mapper.updates).singleElement()
        .extracting(AiLlmCallUsageUpdate::status, AiLlmCallUsageUpdate::errorCode)
        .containsExactly(AiLlmCallStatus.FAILED, "TIMEOUT");
  }

  @Test
  void streamCompletionUsesLastUsageSnapshotAndSettlesOnce() {
    RecordingMapper mapper = new RecordingMapper();
    RecordingUsageStore usageStore = new RecordingUsageStore();
    AiAccountingLlmGateway gateway = new AiAccountingLlmGateway(
        new StreamingGateway(),
        accountingService(mapper, usageStore));

    gateway.stream(request(trustedMetadata())).subscribe(new Flow.Subscriber<>() {
      @Override
      public void onSubscribe(Flow.Subscription subscription) {
        subscription.request(Long.MAX_VALUE);
      }

      @Override
      public void onNext(LlmStreamEvent item) {
      }

      @Override
      public void onError(Throwable throwable) {
        throw new AssertionError(throwable);
      }

      @Override
      public void onComplete() {
      }
    });

    assertThat(mapper.rows).hasSize(1);
    assertThat(mapper.updates).singleElement()
        .extracting(AiLlmCallUsageUpdate::status, update -> update.usage().totalTokens())
        .containsExactly(AiLlmCallStatus.COMPLETED, 21L);
    assertThat(usageStore.usages).singleElement().extracting(AiUsage::totalTokens).isEqualTo(21L);
  }

  @Test
  void accountingPersistenceFailureDoesNotHideCompletionResult() {
    RecordingMapper mapper = new RecordingMapper();
    mapper.fail = true;
    AiAccountingLlmGateway gateway = new AiAccountingLlmGateway(
        new CompletionGateway(result()),
        accountingService(mapper, new RecordingUsageStore()));

    assertThat(gateway.complete(request(trustedMetadata()))).isEqualTo(result());
  }

  @Test
  void missingMetadataFallsBackToUnknownDirectRecord() {
    RecordingMapper mapper = new RecordingMapper();
    AiAccountingLlmGateway gateway = new AiAccountingLlmGateway(
        new CompletionGateway(result()),
        accountingService(mapper, new RecordingUsageStore()));

    gateway.complete(request(Map.of()));

    assertThat(mapper.rows).singleElement()
        .extracting(AiLlmCallUsageRow::purpose, AiLlmCallUsageRow::source, AiLlmCallUsageRow::callKind)
        .containsExactly(AiLlmCallContext.UNKNOWN, AiLlmCallContext.UNKNOWN, AiLlmCallKind.DIRECT);
  }

  private static AiLlmCallAccountingService accountingService(
      RecordingMapper mapper,
      RecordingUsageStore usageStore
  ) {
    return new AiLlmCallAccountingService(
        mapper,
        usageStore,
        new AiLlmCallContextResolver(),
        Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC),
        ZoneOffset.UTC,
        null);
  }

  private static LlmCompletionRequest request(Map<String, Object> metadata) {
    return LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(PROVIDER, MODEL))
        .messages(List.of(LlmMessage.user("hello")))
        .metadata(metadata)
        .build();
  }

  private static LlmCompletionRequest dynamicRequest(Map<String, Object> metadata) {
    return LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.requiring(Set.of()))
        .messages(List.of(LlmMessage.user("hello")))
        .metadata(metadata)
        .invocationTarget(new LlmInvocationTarget(
            LlmProviderType.of("openai"),
            12L,
            42L,
            MODEL,
            Instant.parse("2026-07-27T00:00:00Z"),
            Set.of(LlmCapability.CHAT_COMPLETION),
            mock(LlmProviderClient.class)))
        .build();
  }

  private static Map<String, Object> trustedMetadata() {
    return Map.of(
        AiGovernanceMetadataKeys.RUN_ID, "run-1",
        AiGovernanceMetadataKeys.USER_ID, 7L,
        AiGovernanceMetadataKeys.PURPOSE, "LEARNING_CHAT",
        AiGovernanceMetadataKeys.SOURCE, "PRACTICE_CHAT",
        AiGovernanceMetadataKeys.QUOTA_SCOPE, "ALL");
  }

  private static LlmCompletionResult result() {
    return new LlmCompletionResult(
        LlmMessage.assistant("ready"),
        List.of(),
        JsonNodeFactory.instance.nullNode(),
        LlmFinishReason.STOP,
        new LlmUsage(10, 4, 3, 1, 14),
        PROVIDER,
        MODEL,
        Map.of());
  }

  private static final class RecordingMapper implements AiLlmCallUsageMapper {

    private final List<AiLlmCallUsageRow> rows = new ArrayList<>();
    private final List<AiLlmCallUsageUpdate> updates = new ArrayList<>();
    private boolean fail;

    @Override
    public int insert(AiLlmCallUsageRow row) {
      if (fail) {
        throw new IllegalStateException("database unavailable");
      }
      rows.add(row);
      return 1;
    }

    @Override
    public int updateTerminal(AiLlmCallUsageUpdate update) {
      if (fail) {
        throw new IllegalStateException("database unavailable");
      }
      updates.add(update);
      return 1;
    }
  }

  private static final class RecordingUsageStore implements AiDailyUsageStore {

    private final List<AiUsage> usages = new ArrayList<>();

    @Override
    public boolean tryConsumeRequest(long userId, java.time.LocalDate quotaDate, String scope, long limitCount) {
      return true;
    }

    @Override
    public void addUsage(long userId, java.time.LocalDate quotaDate, String scope, AiUsage usage) {
      usages.add(usage);
    }
  }

  private static final class CompletionGateway implements LlmGateway {

    private final LlmCompletionResult result;
    private final RuntimeException exception;

    private CompletionGateway(LlmCompletionResult result) {
      this.result = result;
      this.exception = null;
    }

    private CompletionGateway(RuntimeException exception) {
      this.result = null;
      this.exception = exception;
    }

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
      if (exception != null) {
        throw exception;
      }
      return result;
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class StreamingGateway implements LlmGateway {

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
      return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
        private boolean emitted;

        @Override
        public void request(long count) {
          if (emitted) {
            return;
          }
          emitted = true;
          subscriber.onNext(new LlmStreamEvent.MessageStart(PROVIDER, MODEL));
          subscriber.onNext(new LlmStreamEvent.Usage(new LlmUsage(12, 6, 2, 0, 21)));
          subscriber.onComplete();
        }

        @Override
        public void cancel() {
        }
      });
    }
  }
}
