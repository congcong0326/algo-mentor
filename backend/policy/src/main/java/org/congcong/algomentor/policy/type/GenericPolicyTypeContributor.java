package org.congcong.algomentor.policy.type;

import java.util.Collection;

/** 允许业务模块批量贡献动态策略类型，避免每个类型单独声明 Bean。 */
public interface GenericPolicyTypeContributor {

  Collection<GenericPolicyType<?>> policyTypes();
}
