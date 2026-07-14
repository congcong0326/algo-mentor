package org.congcong.algomentor.api.controller.admin.ai.model;

import java.util.List;

public record AdminAiModelPricePageResponse(
    List<AdminAiModelPriceResponse> items,
    List<AdminAiUnpricedModelResponse> unpricedModels
) {
}
