package org.congcong.algomentor.queue.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.congcong.algomentor.queue.PersistentQueueConstants;
import org.congcong.algomentor.queue.postgres.MyBatisQueueMessageRepository;
import org.congcong.algomentor.queue.postgres.QueueMessageMapper;
import org.congcong.algomentor.queue.publisher.PostgresQueuePublisher;
import org.congcong.algomentor.queue.publisher.QueuePublisher;
import org.congcong.algomentor.queue.repository.QueueMessageRepository;
import org.congcong.algomentor.queue.consumer.BatchQueueConsumer;
import org.congcong.algomentor.queue.consumer.QueueConsumer;
import org.congcong.algomentor.queue.consumer.QueueConsumerRegistry;
import org.congcong.algomentor.queue.dispatch.QueueDequeueService;
import org.congcong.algomentor.queue.dispatch.QueueDispatcher;
import org.congcong.algomentor.queue.metrics.MicrometerQueueMetrics;
import org.congcong.algomentor.queue.metrics.QueueMetrics;
import org.congcong.algomentor.queue.runtime.PersistentQueueWorkerManager;
import org.congcong.algomentor.queue.runtime.QueueCleanupScheduler;
import org.congcong.algomentor.queue.runtime.QueueCleanupService;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 持久化队列存储 Bean 装配；不依赖或启动任何消费者。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(SqlSessionTemplate.class)
@EnableConfigurationProperties(PersistentQueueProperties.class)
public class PersistentQueueAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public QueueMessageMapper queueMessageMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(QueueMessageMapper.class);
  }

  @Bean
  @ConditionalOnMissingBean(QueueMessageRepository.class)
  public QueueMessageRepository queueMessageRepository(QueueMessageMapper mapper) {
    return new MyBatisQueueMessageRepository(mapper);
  }

  @Bean
  @ConditionalOnMissingBean(QueuePublisher.class)
  public QueuePublisher queuePublisher(
      ObjectMapper objectMapper,
      QueueMessageRepository repository,
      PersistentQueueProperties properties) {
    return new PostgresQueuePublisher(objectMapper, repository, properties);
  }

  @Bean
  @ConditionalOnMissingBean
  public QueueConsumerRegistry queueConsumerRegistry(
      org.springframework.beans.factory.ObjectProvider<QueueConsumer> consumers,
      org.springframework.beans.factory.ObjectProvider<BatchQueueConsumer> batchConsumers) {
    return new QueueConsumerRegistry(consumers.orderedStream().toList(), batchConsumers.orderedStream().toList());
  }

  @Bean
  @ConditionalOnMissingBean
  public QueueDequeueService queueDequeueService(
      QueueMessageRepository repository, PlatformTransactionManager transactionManager) {
    return new QueueDequeueService(repository, new TransactionTemplate(transactionManager));
  }

  @Bean
  @ConditionalOnMissingBean
  public QueueDispatcher queueDispatcher(
      QueueConsumerRegistry registry, QueueMessageRepository repository, QueueDequeueService dequeueService) {
    return new QueueDispatcher(registry, repository, dequeueService);
  }

  @Bean
  @ConditionalOnMissingBean
  public QueueMetrics queueMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
    MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
    return meterRegistry == null ? QueueMetrics.NOOP : new MicrometerQueueMetrics(meterRegistry);
  }

  @Bean
  @ConditionalOnProperty(prefix = PersistentQueueConstants.CONFIG_PREFIX + ".consumer", name = "enabled", havingValue = "true")
  @ConditionalOnMissingBean
  public QueueCleanupService queueCleanupService(
      QueueMessageRepository repository,
      PersistentQueueProperties properties,
      PlatformTransactionManager transactionManager,
      QueueMetrics metrics) {
    return new QueueCleanupService(repository, properties.getCleanup(), new TransactionTemplate(transactionManager), metrics);
  }

  @Bean
  @ConditionalOnProperty(prefix = PersistentQueueConstants.CONFIG_PREFIX + ".consumer", name = "enabled", havingValue = "true")
  @ConditionalOnMissingBean
  public QueueCleanupScheduler queueCleanupScheduler(
      QueueCleanupService cleanupService, PersistentQueueProperties properties) {
    return new QueueCleanupScheduler(cleanupService, properties.getCleanup());
  }

  @Bean
  @ConditionalOnProperty(prefix = PersistentQueueConstants.CONFIG_PREFIX + ".consumer", name = "enabled", havingValue = "true")
  @ConditionalOnMissingBean
  public PersistentQueueWorkerManager persistentQueueWorkerManager(
      QueueConsumerRegistry registry,
      QueueDispatcher dispatcher,
      PersistentQueueProperties properties,
      QueueMetrics metrics,
      QueueMessageRepository repository,
      QueueCleanupScheduler cleanupScheduler) {
    return new PersistentQueueWorkerManager(
        registry, dispatcher, properties.getConsumer(), metrics, repository, cleanupScheduler);
  }
}
