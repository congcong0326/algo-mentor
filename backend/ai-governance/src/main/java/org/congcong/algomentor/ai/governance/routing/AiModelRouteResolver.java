package org.congcong.algomentor.ai.governance.routing;

import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;

/** 在一次 AI 业务执行开始时解析不可变的模型执行快照。 */
public interface AiModelRouteResolver {

  ResolvedAiModelSnapshot resolve(AiBusinessScenario scenario, long userId);
}
