package org.congcong.algomentor.auth.autoconfigure;

import java.time.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import org.congcong.algomentor.auth.betaaccess.repository.BetaAccessRepository;
import org.congcong.algomentor.auth.betaaccess.repository.mybatis.BetaAccessMapper;
import org.congcong.algomentor.auth.betaaccess.repository.mybatis.MyBatisBetaAccessRepository;
import org.congcong.algomentor.auth.betaaccess.service.BetaAccessAdminService;
import org.congcong.algomentor.auth.betaaccess.service.BetaAccessMetrics;
import org.congcong.algomentor.auth.betaaccess.service.BetaAccessPolicy;
import org.congcong.algomentor.auth.betaaccess.service.BetaAllowedEmailRemovalExecutor;
import org.congcong.algomentor.auth.betaaccess.service.MicrometerBetaAccessMetrics;
import org.congcong.algomentor.auth.betaaccess.service.NoopBetaAccessMetrics;
import org.congcong.algomentor.auth.cache.AuthAccessSnapshotCache;
import org.congcong.algomentor.auth.cache.AuthCacheProperties;
import org.congcong.algomentor.auth.cache.BetaAccessCache;
import org.congcong.algomentor.auth.cache.IdentityUserAccessCacheInvalidationListener;
import org.congcong.algomentor.auth.config.AuthProperties;
import org.congcong.algomentor.auth.controller.admin.BetaAccessController;
import org.congcong.algomentor.auth.controller.admin.BetaAccessExceptionHandler;
import org.congcong.algomentor.auth.controller.admin.AdminPasswordResetController;
import org.congcong.algomentor.auth.controller.admin.AdminPasswordResetExceptionHandler;
import org.congcong.algomentor.auth.controller.CurrentUserController;
import org.congcong.algomentor.auth.controller.CurrentUserResponseFactory;
import org.congcong.algomentor.auth.controller.PasswordAuthController;
import org.congcong.algomentor.auth.controller.UserPasswordController;
import org.congcong.algomentor.auth.password.MicrometerUserPasswordMetrics;
import org.congcong.algomentor.auth.password.NoopUserPasswordMetrics;
import org.congcong.algomentor.auth.password.UserPasswordMetrics;
import org.congcong.algomentor.auth.password.UserPasswordMutationExecutor;
import org.congcong.algomentor.auth.password.UserPasswordService;
import org.congcong.algomentor.auth.repository.AuthUserRepository;
import org.congcong.algomentor.auth.repository.mybatis.AuthUserMapper;
import org.congcong.algomentor.auth.repository.mybatis.MyBatisAuthUserRepository;
import org.congcong.algomentor.auth.security.AuthenticatedDaoAuthenticationProvider;
import org.congcong.algomentor.auth.security.AuthenticatedOAuth2UserService;
import org.congcong.algomentor.auth.security.AuthenticatedOidcUserService;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.auth.security.CurrentAuthenticationContextResolver;
import org.congcong.algomentor.auth.security.PasswordUserDetailsService;
import org.congcong.algomentor.auth.security.SecurityContextCurrentUserIdProvider;
import org.congcong.algomentor.auth.passwordreset.PasswordResetMutationExecutor;
import org.congcong.algomentor.auth.passwordreset.PasswordResetService;
import org.congcong.algomentor.auth.passwordreset.TemporaryPasswordGenerator;
import org.congcong.algomentor.auth.session.AuthSessionMetrics;
import org.congcong.algomentor.auth.session.AuthSessionRepository;
import org.congcong.algomentor.auth.session.AuthSessionRevocationService;
import org.congcong.algomentor.auth.session.IdentityUserStatusChangedEventListener;
import org.congcong.algomentor.auth.session.MicrometerAuthSessionMetrics;
import org.congcong.algomentor.auth.session.NoopAuthSessionMetrics;
import org.congcong.algomentor.auth.session.SpringSessionAuthSessionRepository;
import org.congcong.algomentor.auth.session.SpringSessionAuthSessionRevocationService;
import org.congcong.algomentor.auth.session.policy.AuthSessionPolicyLoginService;
import org.congcong.algomentor.auth.session.policy.AuthSessionPolicyMetrics;
import org.congcong.algomentor.auth.session.policy.AuthSessionPolicyResolver;
import org.congcong.algomentor.auth.session.policy.MicrometerAuthSessionPolicyMetrics;
import org.congcong.algomentor.auth.session.policy.NoopAuthSessionPolicyMetrics;
import org.congcong.algomentor.auth.session.policy.AuthSessionPolicyConstants;
import org.congcong.algomentor.auth.session.policy.UserSessionPolicy;
import org.congcong.algomentor.auth.session.policy.UserSessionPolicyConstraints;
import org.congcong.algomentor.auth.session.admin.controller.AdminAuthSessionController;
import org.congcong.algomentor.auth.session.admin.controller.AdminAuthSessionExceptionHandler;
import org.congcong.algomentor.auth.session.admin.repository.AuthSessionAdminRepository;
import org.congcong.algomentor.auth.session.admin.repository.mybatis.AuthSessionAdminMapper;
import org.congcong.algomentor.auth.session.admin.repository.mybatis.MyBatisAuthSessionAdminRepository;
import org.congcong.algomentor.auth.session.admin.service.AuthSessionAdminMetrics;
import org.congcong.algomentor.auth.session.admin.service.AuthSessionAdminService;
import org.congcong.algomentor.auth.session.admin.service.MicrometerAuthSessionAdminMetrics;
import org.congcong.algomentor.auth.session.admin.service.NoopAuthSessionAdminMetrics;
import org.congcong.algomentor.auth.service.AdminEmailRoleService;
import org.congcong.algomentor.auth.service.AuthPermissionService;
import org.congcong.algomentor.auth.service.OAuth2LoginUserService;
import org.congcong.algomentor.auth.service.PasswordUserService;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.congcong.algomentor.common.api.ApiErrorMessageResolver;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditRecorder;
import org.congcong.algomentor.common.admin.audit.NoopAdminOperationAuditRecorder;
import org.congcong.algomentor.cache.coherence.SharedCacheInvalidationCoordinator;
import org.congcong.algomentor.cache.factory.SharedCacheRegionFactory;
import org.congcong.algomentor.cache.config.CacheAutoConfiguration;
import org.congcong.algomentor.identity.autoconfigure.IdentityAutoConfiguration;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.congcong.algomentor.policy.autoconfigure.GenericPolicyAutoConfiguration;
import org.congcong.algomentor.policy.service.GenericPolicyQueryService;
import org.congcong.algomentor.policy.type.GenericPolicyType;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.session.SessionAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.transaction.PlatformTransactionManager;

@AutoConfiguration(after = {
    CacheAutoConfiguration.class,
    IdentityAutoConfiguration.class,
    SessionAutoConfiguration.class,
    GenericPolicyAutoConfiguration.class
})
@EnableConfigurationProperties({AuthProperties.class, AuthCacheProperties.class})
public class AuthApiAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public CurrentUserIdProvider currentUserIdProvider() {
    return new SecurityContextCurrentUserIdProvider();
  }

  @Bean
  @ConditionalOnMissingBean
  public CurrentAuthenticationContextResolver currentAuthenticationContextResolver() {
    return new CurrentAuthenticationContextResolver();
  }

  @Bean
  @ConditionalOnMissingBean
  public CurrentUserResponseFactory currentUserResponseFactory(
      ObjectProvider<AuthUserRepository> authUserRepositoryProvider,
      CurrentAuthenticationContextResolver authenticationContextResolver,
      AuthPermissionService authPermissionService
  ) {
    return new CurrentUserResponseFactory(
        authUserRepositoryProvider.getIfAvailable(),
        authenticationContextResolver,
        authPermissionService);
  }

  @Bean
  @ConditionalOnMissingBean
  public CurrentUserController currentUserController(
      CurrentUserIdProvider currentUserIdProvider,
      ObjectProvider<ApiErrorResponseFactory> apiErrorResponseFactoryProvider,
      CurrentUserResponseFactory currentUserResponseFactory
  ) {
    ApiErrorResponseFactory responseFactory = apiErrorResponseFactoryProvider.getIfAvailable();
    return responseFactory == null
        ? new CurrentUserController(currentUserIdProvider)
        : new CurrentUserController(currentUserIdProvider, responseFactory, currentUserResponseFactory);
  }

  @Bean
  @ConditionalOnBean(SqlSessionTemplate.class)
  @ConditionalOnMissingBean
  public AuthUserMapper authUserMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(AuthUserMapper.class);
  }

  @Bean
  @ConditionalOnBean(AuthUserMapper.class)
  @ConditionalOnMissingBean
  public AuthUserRepository authUserRepository(AuthUserMapper authUserMapper) {
    return new MyBatisAuthUserRepository(authUserMapper);
  }

  @Bean
  @ConditionalOnBean(SqlSessionTemplate.class)
  @ConditionalOnMissingBean
  public BetaAccessMapper betaAccessMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(BetaAccessMapper.class);
  }

  @Bean
  @ConditionalOnBean(BetaAccessMapper.class)
  @ConditionalOnMissingBean
  public BetaAccessRepository betaAccessRepository(BetaAccessMapper betaAccessMapper) {
    return new MyBatisBetaAccessRepository(betaAccessMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public Clock authClock() {
    return Clock.systemUTC();
  }

  @Bean("authUserSessionPolicyType")
  @ConditionalOnMissingBean(name = "authUserSessionPolicyType")
  public GenericPolicyType<UserSessionPolicy> authUserSessionPolicyType() {
    return GenericPolicyType.of(
        AuthSessionPolicyConstants.TYPE_CODE,
        UserSessionPolicy.class,
        UserSessionPolicyConstraints::validateContent);
  }

  @Bean
  @ConditionalOnMissingBean
  public AuthPermissionService authPermissionService() {
    return new AuthPermissionService();
  }

  @Bean
  @ConditionalOnBean({SharedCacheRegionFactory.class, SharedCacheInvalidationCoordinator.class})
  @ConditionalOnMissingBean
  public AuthAccessSnapshotCache authAccessSnapshotCache(
      SharedCacheRegionFactory cacheFactory,
      SharedCacheInvalidationCoordinator invalidationCoordinator,
      AuthCacheProperties properties) {
    return new AuthAccessSnapshotCache(cacheFactory, invalidationCoordinator, properties);
  }

  @Bean
  @ConditionalOnBean({SharedCacheRegionFactory.class, SharedCacheInvalidationCoordinator.class})
  @ConditionalOnMissingBean
  public BetaAccessCache betaAccessCache(
      SharedCacheRegionFactory cacheFactory,
      SharedCacheInvalidationCoordinator invalidationCoordinator,
      AuthCacheProperties properties) {
    return new BetaAccessCache(cacheFactory, invalidationCoordinator, properties);
  }

  @Bean
  @ConditionalOnBean(AuthAccessSnapshotCache.class)
  @ConditionalOnMissingBean
  public IdentityUserAccessCacheInvalidationListener identityUserAccessCacheInvalidationListener(
      AuthAccessSnapshotCache cache) {
    return new IdentityUserAccessCacheInvalidationListener(cache);
  }

  @Bean
  @ConditionalOnBean(IdentityUserRepository.class)
  @ConditionalOnMissingBean
  public AdminEmailRoleService adminEmailRoleService(
      IdentityUserRepository identityUserRepository,
      AuthProperties authProperties
  ) {
    return new AdminEmailRoleService(identityUserRepository, authProperties.getAdminEmails());
  }

  @Bean
  @ConditionalOnBean(BetaAccessRepository.class)
  @ConditionalOnMissingBean
  public BetaAccessPolicy betaAccessPolicy(
      BetaAccessRepository betaAccessRepository,
      ObjectProvider<AdminEmailRoleService> adminEmailRoleServiceProvider,
      ObjectProvider<BetaAccessCache> cacheProvider
  ) {
    return new BetaAccessPolicy(
        betaAccessRepository,
        adminEmailRoleServiceProvider.getIfAvailable(),
        cacheProvider.getIfAvailable());
  }

  @Bean
  @ConditionalOnMissingBean
  public BetaAccessMetrics betaAccessMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
    MeterRegistry registry = meterRegistryProvider.getIfAvailable();
    return registry == null ? new NoopBetaAccessMetrics() : new MicrometerBetaAccessMetrics(registry);
  }

  @Bean
  @ConditionalOnBean(BetaAccessRepository.class)
  @ConditionalOnMissingBean
  public BetaAllowedEmailRemovalExecutor betaAllowedEmailRemovalExecutor(
      BetaAccessRepository betaAccessRepository,
      ObjectProvider<PlatformTransactionManager> transactionManagerProvider,
      ObjectProvider<BetaAccessCache> cacheProvider
  ) {
    return new BetaAllowedEmailRemovalExecutor(
        betaAccessRepository,
        transactionManagerProvider.getIfAvailable(),
        cacheProvider.getIfAvailable());
  }

  @Bean
  @ConditionalOnBean({BetaAccessRepository.class, IdentityUserRepository.class})
  @ConditionalOnMissingBean
  public BetaAccessAdminService betaAccessAdminService(
      BetaAccessRepository betaAccessRepository,
      IdentityUserRepository identityUserRepository,
      ObjectProvider<AuthSessionRevocationService> sessionRevocationServiceProvider,
      ObjectProvider<AdminOperationAuditRecorder> auditRecorderProvider,
      BetaAllowedEmailRemovalExecutor removalExecutor,
      BetaAccessMetrics betaAccessMetrics,
      Clock authClock,
      ObjectProvider<BetaAccessCache> cacheProvider
  ) {
    return new BetaAccessAdminService(
        betaAccessRepository,
        identityUserRepository,
        sessionRevocationServiceProvider.getIfAvailable(),
        auditRecorderProvider.getIfAvailable(NoopAdminOperationAuditRecorder::new),
        removalExecutor,
        betaAccessMetrics,
        authClock,
        cacheProvider.getIfAvailable());
  }

  @Bean
  @ConditionalOnBean(BetaAccessAdminService.class)
  @ConditionalOnMissingBean
  public BetaAccessController betaAccessController(BetaAccessAdminService betaAccessAdminService) {
    return new BetaAccessController(betaAccessAdminService);
  }

  @Bean
  @ConditionalOnBean(BetaAccessController.class)
  @ConditionalOnMissingBean
  public BetaAccessExceptionHandler betaAccessExceptionHandler(
      ObjectProvider<ApiErrorResponseFactory> responseFactoryProvider
  ) {
    return new BetaAccessExceptionHandler(responseFactoryProvider.getIfAvailable(
        () -> new ApiErrorResponseFactory(new ApiErrorMessageResolver())));
  }

  @Bean
  @ConditionalOnBean({AuthUserRepository.class, IdentityUserRepository.class})
  @ConditionalOnMissingBean
  public PasswordUserDetailsService passwordUserDetailsService(
      AuthUserRepository authUserRepository,
      IdentityUserRepository identityUserRepository,
      Clock authClock,
      ObjectProvider<AdminEmailRoleService> adminEmailRoleServiceProvider
  ) {
    return new PasswordUserDetailsService(
        authUserRepository,
        identityUserRepository,
        authClock,
        adminEmailRoleServiceProvider.getIfAvailable());
  }

  @Bean
  @ConditionalOnBean({AuthUserRepository.class, IdentityUserRepository.class})
  @ConditionalOnMissingBean
  public PasswordUserService passwordUserService(
      AuthUserRepository authUserRepository,
      IdentityUserRepository identityUserRepository,
      PasswordEncoder passwordEncoder,
      Clock authClock,
      ObjectProvider<AdminEmailRoleService> adminEmailRoleServiceProvider,
      ObjectProvider<BetaAccessPolicy> betaAccessPolicyProvider
  ) {
    return new PasswordUserService(
        authUserRepository,
        identityUserRepository,
        passwordEncoder,
        authClock,
        adminEmailRoleServiceProvider.getIfAvailable(),
        betaAccessPolicyProvider.getIfAvailable());
  }

  @Bean
  @ConditionalOnBean(PasswordUserDetailsService.class)
  @ConditionalOnMissingBean
  public AuthenticationManager passwordAuthenticationManager(
      PasswordUserDetailsService passwordUserDetailsService,
      PasswordEncoder passwordEncoder,
      AuthUserRepository authUserRepository,
      Clock authClock,
      ObjectProvider<BetaAccessPolicy> betaAccessPolicyProvider
  ) {
    return new ProviderManager(new AuthenticatedDaoAuthenticationProvider(
        passwordEncoder,
        passwordUserDetailsService,
        authUserRepository,
        authClock,
        betaAccessPolicyProvider.getIfAvailable()));
  }

  @Bean
  @ConditionalOnMissingBean
  public TemporaryPasswordGenerator temporaryPasswordGenerator() {
    return new TemporaryPasswordGenerator();
  }

  @Bean
  @ConditionalOnBean(AuthUserRepository.class)
  @ConditionalOnMissingBean
  public PasswordResetMutationExecutor passwordResetMutationExecutor(
      AuthUserRepository authUserRepository,
      ObjectProvider<AuthSessionRevocationService> sessionRevocationServiceProvider,
      ObjectProvider<PlatformTransactionManager> transactionManagerProvider
  ) {
    return new PasswordResetMutationExecutor(
        authUserRepository,
        sessionRevocationServiceProvider.getIfAvailable(),
        transactionManagerProvider.getIfAvailable());
  }

  @Bean
  @ConditionalOnMissingBean
  public UserPasswordMetrics userPasswordMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
    MeterRegistry registry = meterRegistryProvider.getIfAvailable();
    return registry == null ? new NoopUserPasswordMetrics() : new MicrometerUserPasswordMetrics(registry);
  }

  @Bean
  @ConditionalOnBean(AuthUserRepository.class)
  @ConditionalOnMissingBean
  public UserPasswordMutationExecutor userPasswordMutationExecutor(
      AuthUserRepository authUserRepository,
      ObjectProvider<AuthSessionRevocationService> sessionRevocationServiceProvider,
      ObjectProvider<PlatformTransactionManager> transactionManagerProvider
  ) {
    return new UserPasswordMutationExecutor(
        authUserRepository,
        sessionRevocationServiceProvider.getIfAvailable(),
        transactionManagerProvider.getIfAvailable());
  }

  @Bean
  @ConditionalOnBean({AuthUserRepository.class, IdentityUserRepository.class, UserPasswordMutationExecutor.class})
  @ConditionalOnMissingBean
  public UserPasswordService userPasswordService(
      AuthUserRepository authUserRepository,
      IdentityUserRepository identityUserRepository,
      CurrentAuthenticationContextResolver authenticationContextResolver,
      PasswordEncoder passwordEncoder,
      UserPasswordMutationExecutor mutationExecutor,
      UserPasswordMetrics metrics,
      Clock authClock
  ) {
    return new UserPasswordService(
        authUserRepository,
        identityUserRepository,
        authenticationContextResolver,
        passwordEncoder,
        mutationExecutor,
        metrics,
        authClock);
  }

  @Bean
  @ConditionalOnBean(UserPasswordService.class)
  @ConditionalOnMissingBean
  public UserPasswordController userPasswordController(
      UserPasswordService userPasswordService,
      ObjectProvider<ApiErrorResponseFactory> responseFactoryProvider
  ) {
    return new UserPasswordController(userPasswordService, responseFactoryProvider.getIfAvailable(
        () -> new ApiErrorResponseFactory(new ApiErrorMessageResolver())));
  }

  @Bean
  @ConditionalOnBean({AuthUserRepository.class, IdentityUserRepository.class, PasswordResetMutationExecutor.class})
  @ConditionalOnMissingBean
  public PasswordResetService passwordResetService(
      AuthUserRepository authUserRepository,
      IdentityUserRepository identityUserRepository,
      PasswordEncoder passwordEncoder,
      TemporaryPasswordGenerator temporaryPasswordGenerator,
      PasswordResetMutationExecutor mutationExecutor,
      ObjectProvider<AdminOperationAuditRecorder> auditRecorderProvider,
      Clock authClock
  ) {
    return new PasswordResetService(
        authUserRepository,
        identityUserRepository,
        passwordEncoder,
        temporaryPasswordGenerator,
        mutationExecutor,
        auditRecorderProvider.getIfAvailable(NoopAdminOperationAuditRecorder::new),
        authClock);
  }

  @Bean
  @ConditionalOnBean(PasswordResetService.class)
  @ConditionalOnMissingBean
  public AdminPasswordResetController adminPasswordResetController(PasswordResetService passwordResetService) {
    return new AdminPasswordResetController(passwordResetService);
  }

  @Bean
  @ConditionalOnBean(AdminPasswordResetController.class)
  @ConditionalOnMissingBean
  public AdminPasswordResetExceptionHandler adminPasswordResetExceptionHandler(
      ObjectProvider<ApiErrorResponseFactory> responseFactoryProvider
  ) {
    return new AdminPasswordResetExceptionHandler(responseFactoryProvider.getIfAvailable(
        () -> new ApiErrorResponseFactory(new ApiErrorMessageResolver())));
  }

  @Bean
  @ConditionalOnMissingBean
  public SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  @ConditionalOnMissingBean
  public AuthSessionMetrics authSessionMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
    MeterRegistry registry = meterRegistryProvider.getIfAvailable();
    return registry == null ? new NoopAuthSessionMetrics() : new MicrometerAuthSessionMetrics(registry);
  }

  @Bean
  @ConditionalOnMissingBean
  public AuthSessionPolicyMetrics authSessionPolicyMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
    MeterRegistry registry = meterRegistryProvider.getIfAvailable();
    return registry == null ? new NoopAuthSessionPolicyMetrics()
        : new MicrometerAuthSessionPolicyMetrics(registry);
  }

  @Bean
  @ConditionalOnBean(FindByIndexNameSessionRepository.class)
  @ConditionalOnMissingBean
  public AuthSessionRevocationService authSessionRevocationService(
      FindByIndexNameSessionRepository<? extends Session> sessionRepository
  ) {
    return new SpringSessionAuthSessionRevocationService(sessionRepository);
  }

  @Bean
  @ConditionalOnBean(FindByIndexNameSessionRepository.class)
  @ConditionalOnMissingBean
  public AuthSessionRepository authSessionRepository(
      FindByIndexNameSessionRepository<? extends Session> sessionRepository
  ) {
    return new SpringSessionAuthSessionRepository(sessionRepository);
  }

  @Bean
  @ConditionalOnBean(GenericPolicyQueryService.class)
  @ConditionalOnMissingBean
  public AuthSessionPolicyResolver authSessionPolicyResolver(
      GenericPolicyQueryService genericPolicyQueryService,
      GenericPolicyType<UserSessionPolicy> authUserSessionPolicyType,
      AuthSessionPolicyMetrics metrics
  ) {
    return new AuthSessionPolicyResolver(genericPolicyQueryService, authUserSessionPolicyType, metrics);
  }

  @Bean
  @ConditionalOnMissingBean
  public AuthSessionPolicyLoginService authSessionPolicyLoginService(
      ObjectProvider<AuthSessionPolicyResolver> resolverProvider,
      ObjectProvider<AuthSessionRepository> sessionRepositoryProvider,
      ObjectProvider<AuthSessionRevocationService> revocationServiceProvider,
      AuthSessionPolicyMetrics metrics,
      Clock authClock,
      AuthProperties authProperties
  ) {
    AuthSessionPolicyResolver resolver = resolverProvider.getIfAvailable();
    AuthSessionRepository sessionRepository = sessionRepositoryProvider.getIfAvailable();
    AuthSessionRevocationService revocationService = revocationServiceProvider.getIfAvailable();
    if (resolver == null || sessionRepository == null || revocationService == null) {
      return AuthSessionPolicyLoginService.unavailable(metrics);
    }
    return new AuthSessionPolicyLoginService(
        resolver, sessionRepository, revocationService, metrics, authClock, authProperties);
  }

  @Bean
  @ConditionalOnBean(SqlSessionTemplate.class)
  @ConditionalOnMissingBean
  public AuthSessionAdminMapper authSessionAdminMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(AuthSessionAdminMapper.class);
  }

  @Bean
  @ConditionalOnBean(AuthSessionAdminMapper.class)
  @ConditionalOnMissingBean
  public AuthSessionAdminRepository authSessionAdminRepository(AuthSessionAdminMapper authSessionAdminMapper) {
    return new MyBatisAuthSessionAdminRepository(authSessionAdminMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public AuthSessionAdminMetrics authSessionAdminMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
    MeterRegistry registry = meterRegistryProvider.getIfAvailable();
    return registry == null ? new NoopAuthSessionAdminMetrics() : new MicrometerAuthSessionAdminMetrics(registry);
  }

  @Bean
  @ConditionalOnBean({AuthSessionAdminRepository.class, AuthSessionRevocationService.class})
  @ConditionalOnMissingBean
  public AuthSessionAdminService authSessionAdminService(
      AuthSessionAdminRepository repository,
      AuthSessionRevocationService revocationService,
      ObjectProvider<AdminOperationAuditRecorder> auditRecorderProvider,
      AuthSessionAdminMetrics metrics,
      Clock authClock,
      AuthProperties authProperties
  ) {
    return new AuthSessionAdminService(
        repository,
        revocationService,
        auditRecorderProvider.getIfAvailable(NoopAdminOperationAuditRecorder::new),
        metrics,
        authClock,
        authProperties);
  }

  @Bean
  @ConditionalOnBean(AuthSessionAdminService.class)
  @ConditionalOnMissingBean
  public AdminAuthSessionController adminAuthSessionController(AuthSessionAdminService service) {
    return new AdminAuthSessionController(service);
  }

  @Bean
  @ConditionalOnBean(AdminAuthSessionController.class)
  @ConditionalOnMissingBean
  public AdminAuthSessionExceptionHandler adminAuthSessionExceptionHandler(
      ObjectProvider<ApiErrorResponseFactory> responseFactoryProvider
  ) {
    return new AdminAuthSessionExceptionHandler(responseFactoryProvider.getIfAvailable(
        () -> new ApiErrorResponseFactory(new ApiErrorMessageResolver())));
  }

  @Bean
  @ConditionalOnBean(AuthSessionRevocationService.class)
  @ConditionalOnMissingBean
  public IdentityUserStatusChangedEventListener identityUserStatusChangedEventListener(
      AuthSessionRevocationService revocationService,
      AuthSessionMetrics metrics
  ) {
    return new IdentityUserStatusChangedEventListener(revocationService, metrics);
  }

  @Bean
  @ConditionalOnBean({AuthUserRepository.class, IdentityUserRepository.class})
  @ConditionalOnMissingBean
  public OAuth2LoginUserService oAuth2LoginUserService(
      AuthUserRepository authUserRepository,
      IdentityUserRepository identityUserRepository,
      Clock authClock,
      ObjectProvider<AdminEmailRoleService> adminEmailRoleServiceProvider,
      ObjectProvider<BetaAccessPolicy> betaAccessPolicyProvider
  ) {
    return new OAuth2LoginUserService(
        authUserRepository,
        identityUserRepository,
        authClock,
        adminEmailRoleServiceProvider.getIfAvailable(),
        betaAccessPolicyProvider.getIfAvailable());
  }

  @Bean
  @ConditionalOnMissingBean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  @ConditionalOnBean({PasswordUserService.class, AuthenticationManager.class, SecurityContextRepository.class})
  @ConditionalOnMissingBean
  public PasswordAuthController passwordAuthController(
      PasswordUserService passwordUserService,
      AuthenticationManager authenticationManager,
      SecurityContextRepository securityContextRepository,
      CurrentUserResponseFactory currentUserResponseFactory,
      ObjectProvider<PasswordResetService> passwordResetServiceProvider,
      ObjectProvider<ApiErrorResponseFactory> apiErrorResponseFactoryProvider,
      AuthSessionPolicyLoginService authSessionPolicyLoginService
  ) {
    ApiErrorResponseFactory responseFactory = apiErrorResponseFactoryProvider.getIfAvailable();
    return new PasswordAuthController(
        passwordUserService,
        authenticationManager,
        securityContextRepository,
        responseFactory == null
            ? new ApiErrorResponseFactory(new ApiErrorMessageResolver())
            : responseFactory,
        currentUserResponseFactory,
        passwordResetServiceProvider.getIfAvailable(),
        authSessionPolicyLoginService);
  }

  @Bean
  @ConditionalOnBean(OAuth2LoginUserService.class)
  @ConditionalOnMissingBean
  public AuthenticatedOAuth2UserService authenticatedOAuth2UserService(
      OAuth2LoginUserService oAuth2LoginUserService
  ) {
    return new AuthenticatedOAuth2UserService(oAuth2LoginUserService);
  }

  @Bean
  @ConditionalOnBean(OAuth2LoginUserService.class)
  @ConditionalOnMissingBean
  public AuthenticatedOidcUserService authenticatedOidcUserService(
      OAuth2LoginUserService oAuth2LoginUserService
  ) {
    return new AuthenticatedOidcUserService(oAuth2LoginUserService);
  }
}
