package com.toy.cinema;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * T-09(2026-07-21) 도메인별 스키마 분리 이전엔 여기 전역 @MapperScan(annotationClass = Mapper.class)
 * 하나로 com.toy.cinema 아래 @Mapper 붙은 인터페이스를 전부 스캔했다. 지금은 도메인마다 DB(스키마)가
 * 갈라져서 "어떤 SqlSessionFactory(=어떤 DB 연결)를 쓸지"까지 지정해야 하므로, 그 지정이 불가능한
 * 전역 스캔은 제거하고 config 패키지의 도메인별 DataSourceConfig(ScreeningDataSourceConfig 등)가
 * 각자 @MapperScan(sqlSessionFactoryRef=...)으로 자기 도메인만 스캔한다.
 *
 * @EnableScheduling: BookingTimeoutBatch(T-04)의 @Scheduled는 지금 주석 처리라 당장은 아무 효과 없지만,
 * 나중에 주석을 풀 때 이 어노테이션 추가를 까먹지 않도록 미리 켜둔다.
 */
@SpringBootApplication
@EnableScheduling
public class CinemaApplication {

	public static void main(String[] args) {
		SpringApplication.run(CinemaApplication.class, args);
	}

}
