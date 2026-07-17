package org.congcong.algomentor.api.problem.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.congcong.algomentor.api.problem.mapper.ProblemTagMapper;
import org.congcong.algomentor.api.problem.mapper.model.ProblemTagAssignmentRow;
import org.congcong.algomentor.api.problem.mapper.model.ProblemTagCatalogUpsertRow;
import org.congcong.algomentor.api.problem.model.ProblemSeedTag;
import org.congcong.algomentor.api.problem.model.ProblemTagDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class MyBatisProblemTagRepositoryTest {

  @Test
  void writesCatalogOnceAndReplacesAssignmentsInOrdinalOrder() {
    ProblemTagMapper mapper = mock(ProblemTagMapper.class);
    when(mapper.insertAssignments(any())).thenReturn(2);
    MyBatisProblemTagRepository repository = new MyBatisProblemTagRepository(mapper);

    repository.upsertCatalog(List.of(
        new ProblemTagDefinition("array", "Array", "数组"),
        new ProblemTagDefinition("hash-table", "Hash Table", "哈希表")));
    repository.replaceAssignments("two-sum", List.of(
        new ProblemSeedTag("array", "Array", "数组", 0),
        new ProblemSeedTag("hash-table", "Hash Table", "哈希表", 1)));

    verify(mapper).upsertCatalog(List.of(
        new ProblemTagCatalogUpsertRow("array", "Array", "数组"),
        new ProblemTagCatalogUpsertRow("hash-table", "Hash Table", "哈希表")));
    InOrder inOrder = inOrder(mapper);
    inOrder.verify(mapper).deleteAssignmentsByProblemSlug("two-sum");
    inOrder.verify(mapper).insertAssignments(List.of(
        new ProblemTagAssignmentRow("two-sum", "array", 0),
        new ProblemTagAssignmentRow("two-sum", "hash-table", 1)));
  }

  @Test
  void deletesAssignmentsForAProblemWithoutTags() {
    ProblemTagMapper mapper = mock(ProblemTagMapper.class);
    MyBatisProblemTagRepository repository = new MyBatisProblemTagRepository(mapper);

    repository.replaceAssignments("no-tags", List.of());

    verify(mapper).deleteAssignmentsByProblemSlug("no-tags");
    verifyNoMoreInteractions(mapper);
  }

  @Test
  void failsWhenTheMapperCannotInsertEveryAssignment() {
    ProblemTagMapper mapper = mock(ProblemTagMapper.class);
    when(mapper.insertAssignments(any())).thenReturn(0);
    MyBatisProblemTagRepository repository = new MyBatisProblemTagRepository(mapper);

    assertThatThrownBy(() -> repository.replaceAssignments("two-sum", List.of(
        new ProblemSeedTag("array", "Array", "数组", 0))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("slug=two-sum");
  }
}
