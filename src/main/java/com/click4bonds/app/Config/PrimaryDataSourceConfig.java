package com.click4bonds.app.Config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.zaxxer.hikari.HikariDataSource;

/**
 * The application's primary (PostgreSQL) datasource, declared explicitly.
 *
 * <p>Spring Boot only creates its auto-configured datasource when no
 * {@code DataSource} bean exists yet — {@code DataSourceAutoConfiguration}'s
 * pooled configuration carries
 * {@code @ConditionalOnMissingBean({DataSource.class, XADataSource.class})}.
 * The analytics module contributes a second {@code DataSource} for ClickHouse,
 * so without this class the auto-configured one would back off, Hibernate would
 * be handed the ClickHouse datasource as its only candidate, and startup would
 * fail while trying to determine a dialect against ClickHouse.</p>
 *
 * <p>Declaring the primary datasource here and marking it {@link Primary} keeps
 * that from happening: JPA and every other unqualified {@code DataSource}
 * consumer keeps resolving to PostgreSQL, and the analytics module's datasource
 * is reachable only through its own {@code @Qualifier}.</p>
 *
 * <p>All values still come from the existing {@code spring.datasource.*} and
 * {@code spring.datasource.hikari.*} properties, including the Hikari pool
 * tuning, so the connection behaves exactly as it did when Boot built it.</p>
 */
@Configuration(proxyBeanMethods = false)
public class PrimaryDataSourceConfig {

    /**
     * @param properties the {@code spring.datasource.*} properties Boot still
     *                   binds, used to resolve url/user/password/driver
     * @return the primary datasource, preferred by type for every injection
     *         point that does not name a qualifier
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(DataSourceProperties properties) {

        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
}
