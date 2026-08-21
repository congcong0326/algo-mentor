package org.congcong.algomentor.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 为个人工作台和认证页面补充服务端 noindex 响应头。 */
@Component
public class PrivatePageRobotsFilter extends OncePerRequestFilter {

  static final String ROBOTS_HEADER = "X-Robots-Tag";
  static final String NOINDEX_VALUE = "noindex, nofollow";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    if (isPrivatePage(request.getRequestURI(), request.getContextPath())) {
      response.setHeader(ROBOTS_HEADER, NOINDEX_VALUE);
    }
    filterChain.doFilter(request, response);
  }

  private static boolean isPrivatePage(String requestUri, String contextPath) {
    String path = requestUri;
    if (contextPath != null && !contextPath.isBlank() && requestUri.startsWith(contextPath)) {
      path = requestUri.substring(contextPath.length());
    }
    for (String prefix : SpaRoutes.PRIVATE_PAGE_PATH_PREFIXES) {
      if (path.equals(prefix) || path.startsWith(prefix + "/")) {
        return true;
      }
    }
    return false;
  }
}
