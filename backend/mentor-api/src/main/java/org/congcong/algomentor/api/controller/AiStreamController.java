package org.congcong.algomentor.api.controller;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionException;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.model.AiRunStatus;
import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.api.service.AiExplanationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Validated
@RestController
@RequestMapping(ApiContractConstants.AI_API_BASE_PATH)
public class AiStreamController {

  private final ObjectProvider<AiExplanationService> aiExplanationServiceProvider;

  public AiStreamController(ObjectProvider<AiExplanationService> aiExplanationServiceProvider) {
    this.aiExplanationServiceProvider = aiExplanationServiceProvider;
  }

  @GetMapping(value = ApiContractConstants.AI_EXPLANATIONS_STREAM_PATH, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamExplanation(@RequestParam(ApiContractConstants.TOPIC_PARAM) @NotBlank String topic) {
    AiExplanationService aiExplanationService = aiExplanationServiceProvider.getIfAvailable(() -> {
      throw new AiRunAdmissionException(
          AiGovernanceErrorCode.AI_PROVIDER_UNAVAILABLE,
          AiRunStatus.REJECTED_DISABLED,
          "AI 治理服务暂不可用。",
          HttpStatus.SERVICE_UNAVAILABLE,
          Map.of());
    });
    return aiExplanationService.streamExplanation(topic);
  }
}
