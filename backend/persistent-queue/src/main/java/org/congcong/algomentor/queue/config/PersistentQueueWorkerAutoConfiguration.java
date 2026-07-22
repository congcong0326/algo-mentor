package org.congcong.algomentor.queue.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.congcong.algomentor.queue.PersistentQueueConstants;
import org.congcong.algomentor.queue.consumer.BatchQueueConsumer;
import org.congcong.algomentor.queue.consumer.QueueConsumer;
import org.congcong.algomentor.queue.consumer.QueueConsumerRegistry;
import org.congcong.algomentor.queue.dispatch.QueueDequeueService;
import org.congcong.algomentor.queue.dispatch.QueueDispatcher;
import org.congcong.algomentor.queue.metrics.MicrometerQueueMetrics;
import org.congcong.algomentor.queue.metrics.QueueMetrics;
import org.congcong.algomentor.queue.repository.QueueMessageRepository;
import org.congcong.algomentor.queue.runtime.PersistentQueueWorkerManager;
import org.congcong.algomentor.queue.runtime.QueueCleanupScheduler;
import org.congcong.algomentor.queue.runtime.QueueCleanupService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 显式开启消费者后装配队列派发、清理和 worker 运行时。 */
@AutoConfiguration(after = PersistentQueueAutoConfiguration.class)
@ConditionalOnProperty(
    prefix = PersistentQueueConstants.CONFIG_PREFIX + ".consumer",
    name = "enabled",
    havingValue = "true")
public class PersistentQueueWorkerAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public QueueConsumerRegistry queueConsumerRegistry(
      ObjectProvider<QueueConsumer> consumers,
      ObjectProvider<BatchQueueConsumer> batchConsumers) {
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
  @ConditionalOnMissingBean
  public QueueCleanupService queueCleanupService(
      QueueMessageRepository repository,
      PersistentQueueProperties properties,
      PlatformTransactionManager transactionManager,
      QueueMetrics metrics) {
    return new QueueCleanupService(
        repository,
        properties.getCleanup(),
        new TransactionTemplate(transactionManager),
        metrics);
  }

  @Bean
  @ConditionalOnMissingBean
  public QueueCleanupScheduler queueCleanupScheduler(
      QueueCleanupService cleanupService, PersistentQueueProperties properties) {
    return new QueueCleanupScheduler(cleanupService, properties.getCleanup());
  }

  @Bean
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
