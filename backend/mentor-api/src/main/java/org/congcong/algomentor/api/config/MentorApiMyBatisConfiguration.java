package org.congcong.algomentor.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.congcong.algomentor.api.ability.mapper.AbilityProfileMapper;
import org.congcong.algomentor.api.learningplan.mapper.LearningPlanMapper;
import org.congcong.algomentor.api.learningplan.mapper.LearningPlanTemplateMapper;
import org.congcong.algomentor.api.learningplan.repository.MyBatisLearningPlanRepository;
import org.congcong.algomentor.api.learningplan.repository.MyBatisLearningPlanProposalRepository;
import org.congcong.algomentor.api.learningplan.repository.MyBatisLearningPlanTemplateRepository;
import org.congcong.algomentor.api.learningplan.repository.LearningPlanTemplateCacheProperties;
import org.congcong.algomentor.api.preference.mapper.UserAiPreferenceMapper;
import org.congcong.algomentor.api.preference.repository.MyBatisUserAiPreferenceRepository;
import org.congcong.algomentor.api.practice.mapper.PracticeCodeReviewMapper;
import org.congcong.algomentor.api.practice.mapper.PracticeSessionMapper;
import org.congcong.algomentor.api.practice.repository.MyBatisPracticeCodeReviewRepository;
import org.congcong.algomentor.api.practice.repository.MyBatisPracticeSessionRepository;
import org.congcong.algomentor.api.profile.mapper.LearnerProfileMapper;
import org.congcong.algomentor.api.profile.repository.MyBatisLearnerProfileRepository;
import org.congcong.algomentor.api.problem.mapper.ProblemMapper;
import org.congcong.algomentor.api.problem.mapper.ProblemCompanyMapper;
import org.congcong.algomentor.api.problem.mapper.ProblemTagMapper;
import org.congcong.algomentor.api.problem.repository.MyBatisProblemCompanyRepository;
import org.congcong.algomentor.api.problem.repository.MyBatisProblemRepository;
import org.congcong.algomentor.api.problem.repository.MyBatisProblemTagRepository;
import org.congcong.algomentor.api.problem.repository.ProblemCompanyRepository;
import org.congcong.algomentor.api.problem.repository.ProblemRepository;
import org.congcong.algomentor.api.problem.repository.ProblemTagRepository;
import org.congcong.algomentor.api.problem.service.ProblemCacheProperties;
import org.congcong.algomentor.api.review.mapper.ProblemReviewAttemptMapper;
import org.congcong.algomentor.api.review.mapper.ProblemReviewCardMapper;
import org.congcong.algomentor.api.review.mapper.ReviewPreferenceMapper;
import org.congcong.algomentor.api.review.mapper.UserProblemNoteMapper;
import org.congcong.algomentor.api.review.repository.MyBatisReviewAttemptRepository;
import org.congcong.algomentor.api.review.repository.MyBatisReviewCardRepository;
import org.congcong.algomentor.api.review.repository.MyBatisReviewPreferenceRepository;
import org.congcong.algomentor.api.review.repository.MyBatisUserProblemNoteRepository;
import org.congcong.algomentor.agent.persistence.postgres.json.AgentMessageRoleTypeHandler;
import org.congcong.algomentor.agent.persistence.postgres.json.JsonbTypeHandler;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRepository;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateRepository;
import org.congcong.algomentor.mentor.application.preference.UserAiPreferenceRepository;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewRepository;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionRepository;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileContentPolicy;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileQueryService;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileRepository;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileUpdateService;
import org.congcong.algomentor.mentor.application.review.attempt.ReviewAttemptRepository;
import org.congcong.algomentor.mentor.application.review.card.ReviewCardRepository;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNoteRepository;
import org.congcong.algomentor.mentor.application.review.preference.ReviewPreferenceRepository;
import org.congcong.algomentor.cache.factory.LocalCacheRegionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.datasource.url")
@EnableTransactionManagement
@EnableConfigurationProperties({
    LearnerProfileProperties.class,
    ProblemCacheProperties.class,
    LearningPlanTemplateCacheProperties.class
})
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
  public ProblemCompanyMapper problemCompanyMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(ProblemCompanyMapper.class);
  }

  @Bean
  @ConditionalOnMissingBean
  public ProblemTagMapper problemTagMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(ProblemTagMapper.class);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanMapper learningPlanMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(LearningPlanMapper.class);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanTemplateMapper learningPlanTemplateMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(LearningPlanTemplateMapper.class);
  }

  @Bean
  @ConditionalOnMissingBean
  public PracticeSessionMapper practiceSessionMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(PracticeSessionMapper.class);
  }

  @Bean
  @ConditionalOnMissingBean
  public PracticeCodeReviewMapper practiceCodeReviewMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(PracticeCodeReviewMapper.class);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearnerProfileMapper learnerProfileMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(LearnerProfileMapper.class);
  }

  @Bean
  @ConditionalOnMissingBean
  public ProblemReviewCardMapper problemReviewCardMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(ProblemReviewCardMapper.class);
  }

  @Bean
  @ConditionalOnMissingBean
  public ProblemReviewAttemptMapper problemReviewAttemptMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(ProblemReviewAttemptMapper.class);
  }

  @Bean
  @ConditionalOnMissingBean
  public ReviewPreferenceMapper reviewPreferenceMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(ReviewPreferenceMapper.class);
  }

  @Bean
  @ConditionalOnMissingBean
  public UserProblemNoteMapper userProblemNoteMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(UserProblemNoteMapper.class);
  }

  @Bean
  @ConditionalOnMissingBean
  public AbilityProfileMapper abilityProfileMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(AbilityProfileMapper.class);
  }

  @Bean
  @ConditionalOnMissingBean
  public UserAiPreferenceMapper userAiPreferenceMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(UserAiPreferenceMapper.class);
  }

  @Bean
  @ConditionalOnMissingBean(ProblemRepository.class)
  public ProblemRepository problemRepository(ProblemMapper problemMapper, DataSource dataSource) {
    return new MyBatisProblemRepository(problemMapper, dataSource);
  }

  @Bean
  @ConditionalOnMissingBean(ProblemTagRepository.class)
  public ProblemTagRepository problemTagRepository(ProblemTagMapper problemTagMapper) {
    return new MyBatisProblemTagRepository(problemTagMapper);
  }

  @Bean
  @ConditionalOnMissingBean(ProblemCompanyRepository.class)
  public ProblemCompanyRepository problemCompanyRepository(ProblemCompanyMapper problemCompanyMapper) {
    return new MyBatisProblemCompanyRepository(problemCompanyMapper);
  }

  @Bean
  @ConditionalOnMissingBean({LearningPlanDraftRepository.class, LearningPlanRepository.class})
  public MyBatisLearningPlanRepository learningPlanRepository(
      LearningPlanMapper learningPlanMapper,
      ObjectMapper objectMapper) {
    return new MyBatisLearningPlanRepository(learningPlanMapper, objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean(LearningPlanTemplateRepository.class)
  public LearningPlanTemplateRepository learningPlanTemplateRepository(
      LearningPlanTemplateMapper learningPlanTemplateMapper,
      ObjectMapper objectMapper,
      LocalCacheRegionFactory cacheFactory,
      LearningPlanTemplateCacheProperties cacheProperties) {
    return new MyBatisLearningPlanTemplateRepository(
        learningPlanTemplateMapper, objectMapper, cacheFactory, cacheProperties);
  }

  @Bean
  @ConditionalOnMissingBean(LearningPlanProposalRepository.class)
  public LearningPlanProposalRepository learningPlanProposalRepository(
      LearningPlanMapper learningPlanMapper,
      ObjectMapper objectMapper) {
    return new MyBatisLearningPlanProposalRepository(learningPlanMapper, objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean(PracticeSessionRepository.class)
  public PracticeSessionRepository practiceSessionRepository(PracticeSessionMapper practiceSessionMapper) {
    return new MyBatisPracticeSessionRepository(practiceSessionMapper);
  }

  @Bean
  @ConditionalOnMissingBean(PracticeCodeReviewRepository.class)
  public PracticeCodeReviewRepository practiceCodeReviewRepository(
      PracticeCodeReviewMapper mapper,
      ObjectMapper objectMapper
  ) {
    return new MyBatisPracticeCodeReviewRepository(mapper, objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean(LearnerProfileRepository.class)
  public LearnerProfileRepository learnerProfileRepository(LearnerProfileMapper mapper) {
    return new MyBatisLearnerProfileRepository(mapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearnerProfileContentPolicy learnerProfileContentPolicy(LearnerProfileProperties properties) {
    return new LearnerProfileContentPolicy(properties.getMaxChars());
  }

  @Bean
  @ConditionalOnMissingBean
  public LearnerProfileQueryService learnerProfileQueryService(LearnerProfileRepository repository) {
    return new LearnerProfileQueryService(repository);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearnerProfileUpdateService learnerProfileUpdateService(
      LearnerProfileRepository repository,
      LearnerProfileContentPolicy contentPolicy,
      PlatformTransactionManager transactionManager) {
    return new LearnerProfileUpdateService(repository, contentPolicy, new TransactionTemplate(transactionManager));
  }

  @Bean
  @ConditionalOnMissingBean(ReviewCardRepository.class)
  public ReviewCardRepository reviewCardRepository(ProblemReviewCardMapper mapper, ObjectMapper objectMapper) {
    return new MyBatisReviewCardRepository(mapper, objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean(ReviewAttemptRepository.class)
  public ReviewAttemptRepository reviewAttemptRepository(
      ProblemReviewAttemptMapper mapper,
      ObjectMapper objectMapper
  ) {
    return new MyBatisReviewAttemptRepository(mapper, objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean(ReviewPreferenceRepository.class)
  public ReviewPreferenceRepository reviewPreferenceRepository(ReviewPreferenceMapper mapper) {
    return new MyBatisReviewPreferenceRepository(mapper);
  }

  @Bean
  @ConditionalOnMissingBean(UserProblemNoteRepository.class)
  public UserProblemNoteRepository userProblemNoteRepository(
      UserProblemNoteMapper mapper,
      ObjectMapper objectMapper
  ) {
    return new MyBatisUserProblemNoteRepository(mapper, objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean(UserAiPreferenceRepository.class)
  public UserAiPreferenceRepository userAiPreferenceRepository(UserAiPreferenceMapper mapper) {
    return new MyBatisUserAiPreferenceRepository(mapper);
  }
}
