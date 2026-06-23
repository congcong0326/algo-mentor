package org.congcong.algomentor.ai.governance.usage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.congcong.algomentor.ai.governance.repository.mybatis.AiDailyUsageMapper;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiDailyUsageRow;
import org.junit.jupiter.api.Test;

class PostgresAiDailyUsageStoreTest {

  @Test
  void incrementsSharedDailyRequestWithinLimit() {
    FakeDailyUsageMapper mapper = new FakeDailyUsageMapper();
    PostgresAiDailyUsageStore store = new PostgresAiDailyUsageStore(mapper);

    boolean admitted = store.tryConsumeRequest(7L, LocalDate.parse("2026-06-23"), "ALL", 50);

    assertThat(admitted).isTrue();
    assertThat(mapper.row.requestCount()).isEqualTo(1);
    assertThat(mapper.row.limitCount()).isEqualTo(50);
  }

  @Test
  void rejectsWhenDailyRequestLimitReached() {
    FakeDailyUsageMapper mapper = new FakeDailyUsageMapper();
    mapper.row = new AiDailyUsageRow(null, 7L, LocalDate.parse("2026-06-23"), "ALL", 50, 0, 0, 0, 0, 0, 50, null);
    PostgresAiDailyUsageStore store = new PostgresAiDailyUsageStore(mapper);

    boolean admitted = store.tryConsumeRequest(7L, LocalDate.parse("2026-06-23"), "ALL", 50);

    assertThat(admitted).isFalse();
    assertThat(mapper.row.requestCount()).isEqualTo(50);
  }

  @Test
  void accumulatesTokenUsageAfterRunCompletion() {
    FakeDailyUsageMapper mapper = new FakeDailyUsageMapper();
    mapper.row = new AiDailyUsageRow(null, 7L, LocalDate.parse("2026-06-23"), "ALL", 1, 0, 0, 0, 0, 0, 50, null);
    PostgresAiDailyUsageStore store = new PostgresAiDailyUsageStore(mapper);

    store.addUsage(7L, LocalDate.parse("2026-06-23"), "ALL", new AiUsage(10, 20, 3, 4, 34));

    assertThat(mapper.row.inputTokens()).isEqualTo(10);
    assertThat(mapper.row.outputTokens()).isEqualTo(20);
    assertThat(mapper.row.cachedTokens()).isEqualTo(3);
    assertThat(mapper.row.reasoningTokens()).isEqualTo(4);
    assertThat(mapper.row.totalTokens()).isEqualTo(34);
  }

  private static final class FakeDailyUsageMapper implements AiDailyUsageMapper {

    private AiDailyUsageRow row;

    @Override
    public int insertIfAbsent(long userId, LocalDate quotaDate, String scope, long limitCount) {
      if (row == null) {
        row = new AiDailyUsageRow(null, userId, quotaDate, scope, 0, 0, 0, 0, 0, 0, limitCount, Instant.now());
        return 1;
      }
      return 0;
    }

    @Override
    public int incrementRequestIfWithinLimit(long userId, LocalDate quotaDate, String scope, long limitCount) {
      if (row.requestCount() >= limitCount) {
        return 0;
      }
      row = new AiDailyUsageRow(row.id(), userId, quotaDate, scope, row.requestCount() + 1,
          row.inputTokens(), row.outputTokens(), row.cachedTokens(), row.reasoningTokens(),
          row.totalTokens(), limitCount, Instant.now());
      return 1;
    }

    @Override
    public int addUsage(long userId, LocalDate quotaDate, String scope, AiUsage usage) {
      row = new AiDailyUsageRow(row.id(), userId, quotaDate, scope, row.requestCount(),
          row.inputTokens() + usage.inputTokens(),
          row.outputTokens() + usage.outputTokens(),
          row.cachedTokens() + usage.cachedTokens(),
          row.reasoningTokens() + usage.reasoningTokens(),
          row.totalTokens() + usage.totalTokens(),
          row.limitCount(),
          Instant.now());
      return 1;
    }
  }
}
