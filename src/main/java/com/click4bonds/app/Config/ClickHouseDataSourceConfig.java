//package com.click4bonds.app.Config;
//
//import com.zaxxer.hikari.HikariDataSource;
//import org.springframework.boot.context.properties.ConfigurationProperties;
//import org.springframework.boot.jdbc.DataSourceBuilder;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class ClickHouseDataSourceConfig {
//
//    @Bean(name = "clickHouseDataSource")
//    @ConfigurationProperties("clickhouse.datasource")
//    public HikariDataSource clickHouseDataSource() {
//        return DataSourceBuilder
//                .create()
//                .type(HikariDataSource.class)
//                .build();
//    }
//}