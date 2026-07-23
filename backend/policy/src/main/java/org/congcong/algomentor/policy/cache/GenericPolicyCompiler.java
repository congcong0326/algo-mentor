package org.congcong.algomentor.policy.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.congcong.algomentor.policy.metrics.GenericPolicyMetrics;
import org.congcong.algomentor.policy.model.CompiledPolicy;
import org.congcong.algomentor.policy.model.CompiledPolicySet;
import org.congcong.algomentor.policy.model.GenericPolicy;
import org.congcong.algomentor.policy.model.PolicySubject;
import org.congcong.algomentor.policy.model.PolicySubjectRange;
import org.congcong.algomentor.policy.model.PolicySubjectType;
import org.congcong.algomentor.policy.repository.GenericPolicyRepository;
import org.congcong.algomentor.policy.service.PolicyCompilationException;
import org.congcong.algomentor.policy.type.GenericPolicyType;
import org.congcong.algomentor.policy.type.GenericPolicyTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 将一个 typeCode 的已启用数据库记录完整编译为不可变运行时快照。 */
public final class GenericPolicyCompiler {

  private static final Logger log = LoggerFactory.getLogger(GenericPolicyCompiler.class);

  private final GenericPolicyRepository repository;
  private final GenericPolicyTypeRegistry typeRegistry;
  private final ObjectMapper objectMapper;
  private final GenericPolicyMetrics metrics;

  public GenericPolicyCompiler(
      GenericPolicyRepository repository,
      GenericPolicyTypeRegistry typeRegistry,
      ObjectMapper objectMapper,
      GenericPolicyMetrics metrics
  ) {
    this.repository = repository;
    this.typeRegistry = typeRegistry;
    this.objectMapper = objectMapper;
    this.metrics = metrics;
  }

  public CompiledPolicySet compile(String typeCode) {
    GenericPolicyType<?> policyType;
    try {
      policyType = typeRegistry.require(typeCode);
    } catch (RuntimeException exception) {
      throw compileFailure(typeCode, 0, 0, "unregistered", exception);
    }
    try {
      List<CompiledPolicy<?>> policies = new ArrayList<>();
      for (GenericPolicy policy : repository.findEnabledByType(typeCode)) {
        policies.add(compilePolicy(policy, policyType));
      }
      policies.sort(Comparator.comparingInt((CompiledPolicy<?> policy) -> policy.priority())
          .thenComparingLong(policy -> policy.id()));
      metrics.recordCompile(typeCode, "success");
      return new CompiledPolicySet(typeCode, policyType, policies);
    } catch (PolicyCompilationException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw compileFailure(typeCode, 0, 0, policyType.javaType(objectMapper).toCanonical(), exception);
    }
  }

  private CompiledPolicy<?> compilePolicy(GenericPolicy policy, GenericPolicyType<?> policyType) {
    try {
      PolicySubjectRange normalizedRange = policy.subjectRange().normalized();
      Set<Long> userIds = subjectIds(normalizedRange, PolicySubjectType.USER);
      Set<Long> groupIds = subjectIds(normalizedRange, PolicySubjectType.GROUP);
      Object content = policyType.deserialize(objectMapper, policy.content());
      return new CompiledPolicy<>(
          policy.id(),
          policy.typeCode(),
          policy.name(),
          policy.priority(),
          normalizedRange.allSubject(),
          userIds,
          groupIds,
          policy.content(),
          content,
          policy.version());
    } catch (RuntimeException exception) {
      throw compileFailure(
          policy.typeCode(),
          policy.id(),
          policy.version(),
          policyType.javaType(objectMapper).toCanonical(),
          exception);
    }
  }

  private Set<Long> subjectIds(PolicySubjectRange range, PolicySubjectType type) {
    LinkedHashSet<Long> ids = new LinkedHashSet<>();
    for (PolicySubject subject : range.subjects()) {
      if (subject.type() == type) {
        ids.add(subject.id());
      }
    }
    return Set.copyOf(ids);
  }

  private PolicyCompilationException compileFailure(
      String typeCode,
      long policyId,
      long policyVersion,
      String targetJavaType,
      Throwable cause
  ) {
    PolicyCompilationException exception = new PolicyCompilationException(
        typeCode,
        policyId,
        policyVersion,
        targetJavaType,
        "无法编译通用策略运行时快照。",
        cause);
    log.error(
        "event=generic_policy_compile_failed typeCode={} policyId={} policyVersion={} targetJavaType={} "
            + "errorCode={} exceptionClass={}",
        typeCode,
        policyId,
        policyVersion,
        targetJavaType,
        exception.code(),
        cause.getClass().getName(),
        cause);
    metrics.recordCompile(typeCode, "failure");
    return exception;
  }
}
