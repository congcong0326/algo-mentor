package org.congcong.algomentor.mentor.application.practice;

import java.util.Optional;

public interface PracticeChatProblemCatalog {

  Optional<PracticeChatProblemDetail> findProblemBySlug(String slug, String locale);
}
