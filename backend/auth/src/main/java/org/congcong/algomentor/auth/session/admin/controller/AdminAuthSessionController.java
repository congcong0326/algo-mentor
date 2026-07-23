package org.congcong.algomentor.auth.session.admin.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.congcong.algomentor.auth.session.admin.controller.model.AdminAuthSessionListQuery;
import org.congcong.algomentor.auth.session.admin.controller.model.AdminAuthSessionPageResponse;
import org.congcong.algomentor.auth.session.admin.controller.model.AdminAuthSessionRevocationResponse;
import org.congcong.algomentor.auth.session.admin.service.AuthSessionAdminErrorCode;
import org.congcong.algomentor.auth.session.admin.service.AuthSessionAdminException;
import org.congcong.algomentor.auth.session.admin.service.AuthSessionAdminService;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AdminAuthSessionApiContractConstants.ADMIN_AUTH_SESSIONS_BASE_PATH)
public class AdminAuthSessionController {

  private final AuthSessionAdminService service;

  public AdminAuthSessionController(AuthSessionAdminService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<AdminAuthSessionPageResponse> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(defaultValue = "") String keyword,
      @RequestParam(defaultValue = "ALL") String activity,
      HttpServletRequest request
  ) {
    return ApiResponse.success(AdminAuthSessionPageResponse.from(service.list(
        new AdminAuthSessionListQuery(page, pageSize, keyword, activity),
        currentSessionId(request))));
  }

  @DeleteMapping(AdminAuthSessionApiContractConstants.SESSION_REF_PATH)
  public ApiResponse<AdminAuthSessionRevocationResponse> revoke(
      @PathVariable String sessionRef,
      Authentication authentication,
      HttpServletRequest request
  ) {
    return ApiResponse.success(AdminAuthSessionRevocationResponse.from(service.revoke(
        sessionRef,
        currentSessionId(request),
        operatorId(authentication))));
  }

  private static String currentSessionId(HttpServletRequest request) {
    HttpSession session = request == null ? null : request.getSession(false);
    return session == null ? null : session.getId();
  }

  private static long operatorId(Authentication authentication) {
    if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
      throw new AuthSessionAdminException(
          AuthSessionAdminErrorCode.AUTH_SESSION_QUERY_INVALID,
          "无法解析管理员身份。");
    }
    try {
      return Long.parseLong(authentication.getName());
    } catch (NumberFormatException exception) {
      throw new AuthSessionAdminException(
          AuthSessionAdminErrorCode.AUTH_SESSION_QUERY_INVALID,
          "无法解析管理员身份。");
    }
  }
}
