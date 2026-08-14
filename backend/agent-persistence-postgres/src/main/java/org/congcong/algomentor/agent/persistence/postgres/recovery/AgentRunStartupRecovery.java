package org.congcong.algomentor.agent.persistence.postgres.recovery;

import java.util.Objects;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentRunMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 进程启动时一次性收束遗留运行；不尝试恢复或续跑任何 Agent worker。
 */
public final class AgentRunStartupRecovery implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(AgentRunStartupRecovery.class);

  private final AgentRunMapper runMapper;
  private final TransactionTemplate transactionTemplate;

  public AgentRunStartupRecovery(AgentRunMapper runMapper, TransactionTemplate transactionTemplate) {
    this.runMapper = Objects.requireNonNull(runMapper, "Agent run mapper must not be null");
    this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "Transaction template must not be null");
  }

  @Override
  public void run(ApplicationArguments args) {
    int recoveredRunCount = transactionTemplate.execute(status -> {
      runMapper.failRunningTurnsAtStartup();
      return runMapper.failRunningRunsAtStartup();
    });
    if (recoveredRunCount > 0) {
      log.warn("Marked interrupted Agent runs as failed at application startup. recoveredRunCount={}",
          recoveredRunCount);
    }
  }
}
