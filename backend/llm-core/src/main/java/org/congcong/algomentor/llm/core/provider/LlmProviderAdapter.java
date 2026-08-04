package org.congcong.algomentor.llm.core.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.Set;
import org.congcong.algomentor.llm.core.request.LlmReasoningEffort;

/**
 * 代码注册的 provider 协议适配器。
 *
 * <p>适配器不读取数据库、不拥有管理员配置实例，也不实现业务路由。</p>
 */
public interface LlmProviderAdapter {

  LlmProviderType providerType();

  String displayName();

  Set<LlmCapability> supportedCapabilities();

  /** 返回该 adapter 可以映射的 reasoning effort 协议子集。 */
  default Set<LlmReasoningEffort> acceptedReasoningEfforts() {
    return Set.of();
  }

  /** 返回可安全展示的新 provider 实例配置模板，不能包含已保存实例的 secret。 */
  default JsonNode defaultConfig() {
    return JsonNodeFactory.instance.objectNode();
  }

  /** 仅校验 JSON 结构和本地字段语义，绝不发送远程探测请求。 */
  void validateConfig(JsonNode config);

  LlmProviderClient createClient(LlmProviderInstanceSpec instance);
}
