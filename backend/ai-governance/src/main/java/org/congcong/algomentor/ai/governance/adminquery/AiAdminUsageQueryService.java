package org.congcong.algomentor.ai.governance.adminquery;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicyResolver;
import org.congcong.algomentor.ai.governance.policy.runtime.EffectiveAiRuntimePolicy;
import org.congcong.algomentor.ai.governance.policy.runtime.AiRuntimePolicyService;
import org.congcong.algomentor.ai.governance.pricing.AiCostCalculator;
import org.congcong.algomentor.ai.governance.pricing.AiModelPrice;
import org.congcong.algomentor.ai.governance.repository.mybatis.AiAdminUsageMapper;
import org.congcong.algomentor.ai.governance.repository.mybatis.AiDailyUsageMapper;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiObservedUnpricedModelRow;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiUsageAggregationRow;
import org.congcong.algomentor.identity.model.AuthUser;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;

/** 管理员用量与按当前价格估算成本的查询服务。 */
public class AiAdminUsageQueryService {

  public static final int DEFAULT_PAGE_SIZE = 20;
  public static final int MAX_PAGE_SIZE = 100;
  public static final int MAX_DIMENSION_ROWS = 200;

  private final AiAdminUsageMapper usageMapper;
  private final AiDailyUsageMapper dailyUsageMapper;
  private final AiCostCalculator costCalculator;
  private final AiRuntimePolicyService runtimePolicyService;
  private final AiPurposePolicyResolver policyResolver;
  private final IdentityUserRepository identityUserRepository;

  public AiAdminUsageQueryService(
      AiAdminUsageMapper usageMapper,
      AiDailyUsageMapper dailyUsageMapper,
      AiCostCalculator costCalculator,
      AiRuntimePolicyService runtimePolicyService,
      AiPurposePolicyResolver policyResolver,
      IdentityUserRepository identityUserRepository
  ) {
    this.usageMapper = usageMapper;
    this.dailyUsageMapper = dailyUsageMapper;
    this.costCalculator = costCalculator;
    this.runtimePolicyService = runtimePolicyService;
    this.policyResolver = policyResolver;
    this.identityUserRepository = identityUserRepository;
  }

  public AiUsageSummary summary(AiUsageQuery query) {
    return new AiUsageSummary(
        query.from(),
        query.to(),
        query.quotaZone().getId(),
        usageMapper.admittedEntryRequestCount(query),
        aggregate(usageMapper.summary(query)));
  }

  public AiUsageByUserPage byUser(AiUsageQuery query, int page, int pageSize) {
    validatePage(page, pageSize);
    Map<Long, MetricsAccumulator> grouped = new LinkedHashMap<>();
    for (AiUsageAggregationRow row : usageMapper.byUser(query)) {
      if (row.userId() != null) {
        grouped.computeIfAbsent(row.userId(), ignored -> new MetricsAccumulator()).add(row);
      }
    }
    LocalDate today = LocalDate.now(query.quotaZone());
    List<AiUsageByUserRow> rows = grouped.entrySet().stream()
        .map(entry -> toUserRow(entry.getKey(), entry.getValue().toMetrics(), today))
        .sorted(Comparator
            .comparingLong((AiUsageByUserRow row) -> row.metrics().totalTokens()).reversed()
            .thenComparingLong(AiUsageByUserRow::userId))
        .toList();
    int fromIndex = Math.min((page - 1) * pageSize, rows.size());
    int toIndex = Math.min(fromIndex + pageSize, rows.size());
    return new AiUsageByUserPage(List.copyOf(rows.subList(fromIndex, toIndex)), rows.size(), page, pageSize);
  }

  public List<AiUsageByModelRow> byModel(AiUsageQuery query) {
    Map<ModelKey, MetricsAccumulator> grouped = new LinkedHashMap<>();
    for (AiUsageAggregationRow row : usageMapper.byModel(query)) {
      ModelKey key = new ModelKey(row.provider(), row.model());
      grouped.computeIfAbsent(key, ignored -> new MetricsAccumulator()).add(row);
    }
    return grouped.entrySet().stream()
        .map(entry -> new AiUsageByModelRow(
            entry.getKey().provider(),
            entry.getKey().model(),
            entry.getValue().pricedCallCount > 0,
            entry.getValue().toMetrics()))
        .sorted(Comparator.comparingLong((AiUsageByModelRow row) -> row.metrics().totalTokens()).reversed())
        .limit(MAX_DIMENSION_ROWS)
        .toList();
  }

  public List<AiUsageBySourceRow> bySource(AiUsageQuery query) {
    Map<String, MetricsAccumulator> grouped = new LinkedHashMap<>();
    for (AiUsageAggregationRow row : usageMapper.bySource(query)) {
      String source = row.source() == null ? "UNKNOWN" : row.source();
      grouped.computeIfAbsent(source, ignored -> new MetricsAccumulator()).add(row);
    }
    return grouped.entrySet().stream()
        .map(entry -> new AiUsageBySourceRow(entry.getKey(), entry.getValue().toMetrics()))
        .sorted(Comparator.comparingLong((AiUsageBySourceRow row) -> row.metrics().totalTokens()).reversed())
        .limit(MAX_DIMENSION_ROWS)
        .toList();
  }

  public List<AiObservedUnpricedModel> observedUnpricedModels(AiUsageQuery query) {
    return usageMapper.observedUnpricedModels(query).stream()
        .map(this::toObservedUnpricedModel)
        .toList();
  }

  private AiUsageByUserRow toUserRow(long userId, AiUsageMetrics metrics, LocalDate today) {
    AuthUser user = identityUserRepository.findUserById(userId).orElse(null);
    EffectiveAiRuntimePolicy policy = runtimePolicyService.resolve(
        policyResolver.resolve(AiPurpose.LEARNING_CHAT),
        userId);
    long todayEntryRequestCount = dailyUsageMapper.findRequestCount(userId, today, "ALL");
    return new AiUsageByUserRow(
        userId,
        user == null ? null : user.email(),
        user == null ? null : user.displayName(),
        user == null ? null : user.status().name(),
        metrics,
        todayEntryRequestCount,
        policy.effectiveDailyRequestLimit(),
        policy.effectiveAiEnabled());
  }

  private AiUsageMetrics aggregate(List<AiUsageAggregationRow> rows) {
    MetricsAccumulator accumulator = new MetricsAccumulator();
    rows.forEach(accumulator::add);
    return accumulator.toMetrics();
  }

  private AiObservedUnpricedModel toObservedUnpricedModel(AiObservedUnpricedModelRow row) {
    return new AiObservedUnpricedModel(
        row.provider(),
        row.model(),
        row.modelCallCount(),
        row.totalTokens(),
        row.lastSeenAt());
  }

  private void validatePage(int page, int pageSize) {
    if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
      throw new org.congcong.algomentor.ai.governance.model.AiGovernanceAdminException(
          org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode.AI_USAGE_QUERY_INVALID,
          "AI usage pagination is invalid.");
    }
  }

  private final class MetricsAccumulator {

    private long modelCallCount;
    private long inputTokens;
    private long cachedTokens;
    private long outputTokens;
    private long reasoningTokens;
    private long totalTokens;
    private long pricedCallCount;
    private long pricedTokenCount;
    private BigDecimal estimatedCostUsd = BigDecimal.ZERO;
    private long unpricedCallCount;
    private long unpricedTokenCount;

    private void add(AiUsageAggregationRow row) {
      modelCallCount += row.modelCallCount();
      inputTokens += row.inputTokens();
      cachedTokens += row.cachedTokens();
      outputTokens += row.outputTokens();
      reasoningTokens += row.reasoningTokens();
      totalTokens += row.totalTokens();
      if (row.priceId() == null) {
        unpricedCallCount += row.modelCallCount();
        unpricedTokenCount += row.totalTokens();
        return;
      }
      pricedCallCount += row.modelCallCount();
      pricedTokenCount += row.totalTokens();
      AiModelPrice price = new AiModelPrice(
          row.priceId(),
          "price",
          "aggregate",
          AiModelPrice.CURRENCY_USD,
          row.inputPricePerMillion(),
          row.cachedInputPricePerMillion(),
          row.outputPricePerMillion(),
          row.costMultiplier(),
          true,
          null,
          null,
          null);
      estimatedCostUsd = estimatedCostUsd.add(costCalculator.estimate(new AiUsage(
          row.inputTokens(),
          row.outputTokens(),
          row.cachedTokens(),
          row.reasoningTokens(),
          row.totalTokens()), price).estimatedCostUsd());
    }

    private AiUsageMetrics toMetrics() {
      return new AiUsageMetrics(
          modelCallCount,
          inputTokens,
          cachedTokens,
          outputTokens,
          reasoningTokens,
          totalTokens,
          pricedCallCount,
          pricedTokenCount,
          estimatedCostUsd.setScale(AiCostCalculator.COST_SCALE, RoundingMode.HALF_UP),
          unpricedCallCount,
          unpricedTokenCount);
    }
  }

  private record ModelKey(String provider, String model) {
  }
}
