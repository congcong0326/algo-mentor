package org.congcong.algomentor.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.congcong.algomentor.auth.controller.AuthApiContractConstants;
import org.congcong.algomentor.common.api.ApiErrorLocales;
import org.congcong.algomentor.common.api.ApiErrorMessageResolver;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;
  private final ApiErrorResponseFactory responseFactory;

  public ApiAuthenticationEntryPoint(ObjectMapper objectMapper) {
    this(objectMapper, new ApiErrorResponseFactory(new ApiErrorMessageResolver()));
  }

  public ApiAuthenticationEntryPoint(ObjectMapper objectMapper, ApiErrorResponseFactory responseFactory) {
    this.objectMapper = objectMapper;
    this.responseFactory = responseFactory;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException
  ) throws IOException, ServletException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    ApiResponse<Void> body = responseFactory.failure(
        AuthApiContractConstants.AUTH_UNAUTHENTICATED_CODE,
        "当前请求未登录或登录状态已失效。",
        ApiErrorLocales.parse(request.getHeader("Accept-Language")));
    objectMapper.writeValue(response.getOutputStream(), body);
  }
}
