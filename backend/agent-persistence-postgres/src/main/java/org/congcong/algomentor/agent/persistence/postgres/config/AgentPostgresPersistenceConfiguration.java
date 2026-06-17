package org.congcong.algomentor.agent.persistence.postgres.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.congcong.algomentor.agent.core.runtime.repository.AgentConversationRepository;
import org.congcong.algomentor.agent.persistence.postgres.json.AgentMessageRoleTypeHandler;
import org.congcong.algomentor.agent.persistence.postgres.json.JsonbTypeHandler;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentArtifactMapper;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentContextSnapshotMapper;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentConversationMapper;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentRunMapper;
import org.congcong.algomentor.agent.persistence.postgres.observer.PersistentAgentRunObserver;
import org.congcong.algomentor.agent.persistence.postgres.observer.PersistentAgentTraceObserver;
import org.congcong.algomentor.agent.persistence.postgres.repository.PostgresAgentConversationRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@EnableTransactionManagement
public class AgentPostgresPersistenceConfiguration {

  @Bean
  @ConditionalOnBean(DataSource.class)
  @ConditionalOnMissingBean
  public SqlSessionFactory agentSqlSessionFactory(
      DataSource dataSource,
      ObjectMapper objectMapper
  ) throws Exception {
    org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
    configuration.setMapUnderscoreToCamelCase(true);

    SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
    factoryBean.setDataSource(dataSource);
    factoryBean.setConfiguration(configuration);
    factoryBean.setMapperLocations(
        new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/agent/*.xml"));
    factoryBean.setTypeHandlers(
        new JsonbTypeHandler(objectMapper),
        new AgentMessageRoleTypeHandler());
    return factoryBean.getObject();
  }

  @Bean
  @ConditionalOnBean(SqlSessionFactory.class)
  @ConditionalOnMissingBean
  public SqlSessionTemplate agentSqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
    return new SqlSessionTemplate(sqlSessionFactory);
  }

  @Bean
  @ConditionalOnBean(SqlSessionTemplate.class)
  @ConditionalOnMissingBean
  public AgentConversationMapper agentConversationMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(AgentConversationMapper.class);
  }

  @Bean
  @ConditionalOnBean(SqlSessionTemplate.class)
  @ConditionalOnMissingBean
  public AgentRunMapper agentRunMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(AgentRunMapper.class);
  }

  @Bean
  @ConditionalOnBean(SqlSessionTemplate.class)
  @ConditionalOnMissingBean
  public AgentContextSnapshotMapper agentContextSnapshotMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(AgentContextSnapshotMapper.class);
  }

  @Bean
  @ConditionalOnBean(SqlSessionTemplate.class)
  @ConditionalOnMissingBean
  public AgentArtifactMapper agentArtifactMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(AgentArtifactMapper.class);
  }

  @Bean
  @ConditionalOnBean(AgentConversationMapper.class)
  @ConditionalOnMissingBean
  public AgentConversationRepository agentConversationRepository(AgentConversationMapper conversationMapper) {
    return new PostgresAgentConversationRepository(conversationMapper);
  }

  @Bean
  @ConditionalOnBean(AgentContextSnapshotMapper.class)
  @ConditionalOnMissingBean
  public PersistentAgentTraceObserver persistentAgentTraceObserver(
      AgentContextSnapshotMapper snapshotMapper,
      ObjectMapper objectMapper
  ) {
    return new PersistentAgentTraceObserver(snapshotMapper, objectMapper);
  }

  @Bean
  @ConditionalOnBean(AgentRunMapper.class)
  @ConditionalOnMissingBean
  public PersistentAgentRunObserver persistentAgentRunObserver(
      AgentRunMapper runMapper,
      ObjectMapper objectMapper
  ) {
    return new PersistentAgentRunObserver(runMapper, objectMapper);
  }
}
