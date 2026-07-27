package org.congcong.algomentor.llm.openai;

/** OpenAI SDK client 的可替换构造边界，供 adapter 单元测试注入 fake client。 */
@FunctionalInterface
public interface OpenAiResponsesClientFactory {

  OpenAiResponsesClient create(OpenAiProviderConfig config);
}
