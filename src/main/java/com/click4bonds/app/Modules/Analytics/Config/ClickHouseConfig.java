package com.click4bonds.app.Modules.Analytics.Config;

import java.sql.SQLException;
import java.util.Properties;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.clickhouse.jdbc.ClickHouseDataSource;

/**
 * The analytics module's ClickHouse datasource.
 *
 * <p>The application already has a PostgreSQL datasource, and Hibernate, JPA
 * repositories and transaction management all resolve a {@code DataSource}
 * without naming one. This bean therefore stays strictly secondary: it is named
 * {@value #CLICKHOUSE_DATASOURCE} and is deliberately not {@code @Primary}, so
 * the only way to reach it is an explicit qualifier.</p>
 *
 * <p>Note that this bean, on its own, is not enough to keep the two apart.
 * Spring Boot skips its own auto-configured datasource as soon as <em>any</em>
 * {@code DataSource} bean is present, so the primary PostgreSQL datasource is
 * declared explicitly in
 * {@link com.click4bonds.app.Config.PrimaryDataSourceConfig}. Do not delete
 * that class while this one exists.</p>
 *
 * <p>The URL comes from {@code clickhouse.url} and is expected to point at the
 * HTTP endpoint (port 8123) of a ClickHouse server.</p>
 */
@Configuration(proxyBeanMethods = false)
public class ClickHouseConfig {

    /** Bean name of the ClickHouse datasource. Inject with this qualifier. */
    public static final String CLICKHOUSE_DATASOURCE = "clickHouseDataSource";

    /**
     * @param url      JDBC URL of the analytics ClickHouse database
     * @param username ClickHouse user
     * @param password ClickHouse password, may be empty
     * @return a lazily-connecting datasource; constructing it opens no socket,
     *         so a ClickHouse that is down does not stop the application booting
     * @throws SQLException if the JDBC URL is not a valid ClickHouse URL
     */
    @Bean(name = CLICKHOUSE_DATASOURCE)
    public DataSource clickHouseDataSource(
            @Value("${clickhouse.url}") String url,
            @Value("${clickhouse.username}") String username,
            @Value("${clickhouse.password:}") String password) throws SQLException {

        Properties properties = new Properties();

        properties.setProperty("user", username);
        properties.setProperty("password", password);

        return new ClickHouseDataSource(url, properties);
    }
}
