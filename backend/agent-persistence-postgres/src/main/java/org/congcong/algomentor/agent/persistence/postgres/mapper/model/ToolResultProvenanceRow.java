package org.congcong.algomentor.agent.persistence.postgres.mapper.model;

/** 持久化工具结果的最小来源信息，不包含 blob 内容。 */
public record ToolResultProvenanceRow(int stepIndex, String toolCallId, String toolName) {
}
