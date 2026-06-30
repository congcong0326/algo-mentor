package org.congcong.algomentor.auth.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

class SpringSessionAuthSessionRevocationServiceTest {

  @Test
  void revokesAllSessionsForUserPrincipalName() {
    FakeSessionRepository repository = new FakeSessionRepository();
    repository.sessionsByPrincipalName.put("42", Map.of(
        "session-1", new FakeSession("session-1"),
        "session-2", new FakeSession("session-2")));
    repository.sessionsByPrincipalName.put("43", Map.of(
        "session-3", new FakeSession("session-3")));
    SpringSessionAuthSessionRevocationService service = new SpringSessionAuthSessionRevocationService(repository);

    service.revokeSessionsForUser(42L);

    assertThat(repository.lastPrincipalName).isEqualTo("42");
    assertThat(repository.deletedSessionIds).containsExactlyInAnyOrder("session-1", "session-2");
  }

  private static final class FakeSessionRepository implements FindByIndexNameSessionRepository<FakeSession> {

    private final Map<String, Map<String, FakeSession>> sessionsByPrincipalName = new HashMap<>();
    private final List<String> deletedSessionIds = new ArrayList<>();
    private String lastPrincipalName;

    @Override
    public Map<String, FakeSession> findByPrincipalName(String principalName) {
      lastPrincipalName = principalName;
      return sessionsByPrincipalName.getOrDefault(principalName, Map.of());
    }

    @Override
    public Map<String, FakeSession> findByIndexNameAndIndexValue(String indexName, String indexValue) {
      if (PRINCIPAL_NAME_INDEX_NAME.equals(indexName)) {
        return findByPrincipalName(indexValue);
      }
      return Map.of();
    }

    @Override
    public FakeSession createSession() {
      return new FakeSession("new-session");
    }

    @Override
    public void save(FakeSession session) {
    }

    @Override
    public FakeSession findById(String id) {
      return sessionsByPrincipalName.values().stream()
          .map(sessions -> sessions.get(id))
          .filter(session -> session != null)
          .findFirst()
          .orElse(null);
    }

    @Override
    public void deleteById(String id) {
      deletedSessionIds.add(id);
    }
  }

  private static final class FakeSession implements Session {

    private String id;
    private final Instant creationTime = Instant.parse("2026-06-30T00:00:00Z");
    private Instant lastAccessedTime = creationTime;
    private Duration maxInactiveInterval = Duration.ofMinutes(30);
    private final Map<String, Object> attributes = new HashMap<>();

    private FakeSession(String id) {
      this.id = id;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public String changeSessionId() {
      id = id + "-changed";
      return id;
    }

    @Override
    public <T> T getAttribute(String attributeName) {
      return (T) attributes.get(attributeName);
    }

    @Override
    public Set<String> getAttributeNames() {
      return new HashSet<>(attributes.keySet());
    }

    @Override
    public void setAttribute(String attributeName, Object attributeValue) {
      attributes.put(attributeName, attributeValue);
    }

    @Override
    public void removeAttribute(String attributeName) {
      attributes.remove(attributeName);
    }

    @Override
    public Instant getCreationTime() {
      return creationTime;
    }

    @Override
    public void setLastAccessedTime(Instant lastAccessedTime) {
      this.lastAccessedTime = lastAccessedTime;
    }

    @Override
    public Instant getLastAccessedTime() {
      return lastAccessedTime;
    }

    @Override
    public void setMaxInactiveInterval(Duration interval) {
      this.maxInactiveInterval = interval;
    }

    @Override
    public Duration getMaxInactiveInterval() {
      return maxInactiveInterval;
    }

    @Override
    public boolean isExpired() {
      return false;
    }
  }
}
