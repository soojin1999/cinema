# charset.cnf를 bind mount하면 Windows + Docker Desktop 환경에서 파일이 world-writable로 보여
# MySQL이 보안상 이유로 무시해버린다 ("World-writable config file ... is ignored").
# 이미지 빌드 시 COPY로 넣으면 리눅스 표준 권한(644)으로 생성되어 이 문제를 피할 수 있다.
FROM mysql:8.0
COPY charset.cnf /etc/mysql/conf.d/charset.cnf
