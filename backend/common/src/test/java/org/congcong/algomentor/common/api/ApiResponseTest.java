package org.congcong.algomentor.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiResponseTest {

  @Test
  void successCreatesEnvelopeWithPayload() {
    ApiResponse<String> response = ApiResponse.success("ready");

    assertThat(response.success()).isTrue();
    assertThat(response.data()).isEqualTo("ready");
    assertThat(response.error()).isNull();
    assertThat(response.timestamp()).isNotNull();
  }

  @Test
  void failureCreatesEnvelopeWithoutPayload() {
    ApiResponse<Void> response = ApiResponse.failure("INVALID_REQUEST", "Topic is required");

    assertThat(response.success()).isFalse();
    assertThat(response.data()).isNull();
    assertThat(response.error()).isNotNull();
    assertThat(response.error().code()).isEqualTo("INVALID_REQUEST");
    assertThat(response.error().message()).isEqualTo("Topic is required");
  }
}

