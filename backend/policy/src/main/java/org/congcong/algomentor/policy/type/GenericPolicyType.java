package org.congcong.algomentor.policy.type;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import org.congcong.algomentor.policy.service.GenericPolicyConstraints;

/**
 * 业务注册的策略内容类型描述符。
 *
 * <p>该对象是 typeCode 与受信 Java 目标类型之间唯一的服务端映射，JSON 内容本身不能指定类名。</p>
 */
public final class GenericPolicyType<T> {

  private static final Pattern TYPE_CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_.-]{0,63}$");

  private final String typeCode;
  private final Type contentType;
  private final BiConsumer<JsonNode, T> contentValidator;
  private final GenericPolicyTypeExposure exposure;

  private GenericPolicyType(
      String typeCode,
      Type contentType,
      BiConsumer<JsonNode, T> contentValidator,
      GenericPolicyTypeExposure exposure
  ) {
    this.typeCode = normalizeTypeCode(typeCode);
    this.contentType = Objects.requireNonNull(contentType, "contentType must not be null");
    this.contentValidator = Objects.requireNonNull(contentValidator, "contentValidator must not be null");
    this.exposure = Objects.requireNonNull(exposure, "exposure must not be null");
  }

  public static <T> GenericPolicyType<T> of(String typeCode, Class<T> contentType) {
    return new GenericPolicyType<>(typeCode, contentType, GenericPolicyType::noOpValidation,
        GenericPolicyTypeExposure.CURRENT_USER_READABLE);
  }

  /** 注册带有业务 JSON 语义校验的强类型策略内容。 */
  public static <T> GenericPolicyType<T> of(
      String typeCode,
      Class<T> contentType,
      BiConsumer<JsonNode, T> contentValidator
  ) {
    return new GenericPolicyType<>(typeCode, contentType, contentValidator,
        GenericPolicyTypeExposure.CURRENT_USER_READABLE);
  }

  public static <T> GenericPolicyType<T> of(
      String typeCode,
      Class<T> contentType,
      BiConsumer<JsonNode, T> contentValidator,
      GenericPolicyTypeExposure exposure
  ) {
    return new GenericPolicyType<>(typeCode, contentType, contentValidator, exposure);
  }

  public static <T> GenericPolicyType<T> of(String typeCode, TypeReference<T> contentType) {
    Objects.requireNonNull(contentType, "contentType must not be null");
    return new GenericPolicyType<>(typeCode, contentType.getType(), GenericPolicyType::noOpValidation,
        GenericPolicyTypeExposure.CURRENT_USER_READABLE);
  }

  public String typeCode() {
    return typeCode;
  }

  public GenericPolicyTypeExposure exposure() {
    return exposure;
  }

  public JavaType javaType(ObjectMapper objectMapper) {
    return Objects.requireNonNull(objectMapper, "objectMapper must not be null")
        .getTypeFactory()
        .constructType(contentType);
  }

  public T deserialize(ObjectMapper objectMapper, JsonNode content) {
    Objects.requireNonNull(content, "content must not be null");
    T value = objectMapper.convertValue(content, javaType(objectMapper));
    contentValidator.accept(content, value);
    return value;
  }

  private static <T> void noOpValidation(JsonNode content, T value) {
  }

  public static String normalizeTypeCode(String value) {
    String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    if (normalized.length() > GenericPolicyConstraints.MAX_TYPE_CODE_LENGTH
        || !TYPE_CODE_PATTERN.matcher(normalized).matches()) {
      throw new IllegalArgumentException(
          "typeCode must start with a lowercase letter and contain only lowercase letters, digits, '.', '_' or '-'");
    }
    return normalized;
  }
}
