package org.congcong.algomentor.mentor.application.practice;

import java.util.List;

/** 按题目读取受信标签候选的应用层端口。 */
public interface TrustedProblemTagCatalog {

  List<TrustedProblemTag> findByProblemSlug(String problemSlug);

  static TrustedProblemTagCatalog empty() {
    return ignored -> List.of();
  }
}
