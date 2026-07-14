package org.congcong.algomentor.api.controller.admin.ai;

import org.congcong.algomentor.ai.governance.adminquery.AiAdminUsageQueryService;
import org.congcong.algomentor.ai.governance.adminquery.AiUsageQuery;
import org.congcong.algomentor.ai.governance.policy.AiGovernanceProperties;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiUsageByModelResponse;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiUsageBySourceResponse;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiUsageByUserPageResponse;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiUsageSummaryResponse;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "spring.datasource.url")
@RequestMapping(AdminAiApiContractConstants.ADMIN_AI_BASE_PATH)
public class AdminAiUsageController {

  private final AiAdminUsageQueryService service;
  private final AiGovernanceProperties properties;
  private final AdminAiResponseMapper responseMapper;

  public AdminAiUsageController(
      AiAdminUsageQueryService service,
      AiGovernanceProperties properties,
      IdentityUserRepository identityUserRepository
  ) {
    this.service = service;
    this.properties = properties;
    this.responseMapper = new AdminAiResponseMapper(identityUserRepository);
  }

  @GetMapping(AdminAiApiContractConstants.USAGE_SUMMARY_PATH)
  public ApiResponse<AdminAiUsageSummaryResponse> summary(
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) Long userId,
      @RequestParam(required = false) String provider,
      @RequestParam(required = false) String model,
      @RequestParam(required = false) String purpose,
      @RequestParam(required = false) String source
  ) {
    return ApiResponse.success(responseMapper.summary(
        service.summary(query(from, to, userId, provider, model, purpose, source))));
  }

  @GetMapping(AdminAiApiContractConstants.USAGE_BY_USER_PATH)
  public ApiResponse<AdminAiUsageByUserPageResponse> byUser(
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) Long userId,
      @RequestParam(required = false) String provider,
      @RequestParam(required = false) String model,
      @RequestParam(required = false) String purpose,
      @RequestParam(required = false) String source,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(defaultValue = "1") int page
  ) {
    return ApiResponse.success(responseMapper.userPage(
        service.byUser(query(from, to, userId, provider, model, purpose, source), page, pageSize)));
  }

  @GetMapping(AdminAiApiContractConstants.USAGE_BY_MODEL_PATH)
  public ApiResponse<java.util.List<AdminAiUsageByModelResponse>> byModel(
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) Long userId,
      @RequestParam(required = false) String provider,
      @RequestParam(required = false) String model,
      @RequestParam(required = false) String purpose,
      @RequestParam(required = false) String source
  ) {
    return ApiResponse.success(responseMapper.models(
        service.byModel(query(from, to, userId, provider, model, purpose, source))));
  }

  @GetMapping(AdminAiApiContractConstants.USAGE_BY_SOURCE_PATH)
  public ApiResponse<java.util.List<AdminAiUsageBySourceResponse>> bySource(
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) Long userId,
      @RequestParam(required = false) String provider,
      @RequestParam(required = false) String model,
      @RequestParam(required = false) String purpose,
      @RequestParam(required = false) String source
  ) {
    return ApiResponse.success(responseMapper.sources(
        service.bySource(query(from, to, userId, provider, model, purpose, source))));
  }

  private AiUsageQuery query(
      String from,
      String to,
      Long userId,
      String provider,
      String model,
      String purpose,
      String source
  ) {
    return AdminAiRequestSupport.usageQuery(
        from, to, userId, provider, model, purpose, source, properties);
  }
}
