package org.congcong.algomentor.api.controller.admin.overview;

import org.congcong.algomentor.api.admin.overview.AdminOverviewService;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "spring.datasource.url")
@RequestMapping(AdminOverviewApiContractConstants.BASE_PATH)
public class AdminOverviewController {
  private final AdminOverviewService service;
  public AdminOverviewController(AdminOverviewService service) { this.service = service; }
  @GetMapping public ApiResponse<AdminOverviewService.AdminOverview> get() { return ApiResponse.success(service.getOverview()); }
}
