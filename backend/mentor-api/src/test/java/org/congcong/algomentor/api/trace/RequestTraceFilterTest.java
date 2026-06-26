package org.congcong.algomentor.api.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicReference;
import org.congcong.algomentor.common.trace.RequestTraceConstants;
import org.congcong.algomentor.common.trace.RequestTraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class RequestTraceFilterTest {

  private final TraceController controller = new TraceController();
  private final MockMvc mockMvc = MockMvcBuilders
      .standaloneSetup(controller)
      .addFilters(new RequestTraceFilter())
      .build();

  @AfterEach
  void clearMdc() {
    org.slf4j.MDC.clear();
  }

  @Test
  void usesRequestIdHeaderInMdcAndResponse() throws Exception {
    mockMvc.perform(get("/trace")
            .header(RequestTraceConstants.REQUEST_ID_HEADER, "client-request-1"))
        .andExpect(status().isOk())
        .andExpect(header().string(RequestTraceConstants.REQUEST_ID_HEADER, "client-request-1"));

    assertThat(controller.lastRequestId).hasValue("client-request-1");
    assertThat(RequestTraceContext.currentRequestId()).isEmpty();
  }

  @Test
  void generatesRequestIdWhenHeaderIsMissing() throws Exception {
    mockMvc.perform(get("/trace"))
        .andExpect(status().isOk())
        .andExpect(header().exists(RequestTraceConstants.REQUEST_ID_HEADER));

    assertThat(controller.lastRequestId).hasValueSatisfying(value -> assertThat(value).matches("[0-9a-f]{12}"));
    assertThat(RequestTraceContext.currentRequestId()).isEmpty();
  }

  @Test
  void replacesOverlongRequestIdHeaderWithShortGeneratedValue() throws Exception {
    mockMvc.perform(get("/trace")
            .header(RequestTraceConstants.REQUEST_ID_HEADER, "f92ee24f-8a64-4f8d-aa57-781af4264dce"))
        .andExpect(status().isOk())
        .andExpect(header().exists(RequestTraceConstants.REQUEST_ID_HEADER));

    assertThat(controller.lastRequestId)
        .hasValueSatisfying(value -> assertThat(value).matches("[0-9a-f]{12}"));
    assertThat(RequestTraceContext.currentRequestId()).isEmpty();
  }

  @Test
  void rejectsUnsafeRequestIdHeaderAndUsesGeneratedValue() throws Exception {
    mockMvc.perform(get("/trace")
            .header(RequestTraceConstants.REQUEST_ID_HEADER, "bad\nvalue"))
        .andExpect(status().isOk())
        .andExpect(header().exists(RequestTraceConstants.REQUEST_ID_HEADER));

    assertThat(controller.lastRequestId)
        .hasValueSatisfying(value -> assertThat(value).isNotEqualTo("bad\nvalue"));
    assertThat(RequestTraceContext.currentRequestId()).isEmpty();
  }

  @RestController
  static class TraceController {
    private final AtomicReference<String> lastRequestId = new AtomicReference<>();

    @GetMapping("/trace")
    ResponseEntity<Void> trace() {
      lastRequestId.set(RequestTraceContext.currentRequestId().orElse(null));
      return ResponseEntity.ok().build();
    }
  }
}
