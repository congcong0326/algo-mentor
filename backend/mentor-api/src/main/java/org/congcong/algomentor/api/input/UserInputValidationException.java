package org.congcong.algomentor.api.input;

import java.util.LinkedHashMap;
import java.util.Map;

/** 用户输入在进入业务服务前的统一校验异常。 */
public class UserInputValidationException extends RuntimeException {

  public static final String LIMIT_EXCEEDED_CODE = "USER_INPUT_LIMIT_EXCEEDED";
  public static final String INVALID_CODE = "USER_INPUT_INVALID";

  private final String code;
  private final Map<String, Object> metadata;

  private UserInputValidationException(String code, String message, Map<String, Object> metadata) {
    super(message);
    this.code = code;
    this.metadata = Map.copyOf(metadata);
  }

  public static UserInputValidationException limitExceeded(
      String field,
      long max,
      String unit,
      long actual
  ) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("field", field);
    metadata.put("max", max);
    metadata.put("unit", unit);
    metadata.put("actual", actual);
    return new UserInputValidationException(
        LIMIT_EXCEEDED_CODE,
        "用户输入超过允许上限。",
        metadata);
  }

  public static UserInputValidationException invalid(String field, String reason) {
    return new UserInputValidationException(
        INVALID_CODE,
        "用户输入不符合接口约束。",
        Map.of("field", field, "reason", reason));
  }

  public String code() {
    return code;
  }

  public Map<String, Object> metadata() {
    return metadata;
  }
}
