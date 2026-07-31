package org.congcong.algomentor.mentor.application.profile.document;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 绑定用户和 ACTIVE revision 的版本化 HMAC statement ref / evidence cursor。 */
public final class LearnerProfileStatementReferenceCodec {

  private static final String VERSION = "v1";
  private static final String STATEMENT_KIND = "s";
  private static final String CURSOR_KIND = "c";
  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private final byte[] secret;

  public LearnerProfileStatementReferenceCodec(String secret) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalArgumentException("learner profile statement reference secret is required");
    }
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
  }

  public String encodeStatementRef(long userId, long claimRevisionId) {
    return sign(VERSION + "|" + STATEMENT_KIND + "|" + userId + "|" + claimRevisionId);
  }

  public Optional<StatementReference> decodeStatementRef(long expectedUserId, String value) {
    Optional<String[]> parts = verifiedParts(value, 4);
    if (parts.isEmpty()) {
      return Optional.empty();
    }
    String[] values = parts.get();
    if (!VERSION.equals(values[0]) || !STATEMENT_KIND.equals(values[1])) {
      return Optional.empty();
    }
    try {
      long userId = Long.parseLong(values[2]);
      long claimRevisionId = Long.parseLong(values[3]);
      if (userId != expectedUserId || userId <= 0 || claimRevisionId <= 0) {
        return Optional.empty();
      }
      return Optional.of(new StatementReference(claimRevisionId));
    } catch (NumberFormatException exception) {
      return Optional.empty();
    }
  }

  public String encodeCursor(long userId, long claimRevisionId, int pageSize, EvidenceCursor cursor) {
    return sign(VERSION + "|" + CURSOR_KIND + "|" + userId + "|" + claimRevisionId + "|" + pageSize
        + "|" + cursor.occurredAt().getEpochSecond() + "|" + cursor.occurredAt().getNano() + "|"
        + cursor.type().name() + "|" + cursor.sourceId());
  }

  public Optional<EvidenceCursor> decodeCursor(
      long expectedUserId,
      long expectedClaimRevisionId,
      int expectedPageSize,
      String value) {
    if (value == null || value.isBlank()) {
      return Optional.ofNullable(null);
    }
    Optional<String[]> parts = verifiedParts(value, 9);
    if (parts.isEmpty()) {
      return Optional.empty();
    }
    String[] values = parts.get();
    if (!VERSION.equals(values[0]) || !CURSOR_KIND.equals(values[1])) {
      return Optional.empty();
    }
    try {
      long userId = Long.parseLong(values[2]);
      long revisionId = Long.parseLong(values[3]);
      int pageSize = Integer.parseInt(values[4]);
      Instant occurredAt = Instant.ofEpochSecond(Long.parseLong(values[5]), Long.parseLong(values[6]));
      LearnerProfileDocument.EvidenceType type = LearnerProfileDocument.EvidenceType.valueOf(values[7]);
      long sourceId = Long.parseLong(values[8]);
      if (userId != expectedUserId || revisionId != expectedClaimRevisionId || pageSize != expectedPageSize
          || userId <= 0 || revisionId <= 0 || pageSize <= 0 || sourceId <= 0) {
        return Optional.empty();
      }
      return Optional.of(new EvidenceCursor(occurredAt, type, sourceId));
    } catch (RuntimeException exception) {
      return Optional.empty();
    }
  }

  private Optional<String[]> verifiedParts(String value, int expectedPartCount) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    String[] token = value.split("\\.", -1);
    if (token.length != 2) {
      return Optional.empty();
    }
    try {
      byte[] payload = Base64.getUrlDecoder().decode(token[0]);
      byte[] actualSignature = Base64.getUrlDecoder().decode(token[1]);
      byte[] expectedSignature = hmac(payload);
      if (!MessageDigest.isEqual(actualSignature, expectedSignature)) {
        return Optional.empty();
      }
      String[] parts = new String(payload, StandardCharsets.UTF_8).split("\\|", -1);
      return parts.length == expectedPartCount ? Optional.of(parts) : Optional.empty();
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
  }

  private String sign(String payload) {
    byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes) + "."
        + Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(payloadBytes));
  }

  private byte[] hmac(byte[] payload) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
      return mac.doFinal(payload);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to sign learner profile reference", exception);
    }
  }

  public record StatementReference(long claimRevisionId) {
  }

  public record EvidenceCursor(
      Instant occurredAt,
      LearnerProfileDocument.EvidenceType type,
      long sourceId
  ) {
    public EvidenceCursor {
      if (occurredAt == null || type == null || sourceId <= 0) {
        throw new IllegalArgumentException("evidence cursor is invalid");
      }
    }
  }
}
