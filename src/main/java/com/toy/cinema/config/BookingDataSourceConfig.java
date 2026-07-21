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

import javax.sql.DataSource;

/**
 * booking_db(booking) 전용 DataSource + SqlSessionFactory.
 * T-09(2026-07-21) 도메인별 스키마 분리 — booking 패키지의 매퍼는 이 SqlSessionFactory로만 SQL을 실행한다.
 */
@Configuration
@MapperScan(basePackages = "com.toy.cinema.booking", annotationClass = Mapper.class,
        sqlSessionFactoryRef = "bookingSqlSessionFactory")
public class BookingDataSourceConfig {

    @Bean
    @ConfigurationProperties("app.datasource.booking")  //application.yaml의 경로
    public DataSource bookingDataSource() {
        return DataSourceBuilder.create().build();
    }   //db정보, id, pw를 자동으로 매핑해서 디비에 연결

    //Mybatis와 spring을 이어주는 SqlSessionFactory를 만드는 조립기
    @Bean
    public SqlSessionFactory bookingSqlSessionFactory(DataSource bookingDataSource) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(bookingDataSource);
        factoryBean.setMapperLocations(
                new PathMatchingResourcePatternResolver().getResources("classpath:mapper/booking/*.xml"));

        org.apache.ibatis.session.Configuration mybatisConfig = new org.apache.ibatis.session.Configuration();
        mybatisConfig.setMapUnderscoreToCamelCase(true);
        mybatisConfig.setLogImpl(NoLoggingImpl.class);
        factoryBean.setConfiguration(mybatisConfig);

        return factoryBean.getObject();
    }
}
