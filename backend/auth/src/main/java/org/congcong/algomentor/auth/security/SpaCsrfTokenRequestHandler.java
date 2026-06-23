package org.congcong.algomentor.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * 支持前端从 XSRF-TOKEN cookie 读取原始 token 后通过 X-XSRF-TOKEN 请求头提交。
 */
public class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

  private final XorCsrfTokenRequestAttributeHandler xorHandler = new XorCsrfTokenRequestAttributeHandler();
  private final CsrfTokenRequestAttributeHandler plainHandler = new CsrfTokenRequestAttributeHandler();

  @Override
  public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
    xorHandler.handle(request, response, csrfToken);
    csrfToken.get();
  }

  @Override
  public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
    if (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))) {
      return plainHandler.resolveCsrfTokenValue(request, csrfToken);
    }
    return xorHandler.resolveCsrfTokenValue(request, csrfToken);
  }
}
