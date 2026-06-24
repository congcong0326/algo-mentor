package org.congcong.algomentor.api.practice.model;

import java.util.List;

public record PracticeProblemSummaryResponse(
    String slug,
    Integer frontendId,
    String title,
    String titleCn,
    String difficulty,
    List<String> tags,
    String leetcodeUrl) {
}
