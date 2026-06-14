package org.congcong.algomentor.api.controller;

import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

  @GetMapping
  public ApiResponse<HealthStatus> health() {
    return ApiResponse.success(new HealthStatus("UP"));
  }

  public record HealthStatus(String status) {
  }
}

