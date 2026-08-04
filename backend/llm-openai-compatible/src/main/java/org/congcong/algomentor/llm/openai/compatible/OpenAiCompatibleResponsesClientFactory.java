package org.congcong.algomentor.llm.openai.compatible;

/** 创建 SDK Responses client 的窄注入边界，便于 provider adapter 测试。 */
@FunctionalInterface
public interface OpenAiCompatibleResponsesClientFactory {

  OpenAiCompatibleResponsesClient create(OpenAiCompatibleConnectionConfig config);
}
