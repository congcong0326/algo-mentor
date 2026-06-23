package org.congcong.algomentor.ai.governance.admission;

import java.util.Map;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.model.AiRunStatus;
import org.springframework.http.HttpStatus;

public class AiRunAdmissionException extends RuntimeException {

  private final AiGovernanceErrorCode code;
  private final AiRunStatus status;
  private final HttpStatus suggestedStatus;
  private final Map<String, Object> metadata;

  public AiRunAdmissionException(
      AiGovernanceErrorCode code,
      AiRunStatus status,
      String message,
      HttpStatus suggestedStatus,
      Map<String, Object> metadata) {
    super(message);
    this.code = code;
    this.status = status;
    this.suggestedStatus = suggestedStatus;
    this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }

  public AiGovernanceErrorCode code() {
    return code;
  }

  public AiRunStatus status() {
    return status;
  }

  public HttpStatus suggestedStatus() {
    return suggestedStatus;
  }

  public Map<String, Object> metadata() {
    return metadata;
  }
}
