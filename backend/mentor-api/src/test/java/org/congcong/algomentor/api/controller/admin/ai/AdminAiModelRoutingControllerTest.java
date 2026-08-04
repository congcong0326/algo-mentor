package org.congcong.algomentor.api.controller.admin.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.ai.governance.provider.model.AiConfiguredModel;
import org.congcong.algomentor.ai.governance.provider.model.AiProviderInstance;
import org.congcong.algomentor.ai.governance.provider.repository.AiConfiguredModelRepository;
import org.congcong.algomentor.ai.governance.provider.repository.AiProviderInstanceRepository;
import org.congcong.algomentor.ai.governance.routing.AiModelRoutePolicyContent;
import org.congcong.algomentor.llm.core.request.LlmReasoningEffort;
import org.congcong.algomentor.ai.governance.routing.AiModelRoutePolicyTypeContributor;
import org.congcong.algomentor.ai.governance.provider.service.AiProviderManagementService;
import org.congcong.algomentor.policy.model.GenericPolicyStatus;
import org.congcong.algomentor.policy.model.PolicyMatchSource;
import org.congcong.algomentor.policy.model.ResolvedPolicy;
import org.congcong.algomentor.policy.repository.GenericPolicyPage;
import org.congcong.algomentor.policy.repository.GenericPolicySearchQuery;
import org.congcong.algomentor.policy.service.GenericPolicyManagementService;
import org.congcong.algomentor.policy.service.GenericPolicyQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminAiModelRoutingControllerTest {

  private final AiProviderManagementService providerManagementService =
      org.mockito.Mockito.mock(AiProviderManagementService.class);
  private final AiModelRoutePolicyTypeContributor typeContributor =
      new AiModelRoutePolicyTypeContributor(providerManagementService);
  private final GenericPolicyQueryService policyQueryService = org.mockito.Mockito.mock(GenericPolicyQueryService.class);
  private final GenericPolicyManagementService policyManagementService =
      org.mockito.Mockito.mock(GenericPolicyManagementService.class);
  private final AiConfiguredModelRepository modelRepository = org.mockito.Mockito.mock(AiConfiguredModelRepository.class);
  private final AiProviderInstanceRepository providerRepository = org.mockito.Mockito.mock(AiProviderInstanceRepository.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new AdminAiModelRoutingController(
        typeContributor,
        policyQueryService,
        policyManagementService,
        modelRepository,
        providerRepository)).build();
    when(policyManagementService.search(any(GenericPolicySearchQuery.class))).thenAnswer(invocation -> {
      GenericPolicySearchQuery query = invocation.getArgument(0);
      long total = query.status() == GenericPolicyStatus.ENABLED ? 1L : 2L;
      return new GenericPolicyPage(List.of(), total, query.page(), query.pageSize());
    });
    when(policyQueryService.resolve(any(), eq(42L))).thenReturn(Optional.empty());
  }

  @Test
  void scenariosComeFromTheRegisteredDirectoryWithoutDebugScenario() throws Exception {
    mockMvc.perform(get("/api/admin/ai/model-routing/scenarios"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.items.length()").value(7))
        .andExpect(jsonPath("$.data.items[0].scenarioCode").value("practice-chat"))
        .andExpect(jsonPath("$.data.items[0].enabledPolicyCount").value(1))
        .andExpect(jsonPath("$.data.items[0].totalPolicyCount").value(2));
  }

  @Test
  void unmatchedSimulationReturnsStableReasonWithoutLoadingModelOrProvider() throws Exception {
    mockMvc.perform(get("/api/admin/ai/model-routing/scenarios/practice-chat/effective")
            .param("userId", "42"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.scenarioCode").value("practice-chat"))
        .andExpect(jsonPath("$.data.configured").value(true))
        .andExpect(jsonPath("$.data.matched").value(false))
        .andExpect(jsonPath("$.data.reason").value("AI_MODEL_ROUTE_NOT_CONFIGURED"));

    verify(policyQueryService).resolve(any(), eq(42L));
    verifyNoInteractions(modelRepository, providerRepository);
  }

  @Test
  void matchedUnavailableModelIsReportedWithoutDispatchingProviderClient() throws Exception {
    when(policyQueryService.resolve(any(), eq(42L))).thenReturn(Optional.of(new ResolvedPolicy<>(
        7L,
        "ai.model-route.practice-chat.v1",
        "Disabled model",
        2,
        new AiModelRoutePolicyContent(101L, LlmReasoningEffort.NONE),
        PolicyMatchSource.GROUP,
        9L,
        3L)));
    when(modelRepository.findById(101L)).thenReturn(Optional.of(new AiConfiguredModel(
        101L,
        1L,
        "Primary model",
        "gpt-test",
        false,
        Instant.parse("2026-07-27T00:00:00Z"),
        Instant.parse("2026-07-27T00:00:00Z"))));
    when(providerRepository.findById(1L)).thenReturn(Optional.of(new AiProviderInstance(
        1L,
        "OpenAI primary",
        "openai",
        true,
        JsonNodeFactory.instance.objectNode().put("apiKey", "sk-full-key"),
        Instant.parse("2026-07-27T00:00:00Z"),
        Instant.parse("2026-07-27T00:00:00Z"))));

    mockMvc.perform(get("/api/admin/ai/model-routing/scenarios/practice-chat/effective")
            .param("userId", "42"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.matched").value(true))
        .andExpect(jsonPath("$.data.priority").value(2))
        .andExpect(jsonPath("$.data.matchSource").value("GROUP"))
        .andExpect(jsonPath("$.data.reasoningEffort").value("none"))
        .andExpect(jsonPath("$.data.reason").value("AI_MODEL_UNAVAILABLE"))
        .andExpect(jsonPath("$.data.model.id").value(101))
        .andExpect(jsonPath("$.data.model.enabled").value(false))
        .andExpect(jsonPath("$.data.model.providerInstanceName").value("OpenAI primary"));
  }

}
