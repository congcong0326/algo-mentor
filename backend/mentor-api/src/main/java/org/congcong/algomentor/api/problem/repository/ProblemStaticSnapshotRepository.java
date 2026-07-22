package org.congcong.algomentor.api.problem.repository;

import java.util.Optional;
import org.congcong.algomentor.api.problem.model.ProblemStaticSnapshot;

/** 读取题库发布期不可变双语快照的持久化能力。 */
public interface ProblemStaticSnapshotRepository {

  Optional<ProblemStaticSnapshot> findStaticSnapshotBySlug(String slug);
}
