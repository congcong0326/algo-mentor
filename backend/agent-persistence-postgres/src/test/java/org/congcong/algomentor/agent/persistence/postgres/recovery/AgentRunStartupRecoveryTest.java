package org.congcong.algomentor.agent.persistence.postgres.recovery;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentRunMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class AgentRunStartupRecoveryTest {

  @Test
  void marksAllInterruptedRunsFailedOnceAtApplicationStartup() throws Exception {
    AgentRunMapper mapper = mock(AgentRunMapper.class);
    when(mapper.failRunningRunsAtStartup()).thenReturn(3);

    new AgentRunStartupRecovery(mapper, new TransactionTemplate(new NoOpTransactionManager()))
        .run(new DefaultApplicationArguments());

    verify(mapper).failRunningTurnsAtStartup();
    verify(mapper).failRunningRunsAtStartup();
  }

  private static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
    }
  }
}
