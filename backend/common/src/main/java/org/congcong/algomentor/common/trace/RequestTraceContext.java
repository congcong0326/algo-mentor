package org.congcong.algomentor.common.trace;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.slf4j.MDC;

/**
 * 基于 MDC 的请求追踪上下文工具。
 */
public final class RequestTraceContext {

  private RequestTraceContext() {
  }

  public static Optional<String> currentRequestId() {
    return Optional.ofNullable(MDC.get(RequestTraceConstants.REQUEST_ID_MDC_KEY))
        .filter(value -> !value.isBlank());
  }

  public static void setRequestId(String requestId) {
    if (requestId == null || requestId.isBlank()) {
      MDC.remove(RequestTraceConstants.REQUEST_ID_MDC_KEY);
      return;
    }
    MDC.put(RequestTraceConstants.REQUEST_ID_MDC_KEY, requestId);
  }

  public static void clearRequestId() {
    MDC.remove(RequestTraceConstants.REQUEST_ID_MDC_KEY);
  }

  public static Map<String, String> capture() {
    Map<String, String> context = MDC.getCopyOfContextMap();
    return context == null ? Map.of() : Map.copyOf(context);
  }

  public static void restore(Map<String, String> context) {
    if (context == null || context.isEmpty()) {
      MDC.clear();
      return;
    }
    MDC.setContextMap(context);
  }

  public static RequestTraceScope withRequestId(String requestId) {
    Map<String, String> previous = capture();
    setRequestId(requestId);
    return () -> restore(previous);
  }

  public static Runnable wrap(Runnable task) {
    Map<String, String> captured = capture();
    return () -> {
      Map<String, String> previous = capture();
      restore(captured);
      try {
        task.run();
      } finally {
        restore(previous);
      }
    };
  }

  public static Executor contextAwareExecutor(Executor delegate) {
    return command -> delegate.execute(wrap(command));
  }

  public interface RequestTraceScope extends AutoCloseable {
    @Override
    void close();
  }
}
