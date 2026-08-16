package org.congcong.algomentor.mentor.application.review.schedule;

import java.time.DateTimeException;
import java.time.ZoneId;
import org.congcong.algomentor.mentor.application.review.ReviewException;

/** 统一解析复习接口传入的用户时区。 */
public final class ReviewZoneId {

  private static final String DEFAULT_TIMEZONE = "UTC";

  private ReviewZoneId() {
  }

  public static ZoneId parse(String timezone) {
    String normalized = timezone == null || timezone.isBlank() ? DEFAULT_TIMEZONE : timezone.trim();
    try {
      return ZoneId.of(normalized);
    } catch (DateTimeException exception) {
      throw new ReviewException("REVIEW_TIMEZONE_INVALID", "时区参数无效。");
    }
  }
}
