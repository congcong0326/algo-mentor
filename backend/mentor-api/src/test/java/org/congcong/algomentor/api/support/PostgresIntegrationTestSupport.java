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
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.junit.jupiter.api.BeforeEach;

@Testcontainers
public abstract class PostgresIntegrationTestSupport {

  @Container
  protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
      DockerImageName.parse("postgres:16-alpine"));

  private DataSource dataSource;

  @BeforeEach
  void cleanDatabase() {
    flyway().clean();
  }

  protected void migrateToV32() {
    Flyway.configure()
        .dataSource(dataSource())
        .locations("classpath:db/migration")
        .target("32")
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
        .cleanDisabled(false)
        .load();
  }

  protected DataSource dataSource() {
    if (dataSource == null) {
      dataSource = new SimpleDriverDataSource(
          new org.postgresql.Driver(),
          POSTGRES.getJdbcUrl(),
          POSTGRES.getUsername(),
          POSTGRES.getPassword());
    }
    return dataSource;
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
}
