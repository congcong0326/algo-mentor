package org.congcong.algomentor.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import org.congcong.algomentor.auth.security.ActiveIdentityUserFilter;
import org.congcong.algomentor.auth.security.ApiAuthenticationEntryPoint;
import org.congcong.algomentor.auth.security.AuthenticatedOAuth2UserService;
import org.congcong.algomentor.auth.security.AuthenticatedOidcUserService;
import org.congcong.algomentor.auth.security.CsrfTokenCookieFilter;
import org.congcong.algomentor.auth.security.OAuth2AuthenticationFailureHandler;
import org.congcong.algomentor.auth.security.OAuth2AuthenticationSuccessHandler;
import org.congcong.algomentor.auth.security.SpaCsrfTokenRequestHandler;
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
  public SecurityFilterChain authSecurityFilterChain(
      HttpSecurity http,
      ObjectProvider<ObjectMapper> objectMapperProvider,
      ObjectProvider<ApiErrorResponseFactory> apiErrorResponseFactoryProvider,
      ObjectProvider<AuthenticatedOAuth2UserService> authenticatedOAuth2UserService,
      ObjectProvider<AuthenticatedOidcUserService> authenticatedOidcUserService,
      ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository,
      ObjectProvider<SecurityContextRepository> securityContextRepository,
      ObjectProvider<IdentityUserRepository> identityUserRepositoryProvider,
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
    logGoogleRegistration(registrations);

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
            .anyRequest().permitAll())
        .oauth2Login(oauth2 -> oauth2
            .loginPage("/login")
            .userInfoEndpoint(userInfo -> {
              authenticatedOAuth2UserService.ifAvailable(userInfo::userService);
              authenticatedOidcUserService.ifAvailable(userInfo::oidcUserService);
            })
            .successHandler(new OAuth2AuthenticationSuccessHandler(properties.getLoginSuccessUrl()))
            .failureHandler(new OAuth2AuthenticationFailureHandler()))
        .logout(logout -> logout
            .logoutUrl(AuthSecurityPaths.AUTH_LOGOUT_PATH)
            .logoutSuccessUrl(properties.getLogoutSuccessUrl())
            .invalidateHttpSession(true)
            .deleteCookies("JSESSIONID"))
        .sessionManagement(Customizer.withDefaults());

    identityUserRepositoryProvider.ifAvailable(repository -> http.addFilterAfter(
        new ActiveIdentityUserFilter(repository, apiAuthenticationEntryPoint),
        SecurityContextHolderFilter.class));

    return http.build();
  }

  private static void logGoogleRegistration(ClientRegistrationRepository registrations) {
    if (registrations == null) {
      log.info("Google OAuth2 registration diagnostics. repositoryPresent=false");
      return;
    }
    ClientRegistration google = registrations.findByRegistrationId("google");
    if (google == null) {
      log.info("Google OAuth2 registration diagnostics. registrationPresent=false");
      return;
    }
    log.info(
        "Google OAuth2 registration diagnostics. registrationPresent=true clientIdPresent={} redirectUri={} scopes={} authorizationUriPresent={} tokenUriPresent={} userInfoUriPresent={} jwkSetUriPresent={} jwkSetUri={}",
        google.getClientId() != null && !google.getClientId().isBlank(),
        google.getRedirectUri(),
        google.getScopes(),
        hasText(google.getProviderDetails().getAuthorizationUri()),
        hasText(google.getProviderDetails().getTokenUri()),
        hasText(google.getProviderDetails().getUserInfoEndpoint().getUri()),
        hasText(google.getProviderDetails().getJwkSetUri()),
        google.getProviderDetails().getJwkSetUri());
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
