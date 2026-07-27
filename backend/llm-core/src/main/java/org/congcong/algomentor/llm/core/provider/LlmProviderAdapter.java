package org.congcong.algomentor.llm.core.provider;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

/**
 * 代码注册的 provider 协议适配器。
 *
 * <p>适配器不读取数据库、不拥有管理员配置实例，也不实现业务路由。</p>
 */
public interface LlmProviderAdapter {

  LlmProviderType providerType();

  String displayName();

  Set<LlmCapability> supportedCapabilities();

  /** 仅校验 JSON 结构和本地字段语义，绝不发送远程探测请求。 */
  void validateConfig(JsonNode config);

  LlmProviderClient createClient(LlmProviderInstanceSpec instance);
}
