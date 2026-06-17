package org.congcong.algomentor.api.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationCommand;
import org.congcong.algomentor.mentor.application.conversation.ConversationDraft;
import org.congcong.algomentor.mentor.application.conversation.ConversationMessage;
import org.congcong.algomentor.mentor.application.conversation.ConversationRepository;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.annotation.Transactional;

public class JdbcConversationRepository implements ConversationRepository {

  private final JdbcOperations jdbcOperations;

  public JdbcConversationRepository(JdbcOperations jdbcOperations) {
    this.jdbcOperations = jdbcOperations;
  }

  @Override
  @Transactional
  public ConversationDraft createOrReuseRun(AgentConversationCommand command) {
    Long existingRunId = queryLong(
        "SELECT id FROM agent_run WHERE idempotency_key = ?",
        command.idempotencyKey());
    if (existingRunId != null) {
      return existingDraft(existingRunId);
    }

    long taskId = command.taskId() == null ? createTask(command) : command.taskId();
    long turnSequence = nextSequence("agent_turn", taskId);
    long turnId = insertTurn(taskId, turnSequence);
    long messageSequence = nextSequence("agent_message", taskId);
    long userMessageId = insertUserMessage(taskId, turnId, messageSequence, command.userMessage());
    long runId = insertRun(taskId, turnId, command.idempotencyKey());
    jdbcOperations.update("""
        UPDATE agent_turn
        SET user_message_id = ?, current_run_id = ?, updated_at = NOW()
        WHERE id = ?
        """, userMessageId, runId, turnId);
    return new ConversationDraft(
        taskId,
        turnId,
        runId,
        runUuid(runId),
        command.idempotencyKey(),
        "You are an algorithm learning mentor. Explain clearly, ask guiding questions when useful, and prefer Java examples.",
        null,
        Map.of("triggerType", "user_request"));
  }

  @Override
  public List<ConversationMessage> recentMessages(long taskId, int messageLimit) {
    return jdbcOperations.query("""
        SELECT id, task_id, sequence_no, role, content, created_at
        FROM agent_message
        WHERE task_id = ?
          AND status = 'active'
        ORDER BY sequence_no DESC
        LIMIT ?
        """,
        (rs, rowNum) -> mapMessage(rs),
        taskId,
        taskId,
        messageLimit).stream()
        .sorted((left, right) -> Long.compare(left.sequenceNo(), right.sequenceNo()))
        .toList();
  }

  private ConversationDraft existingDraft(long runId) {
    return jdbcOperations.queryForObject("""
        SELECT r.id AS run_id, r.task_id, r.turn_id, r.run_uuid, r.idempotency_key, t.system_prompt
        FROM agent_run r
        JOIN agent_task t ON t.id = r.task_id
        WHERE r.id = ?
        """,
        (rs, rowNum) -> new ConversationDraft(
            rs.getLong("task_id"),
            rs.getLong("turn_id"),
            rs.getLong("run_id"),
            rs.getString("run_uuid"),
            rs.getString("idempotency_key"),
            rs.getString("system_prompt"),
            null,
            Map.of("idempotentReplay", true)),
        runId);
  }

  private long createTask(AgentConversationCommand command) {
    String title = command.userMessage().length() > 80
        ? command.userMessage().substring(0, 80)
        : command.userMessage();
    return jdbcOperations.queryForObject("""
        INSERT INTO agent_task (user_id, title, status, system_prompt, created_at, updated_at)
        VALUES (?, ?, 'active', ?, NOW(), NOW())
        RETURNING id
        """,
        Long.class,
        command.userId(),
        title,
        "You are an algorithm learning mentor. Explain clearly, ask guiding questions when useful, and prefer Java examples.");
  }

  private long insertTurn(long taskId, long sequenceNo) {
    return jdbcOperations.queryForObject("""
        INSERT INTO agent_turn (task_id, sequence_no, status, created_at, updated_at)
        VALUES (?, ?, 'running', NOW(), NOW())
        RETURNING id
        """, Long.class, taskId, sequenceNo);
  }

  private long insertUserMessage(long taskId, long turnId, long sequenceNo, String content) {
    return jdbcOperations.queryForObject("""
        INSERT INTO agent_message (task_id, turn_id, role, content, sequence_no, status, token_estimate, created_at, updated_at)
        VALUES (?, ?, 'user', ?, ?, 'active', ?, NOW(), NOW())
        RETURNING id
        """, Long.class, taskId, turnId, content, sequenceNo, estimateTokens(content));
  }

  private long insertRun(long taskId, long turnId, String idempotencyKey) {
    int attemptNo = jdbcOperations.queryForObject(
        "SELECT COALESCE(MAX(attempt_no), 0) + 1 FROM agent_run WHERE turn_id = ?",
        Integer.class,
        turnId);
    return jdbcOperations.queryForObject("""
        INSERT INTO agent_run (
          task_id, turn_id, run_uuid, attempt_no, idempotency_key, trigger_type, status, max_steps, started_at
        )
        VALUES (?, ?, ?, ?, ?, 'user_request', 'running', 4, NOW())
        RETURNING id
        """, Long.class, taskId, turnId, UUID.randomUUID().toString(), attemptNo, idempotencyKey);
  }

  private long nextSequence(String tableName, long taskId) {
    String sql = "SELECT COALESCE(MAX(sequence_no), 0) + 1 FROM " + tableName + " WHERE task_id = ?";
    return jdbcOperations.queryForObject(sql, Long.class, taskId);
  }

  private Long queryLong(String sql, Object... args) {
    List<Long> ids = jdbcOperations.query(sql, (rs, rowNum) -> rs.getLong(1), args);
    return ids.isEmpty() ? null : ids.get(0);
  }

  private String runUuid(long runId) {
    return jdbcOperations.queryForObject("SELECT run_uuid FROM agent_run WHERE id = ?", String.class, runId);
  }

  private ConversationMessage mapMessage(ResultSet rs) throws SQLException {
    return new ConversationMessage(
        rs.getLong("id"),
        rs.getLong("task_id"),
        rs.getLong("sequence_no"),
        ConversationMessage.Role.valueOf(rs.getString("role").toUpperCase()),
        rs.getString("content"),
        rs.getTimestamp("created_at").toInstant());
  }

  private int estimateTokens(String content) {
    return Math.max(1, content.length() / 4);
  }
}
