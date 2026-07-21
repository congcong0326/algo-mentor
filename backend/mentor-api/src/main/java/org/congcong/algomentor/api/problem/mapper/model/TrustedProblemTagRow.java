package org.congcong.algomentor.api.problem.mapper.model;

/** 题目规范化标签关联的受信读取行。 */
public record TrustedProblemTagRow(long tagId, String value, String labelEn, String labelZh) {
}
