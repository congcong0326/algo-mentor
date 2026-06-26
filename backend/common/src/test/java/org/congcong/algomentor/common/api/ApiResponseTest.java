package org.congcong.algomentor.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Map;
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
    assertThat(response.error().messageKey()).isNull();
    assertThat(response.error().message()).isEqualTo("Topic is required");
  }

  @Test
  void failureCanIncludeMessageKeyAndMetadata() {
    ApiResponse<Void> response = ApiResponse.failureWithMessageKey(
        "INVALID_REQUEST",
        "api.error.INVALID_REQUEST",
        "Topic is required",
        Map.of("field", "topic"));

    assertThat(response.error()).isNotNull();
    assertThat(response.error().code()).isEqualTo("INVALID_REQUEST");
    assertThat(response.error().messageKey()).isEqualTo("api.error.INVALID_REQUEST");
    assertThat(response.error().metadata()).containsEntry("field", "topic");
  }

  @Test
  void emptyMetadataIsNotSerialized() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(
        ApiResponse.failureWithMessageKey("INVALID_REQUEST", "api.error.INVALID_REQUEST", "Topic is required")));

    assertThat(root.at("/error/messageKey").asText()).isEqualTo("api.error.INVALID_REQUEST");
    assertThat(root.at("/error/metadata").isMissingNode()).isTrue();
  }
}
