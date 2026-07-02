package org.congcong.algomentor.mentor.application.review;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record ReviewCardCache(
    JsonNode cardJson,
    CardVariant variant,
    String signature,
    Instant generatedAt
) {
}
