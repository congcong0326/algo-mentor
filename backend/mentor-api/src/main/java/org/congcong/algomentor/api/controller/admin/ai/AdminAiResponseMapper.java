package org.congcong.algomentor.api.controller.admin.ai;

import java.math.BigDecimal;
import java.util.List;
import org.congcong.algomentor.ai.governance.adminquery.AiObservedUnpricedModel;
import org.congcong.algomentor.ai.governance.adminquery.AiUsageByModelRow;
import org.congcong.algomentor.ai.governance.adminquery.AiUsageBySourceRow;
import org.congcong.algomentor.ai.governance.adminquery.AiUsageByUserPage;
import org.congcong.algomentor.ai.governance.adminquery.AiUsageByUserRow;
import org.congcong.algomentor.ai.governance.adminquery.AiUsageMetrics;
import org.congcong.algomentor.ai.governance.adminquery.AiUsageSummary;
import org.congcong.algomentor.ai.governance.pricing.AiModelPrice;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiModelPriceResponse;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiUnpricedModelResponse;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiUsageByModelResponse;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiUsageBySourceResponse;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiUsageByUserPageResponse;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiUsageByUserResponse;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiUsageMetricsResponse;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiUsageSummaryResponse;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;

/** 管理员 AI 核心模型到 HTTP DTO 的集中映射。 */
final class AdminAiResponseMapper {

  private final IdentityUserRepository identityUserRepository;

  AdminAiResponseMapper(IdentityUserRepository identityUserRepository) {
    this.identityUserRepository = identityUserRepository;
  }

  AdminAiModelPriceResponse price(AiModelPrice price) {
    return new AdminAiModelPriceResponse(
        price.id(),
        price.provider(),
        price.model(),
        price.currency(),
        decimal(price.inputPricePerMillion()),
        decimal(price.cachedInputPricePerMillion()),
        decimal(price.outputPricePerMillion()),
        decimal(price.costMultiplier()),
        price.enabled(),
        price.updatedBy(),
        displayName(price.updatedBy()),
        price.createdAt(),
        price.updatedAt());
  }

  AdminAiUsageSummaryResponse summary(AiUsageSummary summary) {
    return new AdminAiUsageSummaryResponse(
        summary.from(),
        summary.to(),
        summary.quotaZone(),
        summary.admittedEntryRequestCount(),
        metrics(summary.metrics()));
  }

  AdminAiUsageByUserPageResponse userPage(AiUsageByUserPage page) {
    return new AdminAiUsageByUserPageResponse(
        page.items().stream().map(this::user).toList(),
        page.total(),
        page.page(),
        page.pageSize());
  }

  List<AdminAiUsageByModelResponse> models(List<AiUsageByModelRow> rows) {
    return rows.stream()
        .map(row -> new AdminAiUsageByModelResponse(row.provider(), row.model(), row.priced(), metrics(row.metrics())))
        .toList();
  }

  List<AdminAiUsageBySourceResponse> sources(List<AiUsageBySourceRow> rows) {
    return rows.stream()
        .map(row -> new AdminAiUsageBySourceResponse(row.source(), metrics(row.metrics())))
        .toList();
  }

  List<AdminAiUnpricedModelResponse> unpriced(List<AiObservedUnpricedModel> models) {
    return models.stream()
        .map(model -> new AdminAiUnpricedModelResponse(
            model.provider(), model.model(), model.modelCallCount(), model.totalTokens(), model.lastSeenAt()))
        .toList();
  }

  String displayName(Long userId) {
    return userId == null ? null : identityUserRepository.findUserById(userId)
        .map(user -> user.displayName())
        .orElse(null);
  }

  private AdminAiUsageByUserResponse user(AiUsageByUserRow row) {
    return new AdminAiUsageByUserResponse(
        row.userId(),
        row.email(),
        row.displayName(),
        row.accountStatus(),
        metrics(row.metrics()),
        row.todayEntryRequestCount(),
        row.effectiveDailyRequestLimit(),
        row.effectiveAiEnabled());
  }

  private AdminAiUsageMetricsResponse metrics(AiUsageMetrics metrics) {
    return new AdminAiUsageMetricsResponse(
        metrics.modelCallCount(),
        metrics.inputTokens(),
        metrics.cachedTokens(),
        metrics.outputTokens(),
        metrics.reasoningTokens(),
        metrics.totalTokens(),
        metrics.pricedCallCount(),
        metrics.pricedTokenCount(),
        decimal(metrics.estimatedCostUsd()),
        metrics.unpricedCallCount(),
        metrics.unpricedTokenCount());
  }

  private static String decimal(BigDecimal value) {
    return value == null ? null : value.toPlainString();
  }
}
