package org.congcong.algomentor.api.activity.model;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;

/**
 * 单个活跃日的紧凑响应项。JSON 以 {@code [offset, count]} 输出，offset 相对响应的 from 日期。
 */
public record ActivityContributionDailyCountResponse(int offset, int count) {

  @JsonValue
  public List<Integer> compactValue() {
    return List.of(offset, count);
  }
}
