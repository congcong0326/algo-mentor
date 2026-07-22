package org.congcong.algomentor.api.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.congcong.algomentor.queue.config.PersistentQueueAutoConfiguration;
import org.congcong.algomentor.queue.config.PersistentQueueWorkerAutoConfiguration;
import org.congcong.algomentor.queue.consumer.QueueConsumerRegistry;
import org.congcong.algomentor.queue.dispatch.QueueDequeueService;
import org.congcong.algomentor.queue.dispatch.QueueDispatcher;
import org.congcong.algomentor.queue.postgres.QueueMessageMapper;
import org.congcong.algomentor.queue.publisher.QueuePublisher;
import org.congcong.algomentor.queue.runtime.PersistentQueueWorkerManager;
import org.congcong.algomentor.queue.runtime.QueueCleanupService;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

class PersistentQueueAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(
          JacksonAutoConfiguration.class,
          PersistentQueueAutoConfiguration.class,
          PersistentQueueWorkerAutoConfiguration.class))
      .withUserConfiguration(QueueDependencies.class);

  @Test
  void storagePublisherExistsWhenConsumerIsDisabled() {
    contextRunner.run(context -> {
      assertThat(context).hasSingleBean(QueuePublisher.class);
      assertThat(context).doesNotHaveBean(QueueConsumerRegistry.class);
      assertThat(context).doesNotHaveBean(PersistentQueueWorkerManager.class);
    });
  }

  @Test
  void workerRuntimeExistsOnlyWhenConsumerIsEnabled() {
    contextRunner
        .withPropertyValues("algo-mentor.queue.consumer.enabled=true")
        .run(context -> {
          assertThat(context).hasSingleBean(QueuePublisher.class);
          assertThat(context).hasSingleBean(QueueConsumerRegistry.class);
          assertThat(context).hasSingleBean(QueueDequeueService.class);
          assertThat(context).hasSingleBean(QueueDispatcher.class);
          assertThat(context).hasSingleBean(QueueCleanupService.class);
          assertThat(context).hasSingleBean(PersistentQueueWorkerManager.class);
        });
  }

  @Configuration(proxyBeanMethods = false)
  static class QueueDependencies {

    @Bean
    SqlSessionTemplate sqlSessionTemplate() {
      SqlSessionTemplate template = mock(SqlSessionTemplate.class);
      when(template.getMapper(QueueMessageMapper.class)).thenReturn(mock(QueueMessageMapper.class));
      return template;
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      return mock(PlatformTransactionManager.class);
    }
  }
}
