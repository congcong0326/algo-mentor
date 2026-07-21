package org.congcong.algomentor.api.support;

import java.io.Reader;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;

/**
 * 使用本机 PostgreSQL 的集成测试基座。
 *
 * <p>每个测试类独占随机 schema，迁移和清理都被限制在该 schema 内，避免 Testcontainers
 * 依赖以及对开发库 public schema 的破坏性操作。</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class PostgresIntegrationTestSupport {

  private DataSource dataSource;
  private String schemaName;

  @BeforeAll
  void createIntegrationSchema() throws SQLException {
    schemaName = "it_" + getClass().getSimpleName().replaceAll("[^A-Za-z0-9]", "").toLowerCase()
        + "_" + Long.toUnsignedString(System.nanoTime(), 36);
    try (Connection connection = adminDataSource().getConnection();
        PreparedStatement statement = connection.prepareStatement("CREATE SCHEMA \"" + schemaName + "\"")) {
      statement.execute();
    }
  }

  @BeforeEach
  void cleanDatabase() {
    flyway().clean();
  }

  @AfterAll
  void dropIntegrationSchema() throws SQLException {
    if (schemaName == null || !schemaName.startsWith("it_")) {
      return;
    }
    try (Connection connection = adminDataSource().getConnection();
        PreparedStatement statement = connection.prepareStatement("DROP SCHEMA IF EXISTS \"" + schemaName + "\" CASCADE")) {
      statement.execute();
    }
  }

  protected void migrateToV32() {
    migrateTo("32");
  }

  protected void migrateTo(String version) {
    Flyway.configure()
        .dataSource(dataSource())
        .locations("classpath:db/migration")
        .schemas(schemaName)
        .defaultSchema(schemaName)
        .createSchemas(true)
        .target(version)
        .load()
        .migrate();
  }

  protected void migrateLatest() {
    flyway().migrate();
  }

  protected Flyway flyway() {
    return Flyway.configure()
        .dataSource(dataSource())
        .locations("classpath:db/migration")
        .schemas(schemaName)
        .defaultSchema(schemaName)
        .createSchemas(true)
        .cleanDisabled(false)
        .load();
  }

  protected DataSource dataSource() {
    if (dataSource == null) {
      DataSource baseDataSource = new SimpleDriverDataSource(
          new org.postgresql.Driver(),
          databaseUrl(),
          databaseUser(),
          databasePassword());
      dataSource = new SchemaScopedDataSource(baseDataSource, schemaName);
    }
    return dataSource;
  }

  private DataSource adminDataSource() {
    return new SimpleDriverDataSource(
        new org.postgresql.Driver(), databaseUrl(), databaseUser(), databasePassword());
  }

  private String databaseUrl() {
    return System.getenv().getOrDefault(
        "ALGO_MENTOR_IT_DATABASE_URL",
        "jdbc:postgresql://" + environment("POSTGRES_HOST", "localhost") + ":"
            + environment("POSTGRES_PORT", "5432") + "/" + environment("POSTGRES_DB", "algo_mentor"));
  }

  private String databaseUser() {
    return environment("POSTGRES_USER", "algo_mentor");
  }

  private String databasePassword() {
    return environment("POSTGRES_PASSWORD", "algo_mentor_dev");
  }

  private String environment(String name, String defaultValue) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? defaultValue : value;
  }

  protected TransactionTemplate transactionTemplate() {
    return new TransactionTemplate(new DataSourceTransactionManager(dataSource()));
  }

  protected SqlSessionTemplate sqlSessionTemplate(String... mapperResources) throws Exception {
    Configuration configuration = new Configuration(new Environment(
        "postgres-it",
        new SpringManagedTransactionFactory(),
        dataSource()));
    configuration.setMapUnderscoreToCamelCase(true);
    for (String mapperResource : mapperResources) {
      try (Reader reader = Resources.getResourceAsReader(mapperResource)) {
        new XMLMapperBuilder(
            reader,
            configuration,
            mapperResource,
            configuration.getSqlFragments()).parse();
      }
    }
    SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    return new SqlSessionTemplate(sqlSessionFactory);
  }

  protected void insertProblem(
      String slug,
      Integer frontendId,
      List<String> values,
      List<String> labelsEn,
      List<String> labelsZh
  ) throws SQLException {
    String sql = """
        INSERT INTO problem (
          slug,
          frontend_id,
          frontend_display_id,
          title_en,
          title_zh,
          difficulty,
          tag_values,
          tag_labels_en,
          tag_labels_zh,
          content_markdown_en,
          content_markdown_zh,
          content_status,
          source_site
        )
        VALUES (?, ?, ?, ?, ?, 'MEDIUM', ?, ?, ?, ?, ?, 'BILINGUAL', 'LEETCODE_COM_CN')
        """;
    try (Connection connection = dataSource().getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      Array valueArray = connection.createArrayOf("text", values.toArray(String[]::new));
      Array labelEnArray = connection.createArrayOf("text", labelsEn.toArray(String[]::new));
      Array labelZhArray = connection.createArrayOf("text", labelsZh.toArray(String[]::new));
      try {
        statement.setString(1, slug);
        if (frontendId == null) {
          statement.setObject(2, null);
          statement.setObject(3, null);
        } else {
          statement.setInt(2, frontendId);
          statement.setString(3, frontendId.toString());
        }
        statement.setString(4, "Title " + slug);
        statement.setString(5, "题目 " + slug);
        statement.setArray(6, valueArray);
        statement.setArray(7, labelEnArray);
        statement.setArray(8, labelZhArray);
        statement.setString(9, "Problem statement " + slug);
        statement.setString(10, "题面 " + slug);
        statement.executeUpdate();
      } finally {
        valueArray.free();
        labelEnArray.free();
        labelZhArray.free();
      }
    }
  }

  protected long insertCatalog(String value, String labelEn, String labelZh, boolean active) throws SQLException {
    try (Connection connection = dataSource().getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO problem_tag (value, label_en, label_zh, active) VALUES (?, ?, ?, ?) RETURNING id")) {
      statement.setString(1, value);
      statement.setString(2, labelEn);
      statement.setString(3, labelZh);
      statement.setBoolean(4, active);
      try (ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        return resultSet.getLong(1);
      }
    }
  }

  protected void assignTag(String problemSlug, long tagId, int ordinal) throws SQLException {
    String sql = """
        INSERT INTO problem_tag_assignment (problem_id, tag_id, ordinal)
        SELECT p.id, ?, ?
        FROM problem p
        WHERE p.slug = ?
        """;
    try (Connection connection = dataSource().getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, tagId);
      statement.setInt(2, ordinal);
      statement.setString(3, problemSlug);
      statement.executeUpdate();
    }
  }

  protected long count(String tableName) throws SQLException {
    try (Connection connection = dataSource().getConnection();
        PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + tableName);
        ResultSet resultSet = statement.executeQuery()) {
      resultSet.next();
      return resultSet.getLong(1);
    }
  }

  protected long insertUser() throws SQLException {
    String email = "it-" + Long.toUnsignedString(System.nanoTime(), 36) + "@example.test";
    return queryLong(
        """
        INSERT INTO auth_users (email, email_normalized, display_name, status, created_at, updated_at)
        VALUES (?, ?, 'Integration Test User', 'ACTIVE', NOW(), NOW())
        RETURNING id
        """,
        email,
        email);
  }

  protected long insertPracticeSession(long userId, String problemSlug) throws SQLException {
    return queryLong(
        """
        INSERT INTO practice_session (user_id, plan_id, phase_index, problem_slug, status, locale)
        VALUES (?, 1, 1, ?, 'ACTIVE', 'zh-CN')
        RETURNING id
        """,
        userId,
        problemSlug);
  }

  protected long insertUserMessage(long userId) throws SQLException {
    long taskId = queryLong(
        """
        INSERT INTO agent_task (user_id, status, context_policy, metadata, created_at, updated_at)
        VALUES (?, 'ACTIVE', '{}'::jsonb, '{}'::jsonb, NOW(), NOW())
        RETURNING id
        """,
        userId);
    long turnId = queryLong(
        """
        INSERT INTO agent_turn (task_id, sequence_no, status, created_at, updated_at)
        VALUES (?, 1, 'COMPLETED', NOW(), NOW())
        RETURNING id
        """,
        taskId);
    return queryLong(
        """
        INSERT INTO agent_message (task_id, turn_id, role, content, sequence_no, status, metadata, created_at, updated_at)
        VALUES (?, ?, 'user', 'class Solution {}', 1, 'COMPLETED', '{}'::jsonb, NOW(), NOW())
        RETURNING id
        """,
        taskId,
        turnId);
  }

  protected void execute(String sql, Object... parameters) throws SQLException {
    try (Connection connection = dataSource().getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      bind(statement, parameters);
      statement.executeUpdate();
    }
  }

  protected String queryString(String sql, Object... parameters) throws SQLException {
    try (Connection connection = dataSource().getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      bind(statement, parameters);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next() ? resultSet.getString(1) : null;
      }
    }
  }

  protected long queryLong(String sql, Object... parameters) throws SQLException {
    try (Connection connection = dataSource().getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      bind(statement, parameters);
      try (ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        return resultSet.getLong(1);
      }
    }
  }

  protected static <T> ObjectProvider<T> objectProvider(T value) {
    return new StaticObjectProvider<>(value);
  }

  private void bind(PreparedStatement statement, Object[] parameters) throws SQLException {
    for (int index = 0; index < parameters.length; index++) {
      statement.setObject(index + 1, parameters[index]);
    }
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

  private static final class SchemaScopedDataSource extends DelegatingDataSource {
    private final String schemaName;

    private SchemaScopedDataSource(DataSource targetDataSource, String schemaName) {
      super(targetDataSource);
      this.schemaName = schemaName;
    }

    @Override
    public Connection getConnection() throws SQLException {
      return configure(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
      return configure(super.getConnection(username, password));
    }

    private Connection configure(Connection connection) throws SQLException {
      try (java.sql.Statement statement = connection.createStatement()) {
        statement.execute("SET search_path TO \"" + schemaName + "\"");
      }
      return connection;
    }
  }
}
