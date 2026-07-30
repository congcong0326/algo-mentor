package org.congcong.algomentor.auth.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestClientGitHubEmailClientTest {

  @Test
  void returnsOnlyThePrimaryVerifiedEmail() {
    RestClient.Builder builder = RestClient.builder().baseUrl(GitHubOAuthConstants.EMAILS_URI);
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo(GitHubOAuthConstants.EMAILS_URI))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
        .andRespond(withSuccess("""
            [
              {"email":"secondary@example.com","primary":false,"verified":true},
              {"email":"unverified@example.com","primary":true,"verified":false},
              {"email":"primary@example.com","primary":true,"verified":true}
            ]
            """, MediaType.APPLICATION_JSON));
    RestClientGitHubEmailClient client = new RestClientGitHubEmailClient(builder.build());

    assertThat(client.findPrimaryVerifiedEmail("access-token")).contains("primary@example.com");
    server.verify();
  }

  @Test
  void skipsTheRequestWhenTheAccessTokenIsBlank() {
    RestClient.Builder builder = RestClient.builder().baseUrl(GitHubOAuthConstants.EMAILS_URI);
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    RestClientGitHubEmailClient client = new RestClientGitHubEmailClient(builder.build());

    assertThat(client.findPrimaryVerifiedEmail(" ")).isEmpty();
    server.verify();
  }
}
