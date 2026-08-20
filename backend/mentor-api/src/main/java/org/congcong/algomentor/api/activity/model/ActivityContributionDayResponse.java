package org.congcong.algomentor.api.activity.model;

import java.time.LocalDate;

public record ActivityContributionDayResponse(LocalDate date, int count, int level) {
}
