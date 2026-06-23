package org.congcong.algomentor.ai.governance.usage;

import java.time.LocalDate;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.congcong.algomentor.ai.governance.repository.mybatis.AiDailyUsageMapper;
import org.springframework.transaction.annotation.Transactional;

public class PostgresAiDailyUsageStore implements AiDailyUsageStore {

  private final AiDailyUsageMapper mapper;

  public PostgresAiDailyUsageStore(AiDailyUsageMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  @Transactional
  public boolean tryConsumeRequest(long userId, LocalDate quotaDate, String scope, long limitCount) {
    mapper.insertIfAbsent(userId, quotaDate, scope, limitCount);
    return mapper.incrementRequestIfWithinLimit(userId, quotaDate, scope, limitCount) == 1;
  }

  @Override
  public void addUsage(long userId, LocalDate quotaDate, String scope, AiUsage usage) {
    mapper.addUsage(userId, quotaDate, scope, usage == null ? AiUsage.zero() : usage);
  }
}
