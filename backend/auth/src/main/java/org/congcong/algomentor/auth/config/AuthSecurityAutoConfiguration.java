package org.congcong.algomentor.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.congcong.algomentor.auth.betaaccess.service.BetaAccessPolicy;
import org.congcong.algomentor.auth.cache.AuthAccessSnapshotCache;
import org.congcong.algomentor.auth.github.GitHubOAuthConstants;
import org.congcong.algomentor.auth.model.OAuthProvider;
import org.congcong.algomentor.auth.security.ActiveIdentityUserFilter;
import org.congcong.algomentor.auth.security.ApiAuthenticationEntryPoint;
import org.congcong.algomentor.auth.security.AuthenticatedOAuth2UserService;
import org.congcong.algomentor.auth.security.AuthenticatedOidcUserService;
import org.congcong.algomentor.auth.security.CsrfTokenCookieFilter;
import org.congcong.algomentor.auth.security.OAuth2AuthenticationFailureHandler;
import org.congcong.algomentor.auth.security.OAuth2AuthenticationSuccessHandler;
import org.congcong.algomentor.auth.security.PasswordChangeRequiredFilter;
import org.congcong.algomentor.auth.security.SpaCsrfTokenRequestHandler;
import org.congcong.algomentor.auth.session.policy.AuthSessionAbsoluteTimeoutFilter;
import org.congcong.algomentor.auth.session.policy.AuthSessionPolicyLoginService;
import org.congcong.algomentor.auth.session.policy.AuthSessionPolicyMetrics;
import org.congcong.algomentor.auth.session.policy.NoopAuthSessionPolicyMetrics;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.boot.web.server.Cookie.SameSite;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.server.CookieSameSiteSupplier;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.jdbc.PostgreSqlJdbcIndexedSessionRepositoryCustomizer;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

@AutoConfiguration
@EnableWebSecurity
@EnableMethodSecurity
@ConditionalOnClass(SecurityFilterChain.class)
@EnableConfigurationProperties(AuthProperties.class)
public class AuthSecurityAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(AuthSecurityAutoConfiguration.class);
  private static final String GOOGLE_TOKEN_URI = "https://oauth2.googleapis.com/token";
  private static final String GOOGLE_USER_INFO_URI = "https://openidconnect.googleapis.com/v1/userinfo";

  @Bean
  public ServletContextInitializer authSessionCookieInitializer(AuthProperties properties) {
    return servletContext -> {
      servletContext.setSessionTimeout(Math.toIntExact(properties.getSessionTimeout().toMinutes()));
      servletContext.getSessionCookieConfig().setHttpOnly(true);
      servletContext.getSessionCookieConfig().setSecure(properties.isCookieSecure());
      servletContext.getSessionCookieConfig().setName(AuthSecurityPaths.SESSION_COOKIE_NAME);
    };
  }

  @Bean
  public CookieSameSiteSupplier authSessionCookieSameSiteSupplier(AuthProperties properties) {
    return CookieSameSiteSupplier.of(sameSite(properties.getCookieSameSite()))
        .whenHasName(AuthSecurityPaths.SESSION_COOKIE_NAME);
  }

  @Bean("springSessionConversionService")
  public GenericConversionService springSessionConversionService() {
    GenericConversionService conversionService = new GenericConversionService();
    conversionService.addConverter(Object.class, byte[].class, new SerializingConverter());
    conversionService.addConverter(byte[].class, Object.class, new DeserializingConverter());
    return conversionService;
  }

  @Bean
  @ConditionalOnMissingBean(PostgreSqlJdbcIndexedSessionRepositoryCustomizer.class)
  public SessionRepositoryCustomizer<JdbcIndexedSessionRepository> authPostgreSqlJdbcSessionRepositoryCustomizer() {
    return new PostgreSqlJdbcIndexedSessionRepositoryCustomizer();
  }

  @Bean
  @Conditional(OAuth2ClientConfiguredCondition.class)
  @ConditionalOnMissingBean(ClientRegistrationRepository.class)
  public ClientRegistrationRepository oauth2ClientRegistrationRepository(Environment environment) {
    List<ClientRegistration> registrations = new ArrayList<>();
    if (hasText(environment.getProperty(AuthConfigurationKeys.GOOGLE_OAUTH2_CLIENT_ID_ENV))) {
      registrations.add(CommonOAuth2Provider.GOOGLE
          .getBuilder(OAuthProvider.GOOGLE.value())
          .clientId(requiredOAuth2Credential(
              environment,
              AuthConfigurationKeys.GOOGLE_OAUTH2_CLIENT_ID_ENV,
              OAuthProvider.GOOGLE))
          .clientSecret(requiredOAuth2Credential(
              environment,
              AuthConfigurationKeys.GOOGLE_OAUTH2_CLIENT_SECRET_ENV,
              OAuthProvider.GOOGLE))
          .tokenUri(GOOGLE_TOKEN_URI)
          .userInfoUri(GOOGLE_USER_INFO_URI)
          .build());
    }
    if (hasText(environment.getProperty(AuthConfigurationKeys.GITHUB_OAUTH2_CLIENT_ID_ENV))) {
      registrations.add(CommonOAuth2Provider.GITHUB
          .getBuilder(OAuthProvider.GITHUB.value())
          .clientId(requiredOAuth2Credential(
              environment,
              AuthConfigurationKeys.GITHUB_OAUTH2_CLIENT_ID_ENV,
              OAuthProvider.GITHUB))
          .clientSecret(requiredOAuth2Credential(
              environment,
              AuthConfigurationKeys.GITHUB_OAUTH2_CLIENT_SECRET_ENV,
              OAuthProvider.GITHUB))
          .scope(GitHubOAuthConstants.READ_USER_SCOPE, GitHubOAuthConstants.USER_EMAIL_SCOPE)
          .build());
    }
    return new InMemoryClientRegistrationRepository(registrations);
  }

  @Bean
  public SecurityFilterChain authSecurityFilterChain(
      HttpSecurity http,
      ObjectProvider<ObjectMapper> objectMapperProvider,
      ObjectProvider<ApiErrorResponseFactory> apiErrorResponseFactoryProvider,
      ObjectProvider<AuthenticatedOAuth2UserService> authenticatedOAuth2UserService,
      ObjectProvider<AuthenticatedOidcUserService> authenticatedOidcUserService,
      ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository,
      ObjectProvider<SecurityContextRepository> securityContextRepository,
      ObjectProvider<IdentityUserRepository> identityUserRepositoryProvider,
      ObjectProvider<BetaAccessPolicy> betaAccessPolicyProvider,
      ObjectProvider<AuthAccessSnapshotCache> accessSnapshotCacheProvider,
      ObjectProvider<AuthSessionPolicyLoginService> sessionPolicyLoginServiceProvider,
      ObjectProvider<AuthSessionPolicyMetrics> sessionPolicyMetricsProvider,
      ObjectProvider<Clock> authClockProvider,
      AuthProperties properties
  ) throws Exception {
    CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(() -> new ObjectMapper().findAndRegisterModules());
    ApiErrorResponseFactory apiErrorResponseFactory = apiErrorResponseFactoryProvider.getIfAvailable();
    AuthenticationEntryPoint apiAuthenticationEntryPoint = apiErrorResponseFactory == null
        ? new ApiAuthenticationEntryPoint(objectMapper)
        : new ApiAuthenticationEntryPoint(objectMapper, apiErrorResponseFactory);
    ClientRegistrationRepository registrations = clientRegistrationRepository.getIfAvailable();
    log.info(
        "Configuring auth security filter chain. oauth2ClientRegistrationRepositoryPresent={} loginSuccessUrl={} cookieSecure={} cookieSameSite={} sessionTimeout={}",
        registrations != null,
        properties.getLoginSuccessUrl(),
        properties.isCookieSecure(),
        properties.getCookieSameSite(),
        properties.getSessionTimeout());
    logOAuth2Registrations(registrations);

    http
        .csrf(csrf -> csrf
            .csrfTokenRepository(csrfTokenRepository)
            .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
        .addFilterAfter(new CsrfTokenCookieFilter(), CsrfFilter.class)
        .securityContext(securityContext -> securityContextRepository.ifAvailable(securityContext::securityContextRepository))
        .exceptionHandling(exceptions -> exceptions
            .defaultAuthenticationEntryPointFor(
                apiAuthenticationEntryPoint,
                new AntPathRequestMatcher(AuthSecurityPaths.API_PATTERN))
            .defaultAuthenticationEntryPointFor(
                new HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED),
                new MediaTypeRequestMatcher(org.springframework.http.MediaType.APPLICATION_JSON)))
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers(new AntPathRequestMatcher(AuthSecurityPaths.HEALTH_PATH)).permitAll()
            .requestMatchers(new AntPathRequestMatcher("/actuator/health")).permitAll()
            .requestMatchers(new AntPathRequestMatcher("/actuator/health/**")).permitAll()
            .requestMatchers(new AntPathRequestMatcher(AuthSecurityPaths.OAUTH2_AUTHORIZATION_PATTERN)).permitAll()
            .requestMatchers(new AntPathRequestMatcher(AuthSecurityPaths.OAUTH2_CALLBACK_PATTERN)).permitAll()
            .requestMatchers(new AntPathRequestMatcher(AuthSecurityPaths.AUTH_CAPABILITIES_PATH)).permitAll()
            .requestMatchers(new AntPathRequestMatcher(AuthSecurityPaths.AUTH_REGISTER_PATH)).permitAll()
            .requestMatchers(new AntPathRequestMatcher(AuthSecurityPaths.AUTH_LOGIN_PATH)).permitAll()
            .requestMatchers(new AntPathRequestMatcher(
                AuthSecurityPaths.AUTH_LOGOUT_PATH,
                AuthSecurityPaths.LOGOUT_METHOD.name())).permitAll()
            .requestMatchers(new AntPathRequestMatcher(AuthSecurityPaths.ADMIN_API_PATTERN)).hasRole("ADMIN")
            .requestMatchers(new AntPathRequestMatcher(AuthSecurityPaths.AGENT_CONVERSATIONS_API_PATTERN)).hasRole("ADMIN")
            .requestMatchers(new AntPathRequestMatcher("/")).permitAll()
            .requestMatchers(new AntPathRequestMatcher("/index.html")).permitAll()
            .requestMatchers(new AntPathRequestMatcher("/assets/**")).permitAll()
            .requestMatchers(new AntPathRequestMatcher("/favicon.ico")).permitAll()
            .requestMatchers(new AntPathRequestMatcher(AuthSecurityPaths.API_PATTERN)).authenticated()
            .anyRequest().permitAll());

    if (registrations != null) {
      http.oauth2Login(oauth2 -> oauth2
          .loginPage("/login")
          .userInfoEndpoint(userInfo -> {
            authenticatedOAuth2UserService.ifAvailable(userInfo::userService);
            authenticatedOidcUserService.ifAvailable(userInfo::oidcUserService);
          })
          .successHandler(new OAuth2AuthenticationSuccessHandler(
              properties.getLoginSuccessUrl(), sessionPolicyLoginServiceProvider.getIfAvailable()))
          .failureHandler(new OAuth2AuthenticationFailureHandler()));
    }

    http
        .logout(logout -> logout
            .logoutUrl(AuthSecurityPaths.AUTH_LOGOUT_PATH)
            .logoutSuccessUrl(properties.getLogoutSuccessUrl())
            .invalidateHttpSession(true)
            .deleteCookies("JSESSIONID"))
        .sessionManagement(Customizer.withDefaults());

    http.addFilterAfter(
        new AuthSessionAbsoluteTimeoutFilter(
            apiAuthenticationEntryPoint,
            sessionPolicyMetricsProvider.getIfAvailable(NoopAuthSessionPolicyMetrics::new),
            authClockProvider.getIfAvailable(Clock::systemUTC),
            properties),
        SecurityContextHolderFilter.class);
    identityUserRepositoryProvider.ifAvailable(repository -> http.addFilterAfter(
        new ActiveIdentityUserFilter(
            repository,
            apiAuthenticationEntryPoint,
            betaAccessPolicyProvider.getIfAvailable(),
            objectMapper,
            apiErrorResponseFactory == null
                ? new ApiErrorResponseFactory(new org.congcong.algomentor.common.api.ApiErrorMessageResolver())
                : apiErrorResponseFactory,
            accessSnapshotCacheProvider.getIfAvailable()),
        SecurityContextHolderFilter.class));
    http.addFilterAfter(
        new PasswordChangeRequiredFilter(
            objectMapper,
            apiErrorResponseFactory == null
                ? new ApiErrorResponseFactory(new org.congcong.algomentor.common.api.ApiErrorMessageResolver())
                : apiErrorResponseFactory),
        SecurityContextHolderFilter.class);

    return http.build();
  }

  private static void logOAuth2Registrations(ClientRegistrationRepository registrations) {
    if (registrations == null) {
      log.info("OAuth2 registration diagnostics. repositoryPresent=false");
      return;
    }
    for (OAuthProvider provider : OAuthProvider.values()) {
      logOAuth2Registration(registrations.findByRegistrationId(provider.value()), provider);
    }
  }

  private static void logOAuth2Registration(ClientRegistration registration, OAuthProvider provider) {
    if (registration == null) {
      log.info("OAuth2 registration diagnostics. provider={} registrationPresent=false", provider.value());
      return;
    }
    log.info(
        "OAuth2 registration diagnostics. provider={} registrationPresent=true clientIdPresent={} redirectUri={} scopes={} authorizationUriPresent={} tokenUriPresent={} userInfoUriPresent={} jwkSetUriPresent={}",
        provider.value(),
        registration.getClientId() != null && !registration.getClientId().isBlank(),
        registration.getRedirectUri(),
        registration.getScopes(),
        hasText(registration.getProviderDetails().getAuthorizationUri()),
        hasText(registration.getProviderDetails().getTokenUri()),
        hasText(registration.getProviderDetails().getUserInfoEndpoint().getUri()),
        hasText(registration.getProviderDetails().getJwkSetUri()));
  }

  private static String requiredOAuth2Credential(
      Environment environment,
      String key,
      OAuthProvider provider
  ) {
    String value = environment.getProperty(key);
    if (!hasText(value)) {
      throw new IllegalStateException(
          key + " must not be blank when " + provider.value() + " OAuth2 is enabled.");
    }
    return value;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static SameSite sameSite(String value) {
    if (value == null || value.isBlank()) {
      return SameSite.LAX;
    }
    return SameSite.valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
  }
}
