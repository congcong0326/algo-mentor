package org.congcong.algomentor.api.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * React History API 路由刷新兜底。
 */
@Controller
public class SpaFallbackController {

  @GetMapping({
      SpaRoutes.FRONTEND_FALLBACK_TOP_LEVEL_PATTERN,
      SpaRoutes.FRONTEND_FALLBACK_NESTED_PATTERN
  })
  public String forwardFrontendRoute(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String path = normalizedRequestPath(request);
    if (isBackendReservedPath(path)) {
      response.sendError(HttpStatus.NOT_FOUND.value());
      return null;
    }
    return SpaRoutes.INDEX_FORWARD;
  }

  private static String normalizedRequestPath(HttpServletRequest request) {
    String requestUri = request.getRequestURI();
    String contextPath = request.getContextPath();
    if (contextPath != null && !contextPath.isBlank() && requestUri.startsWith(contextPath)) {
      return requestUri.substring(contextPath.length());
    }
    return requestUri;
  }

  private static boolean isBackendReservedPath(String path) {
    for (String reservedPath : SpaRoutes.BACKEND_RESERVED_EXACT_PATHS) {
      if (path.equals(reservedPath)) {
        return true;
      }
    }
    for (String prefix : SpaRoutes.BACKEND_RESERVED_PATH_PREFIXES) {
      if (path.equals(prefix) || path.startsWith(prefix + "/")) {
        return true;
      }
    }
    return false;
  }
}
