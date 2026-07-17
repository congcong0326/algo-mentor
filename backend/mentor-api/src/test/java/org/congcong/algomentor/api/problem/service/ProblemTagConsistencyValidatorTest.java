package org.congcong.algomentor.api.problem.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.congcong.algomentor.api.problem.mapper.ProblemTagMapper;
import org.congcong.algomentor.api.problem.model.ProblemTagDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ProblemTagConsistencyValidatorTest {

  @Test
  void acceptsAConsistentCatalog() {
    ProblemTagMapper mapper = mock(ProblemTagMapper.class);
    when(mapper.findArrayConsistencyViolationSlugs()).thenReturn(List.of());
    when(mapper.findAssignmentOrdinalViolationSlugs()).thenReturn(List.of());
    when(mapper.findDanglingAssignmentReferences()).thenReturn(List.of());
    when(mapper.findDuplicateCatalogValues()).thenReturn(List.of());
    when(mapper.findInactiveCatalogValues(List.of("array"))).thenReturn(List.of());
    ProblemTagConsistencyValidator validator = new ProblemTagConsistencyValidator(provider(mapper));

    assertThatCode(() -> validator.validate(List.of(new ProblemTagDefinition("array", "Array", "数组"))))
        .doesNotThrowAnyException();
  }

  @Test
  void reportsTheFirstConsistencyFailureWithASmallSample() {
    ProblemTagMapper mapper = mock(ProblemTagMapper.class);
    when(mapper.findArrayConsistencyViolationSlugs()).thenReturn(List.of("two-sum"));
    ProblemTagConsistencyValidator validator = new ProblemTagConsistencyValidator(provider(mapper));

    assertThatThrownBy(() -> validator.validate(List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("array consistency")
        .hasMessageContaining("two-sum");
  }

  private <T> ObjectProvider<T> provider(T value) {
    return new StaticObjectProvider<>(value);
  }

  private record StaticObjectProvider<T>(T value) implements ObjectProvider<T> {
    @Override
    public T getObject(Object... args) {
      return value;
    }

    @Override
    public T getIfAvailable() {
      return value;
    }

    @Override
    public T getIfUnique() {
      return value;
    }

    @Override
    public T getObject() {
      return value;
    }
  }
}
