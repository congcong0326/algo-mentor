package org.congcong.algomentor.ai.governance.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.congcong.algomentor.ai.governance.repository.mybatis.AiLlmCallUsageMapper;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiLlmCallUsageRow;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiLlmCallUsageUpdate;
import org.congcong.algomentor.ai.governance.usage.AiDailyUsageStore;
import org.congcong.algomentor.llm.core.model.LlmInvocationTarget;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.provider.LlmProviderClient;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.provider.LlmProviderType;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmGenerationOptions;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.request.LlmReasoningEffort;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.junit.jupiter.api.Test;

class AiLlmCallAccountingServiceTest {

  private static final LlmModelId MODEL = LlmModelId.of("gpt-test");

  @Test
  void snapshotsTheResolvedEffortAtStartAndDoesNotRecomputeItAtCompletion() {
    RecordingMapper mapper = new RecordingMapper();
    AiLlmCallAccountingService service = service(mapper);
    LlmCompletionRequest request = LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(LlmProviderId.of("openai"), MODEL))
        .messages(List.of(LlmMessage.user("hello")))
        .options(LlmGenerationOptions.defaults().withReasoningEffort(LlmReasoningEffort.NONE))
        .invocationTarget(new LlmInvocationTarget(
            LlmProviderType.of("openai"),
            1L,
            1L,
            MODEL,
            Instant.parse("2026-08-03T00:00:00Z"),
            Set.of(LlmCapability.CHAT_COMPLETION, LlmCapability.REASONING_EFFORT),
            mock(LlmProviderClient.class),
            LlmReasoningEffort.HIGH))
        .build();

    AiLlmCallUsage call = service.start(request);
    service.complete(call, "openai", MODEL.value(), LlmUsage.empty());

    assertThat(call.reasoningEffort()).isEqualTo(LlmReasoningEffort.NONE);
    assertThat(mapper.rows).singleElement()
        .extracting(AiLlmCallUsageRow::reasoningEffort)
        .isEqualTo("none");
    assertThat(mapper.updates).singleElement()
        .extracting(AiLlmCallUsageUpdate::status)
        .isEqualTo(AiLlmCallStatus.COMPLETED);
  }

  @Test
  void keepsExistingMissingRequestFallbackWithoutAnEffort() {
    RecordingMapper mapper = new RecordingMapper();

    AiLlmCallUsage call = service(mapper).start(null);

    assertThat(call.reasoningEffort()).isNull();
    assertThat(mapper.rows).singleElement()
        .extracting(AiLlmCallUsageRow::provider, AiLlmCallUsageRow::model, AiLlmCallUsageRow::reasoningEffort)
        .containsExactly(null, null, null);
  }

  private static AiLlmCallAccountingService service(RecordingMapper mapper) {
    return new AiLlmCallAccountingService(
        mapper,
        mock(AiDailyUsageStore.class),
        new AiLlmCallContextResolver(),
        Clock.fixed(Instant.parse("2026-08-03T00:00:00Z"), ZoneOffset.UTC),
        ZoneOffset.UTC,
        null);
  }

  private static final class RecordingMapper implements AiLlmCallUsageMapper {
    private final List<AiLlmCallUsageRow> rows = new java.util.ArrayList<>();
    private final List<AiLlmCallUsageUpdate> updates = new java.util.ArrayList<>();

    @Override
    public int insert(AiLlmCallUsageRow row) {
      rows.add(row);
      return 1;
    }

    @Override
    public int updateTerminal(AiLlmCallUsageUpdate update) {
      updates.add(update);
      return 1;
    }
  }
}
