package org.congcong.algomentor.api.controller.admin.ai;

import java.math.BigDecimal;
import java.util.List;
import org.congcong.algomentor.ai.governance.adminquery.AiAdminUsageQueryService;
import org.congcong.algomentor.ai.governance.adminquery.AiUsageQuery;
import org.congcong.algomentor.ai.governance.model.AiGovernanceAdminException;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.policy.AiGovernanceProperties;
import org.congcong.algomentor.ai.governance.pricing.AiModelPrice;
import org.congcong.algomentor.ai.governance.pricing.AiModelPriceAdminService;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiModelPricePageResponse;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiModelPriceResponse;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiModelPriceWriteRequest;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "spring.datasource.url")
@RequestMapping(AdminAiApiContractConstants.ADMIN_AI_BASE_PATH)
public class AdminAiModelPriceController {

  private final AiModelPriceAdminService priceService;
  private final AiAdminUsageQueryService usageQueryService;
  private final AiGovernanceProperties properties;
  private final AdminAiResponseMapper responseMapper;

  public AdminAiModelPriceController(
      AiModelPriceAdminService priceService,
      AiAdminUsageQueryService usageQueryService,
      AiGovernanceProperties properties,
      IdentityUserRepository identityUserRepository
  ) {
    this.priceService = priceService;
    this.usageQueryService = usageQueryService;
    this.properties = properties;
    this.responseMapper = new AdminAiResponseMapper(identityUserRepository);
  }

  @GetMapping(AdminAiApiContractConstants.MODEL_PRICES_PATH)
  public ApiResponse<AdminAiModelPricePageResponse> list(
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to
  ) {
    AiUsageQuery query = AdminAiRequestSupport.usageQuery(
        from, to, null, null, null, null, null, properties);
    List<AdminAiModelPriceResponse> prices = priceService.list().stream().map(responseMapper::price).toList();
    return ApiResponse.success(new AdminAiModelPricePageResponse(
        prices,
        responseMapper.unpriced(usageQueryService.observedUnpricedModels(query))));
  }

  @PostMapping(AdminAiApiContractConstants.MODEL_PRICES_PATH)
  public ApiResponse<AdminAiModelPriceResponse> create(
      @RequestBody AdminAiModelPriceWriteRequest request,
      Authentication authentication
  ) {
    AiModelPrice created = priceService.create(
        value(request, AdminAiModelPriceWriteRequest::provider),
        value(request, AdminAiModelPriceWriteRequest::model),
        decimal(request, AdminAiModelPriceWriteRequest::inputPricePerMillion),
        decimal(request, AdminAiModelPriceWriteRequest::cachedInputPricePerMillion),
        decimal(request, AdminAiModelPriceWriteRequest::outputPricePerMillion),
        decimal(request, AdminAiModelPriceWriteRequest::costMultiplier),
        request == null ? null : request.enabled(),
        AdminAiRequestSupport.requireOperatorId(authentication));
    return ApiResponse.success(responseMapper.price(created));
  }

  @PatchMapping(AdminAiApiContractConstants.MODEL_PRICE_ID_PATH)
  public ApiResponse<AdminAiModelPriceResponse> update(
      @PathVariable long priceId,
      @RequestBody AdminAiModelPriceWriteRequest request,
      Authentication authentication
  ) {
    AiModelPrice updated = priceService.update(
        priceId,
        value(request, AdminAiModelPriceWriteRequest::provider),
        value(request, AdminAiModelPriceWriteRequest::model),
        decimal(request, AdminAiModelPriceWriteRequest::inputPricePerMillion),
        decimal(request, AdminAiModelPriceWriteRequest::cachedInputPricePerMillion),
        decimal(request, AdminAiModelPriceWriteRequest::outputPricePerMillion),
        decimal(request, AdminAiModelPriceWriteRequest::costMultiplier),
        request == null ? null : request.enabled(),
        AdminAiRequestSupport.requireOperatorId(authentication));
    return ApiResponse.success(responseMapper.price(updated));
  }

  private static String value(
      AdminAiModelPriceWriteRequest request,
      java.util.function.Function<AdminAiModelPriceWriteRequest, String> accessor
  ) {
    return request == null ? null : accessor.apply(request);
  }

  private static BigDecimal decimal(
      AdminAiModelPriceWriteRequest request,
      java.util.function.Function<AdminAiModelPriceWriteRequest, String> accessor
  ) {
    String value = value(request, accessor);
    if (value == null || value.isBlank()) {
      throw new AiGovernanceAdminException(
          AiGovernanceErrorCode.AI_MODEL_PRICE_INVALID,
          "AI model price decimal value is required.");
    }
    try {
      return new BigDecimal(value.trim());
    } catch (NumberFormatException exception) {
      throw new AiGovernanceAdminException(
          AiGovernanceErrorCode.AI_MODEL_PRICE_INVALID,
          "AI model price decimal value is invalid.",
          exception);
    }
  }
}
