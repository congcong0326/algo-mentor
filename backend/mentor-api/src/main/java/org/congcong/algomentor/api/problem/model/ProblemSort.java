package org.congcong.algomentor.api.problem.model;

import java.util.Locale;

public enum ProblemSort {
  FRONTEND_ID_ASC,
  FRONTEND_ID_DESC,
  TITLE_ASC,
  UPDATED_DESC;

  public static final ProblemSort DEFAULT = FRONTEND_ID_ASC;

  public static ProblemSort parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return DEFAULT;
    }
    return switch (raw.trim().toLowerCase(Locale.ROOT)) {
      case "frontend_id_desc", "id_desc" -> FRONTEND_ID_DESC;
      case "title_asc", "title" -> TITLE_ASC;
      case "updated_desc", "updated" -> UPDATED_DESC;
      default -> FRONTEND_ID_ASC;
    };
  }
}
