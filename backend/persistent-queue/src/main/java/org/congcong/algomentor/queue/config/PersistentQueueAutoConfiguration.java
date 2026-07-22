package org.congcong.algomentor.queue.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.congcong.algomentor.queue.postgres.MyBatisQueueMessageRepository;
import org.congcong.algomentor.queue.postgres.QueueMessageMapper;
import org.congcong.algomentor.queue.publisher.PostgresQueuePublisher;
import org.congcong.algomentor.queue.publisher.QueuePublisher;
import org.congcong.algomentor.queue.repository.QueueMessageRepository;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** 持久化队列存储与发布能力；不依赖或启动任何消费者。 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
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
}
