package org.congcong.algomentor.api.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.regex.Pattern;
import org.congcong.algomentor.common.trace.RequestTraceConstants;
import org.congcong.algomentor.common.trace.RequestTraceContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 在 HTTP 请求入口建立日志 MDC 中的 requestId。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {

  private static final int MAX_REQUEST_ID_LENGTH = 32;
  private static final int GENERATED_REQUEST_ID_BYTES = 6;
  private static final char[] HEX = "0123456789abcdef".toCharArray();
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:/=+@-]+");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    String requestId = effectiveRequestId(request.getHeader(RequestTraceConstants.REQUEST_ID_HEADER));
    response.setHeader(RequestTraceConstants.REQUEST_ID_HEADER, requestId);

    try (RequestTraceContext.RequestTraceScope ignored = RequestTraceContext.withRequestId(requestId)) {
      filterChain.doFilter(request, response);
    }
  }

  private String effectiveRequestId(String candidate) {
    if (isValid(candidate)) {
      return candidate.trim();
    }
    return generateRequestId();
  }

  private boolean isValid(String candidate) {
    if (candidate == null) {
      return false;
    }
    String value = candidate.trim();
    return !value.isEmpty()
        && value.length() <= MAX_REQUEST_ID_LENGTH
        && SAFE_REQUEST_ID.matcher(value).matches();
  }

  private String generateRequestId() {
    byte[] bytes = new byte[GENERATED_REQUEST_ID_BYTES];
    RANDOM.nextBytes(bytes);
    char[] chars = new char[bytes.length * 2];
    for (int index = 0; index < bytes.length; index++) {
      int value = bytes[index] & 0xff;
      chars[index * 2] = HEX[value >>> 4];
      chars[index * 2 + 1] = HEX[value & 0x0f];
    }
    return new String(chars);
  }
}
