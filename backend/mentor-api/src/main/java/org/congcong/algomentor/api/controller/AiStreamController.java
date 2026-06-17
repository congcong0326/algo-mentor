package org.congcong.algomentor.api.controller;

import jakarta.validation.constraints.NotBlank;
import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.api.service.AiExplanationService;
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

  private final AiExplanationService aiExplanationService;

  public AiStreamController(AiExplanationService aiExplanationService) {
    this.aiExplanationService = aiExplanationService;
  }

  @GetMapping(value = ApiContractConstants.AI_EXPLANATIONS_STREAM_PATH, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamExplanation(@RequestParam(ApiContractConstants.TOPIC_PARAM) @NotBlank String topic) {
    return aiExplanationService.streamExplanation(topic);
  }
}
