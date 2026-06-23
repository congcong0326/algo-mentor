package org.congcong.algomentor.ai.governance.usage;

import java.time.LocalDate;
import org.congcong.algomentor.ai.governance.model.AiUsage;

public interface AiDailyUsageStore {

  boolean tryConsumeRequest(long userId, LocalDate quotaDate, String scope, long limitCount);

  void addUsage(long userId, LocalDate quotaDate, String scope, AiUsage usage);
}
