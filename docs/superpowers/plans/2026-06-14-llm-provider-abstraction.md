# LLM Provider Abstraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a provider-neutral LLM contract in `backend/llm-core` that supports chat completion, streaming events, tools, structured output, model capability discovery, usage, and unified errors.

**Architecture:** Add focused immutable Java records/enums in `llm-core`, keep provider SDK details in `llm-openai`, and expose `LlmGateway` as the upper-layer entry point. Preserve the existing `LlmClient` surface temporarily as a deprecated compatibility adapter so current modules can migrate incrementally.

**Tech Stack:** Java 17 records/sealed interfaces, Maven multi-module, JUnit 5, AssertJ, Jackson `JsonNode`, JDK `Flow.Publisher`.

---

## File Structure

- Modify: `backend/llm-core/pom.xml`
  - Add Jackson Databind for `JsonNode` in tool schemas and structured outputs.
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmProviderId.java`
  - Small value object for provider identifiers.
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmModelId.java`
  - Small value object for model identifiers.
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmCapability.java`
  - Provider/model capability enum.
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmFinishReason.java`
  - Unified completion finish reasons.
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmUsage.java`
  - Token usage record.
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmGenerationOptions.java`
  - Common generation options.
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmModelSelector.java`
  - Request-time provider/model/capability selector.
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmProviderCapabilities.java`
  - Provider capabilities and model registry snapshot.
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmModelDescriptor.java`
  - Model metadata and supported capabilities.
- Modify: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmMessage.java`
  - Upgrade from string content to immutable multi-part content while preserving `ofText` convenience factories.
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmContentPart.java`
  - Sealed content part hierarchy for text, image, file, tool result, and custom parts.
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmToolSpec.java`
  - Tool definition with JSON Schema.
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmToolCall.java`
  - Tool call returned by models.
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmToolChoice.java`
  - Tool choice policy.
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmResponseFormat.java`
  - Text, JSON object, and JSON Schema output modes.
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmCompletionRequest.java`
  - New provider-neutral completion request.
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmCompletionResult.java`
  - New provider-neutral non-streaming result.
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmStreamEvent.java`
  - Stream event sealed hierarchy.
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmProvider.java`
  - Provider implementation contract.
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmGateway.java`
  - Upper-layer gateway contract.
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/DefaultLlmGateway.java`
  - Minimal provider registry, selector, and capability validation implementation.
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmErrorCode.java`
  - Unified error code enum.
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmException.java`
  - Unified runtime exception.
- Modify: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmRequest.java`
  - Mark legacy request as deprecated and adapt to new message factory.
- Modify: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmResponse.java`
  - Mark legacy response as deprecated.
- Modify: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmClient.java`
  - Mark legacy client as deprecated.
- Modify: `backend/llm-core/src/test/java/org/congcong/algomentor/llm/core/LlmRequestTest.java`
  - Keep legacy compatibility coverage.
- Create: `backend/llm-core/src/test/java/org/congcong/algomentor/llm/core/LlmCoreModelTest.java`
  - Unit tests for immutable request, message, tool, format, usage, and error models.
- Create: `backend/llm-core/src/test/java/org/congcong/algomentor/llm/core/DefaultLlmGatewayTest.java`
  - Unit tests for provider selection and capability validation.
- Modify: `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentRunner.java`
  - Migrate from `LlmClient` to `LlmGateway`.
- Modify: `backend/agent-core/src/test/java/org/congcong/algomentor/agent/core/AgentRunnerTest.java`
  - Use fake `LlmGateway`.
- Modify: `backend/llm-openai/src/main/java/org/congcong/algomentor/llm/openai/OpenAiLlmClient.java`
  - Temporarily implement both deprecated `LlmClient` and new `LlmProvider` with unsupported real API calls.
- Modify: `backend/llm-openai/src/test/java/org/congcong/algomentor/llm/openai/OpenAiLlmPropertiesTest.java`
  - Add provider metadata sanity coverage if needed.
- Modify: `docs/code-index.md`
  - Update `llm-core`, `llm-openai`, and `agent-core` descriptions after implementation.

---

### Task 1: Add Core Dependency And Base Value Objects

**Files:**
- Modify: `backend/llm-core/pom.xml`
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmProviderId.java`
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmModelId.java`
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmCapability.java`
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmFinishReason.java`
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmUsage.java`
- Test: `backend/llm-core/src/test/java/org/congcong/algomentor/llm/core/LlmCoreModelTest.java`

- [ ] **Step 1: Write failing tests for identifiers and usage**

Create `backend/llm-core/src/test/java/org/congcong/algomentor/llm/core/LlmCoreModelTest.java`:

```java
package org.congcong.algomentor.llm.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LlmCoreModelTest {

  @Test
  void createsProviderAndModelIdentifiers() {
    assertThat(LlmProviderId.of("openai").value()).isEqualTo("openai");
    assertThat(LlmModelId.of("gpt-5.2").value()).isEqualTo("gpt-5.2");
  }

  @Test
  void rejectsBlankIdentifiers() {
    assertThatThrownBy(() -> LlmProviderId.of(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("LLM provider id must not be blank");
    assertThatThrownBy(() -> LlmModelId.of(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("LLM model id must not be blank");
  }

  @Test
  void calculatesTotalTokensWhenNotProvided() {
    LlmUsage usage = new LlmUsage(12, 8, 3, 2, null);

    assertThat(usage.totalTokens()).isEqualTo(20);
    assertThat(usage.cachedTokens()).isEqualTo(3);
    assertThat(usage.reasoningTokens()).isEqualTo(2);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -f backend/pom.xml -pl llm-core -B -ntp -Dmaven.repo.local=../.m2/repository test
```

Expected: FAIL with compilation errors for missing `LlmProviderId`, `LlmModelId`, and `LlmUsage`.

- [ ] **Step 3: Add Jackson dependency**

Modify `backend/llm-core/pom.xml` dependencies:

```xml
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
    </dependency>
```

- [ ] **Step 4: Add provider id**

Create `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmProviderId.java`:

```java
package org.congcong.algomentor.llm.core;

public record LlmProviderId(String value) {

  public LlmProviderId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("LLM provider id must not be blank");
    }
    value = value.trim();
  }

  public static LlmProviderId of(String value) {
    return new LlmProviderId(value);
  }
}
```

- [ ] **Step 5: Add model id**

Create `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmModelId.java`:

```java
package org.congcong.algomentor.llm.core;

public record LlmModelId(String value) {

  public LlmModelId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("LLM model id must not be blank");
    }
    value = value.trim();
  }

  public static LlmModelId of(String value) {
    return new LlmModelId(value);
  }
}
```

- [ ] **Step 6: Add capability enum**

Create `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmCapability.java`:

```java
package org.congcong.algomentor.llm.core;

public enum LlmCapability {
  CHAT_COMPLETION,
  STREAMING,
  TOOL_CALLING,
  STRUCTURED_OUTPUT,
  JSON_SCHEMA_OUTPUT,
  VISION_INPUT,
  FILE_INPUT,
  REASONING_EFFORT,
  TOKEN_USAGE,
  CACHED_TOKEN_USAGE,
  EMBEDDING
}
```

- [ ] **Step 7: Add finish reason enum**

Create `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmFinishReason.java`:

```java
package org.congcong.algomentor.llm.core;

public enum LlmFinishReason {
  STOP,
  LENGTH,
  TOOL_CALLS,
  CONTENT_FILTER,
  ERROR,
  UNKNOWN
}
```

- [ ] **Step 8: Add usage record**

Create `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmUsage.java`:

```java
package org.congcong.algomentor.llm.core;

public record LlmUsage(
    int inputTokens,
    int outputTokens,
    int cachedTokens,
    int reasoningTokens,
    Integer totalTokens
) {

  public LlmUsage {
    if (inputTokens < 0 || outputTokens < 0 || cachedTokens < 0 || reasoningTokens < 0) {
      throw new IllegalArgumentException("LLM token usage values must not be negative");
    }
    if (totalTokens == null) {
      totalTokens = inputTokens + outputTokens;
    }
    if (totalTokens < 0) {
      throw new IllegalArgumentException("LLM total token usage must not be negative");
    }
  }

  public static LlmUsage empty() {
    return new LlmUsage(0, 0, 0, 0, 0);
  }
}
```

- [ ] **Step 9: Run tests to verify they pass**

Run:

```bash
mvn -f backend/pom.xml -pl llm-core -B -ntp -Dmaven.repo.local=../.m2/repository test
```

Expected: PASS for `LlmCoreModelTest` and existing `LlmRequestTest`.

- [ ] **Step 10: Commit**

```bash
git add backend/llm-core/pom.xml \
  backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmProviderId.java \
  backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmModelId.java \
  backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmCapability.java \
  backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmFinishReason.java \
  backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmUsage.java \
  backend/llm-core/src/test/java/org/congcong/algomentor/llm/core/LlmCoreModelTest.java
git commit -m "feat: add llm core identifiers and usage"
```

---

### Task 2: Add Message Content Parts And Tool Models

**Files:**
- Modify: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmMessage.java`
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmContentPart.java`
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmToolSpec.java`
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmToolCall.java`
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmToolChoice.java`
- Test: `backend/llm-core/src/test/java/org/congcong/algomentor/llm/core/LlmCoreModelTest.java`

- [ ] **Step 1: Extend tests for message content and tools**

Append these tests to `LlmCoreModelTest`:

```java
  @Test
  void createsTextAndToolResultMessages() {
    LlmMessage user = LlmMessage.user("Explain binary search");
    LlmMessage tool = LlmMessage.toolResult("call-1", com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("ok", true));

    assertThat(user.role()).isEqualTo(LlmMessage.Role.USER);
    assertThat(user.text()).isEqualTo("Explain binary search");
    assertThat(tool.role()).isEqualTo(LlmMessage.Role.TOOL);
    assertThat(tool.toolCallId()).isEqualTo("call-1");
  }

  @Test
  void createsToolSpecAndSpecificToolChoice() {
    com.fasterxml.jackson.databind.JsonNode schema =
        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("type", "object");

    LlmToolSpec spec = new LlmToolSpec("search_problem", "Search an algorithm problem", schema, true);
    LlmToolChoice choice = LlmToolChoice.specific("search_problem");

    assertThat(spec.name()).isEqualTo("search_problem");
    assertThat(spec.strict()).isTrue();
    assertThat(choice.mode()).isEqualTo(LlmToolChoice.Mode.SPECIFIC);
    assertThat(choice.toolName()).isEqualTo("search_problem");
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -f backend/pom.xml -pl llm-core -B -ntp -Dmaven.repo.local=../.m2/repository test
```

Expected: FAIL with missing content/tool classes or methods.

- [ ] **Step 3: Add content part hierarchy**

Create `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmContentPart.java`:

```java
package org.congcong.algomentor.llm.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public sealed interface LlmContentPart
    permits LlmContentPart.Text, LlmContentPart.Image, LlmContentPart.File, LlmContentPart.ToolResult, LlmContentPart.Custom {

  record Text(String text) implements LlmContentPart {
    public Text {
      if (text == null || text.isBlank()) {
        throw new IllegalArgumentException("LLM text content must not be blank");
      }
    }
  }

  record Image(String url, String base64Data, String mediaType) implements LlmContentPart {
    public Image {
      if ((url == null || url.isBlank()) && (base64Data == null || base64Data.isBlank())) {
        throw new IllegalArgumentException("LLM image content must include url or base64 data");
      }
    }
  }

  record File(String fileId, String fileName, String mediaType) implements LlmContentPart {
    public File {
      if (fileId == null || fileId.isBlank()) {
        throw new IllegalArgumentException("LLM file content must include file id");
      }
    }
  }

  record ToolResult(JsonNode result) implements LlmContentPart {
    public ToolResult {
      if (result == null) {
        throw new IllegalArgumentException("LLM tool result content must not be null");
      }
    }
  }

  record Custom(String type, Map<String, Object> payload) implements LlmContentPart {
    public Custom {
      if (type == null || type.isBlank()) {
        throw new IllegalArgumentException("LLM custom content type must not be blank");
      }
      payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
  }
}
```

- [ ] **Step 4: Upgrade message model**

Replace `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmMessage.java` with:

```java
package org.congcong.algomentor.llm.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record LlmMessage(
    Role role,
    List<LlmContentPart> content,
    String name,
    String toolCallId,
    Map<String, Object> metadata
) {

  public LlmMessage {
    if (role == null) {
      throw new IllegalArgumentException("LLM message role must not be null");
    }
    if (content == null || content.isEmpty()) {
      throw new IllegalArgumentException("LLM message content must not be empty");
    }
    content = List.copyOf(content);
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }

  public LlmMessage(Role role, String text) {
    this(role, List.of(new LlmContentPart.Text(text)), null, null, Map.of());
  }

  public static LlmMessage system(String text) {
    return new LlmMessage(Role.SYSTEM, text);
  }

  public static LlmMessage user(String text) {
    return new LlmMessage(Role.USER, text);
  }

  public static LlmMessage assistant(String text) {
    return new LlmMessage(Role.ASSISTANT, text);
  }

  public static LlmMessage toolResult(String toolCallId, JsonNode result) {
    if (toolCallId == null || toolCallId.isBlank()) {
      throw new IllegalArgumentException("LLM tool call id must not be blank");
    }
    return new LlmMessage(Role.TOOL, List.of(new LlmContentPart.ToolResult(result)), null, toolCallId, Map.of());
  }

  public String text() {
    return content.stream()
        .filter(LlmContentPart.Text.class::isInstance)
        .map(LlmContentPart.Text.class::cast)
        .map(LlmContentPart.Text::text)
        .collect(Collectors.joining());
  }

  public enum Role {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
  }
}
```

- [ ] **Step 5: Add tool spec**

Create `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmToolSpec.java`:

```java
package org.congcong.algomentor.llm.core;

import com.fasterxml.jackson.databind.JsonNode;

public record LlmToolSpec(String name, String description, JsonNode inputSchema, boolean strict) {

  public LlmToolSpec {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("LLM tool name must not be blank");
    }
    if (description == null || description.isBlank()) {
      throw new IllegalArgumentException("LLM tool description must not be blank");
    }
    if (inputSchema == null) {
      throw new IllegalArgumentException("LLM tool input schema must not be null");
    }
  }
}
```

- [ ] **Step 6: Add tool call**

Create `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmToolCall.java`:

```java
package org.congcong.algomentor.llm.core;

import com.fasterxml.jackson.databind.JsonNode;

public record LlmToolCall(String id, String name, JsonNode arguments) {

  public LlmToolCall {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("LLM tool call id must not be blank");
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("LLM tool call name must not be blank");
    }
    if (arguments == null) {
      throw new IllegalArgumentException("LLM tool call arguments must not be null");
    }
  }
}
```

- [ ] **Step 7: Add tool choice**

Create `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmToolChoice.java`:

```java
package org.congcong.algomentor.llm.core;

public record LlmToolChoice(Mode mode, String toolName) {

  public LlmToolChoice {
    if (mode == null) {
      throw new IllegalArgumentException("LLM tool choice mode must not be null");
    }
    if (mode == Mode.SPECIFIC && (toolName == null || toolName.isBlank())) {
      throw new IllegalArgumentException("LLM specific tool choice must include tool name");
    }
    if (mode != Mode.SPECIFIC) {
      toolName = null;
    }
  }

  public static LlmToolChoice auto() {
    return new LlmToolChoice(Mode.AUTO, null);
  }

  public static LlmToolChoice none() {
    return new LlmToolChoice(Mode.NONE, null);
  }

  public static LlmToolChoice required() {
    return new LlmToolChoice(Mode.REQUIRED, null);
  }

  public static LlmToolChoice specific(String toolName) {
    return new LlmToolChoice(Mode.SPECIFIC, toolName);
  }

  public enum Mode {
    AUTO,
    NONE,
    REQUIRED,
    SPECIFIC
  }
}
```

- [ ] **Step 8: Update legacy request test if constructor expectations changed**

Modify `backend/llm-core/src/test/java/org/congcong/algomentor/llm/core/LlmRequestTest.java` assertion:

```java
    assertThat(request.messages())
        .containsExactly(LlmMessage.user("Explain binary search"));
```

- [ ] **Step 9: Run tests to verify they pass**

Run:

```bash
mvn -f backend/pom.xml -pl llm-core -B -ntp -Dmaven.repo.local=../.m2/repository test
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmMessage.java \
  backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmContentPart.java \
  backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmToolSpec.java \
  backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmToolCall.java \
  backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmToolChoice.java \
  backend/llm-core/src/test/java/org/congcong/algomentor/llm/core/LlmCoreModelTest.java \
  backend/llm-core/src/test/java/org/congcong/algomentor/llm/core/LlmRequestTest.java
git commit -m "feat: add llm message parts and tools"
```

---

### Task 3: Add Completion Request And Response Models

**Files:**
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmGenerationOptions.java`
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmModelSelector.java`
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmResponseFormat.java`
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmCompletionRequest.java`
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmCompletionResult.java`
- Test: `backend/llm-core/src/test/java/org/congcong/algomentor/llm/core/LlmCoreModelTest.java`

- [ ] **Step 1: Extend tests for completion request and structured output**

Append these tests to `LlmCoreModelTest`:

```java
  @Test
  void createsCompletionRequestWithJsonSchemaFormat() {
    com.fasterxml.jackson.databind.JsonNode schema =
        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("type", "object");

    LlmCompletionRequest request = LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(LlmProviderId.of("openai"), LlmModelId.of("gpt-5.2")))
        .messages(java.util.List.of(LlmMessage.user("Create a plan")))
        .responseFormat(new LlmResponseFormat.JsonSchema("plan", schema, true))
        .build();

    assertThat(request.modelSelector().providerId()).contains(LlmProviderId.of("openai"));
    assertThat(request.responseFormat()).isInstanceOf(LlmResponseFormat.JsonSchema.class);
    assertThat(request.toolChoice().mode()).isEqualTo(LlmToolChoice.Mode.AUTO);
  }

  @Test
  void createsCompletionResultWithStructuredOutput() {
    com.fasterxml.jackson.databind.JsonNode output =
        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("title", "Binary Search");

    LlmCompletionResult result = new LlmCompletionResult(
        LlmMessage.assistant("done"),
        java.util.List.of(),
        output,
        LlmFinishReason.STOP,
        LlmUsage.empty(),
        LlmProviderId.of("openai"),
        LlmModelId.of("gpt-5.2"),
        java.util.Map.of("requestId", "req-1"));

    assertThat(result.structuredOutput()).isEqualTo(output);
    assertThat(result.metadata()).containsEntry("requestId", "req-1");
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -f backend/pom.xml -pl llm-core -B -ntp -Dmaven.repo.local=../.m2/repository test
```

Expected: FAIL with missing completion model classes.

- [ ] **Step 3: Add generation options**

Create `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmGenerationOptions.java`:

```java
package org.congcong.algomentor.llm.core;

import java.time.Duration;
import java.util.List;

public record LlmGenerationOptions(
    Double temperature,
    Double topP,
    Integer maxOutputTokens,
    List<String> stop,
    Long seed,
    Duration timeout
) {

  public LlmGenerationOptions {
    if (temperature != null && (temperature < 0 || temperature > 2)) {
      throw new IllegalArgumentException("LLM temperature must be between 0 and 2");
    }
    if (topP != null && (topP < 0 || topP > 1)) {
      throw new IllegalArgumentException("LLM topP must be between 0 and 1");
    }
    if (maxOutputTokens != null && maxOutputTokens <= 0) {
      throw new IllegalArgumentException("LLM max output tokens must be positive");
    }
    stop = stop == null ? List.of() : List.copyOf(stop);
  }

  public static LlmGenerationOptions defaults() {
    return new LlmGenerationOptions(null, null, null, List.of(), null, null);
  }
}
```

- [ ] **Step 4: Add model selector**

Create `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmModelSelector.java`:

```java
package org.congcong.algomentor.llm.core;

import java.util.Optional;
import java.util.Set;

public record LlmModelSelector(
    LlmProviderId providerId,
    LlmModelId modelId,
    Set<LlmCapability> requiredCapabilities,
    String purpose
) {

  public LlmModelSelector {
    requiredCapabilities = requiredCapabilities == null ? Set.of() : Set.copyOf(requiredCapabilities);
  }

  public static LlmModelSelector of(LlmProviderId providerId, LlmModelId modelId) {
    return new LlmModelSelector(providerId, modelId, Set.of(), null);
  }

  public static LlmModelSelector requiring(Set<LlmCapability> capabilities) {
    return new LlmModelSelector(null, null, capabilities, null);
  }

  public Optional<LlmProviderId> providerId() {
    return Optional.ofNullable(providerId);
  }

  public Optional<LlmModelId> modelId() {
    return Optional.ofNullable(modelId);
  }
}
```

- [ ] **Step 5: Add response format**

Create `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmResponseFormat.java`:

```java
package org.congcong.algomentor.llm.core;

import com.fasterxml.jackson.databind.JsonNode;

public sealed interface LlmResponseFormat
    permits LlmResponseFormat.Text, LlmResponseFormat.JsonObject, LlmResponseFormat.JsonSchema {

  record Text() implements LlmResponseFormat {}

  record JsonObject() implements LlmResponseFormat {}

  record JsonSchema(String name, JsonNode schema, boolean strict) implements LlmResponseFormat {
    public JsonSchema {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("LLM JSON schema name must not be blank");
      }
      if (schema == null) {
        throw new IllegalArgumentException("LLM JSON schema must not be null");
      }
    }
  }
}
```

- [ ] **Step 6: Add completion request**

Create `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmCompletionRequest.java`:

```java
package org.congcong.algomentor.llm.core;

import java.util.List;
import java.util.Map;

public record LlmCompletionRequest(
    LlmModelSelector modelSelector,
    List<LlmMessage> messages,
    LlmGenerationOptions options,
    List<LlmToolSpec> tools,
    LlmToolChoice toolChoice,
    LlmResponseFormat responseFormat,
    Map<String, Object> metadata
) {

  public LlmCompletionRequest {
    if (modelSelector == null) {
      throw new IllegalArgumentException("LLM model selector must not be null");
    }
    if (messages == null || messages.isEmpty()) {
      throw new IllegalArgumentException("LLM completion request must include at least one message");
    }
    messages = List.copyOf(messages);
    options = options == null ? LlmGenerationOptions.defaults() : options;
    tools = tools == null ? List.of() : List.copyOf(tools);
    toolChoice = toolChoice == null ? LlmToolChoice.auto() : toolChoice;
    responseFormat = responseFormat == null ? new LlmResponseFormat.Text() : responseFormat;
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private LlmModelSelector modelSelector;
    private List<LlmMessage> messages;
    private LlmGenerationOptions options;
    private List<LlmToolSpec> tools;
    private LlmToolChoice toolChoice;
    private LlmResponseFormat responseFormat;
    private Map<String, Object> metadata;

    public Builder modelSelector(LlmModelSelector modelSelector) {
      this.modelSelector = modelSelector;
      return this;
    }

    public Builder messages(List<LlmMessage> messages) {
      this.messages = messages;
      return this;
    }

    public Builder options(LlmGenerationOptions options) {
      this.options = options;
      return this;
    }

    public Builder tools(List<LlmToolSpec> tools) {
      this.tools = tools;
      return this;
    }

    public Builder toolChoice(LlmToolChoice toolChoice) {
      this.toolChoice = toolChoice;
      return this;
    }

    public Builder responseFormat(LlmResponseFormat responseFormat) {
      this.responseFormat = responseFormat;
      return this;
    }

    public Builder metadata(Map<String, Object> metadata) {
      this.metadata = metadata;
      return this;
    }

    public LlmCompletionRequest build() {
      return new LlmCompletionRequest(modelSelector, messages, options, tools, toolChoice, responseFormat, metadata);
    }
  }
}
```

- [ ] **Step 7: Add completion result**

Create `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmCompletionResult.java`:

```java
package org.congcong.algomentor.llm.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

public record LlmCompletionResult(
    LlmMessage message,
    List<LlmToolCall> toolCalls,
    JsonNode structuredOutput,
    LlmFinishReason finishReason,
    LlmUsage usage,
    LlmProviderId provider,
    LlmModelId model,
    Map<String, Object> metadata
) {

  public LlmCompletionResult {
    if (message == null) {
      throw new IllegalArgumentException("LLM completion result message must not be null");
    }
    toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    finishReason = finishReason == null ? LlmFinishReason.UNKNOWN : finishReason;
    usage = usage == null ? LlmUsage.empty() : usage;
    if (provider == null) {
      throw new IllegalArgumentException("LLM completion result provider must not be null");
    }
    if (model == null) {
      throw new IllegalArgumentException("LLM completion result model must not be null");
    }
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
```

- [ ] **Step 8: Run tests to verify they pass**

Run:

```bash
mvn -f backend/pom.xml -pl llm-core -B -ntp -Dmaven.repo.local=../.m2/repository test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmGenerationOptions.java \
  backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmModelSelector.java \
  backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmResponseFormat.java \
  backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmCompletionRequest.java \
  backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmCompletionResult.java \
  backend/llm-core/src/test/java/org/congcong/algomentor/llm/core/LlmCoreModelTest.java
git commit -m "feat: add llm completion contract models"
```

---

### Task 4: Add Provider Capabilities, Gateway, And Errors

**Files:**
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmProviderCapabilities.java`
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmModelDescriptor.java`
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmProvider.java`
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmGateway.java`
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/DefaultLlmGateway.java`
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmErrorCode.java`
- Create: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmException.java`
- Create: `backend/llm-core/src/test/java/org/congcong/algomentor/llm/core/DefaultLlmGatewayTest.java`

- [ ] **Step 1: Write failing gateway tests**

Create `backend/llm-core/src/test/java/org/congcong/algomentor/llm/core/DefaultLlmGatewayTest.java`:

```java
package org.congcong.algomentor.llm.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

class DefaultLlmGatewayTest {

  @Test
  void routesRequestToSelectedProvider() {
    FakeProvider provider = new FakeProvider(Set.of(LlmCapability.CHAT_COMPLETION, LlmCapability.TOKEN_USAGE));
    DefaultLlmGateway gateway = new DefaultLlmGateway(List.of(provider), LlmProviderId.of("openai"), LlmModelId.of("gpt-5.2"));

    LlmCompletionResult result = gateway.complete(LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(LlmProviderId.of("openai"), LlmModelId.of("gpt-5.2")))
        .messages(List.of(LlmMessage.user("hello")))
        .build());

    assertThat(result.message().text()).isEqualTo("ok");
    assertThat(provider.calls).isEqualTo(1);
  }

  @Test
  void rejectsUnsupportedToolCallingCapabilityBeforeProviderCall() {
    FakeProvider provider = new FakeProvider(Set.of(LlmCapability.CHAT_COMPLETION));
    DefaultLlmGateway gateway = new DefaultLlmGateway(List.of(provider), LlmProviderId.of("openai"), LlmModelId.of("gpt-5.2"));

    LlmCompletionRequest request = LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(LlmProviderId.of("openai"), LlmModelId.of("gpt-5.2")))
        .messages(List.of(LlmMessage.user("hello")))
        .tools(List.of(new LlmToolSpec(
            "search",
            "Search problems",
            com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("type", "object"),
            true)))
        .build();

    assertThatThrownBy(() -> gateway.complete(request))
        .isInstanceOf(LlmException.class)
        .extracting("code")
        .isEqualTo(LlmErrorCode.UNSUPPORTED_CAPABILITY);
    assertThat(provider.calls).isZero();
  }

  private static final class FakeProvider implements LlmProvider {
    private final Set<LlmCapability> capabilities;
    private int calls;

    private FakeProvider(Set<LlmCapability> capabilities) {
      this.capabilities = capabilities;
    }

    @Override
    public LlmProviderId id() {
      return LlmProviderId.of("openai");
    }

    @Override
    public LlmProviderCapabilities capabilities() {
      LlmModelDescriptor model = new LlmModelDescriptor(
          id(),
          LlmModelId.of("gpt-5.2"),
          "GPT 5.2",
          capabilities,
          128000,
          8192,
          LlmGenerationOptions.defaults(),
          Map.of());
      return new LlmProviderCapabilities(capabilities, Map.of("gpt-5.2", model));
    }

    @Override
    public List<LlmModelDescriptor> models() {
      return List.copyOf(capabilities().models().values());
    }

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
      calls++;
      return new LlmCompletionResult(
          LlmMessage.assistant("ok"),
          List.of(),
          null,
          LlmFinishReason.STOP,
          LlmUsage.empty(),
          id(),
          LlmModelId.of("gpt-5.2"),
          Map.of());
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
      throw new UnsupportedOperationException("stream not used in this test");
    }
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -f backend/pom.xml -pl llm-core -B -ntp -Dmaven.repo.local=../.m2/repository test
```

Expected: FAIL with missing provider, gateway, descriptor, event, and error classes.

- [ ] **Step 3: Add model descriptor and provider capabilities**

Create `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmModelDescriptor.java`:

```java
package org.congcong.algomentor.llm.core;

import java.util.Map;
import java.util.Set;

public record LlmModelDescriptor(
    LlmProviderId providerId,
    LlmModelId modelId,
    String displayName,
    Set<LlmCapability> supportedCapabilities,
    int contextWindowTokens,
    int maxOutputTokens,
    LlmGenerationOptions defaultGenerationOptions,
    Map<String, Object> metadata
) {

  public LlmModelDescriptor {
    if (providerId == null) {
      throw new IllegalArgumentException("LLM model descriptor provider id must not be null");
    }
    if (modelId == null) {
      throw new IllegalArgumentException("LLM model descriptor model id must not be null");
    }
    displayName = displayName == null || displayName.isBlank() ? modelId.value() : displayName;
    supportedCapabilities = supportedCapabilities == null ? Set.of() : Set.copyOf(supportedCapabilities);
    if (contextWindowTokens < 0 || maxOutputTokens < 0) {
      throw new IllegalArgumentException("LLM model token limits must not be negative");
    }
    defaultGenerationOptions = defaultGenerationOptions == null ? LlmGenerationOptions.defaults() : defaultGenerationOptions;
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
```

Create `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmProviderCapabilities.java`:

```java
package org.congcong.algomentor.llm.core;

import java.util.Map;
import java.util.Set;

public record LlmProviderCapabilities(Set<LlmCapability> capabilities, Map<String, LlmModelDescriptor> models) {

  public LlmProviderCapabilities {
    capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    models = models == null ? Map.of() : Map.copyOf(models);
  }
}
```

- [ ] **Step 4: Add provider and gateway interfaces**

Create `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmProvider.java`:

```java
package org.congcong.algomentor.llm.core;

import java.util.List;
import java.util.concurrent.Flow;

public interface LlmProvider {

  LlmProviderId id();

  LlmProviderCapabilities capabilities();

  List<LlmModelDescriptor> models();

  LlmCompletionResult complete(LlmCompletionRequest request);

  Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request);
}
```

Create `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmGateway.java`:

```java
package org.congcong.algomentor.llm.core;

import java.util.concurrent.Flow;

public interface LlmGateway {

  LlmCompletionResult complete(LlmCompletionRequest request);

  Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request);
}
```

- [ ] **Step 5: Add error code and exception**

Create `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmErrorCode.java`:

```java
package org.congcong.algomentor.llm.core;

public enum LlmErrorCode {
  INVALID_REQUEST,
  UNSUPPORTED_CAPABILITY,
  AUTHENTICATION_FAILED,
  PERMISSION_DENIED,
  RATE_LIMITED,
  TIMEOUT,
  PROVIDER_UNAVAILABLE,
  CONTENT_FILTERED,
  TOOL_CALL_INVALID,
  RESPONSE_PARSE_FAILED,
  CANCELLED,
  UNKNOWN
}
```

Create `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmException.java`:

```java
package org.congcong.algomentor.llm.core;

import java.util.Map;

public class LlmException extends RuntimeException {

  private final LlmErrorCode code;
  private final LlmProviderId provider;
  private final LlmModelId model;
  private final boolean retryable;
  private final Map<String, Object> metadata;

  public LlmException(
      LlmErrorCode code,
      String message,
      LlmProviderId provider,
      LlmModelId model,
      boolean retryable,
      Map<String, Object> metadata,
      Throwable cause
  ) {
    super(message, cause);
    this.code = code == null ? LlmErrorCode.UNKNOWN : code;
    this.provider = provider;
    this.model = model;
    this.retryable = retryable;
    this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }

  public static LlmException unsupportedCapability(String message, LlmProviderId provider, LlmModelId model) {
    return new LlmException(LlmErrorCode.UNSUPPORTED_CAPABILITY, message, provider, model, false, Map.of(), null);
  }

  public LlmErrorCode code() {
    return code;
  }

  public LlmProviderId provider() {
    return provider;
  }

  public LlmModelId model() {
    return model;
  }

  public boolean retryable() {
    return retryable;
  }

  public Map<String, Object> metadata() {
    return metadata;
  }
}
```

- [ ] **Step 6: Add stream event hierarchy**

Create `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmStreamEvent.java`:

```java
package org.congcong.algomentor.llm.core;

import java.util.Map;

public sealed interface LlmStreamEvent
    permits LlmStreamEvent.MessageStart, LlmStreamEvent.ContentDelta, LlmStreamEvent.ToolCallStart,
    LlmStreamEvent.ToolCallDelta, LlmStreamEvent.ToolCallEnd, LlmStreamEvent.MessageEnd,
    LlmStreamEvent.Usage, LlmStreamEvent.Error, LlmStreamEvent.Heartbeat {

  record MessageStart(LlmProviderId provider, LlmModelId model) implements LlmStreamEvent {}

  record ContentDelta(String content) implements LlmStreamEvent {
    public ContentDelta {
      if (content == null) {
        throw new IllegalArgumentException("LLM stream content delta must not be null");
      }
    }
  }

  record ToolCallStart(String id, String name) implements LlmStreamEvent {}

  record ToolCallDelta(String id, String argumentsDelta) implements LlmStreamEvent {}

  record ToolCallEnd(LlmToolCall toolCall) implements LlmStreamEvent {}

  record MessageEnd(LlmFinishReason finishReason, Map<String, Object> metadata) implements LlmStreamEvent {
    public MessageEnd {
      finishReason = finishReason == null ? LlmFinishReason.UNKNOWN : finishReason;
      metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
  }

  record Usage(LlmUsage usage) implements LlmStreamEvent {}

  record Error(LlmException error) implements LlmStreamEvent {}

  record Heartbeat() implements LlmStreamEvent {}
}
```

- [ ] **Step 7: Add default gateway**

Create `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/DefaultLlmGateway.java`:

```java
package org.congcong.algomentor.llm.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DefaultLlmGateway implements LlmGateway {

  private final Map<LlmProviderId, LlmProvider> providers;
  private final LlmProviderId defaultProviderId;
  private final LlmModelId defaultModelId;

  public DefaultLlmGateway(List<LlmProvider> providers, LlmProviderId defaultProviderId, LlmModelId defaultModelId) {
    if (providers == null || providers.isEmpty()) {
      throw new IllegalArgumentException("LLM gateway requires at least one provider");
    }
    this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(LlmProvider::id, Function.identity()));
    this.defaultProviderId = defaultProviderId;
    this.defaultModelId = defaultModelId;
  }

  @Override
  public LlmCompletionResult complete(LlmCompletionRequest request) {
    LlmProvider provider = resolveProvider(request);
    LlmModelId modelId = resolveModel(request);
    validateCapabilities(provider, modelId, request);
    return provider.complete(request);
  }

  @Override
  public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
    LlmProvider provider = resolveProvider(request);
    LlmModelId modelId = resolveModel(request);
    validateCapabilities(provider, modelId, request);
    validate(provider.capabilities().models().get(modelId.value()).supportedCapabilities().contains(LlmCapability.STREAMING),
        "Model does not support streaming", provider.id(), modelId);
    return provider.stream(request);
  }

  private LlmProvider resolveProvider(LlmCompletionRequest request) {
    LlmProviderId providerId = request.modelSelector().providerId().orElse(defaultProviderId);
    LlmProvider provider = providers.get(providerId);
    if (provider == null) {
      throw new LlmException(LlmErrorCode.INVALID_REQUEST, "Unknown LLM provider: " + providerId.value(),
          providerId, resolveModel(request), false, Map.of(), null);
    }
    return provider;
  }

  private LlmModelId resolveModel(LlmCompletionRequest request) {
    return request.modelSelector().modelId().orElse(defaultModelId);
  }

  private void validateCapabilities(LlmProvider provider, LlmModelId modelId, LlmCompletionRequest request) {
    LlmModelDescriptor model = provider.capabilities().models().get(modelId.value());
    if (model == null) {
      throw new LlmException(LlmErrorCode.INVALID_REQUEST, "Unknown LLM model: " + modelId.value(),
          provider.id(), modelId, false, Map.of(), null);
    }
    validate(model.supportedCapabilities().containsAll(request.modelSelector().requiredCapabilities()),
        "Model does not support required capabilities", provider.id(), modelId);
    validate(request.tools().isEmpty() || model.supportedCapabilities().contains(LlmCapability.TOOL_CALLING),
        "Model does not support tool calling", provider.id(), modelId);
    validate(!(request.responseFormat() instanceof LlmResponseFormat.JsonObject)
        || model.supportedCapabilities().contains(LlmCapability.STRUCTURED_OUTPUT),
        "Model does not support structured output", provider.id(), modelId);
    validate(!(request.responseFormat() instanceof LlmResponseFormat.JsonSchema)
        || model.supportedCapabilities().contains(LlmCapability.JSON_SCHEMA_OUTPUT),
        "Model does not support JSON schema output", provider.id(), modelId);
    boolean hasImage = request.messages().stream()
        .flatMap(message -> message.content().stream())
        .anyMatch(LlmContentPart.Image.class::isInstance);
    validate(!hasImage || model.supportedCapabilities().contains(LlmCapability.VISION_INPUT),
        "Model does not support vision input", provider.id(), modelId);
  }

  private static void validate(boolean condition, String message, LlmProviderId providerId, LlmModelId modelId) {
    if (!condition) {
      throw LlmException.unsupportedCapability(message, providerId, modelId);
    }
  }
}
```

- [ ] **Step 8: Run tests to verify they pass**

Run:

```bash
mvn -f backend/pom.xml -pl llm-core -B -ntp -Dmaven.repo.local=../.m2/repository test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmProviderCapabilities.java \
  backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmModelDescriptor.java \
  backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmProvider.java \
  backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmGateway.java \
  backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/DefaultLlmGateway.java \
  backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmErrorCode.java \
  backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmException.java \
  backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmStreamEvent.java \
  backend/llm-core/src/test/java/org/congcong/algomentor/llm/core/DefaultLlmGatewayTest.java
git commit -m "feat: add llm provider gateway contract"
```

---

### Task 5: Keep Legacy Client Compatibility

**Files:**
- Modify: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmClient.java`
- Modify: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmRequest.java`
- Modify: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmResponse.java`
- Modify: `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmStreamHandler.java`
- Modify: `backend/llm-core/src/test/java/org/congcong/algomentor/llm/core/LlmRequestTest.java`

- [ ] **Step 1: Extend legacy compatibility test**

Append this test to `backend/llm-core/src/test/java/org/congcong/algomentor/llm/core/LlmRequestTest.java`:

```java
  @Test
  void convertsLegacyRequestToCompletionRequest() {
    LlmRequest legacy = LlmRequest.userPrompt("gpt-test", "Explain binary search");

    LlmCompletionRequest request = legacy.toCompletionRequest(LlmProviderId.of("openai"));

    assertThat(request.modelSelector().providerId()).contains(LlmProviderId.of("openai"));
    assertThat(request.modelSelector().modelId()).contains(LlmModelId.of("gpt-test"));
    assertThat(request.messages()).containsExactly(LlmMessage.user("Explain binary search"));
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -f backend/pom.xml -pl llm-core -B -ntp -Dmaven.repo.local=../.m2/repository test
```

Expected: FAIL because `toCompletionRequest` is missing.

- [ ] **Step 3: Mark legacy interfaces deprecated**

Modify `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmClient.java`:

```java
package org.congcong.algomentor.llm.core;

@Deprecated(forRemoval = false)
public interface LlmClient {

  LlmResponse complete(LlmRequest request);

  void stream(LlmRequest request, LlmStreamHandler handler);
}
```

Modify `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmResponse.java`:

```java
package org.congcong.algomentor.llm.core;

@Deprecated(forRemoval = false)
public record LlmResponse(String content) {

  public LlmResponse {
    if (content == null) {
      throw new IllegalArgumentException("LLM response content must not be null");
    }
  }
}
```

Modify `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmStreamHandler.java`:

```java
package org.congcong.algomentor.llm.core;

@Deprecated(forRemoval = false)
public interface LlmStreamHandler {

  void onChunk(String content);

  void onComplete();

  void onError(Throwable error);
}
```

- [ ] **Step 4: Add legacy request adapter**

Modify `backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmRequest.java`:

```java
package org.congcong.algomentor.llm.core;

import java.util.List;

@Deprecated(forRemoval = false)
public record LlmRequest(String model, List<LlmMessage> messages) {

  public LlmRequest {
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("LLM model must not be blank");
    }
    if (messages == null || messages.isEmpty()) {
      throw new IllegalArgumentException("LLM request must include at least one message");
    }
    messages = List.copyOf(messages);
  }

  public static LlmRequest userPrompt(String model, String prompt) {
    return new LlmRequest(model, List.of(LlmMessage.user(prompt)));
  }

  public LlmCompletionRequest toCompletionRequest(LlmProviderId providerId) {
    return LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(providerId, LlmModelId.of(model)))
        .messages(messages)
        .build();
  }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run:

```bash
mvn -f backend/pom.xml -pl llm-core -B -ntp -Dmaven.repo.local=../.m2/repository test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmClient.java \
  backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmRequest.java \
  backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmResponse.java \
  backend/llm-core/src/main/java/org/congcong/algomentor/llm/core/LlmStreamHandler.java \
  backend/llm-core/src/test/java/org/congcong/algomentor/llm/core/LlmRequestTest.java
git commit -m "chore: deprecate legacy llm client contract"
```

---

### Task 6: Migrate Agent Runner To Gateway

**Files:**
- Modify: `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentRunner.java`
- Modify: `backend/agent-core/src/test/java/org/congcong/algomentor/agent/core/AgentRunnerTest.java`

- [ ] **Step 1: Update agent runner test to use fake gateway**

Replace `backend/agent-core/src/test/java/org/congcong/algomentor/agent/core/AgentRunnerTest.java` with:

```java
package org.congcong.algomentor.agent.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import org.congcong.algomentor.domain.learning.LearningTopic;
import org.congcong.algomentor.llm.core.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.LlmCompletionResult;
import org.congcong.algomentor.llm.core.LlmFinishReason;
import org.congcong.algomentor.llm.core.LlmGateway;
import org.congcong.algomentor.llm.core.LlmMessage;
import org.congcong.algomentor.llm.core.LlmModelId;
import org.congcong.algomentor.llm.core.LlmProviderId;
import org.congcong.algomentor.llm.core.LlmStreamEvent;
import org.congcong.algomentor.llm.core.LlmUsage;
import org.junit.jupiter.api.Test;

class AgentRunnerTest {

  @Test
  void runsAgentThroughGateway() {
    FakeGateway gateway = new FakeGateway();
    AgentRunner runner = new AgentRunner(gateway, "gpt-test");

    AgentResponse response = runner.run(new AgentRequest(LearningTopic.of("binary search")));

    assertThat(response.content()).isEqualTo("Binary search explanation");
    assertThat(gateway.lastRequest.messages().get(0).text()).contains("binary search");
  }

  private static final class FakeGateway implements LlmGateway {
    private LlmCompletionRequest lastRequest;

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
      this.lastRequest = request;
      return new LlmCompletionResult(
          LlmMessage.assistant("Binary search explanation"),
          List.of(),
          null,
          LlmFinishReason.STOP,
          LlmUsage.empty(),
          LlmProviderId.of("test"),
          LlmModelId.of("gpt-test"),
          Map.of());
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
      throw new UnsupportedOperationException("stream not used in this test");
    }
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -f backend/pom.xml -pl agent-core -am -B -ntp -Dmaven.repo.local=../.m2/repository test
```

Expected: FAIL because `AgentRunner` still expects `LlmClient`.

- [ ] **Step 3: Migrate agent runner implementation**

Replace `backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentRunner.java` with:

```java
package org.congcong.algomentor.agent.core;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.congcong.algomentor.llm.core.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.LlmGateway;
import org.congcong.algomentor.llm.core.LlmMessage;
import org.congcong.algomentor.llm.core.LlmModelId;
import org.congcong.algomentor.llm.core.LlmModelSelector;

public class AgentRunner {

  private final Function<AgentRequest, AgentResponse> runner;

  public AgentRunner(LlmGateway llmGateway, String model) {
    this(request -> new AgentResponse(llmGateway.complete(buildRequest(model, request)).message().text()));
  }

  protected AgentRunner(Function<AgentRequest, AgentResponse> runner) {
    this.runner = Objects.requireNonNull(runner, "runner must not be null");
  }

  public AgentResponse run(AgentRequest request) {
    return runner.apply(request);
  }

  private static LlmCompletionRequest buildRequest(String model, AgentRequest request) {
    String prompt = "Explain the learning topic for an algorithm student: " + request.topic().title();
    return LlmCompletionRequest.builder()
        .modelSelector(new LlmModelSelector(null, LlmModelId.of(model), java.util.Set.of(), "topic-explanation"))
        .messages(List.of(LlmMessage.user(prompt)))
        .build();
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
mvn -f backend/pom.xml -pl agent-core -am -B -ntp -Dmaven.repo.local=../.m2/repository test
```

Expected: PASS for `llm-core` and `agent-core`.

- [ ] **Step 5: Commit**

```bash
git add backend/agent-core/src/main/java/org/congcong/algomentor/agent/core/AgentRunner.java \
  backend/agent-core/src/test/java/org/congcong/algomentor/agent/core/AgentRunnerTest.java
git commit -m "feat: migrate agent runner to llm gateway"
```

---

### Task 7: Adapt OpenAI Module To New Provider Contract

**Files:**
- Modify: `backend/llm-openai/src/main/java/org/congcong/algomentor/llm/openai/OpenAiLlmClient.java`
- Modify: `backend/llm-openai/src/test/java/org/congcong/algomentor/llm/openai/OpenAiLlmPropertiesTest.java`

- [ ] **Step 1: Add provider contract test**

Append this test to `backend/llm-openai/src/test/java/org/congcong/algomentor/llm/openai/OpenAiLlmPropertiesTest.java`:

```java
  @Test
  void disabledClientExposesOpenAiProviderCapabilities() {
    OpenAiLlmProperties properties = new OpenAiLlmProperties();
    OpenAiLlmClient client = new OpenAiLlmClient(properties);

    assertThat(client.id().value()).isEqualTo("openai");
    assertThat(client.models()).hasSize(1);
    assertThat(client.models().get(0).modelId().value()).isEqualTo("gpt-5.2");
    assertThat(client.capabilities().capabilities()).contains(
        org.congcong.algomentor.llm.core.LlmCapability.CHAT_COMPLETION,
        org.congcong.algomentor.llm.core.LlmCapability.STREAMING,
        org.congcong.algomentor.llm.core.LlmCapability.TOOL_CALLING,
        org.congcong.algomentor.llm.core.LlmCapability.STRUCTURED_OUTPUT,
        org.congcong.algomentor.llm.core.LlmCapability.JSON_SCHEMA_OUTPUT,
        org.congcong.algomentor.llm.core.LlmCapability.TOKEN_USAGE);
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -f backend/pom.xml -pl llm-openai -am -B -ntp -Dmaven.repo.local=../.m2/repository test
```

Expected: FAIL because `OpenAiLlmClient` does not implement `LlmProvider`.

- [ ] **Step 3: Implement provider metadata while preserving legacy client**

Replace `backend/llm-openai/src/main/java/org/congcong/algomentor/llm/openai/OpenAiLlmClient.java` with:

```java
package org.congcong.algomentor.llm.openai;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow;
import org.congcong.algomentor.llm.core.LlmCapability;
import org.congcong.algomentor.llm.core.LlmClient;
import org.congcong.algomentor.llm.core.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.LlmCompletionResult;
import org.congcong.algomentor.llm.core.LlmGenerationOptions;
import org.congcong.algomentor.llm.core.LlmModelDescriptor;
import org.congcong.algomentor.llm.core.LlmModelId;
import org.congcong.algomentor.llm.core.LlmProvider;
import org.congcong.algomentor.llm.core.LlmProviderCapabilities;
import org.congcong.algomentor.llm.core.LlmProviderId;
import org.congcong.algomentor.llm.core.LlmRequest;
import org.congcong.algomentor.llm.core.LlmResponse;
import org.congcong.algomentor.llm.core.LlmStreamEvent;
import org.congcong.algomentor.llm.core.LlmStreamHandler;

public class OpenAiLlmClient implements LlmClient, LlmProvider {

  private static final LlmProviderId PROVIDER_ID = LlmProviderId.of("openai");
  private static final Set<LlmCapability> DEFAULT_CAPABILITIES = Set.of(
      LlmCapability.CHAT_COMPLETION,
      LlmCapability.STREAMING,
      LlmCapability.TOOL_CALLING,
      LlmCapability.STRUCTURED_OUTPUT,
      LlmCapability.JSON_SCHEMA_OUTPUT,
      LlmCapability.TOKEN_USAGE);

  private final OpenAiLlmProperties properties;

  public OpenAiLlmClient(OpenAiLlmProperties properties) {
    this.properties = properties;
  }

  @Override
  public LlmProviderId id() {
    return PROVIDER_ID;
  }

  @Override
  public LlmProviderCapabilities capabilities() {
    LlmModelDescriptor model = new LlmModelDescriptor(
        PROVIDER_ID,
        LlmModelId.of(properties.getModel()),
        properties.getModel(),
        DEFAULT_CAPABILITIES,
        128000,
        8192,
        LlmGenerationOptions.defaults(),
        Map.of("baseUrl", properties.getBaseUrl().toString()));
    return new LlmProviderCapabilities(DEFAULT_CAPABILITIES, Map.of(properties.getModel(), model));
  }

  @Override
  public List<LlmModelDescriptor> models() {
    return List.copyOf(capabilities().models().values());
  }

  @Override
  public LlmCompletionResult complete(LlmCompletionRequest request) {
    if (!properties.isEnabled()) {
      throw new IllegalStateException("OpenAI LLM provider is disabled");
    }
    throw new UnsupportedOperationException("OpenAI completion wiring is not implemented yet");
  }

  @Override
  public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
    if (!properties.isEnabled()) {
      throw new IllegalStateException("OpenAI LLM provider is disabled");
    }
    throw new UnsupportedOperationException("OpenAI streaming wiring is not implemented yet");
  }

  @Override
  public LlmResponse complete(LlmRequest request) {
    LlmCompletionResult result = complete(request.toCompletionRequest(PROVIDER_ID));
    return new LlmResponse(result.message().text());
  }

  @Override
  public void stream(LlmRequest request, LlmStreamHandler handler) {
    if (!properties.isEnabled()) {
      handler.onError(new IllegalStateException("OpenAI LLM provider is disabled"));
      return;
    }
    handler.onError(new UnsupportedOperationException("OpenAI streaming wiring is not implemented yet"));
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
mvn -f backend/pom.xml -pl llm-openai -am -B -ntp -Dmaven.repo.local=../.m2/repository test
```

Expected: PASS for `llm-core` and `llm-openai`.

- [ ] **Step 5: Commit**

```bash
git add backend/llm-openai/src/main/java/org/congcong/algomentor/llm/openai/OpenAiLlmClient.java \
  backend/llm-openai/src/test/java/org/congcong/algomentor/llm/openai/OpenAiLlmPropertiesTest.java
git commit -m "feat: expose openai llm provider contract"
```

---

### Task 8: Update Documentation And Run Full Backend Verification

**Files:**
- Modify: `docs/code-index.md`

- [ ] **Step 1: Update code index descriptions**

Modify the backend module bullets in `docs/code-index.md`:

```markdown
- `backend/llm-core`：项目内 LLM 抽象契约，定义 provider/gateway、请求响应、消息内容 part、工具调用、结构化输出、流式事件、能力发现、用量和统一错误模型。
- `backend/llm-openai`：OpenAI provider 适配模块，隔离 `openai-java` SDK、OpenAI 配置、provider 能力描述和后续请求/响应映射。
- `backend/agent-core`：Agent 核心编排模型，面向 `LlmGateway` 组织模型调用和后续工具执行流程。
```

- [ ] **Step 2: Run full backend tests**

Run:

```bash
make backend-test
```

Expected: PASS for all backend modules.

- [ ] **Step 3: Inspect git status**

Run:

```bash
git status --short
```

Expected: only intentional changes from this task are listed. Existing unrelated user changes may still appear and must not be reverted.

- [ ] **Step 4: Commit documentation update**

```bash
git add docs/code-index.md
git commit -m "docs: update llm module index"
```

---

## Self-Review

- Spec coverage: covered module boundary, provider/gateway interfaces, request/response models, multi-part messages, tools, structured output, capability discovery, stream events, errors, compatibility migration, OpenAI adapter metadata, agent migration, docs, and test strategy.
- Placeholder scan: no unfinished placeholder markers or vague test steps are intentionally left in this plan.
- Type consistency: the plan consistently uses `LlmProviderId`, `LlmModelId`, `LlmCompletionRequest`, `LlmCompletionResult`, `LlmGateway`, `LlmProvider`, `LlmStreamEvent`, `LlmToolSpec`, `LlmToolCall`, `LlmToolChoice`, `LlmResponseFormat`, `LlmUsage`, `LlmException`, and `LlmErrorCode`.
