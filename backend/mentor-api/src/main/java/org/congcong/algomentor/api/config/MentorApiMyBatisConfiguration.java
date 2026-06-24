package org.congcong.algomentor.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.congcong.algomentor.api.learningplan.mapper.LearningPlanMapper;
import org.congcong.algomentor.api.learningplan.repository.MyBatisLearningPlanRepository;
import org.congcong.algomentor.api.practice.mapper.PracticeSessionMapper;
import org.congcong.algomentor.api.practice.repository.MyBatisPracticeSessionRepository;
import org.congcong.algomentor.api.problem.mapper.ProblemMapper;
import org.congcong.algomentor.api.problem.repository.MyBatisProblemRepository;
import org.congcong.algomentor.api.problem.repository.ProblemRepository;
import org.congcong.algomentor.agent.persistence.postgres.json.AgentMessageRoleTypeHandler;
import org.congcong.algomentor.agent.persistence.postgres.json.JsonbTypeHandler;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionRepository;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.datasource.url")
@EnableTransactionManagement
public class MentorApiMyBatisConfiguration {

  @Bean
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
  @ConditionalOnMissingBean
  public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
    return new SqlSessionTemplate(sqlSessionFactory);
  }

  @Bean
  @ConditionalOnMissingBean
  public ProblemMapper problemMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(ProblemMapper.class);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanMapper learningPlanMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(LearningPlanMapper.class);
  }

  @Bean
  @ConditionalOnMissingBean
  public PracticeSessionMapper practiceSessionMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(PracticeSessionMapper.class);
  }

  @Bean
  @ConditionalOnMissingBean(ProblemRepository.class)
  public ProblemRepository problemRepository(ProblemMapper problemMapper, DataSource dataSource) {
    return new MyBatisProblemRepository(problemMapper, dataSource);
  }

  @Bean
  @ConditionalOnMissingBean({LearningPlanDraftRepository.class, LearningPlanRepository.class})
  public MyBatisLearningPlanRepository learningPlanRepository(
      LearningPlanMapper learningPlanMapper,
      ObjectMapper objectMapper) {
    return new MyBatisLearningPlanRepository(learningPlanMapper, objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean(PracticeSessionRepository.class)
  public PracticeSessionRepository practiceSessionRepository(PracticeSessionMapper practiceSessionMapper) {
    return new MyBatisPracticeSessionRepository(practiceSessionMapper);
  }
}
