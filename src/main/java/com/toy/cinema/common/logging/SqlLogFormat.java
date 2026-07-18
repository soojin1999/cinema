package com.toy.cinema.common.logging;

import com.p6spy.engine.spy.appender.MessageFormattingStrategy;

/**
 * p6spy 콘솔 로그 포맷터. p6spy 내장 CustomLineFormat의 %(effectiveSql) 토큰(멀티라인 버전)이
 * 파라미터 치환을 못 하고 원본(?)을 그대로 반환하는 버그가 있어(3.9.1 기준, %(effectiveSqlSingleLine)은
 * 정상 동작 확인함), formatMessage()가 직접 받는 sql 파라미터(치환된 SQL, MyBatis 매퍼 XML의 줄바꿈이
 * 그대로 유지됨)를 써서 우회한다.
 */
public class SqlLogFormat implements MessageFormattingStrategy {

    @Override
    public String formatMessage(int connectionId, String now, long elapsed, String category,
                                 String prepared, String sql, String url) {
        return elapsed + "ms | " + category + "\n" + sql;
    }
}
