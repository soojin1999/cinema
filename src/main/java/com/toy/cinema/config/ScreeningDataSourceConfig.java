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
 * screening_db(movie/theater/schedule) 전용 DataSource + SqlSessionFactory.
 * T-09(2026-07-21) 도메인별 스키마 분리 — screening 패키지의 매퍼는 이 SqlSessionFactory로만 SQL을 실행한다.
 */
@Configuration
@MapperScan(basePackages = "com.toy.cinema.screening", annotationClass = Mapper.class,
        sqlSessionFactoryRef = "screeningSqlSessionFactory")
public class ScreeningDataSourceConfig {

    @Bean
    @ConfigurationProperties("app.datasource.screening")
    public DataSource screeningDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    public SqlSessionFactory screeningSqlSessionFactory(DataSource screeningDataSource) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(screeningDataSource);
        factoryBean.setMapperLocations(
                new PathMatchingResourcePatternResolver().getResources("classpath:mapper/screening/*.xml"));

        org.apache.ibatis.session.Configuration mybatisConfig = new org.apache.ibatis.session.Configuration();
        mybatisConfig.setMapUnderscoreToCamelCase(true);
        mybatisConfig.setLogImpl(NoLoggingImpl.class);
        factoryBean.setConfiguration(mybatisConfig);

        return factoryBean.getObject();
    }

    /**
     * screening은 지금 @Transactional을 쓰는 코드가 없지만(조회 전용), 나중에 필요해질 수 있어
     * 다른 3개 도메인과 동일하게 미리 등록해둔다 (상세 → SeatDataSourceConfig 주석, T-10 4단계에서 발견된 버그).
     */
    @Bean
    public PlatformTransactionManager screeningTransactionManager(DataSource screeningDataSource) {
        return new DataSourceTransactionManager(screeningDataSource);
    }
}
