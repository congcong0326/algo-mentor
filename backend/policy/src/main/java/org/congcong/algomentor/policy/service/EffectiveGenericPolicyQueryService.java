package org.congcong.algomentor.policy.service;

import java.util.Optional;
import org.congcong.algomentor.policy.model.EffectiveGenericPolicy;

/** HTTP 等原始 JSON 消费方使用的运行时查询门面。 */
public interface EffectiveGenericPolicyQueryService {

  Optional<EffectiveGenericPolicy> resolveEffective(String typeCode, long userId);
}
