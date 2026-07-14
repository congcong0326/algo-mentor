package org.congcong.algomentor.auth.controller.admin;

import org.congcong.algomentor.auth.betaaccess.service.BetaAccessAdminService;
import org.congcong.algomentor.auth.betaaccess.service.BetaAccessErrorCode;
import org.congcong.algomentor.auth.betaaccess.service.BetaAccessException;
import org.congcong.algomentor.auth.controller.admin.model.BetaAccessPageResponse;
import org.congcong.algomentor.auth.controller.admin.model.BetaAccessSettingsResponse;
import org.congcong.algomentor.auth.controller.admin.model.BetaAccessSettingsUpdateRequest;
import org.congcong.algomentor.auth.controller.admin.model.BetaAllowedEmailBatchRequest;
import org.congcong.algomentor.auth.controller.admin.model.BetaAllowedEmailBatchResponse;
import org.congcong.algomentor.auth.controller.admin.model.BetaAllowedEmailRemovalResponse;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(BetaAccessApiContractConstants.BASE_PATH)
public class BetaAccessController {

  private final BetaAccessAdminService service;

  public BetaAccessController(BetaAccessAdminService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<BetaAccessPageResponse> get(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(defaultValue = "") String keyword
  ) {
    return ApiResponse.success(BetaAccessPageResponse.from(service.getPage(page, pageSize, keyword)));
  }

  @PatchMapping(BetaAccessApiContractConstants.SETTINGS_PATH)
  public ApiResponse<BetaAccessSettingsResponse> updateSettings(
      @RequestBody BetaAccessSettingsUpdateRequest request,
      Authentication authentication
  ) {
    return ApiResponse.success(BetaAccessSettingsResponse.from(service.updateSettings(
        request == null ? null : request.emailAllowlistEnabled(),
        operatorId(authentication))));
  }

  @PostMapping(BetaAccessApiContractConstants.EMAILS_PATH)
  public ApiResponse<BetaAllowedEmailBatchResponse> addEmails(
      @RequestBody BetaAllowedEmailBatchRequest request,
      Authentication authentication
  ) {
    return ApiResponse.success(BetaAllowedEmailBatchResponse.from(service.addEmails(
        request == null ? null : request.emails(),
        operatorId(authentication))));
  }

  @DeleteMapping(BetaAccessApiContractConstants.EMAIL_ID_PATH)
  public ApiResponse<BetaAllowedEmailRemovalResponse> removeEmail(
      @PathVariable long allowedEmailId,
      Authentication authentication
  ) {
    return ApiResponse.success(BetaAllowedEmailRemovalResponse.from(
        service.removeEmail(allowedEmailId, operatorId(authentication))));
  }

  private static long operatorId(Authentication authentication) {
    if (authentication == null || authentication.getName() == null) {
      throw new BetaAccessException(BetaAccessErrorCode.AUTH_REQUEST_INVALID, "无法解析管理员身份。");
    }
    try {
      return Long.parseLong(authentication.getName());
    } catch (NumberFormatException exception) {
      throw new BetaAccessException(BetaAccessErrorCode.AUTH_REQUEST_INVALID, "无法解析管理员身份。");
    }
  }
}
