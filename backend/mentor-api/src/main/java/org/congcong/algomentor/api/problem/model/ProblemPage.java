package org.congcong.algomentor.api.problem.model;

import java.util.List;

public record ProblemPage<T>(
    List<T> items,
    long total,
    int page,
    int pageSize
) {
}
