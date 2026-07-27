package org.congcong.algomentor.ai.governance.provider.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import org.congcong.algomentor.ai.governance.provider.model.AiProviderInstance;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.provider.LlmProviderAdapter;
import org.congcong.algomentor.llm.core.provider.LlmProviderClient;
import org.congcong.algomentor.llm.core.provider.LlmProviderInstanceSpec;
import org.congcong.algomentor.llm.core.provider.LlmProviderType;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.junit.jupiter.api.Test;

class ProviderClientRegistryTest {

  private static final LlmProviderType OPENAI = LlmProviderType.of("openai");

  @Test
  void reusesClientForTheSameProviderInstanceVersion() {
    RecordingAdapter adapter = new RecordingAdapter();
    ProviderClientRegistry registry = new ProviderClientRegistry();
    AiProviderInstance instance = instance("2026-07-27T00:00:00Z");

    ProviderClientHandle first = registry.acquire(instance, adapter);
    ProviderClientHandle second = registry.acquire(instance, adapter);

    assertThat(second).isSameAs(first);
    assertThat(adapter.creations.get()).isOne();
  }

  @Test
  void replacesCurrentHandleWhenProviderVersionChangesWhileOldHandleRemainsUsable() {
    RecordingAdapter adapter = new RecordingAdapter();
    ProviderClientRegistry registry = new ProviderClientRegistry();

    ProviderClientHandle oldHandle = registry.acquire(instance("2026-07-27T00:00:00Z"), adapter);
    ProviderClientHandle newHandle = registry.acquire(instance("2026-07-27T00:00:01Z"), adapter);

    assertThat(newHandle).isNotSameAs(oldHandle);
    assertThat(oldHandle.client()).isNotSameAs(newHandle.client());
    assertThat(adapter.creations.get()).isEqualTo(2);
  }

  @Test
  void failedCreationDoesNotReplaceTheCurrentHandle() {
    RecordingAdapter adapter = new RecordingAdapter();
    ProviderClientRegistry registry = new ProviderClientRegistry();
    ProviderClientHandle current = registry.acquire(instance("2026-07-27T00:00:00Z"), adapter);
    adapter.fail = true;

    assertThatThrownBy(() -> registry.acquire(instance("2026-07-27T00:00:01Z"), adapter))
        .isInstanceOf(IllegalStateException.class);

    adapter.fail = false;
    assertThat(registry.acquire(instance("2026-07-27T00:00:00Z"), adapter)).isSameAs(current);
  }

  private static AiProviderInstance instance(String updatedAt) {
    return new AiProviderInstance(
        11L,
        "OpenAI main",
        "openai",
        true,
        JsonNodeFactory.instance.objectNode().put("apiKey", "test"),
        Instant.parse("2026-07-26T00:00:00Z"),
        Instant.parse(updatedAt));
  }

  private static final class RecordingAdapter implements LlmProviderAdapter {

    private final AtomicInteger creations = new AtomicInteger();
    private boolean fail;

    @Override
    public LlmProviderType providerType() {
      return OPENAI;
    }

    @Override
    public String displayName() {
      return "OpenAI";
    }

    @Override
    public Set<LlmCapability> supportedCapabilities() {
      return Set.of(LlmCapability.CHAT_COMPLETION);
    }

    @Override
    public void validateConfig(JsonNode config) {
    }

    @Override
    public LlmProviderClient createClient(LlmProviderInstanceSpec instance) {
      if (fail) {
        throw new IllegalStateException("client creation failed");
      }
      return new StubClient(creations.incrementAndGet());
    }
  }

  private record StubClient(int version) implements LlmProviderClient {

    @Override
    public LlmCompletionResult complete(LlmModelId upstreamModelId, LlmCompletionRequest request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmModelId upstreamModelId, LlmCompletionRequest request) {
      throw new UnsupportedOperationException();
    }
  }
}
