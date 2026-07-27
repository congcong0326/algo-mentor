package org.congcong.algomentor.api.controller.admin.ai;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import org.congcong.algomentor.ai.governance.provider.model.AiConfiguredModel;
import org.congcong.algomentor.ai.governance.provider.model.AiProviderInstance;
import org.congcong.algomentor.ai.governance.provider.service.AiProviderManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminAiProviderControllerTest {

  private final AiProviderManagementService managementService = org.mockito.Mockito.mock(AiProviderManagementService.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new AdminAiProviderController(managementService))
        .setMessageConverters(new MappingJackson2HttpMessageConverter(
            Jackson2ObjectMapperBuilder.json()
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .build()))
        .build();
    AiProviderInstance provider = new AiProviderInstance(
        1L,
        "OpenAI primary",
        "openai",
        true,
        JsonNodeFactory.instance.objectNode()
            .put("apiKey", "sk-full-key")
            .put("baseUrl", "https://api.openai.com/v1"),
        Instant.parse("2026-07-27T00:00:00Z"),
        Instant.parse("2026-07-27T00:00:00Z"));
    when(managementService.listProviders()).thenReturn(List.of(provider));
    when(managementService.getProvider(1L)).thenReturn(provider);
    when(managementService.listModels(1L)).thenReturn(List.of(new AiConfiguredModel(
        101L,
        1L,
        "Primary model",
        "gpt-test",
        true,
        Instant.parse("2026-07-27T00:00:00Z"),
        Instant.parse("2026-07-27T00:00:00Z"))));
  }

  @Test
  void listExposesBaseUrlButOmitsFullProviderConfig() throws Exception {
    mockMvc.perform(get("/api/admin/ai/providers"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.items[0].id").value(1))
        .andExpect(jsonPath("$.data.items[0].baseUrl").value("https://api.openai.com/v1"))
        .andExpect(jsonPath("$.data.items[0].config").doesNotExist())
        .andExpect(jsonPath("$.data.items[0].modelCount").value(1));
  }

  @Test
  void detailReturnsTheFullConfigForAdministratorEditing() throws Exception {
    mockMvc.perform(get("/api/admin/ai/providers/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.baseUrl").value("https://api.openai.com/v1"))
        .andExpect(jsonPath("$.data.config.apiKey").value("sk-full-key"))
        .andExpect(jsonPath("$.data.config.baseUrl").value("https://api.openai.com/v1"));
  }
}
