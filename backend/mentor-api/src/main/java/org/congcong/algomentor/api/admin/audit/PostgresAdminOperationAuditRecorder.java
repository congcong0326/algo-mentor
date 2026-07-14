package org.congcong.algomentor.api.admin.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditEvent;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditRecorder;
import org.congcong.algomentor.common.trace.RequestTraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostgresAdminOperationAuditRecorder implements AdminOperationAuditRecorder {

  static final String WRITE_FAILURES_TOTAL = "algo_mentor_admin_audit_write_failures_total";
  private static final Logger log = LoggerFactory.getLogger(PostgresAdminOperationAuditRecorder.class);
  private static final int MAX_REQUEST_ID_LENGTH = 128;

  private final AdminOperationAuditWriteExecutor writeExecutor;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;

  public PostgresAdminOperationAuditRecorder(
      AdminOperationAuditWriteExecutor writeExecutor,
      ObjectMapper objectMapper,
      MeterRegistry meterRegistry
  ) {
    this.writeExecutor = writeExecutor;
    this.objectMapper = objectMapper;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public void record(AdminOperationAuditEvent event) {
    try {
      writeExecutor.write(event, currentRequestId(), metadataJson(event));
    } catch (RuntimeException | JsonProcessingException exception) {
      log.error(
          "Failed to persist admin operation audit. action={} targetType={} outcome={}",
          event.action(),
          event.targetType(),
          event.outcome(),
          exception);
      if (meterRegistry != null) {
        Counter.builder(WRITE_FAILURES_TOTAL).register(meterRegistry).increment();
      }
    }
  }

  private String metadataJson(AdminOperationAuditEvent event) throws JsonProcessingException {
    Map<String, Object> metadata = new LinkedHashMap<>();
    event.metadata().forEach((key, value) -> metadata.put(key.value(), value));
    return objectMapper.writeValueAsString(metadata);
  }

  private static String currentRequestId() {
    return RequestTraceContext.currentRequestId()
        .map(value -> value.length() <= MAX_REQUEST_ID_LENGTH
            ? value
            : value.substring(0, MAX_REQUEST_ID_LENGTH))
        .orElse(null);
  }
}
