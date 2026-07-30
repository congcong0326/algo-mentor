package org.congcong.algomentor.auth.github;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public class RestClientGitHubEmailClient implements GitHubEmailClient {

  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
  private static final ParameterizedTypeReference<List<GitHubEmailResponse>> RESPONSE_TYPE =
      new ParameterizedTypeReference<>() {
      };

  private final RestClient restClient;

  public RestClientGitHubEmailClient() {
    this(defaultRestClient());
  }

  RestClientGitHubEmailClient(RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public Optional<String> findPrimaryVerifiedEmail(String accessToken) {
    if (accessToken == null || accessToken.isBlank()) {
      return Optional.empty();
    }
    List<GitHubEmailResponse> emails = restClient.get()
        .uri("")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .retrieve()
        .body(RESPONSE_TYPE);
    if (emails == null) {
      return Optional.empty();
    }
    return emails.stream()
        .filter(email -> email.primary() && email.verified())
        .map(GitHubEmailResponse::email)
        .filter(email -> email != null && !email.isBlank())
        .findFirst();
  }

  private static RestClient defaultRestClient() {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(REQUEST_TIMEOUT);
    requestFactory.setReadTimeout(REQUEST_TIMEOUT);
    return RestClient.builder()
        .requestFactory(requestFactory)
        .baseUrl(GitHubOAuthConstants.EMAILS_URI)
        .defaultHeader(HttpHeaders.ACCEPT, GitHubOAuthConstants.ACCEPT_HEADER_VALUE)
        .defaultHeader(GitHubOAuthConstants.API_VERSION_HEADER, GitHubOAuthConstants.API_VERSION)
        .defaultHeader(HttpHeaders.USER_AGENT, GitHubOAuthConstants.USER_AGENT)
        .build();
  }

  record GitHubEmailResponse(String email, boolean primary, boolean verified) {
  }
}
