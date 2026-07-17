package org.congcong.algomentor.api.problem;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.congcong.algomentor.api.problem.mapper.ProblemMapper;
import org.congcong.algomentor.api.problem.model.ProblemListRequest;
import org.congcong.algomentor.api.problem.model.ProblemLocale;
import org.congcong.algomentor.api.problem.model.ProblemSort;
import org.congcong.algomentor.api.problem.model.ProblemTag;
import org.congcong.algomentor.api.problem.repository.MyBatisProblemRepository;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;

class ProblemTagReadModelIT extends PostgresIntegrationTestSupport {

  @Test
  void readsTagsFiltersAndCountsFromNormalizedRelationsWhileKeepingTheApiModel() throws Exception {
    migrateLatest();
    long array = insertCatalog("array", "Array", "数组", true);
    long hashTable = insertCatalog("hash-table", "Hash Table", "哈希表", true);
    long graph = insertCatalog("graph", "Graph", "图", false);
    insertProblem(
        "two-tags",
        1,
        List.of("array", "hash-table"),
        List.of("Array", "Hash Table"),
        List.of("数组", "哈希表"));
    insertProblem("inactive-tag", 2, List.of("graph"), List.of("Graph"), List.of("图"));
    insertProblem("no-tags", 3, List.of(), List.of(), List.of());
    assignTag("two-tags", array, 0);
    assignTag("two-tags", hashTable, 1);
    assignTag("inactive-tag", graph, 0);
    MyBatisProblemRepository repository = repository();

    assertThat(repository.findProblemBySlug("two-tags", ProblemLocale.ZH_CN).orElseThrow().tags())
        .containsExactly(new ProblemTag("array", "数组"), new ProblemTag("hash-table", "哈希表"));
    assertThat(repository.findProblemBySlug("two-tags", ProblemLocale.EN_US).orElseThrow().tags())
        .containsExactly(new ProblemTag("array", "Array"), new ProblemTag("hash-table", "Hash Table"));
    assertThat(repository.findProblemBySlug("inactive-tag", ProblemLocale.ZH_CN).orElseThrow().tags())
        .containsExactly(new ProblemTag("graph", "图"));
    assertThat(repository.findProblemBySlug("no-tags", ProblemLocale.ZH_CN).orElseThrow().tags()).isEmpty();

    assertThat(repository.findProblems(request("array")).total()).isEqualTo(1L);
    assertThat(repository.findProblems(request("graph")).total()).isZero();
    assertThat(repository.findProblems(request(null)).items()).hasSize(3);

    var chineseFilters = repository.findProblemFilters(ProblemLocale.ZH_CN);
    assertThat(chineseFilters.tags())
        .containsExactlyInAnyOrder(
            new org.congcong.algomentor.api.problem.model.ProblemFilterOption("array", "数组", 1),
            new org.congcong.algomentor.api.problem.model.ProblemFilterOption("hash-table", "哈希表", 1));
    var englishFilters = repository.findProblemFilters(ProblemLocale.EN_US);
    assertThat(englishFilters.tags())
        .extracting(filter -> List.of(filter.value(), filter.label()))
        .containsExactlyInAnyOrder(List.of("array", "Array"), List.of("hash-table", "Hash Table"));
  }

  private MyBatisProblemRepository repository() throws Exception {
    SqlSessionTemplate sqlSessionTemplate = sqlSessionTemplate("mapper/problem/ProblemMapper.xml");
    return new MyBatisProblemRepository(sqlSessionTemplate.getMapper(ProblemMapper.class), dataSource());
  }

  private ProblemListRequest request(String tag) {
    return new ProblemListRequest(
        null,
        null,
        tag,
        null,
        null,
        null,
        null,
        ProblemSort.DEFAULT,
        1,
        20,
        ProblemLocale.ZH_CN);
  }
}
