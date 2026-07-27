package org.congcong.algomentor.policy.type;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.congcong.algomentor.policy.service.GenericPolicyErrorCode;
import org.congcong.algomentor.policy.service.GenericPolicyException;

/** 收集 Spring Bean 后构建的只读策略类型注册表。 */
public final class GenericPolicyTypeRegistry {

  private final Map<String, GenericPolicyType<?>> typesByCode;

  public GenericPolicyTypeRegistry(Collection<GenericPolicyType<?>> policyTypes) {
    Map<String, GenericPolicyType<?>> types = new LinkedHashMap<>();
    if (policyTypes != null) {
      for (GenericPolicyType<?> policyType : policyTypes) {
        GenericPolicyType<?> nonNullType = Objects.requireNonNull(policyType, "policyType must not be null");
        GenericPolicyType<?> previous = types.putIfAbsent(nonNullType.typeCode(), nonNullType);
        if (previous != null) {
          throw new IllegalStateException(
              "Duplicate GenericPolicyType registration for typeCode=" + nonNullType.typeCode());
        }
      }
    }
    this.typesByCode = Collections.unmodifiableMap(new LinkedHashMap<>(types));
  }

  public GenericPolicyType<?> require(String typeCode) {
    String normalized;
    try {
      normalized = GenericPolicyType.normalizeTypeCode(typeCode);
    } catch (IllegalArgumentException exception) {
      throw new GenericPolicyException(
          GenericPolicyErrorCode.POLICY_INVALID_TYPE_CODE,
          "策略类型编码格式不合法。",
          exception);
    }
    GenericPolicyType<?> policyType = typesByCode.get(normalized);
    if (policyType == null) {
      throw new GenericPolicyException(
          GenericPolicyErrorCode.POLICY_TYPE_NOT_REGISTERED,
          "策略类型未由业务模块注册：" + normalized);
    }
    return policyType;
  }

  public <T> GenericPolicyType<T> requireRegisteredInstance(GenericPolicyType<T> policyType) {
    Objects.requireNonNull(policyType, "policyType must not be null");
    GenericPolicyType<?> registered = require(policyType.typeCode());
    if (registered != policyType) {
      throw new GenericPolicyException(
          GenericPolicyErrorCode.POLICY_TYPE_NOT_REGISTERED,
          "策略查询必须使用已注册的 GenericPolicyType Bean：" + policyType.typeCode());
    }
    return policyType;
  }

  public Map<String, GenericPolicyType<?>> registeredTypes() {
    return typesByCode;
  }
}
