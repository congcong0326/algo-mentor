package org.congcong.algomentor.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.congcong.algomentor.api.problem.mapper.ProblemMapper;
import org.congcong.algomentor.agent.persistence.postgres.json.AgentMessageRoleTypeHandler;
import org.congcong.algomentor.agent.persistence.postgres.json.JsonbTypeHandler;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
public class MentorApiMyBatisConfiguration {

  @Bean
  @ConditionalOnBean(DataSource.class)
  @ConditionalOnMissingBean
  public SqlSessionFactory sqlSessionFactory(DataSource dataSource, ObjectMapper objectMapper) throws Exception {
    org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
    configuration.setMapUnderscoreToCamelCase(true);

    SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
    factoryBean.setDataSource(dataSource);
    factoryBean.setConfiguration(configuration);
    factoryBean.setMapperLocations(
        new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml"));
    factoryBean.setTypeHandlers(
        new JsonbTypeHandler(objectMapper),
        new AgentMessageRoleTypeHandler());
    return factoryBean.getObject();
  }

  @Bean
  @ConditionalOnBean(SqlSessionFactory.class)
  @ConditionalOnMissingBean
  public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
    return new SqlSessionTemplate(sqlSessionFactory);
  }

  @Bean
  @ConditionalOnBean(SqlSessionTemplate.class)
  @ConditionalOnMissingBean
  public ProblemMapper problemMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(ProblemMapper.class);
  }
}
