package com.toy.cinema.config;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * payment_db(payment) 전용 DataSource + SqlSessionFactory.
 * T-09(2026-07-21) 도메인별 스키마 분리 — payment 패키지의 매퍼는 이 SqlSessionFactory로만 SQL을 실행한다.
 */
@Configuration
@MapperScan(basePackages = "com.toy.cinema.payment", annotationClass = Mapper.class,
        sqlSessionFactoryRef = "paymentSqlSessionFactory")
public class PaymentDataSourceConfig {

    @Bean
    @ConfigurationProperties("app.datasource.payment")
    public DataSource paymentDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    public SqlSessionFactory paymentSqlSessionFactory(DataSource paymentDataSource) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(paymentDataSource);
        factoryBean.setMapperLocations(
                new PathMatchingResourcePatternResolver().getResources("classpath:mapper/payment/*.xml"));

        org.apache.ibatis.session.Configuration mybatisConfig = new org.apache.ibatis.session.Configuration();
        mybatisConfig.setMapUnderscoreToCamelCase(true);
        mybatisConfig.setLogImpl(NoLoggingImpl.class);
        factoryBean.setConfiguration(mybatisConfig);

        return factoryBean.getObject();
    }

    /** DataSource가 여러 개면 Spring Boot가 자동으로 안 만들어줘서 도메인마다 명시적으로 등록 (상세 → SeatDataSourceConfig 주석, T-10 4단계에서 발견된 버그). */
    @Bean
    public PlatformTransactionManager paymentTransactionManager(DataSource paymentDataSource) {
        return new DataSourceTransactionManager(paymentDataSource);
    }
}
