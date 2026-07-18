package com.toy.cinema;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @MapperScan(annotationClass = Mapper.class)로 com.toy.cinema 아래에서 @Mapper가 붙은 인터페이스만
 * 매퍼로 등록한다. basePackages만 주고 annotationClass를 안 주면 SeatFacade/PaymentFacade 같은
 * 일반 인터페이스까지 전부 "매퍼"로 착각해서 가짜 프록시를 만들어버려, 실제 Facade 구현체(Impl) 대신
 * 그 가짜 프록시가 주입되는 사고가 난다 (실제로 겪은 버그 — BookingFacade.reserve() 호출 시
 * "Invalid bound statement" 에러). 그래서 각 SeatMapper/PaymentMapper/BookingMapper에 @Mapper를
 * 명시적으로 붙이고, 스캔 대상도 그것만으로 제한한다.
 */
@MapperScan(basePackages = "com.toy.cinema", annotationClass = Mapper.class)
@SpringBootApplication
public class CinemaApplication {

	public static void main(String[] args) {
		SpringApplication.run(CinemaApplication.class, args);
	}

}
