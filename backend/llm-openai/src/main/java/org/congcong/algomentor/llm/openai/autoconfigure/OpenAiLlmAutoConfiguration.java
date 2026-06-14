package org.congcong.algomentor.llm.openai.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.congcong.algomentor.llm.openai.OpenAiLlmProperties;
import org.congcong.algomentor.llm.openai.OpenAiLlmProvider;
import org.congcong.algomentor.llm.openai.OpenAiResponsesClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(OpenAiLlmProperties.class)
public class OpenAiLlmAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(prefix = "algo-mentor.ai.openai", name = "enabled", havingValue = "true")
  public OpenAiResponsesClient openAiResponsesClient(OpenAiLlmProperties properties) {
    return OpenAiResponsesClient.fromProperties(properties);
  }

  @Bean
  @ConditionalOnBean(OpenAiResponsesClient.class)
  @ConditionalOnMissingBean
  @ConditionalOnProperty(prefix = "algo-mentor.ai.openai", name = "enabled", havingValue = "true")
  public OpenAiLlmProvider openAiLlmProvider(
      OpenAiLlmProperties properties,
      OpenAiResponsesClient client,
      ObjectProvider<ObjectMapper> objectMapper) {
    ObjectMapper mapper = objectMapper.getIfAvailable(ObjectMapper::new);
    return new OpenAiLlmProvider(properties, client, mapper);
  }
}
