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
 * seat_db(seat/schedule_seat) 전용 DataSource + SqlSessionFactory.
 * T-09(2026-07-21) 도메인별 스키마 분리 — seat 패키지의 매퍼는 이 SqlSessionFactory로만 SQL을 실행한다.
 */
@Configuration
@MapperScan(basePackages = "com.toy.cinema.seat", annotationClass = Mapper.class,
        sqlSessionFactoryRef = "seatSqlSessionFactory")
public class SeatDataSourceConfig {

    @Bean
    @ConfigurationProperties("app.datasource.seat")
    public DataSource seatDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    public SqlSessionFactory seatSqlSessionFactory(DataSource seatDataSource) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(seatDataSource);
        factoryBean.setMapperLocations(
                new PathMatchingResourcePatternResolver().getResources("classpath:mapper/seat/*.xml"));

        org.apache.ibatis.session.Configuration mybatisConfig = new org.apache.ibatis.session.Configuration();
        mybatisConfig.setMapUnderscoreToCamelCase(true);
        mybatisConfig.setLogImpl(NoLoggingImpl.class);
        factoryBean.setConfiguration(mybatisConfig);

        return factoryBean.getObject();
    }

    /**
     * DataSource가 4개로 늘면서 Spring Boot가 PlatformTransactionManager를 자동으로 못 만들어준다
     * (DataSource가 정확히 1개일 때만 자동 생성됨) — 그 결과 @EnableTransactionManagement 자체가
     * 비활성화돼서 SeatService의 @Transactional이 전부 조용히 무시되고 있었다 (T-10 4단계 동시성
     * 테스트에서 실제로 발견된 버그, 2026-07-21). 도메인마다 명시적으로 만들어줘야 한다.
     */
    @Bean
    public PlatformTransactionManager seatTransactionManager(DataSource seatDataSource) {
        return new DataSourceTransactionManager(seatDataSource);
    }
}
