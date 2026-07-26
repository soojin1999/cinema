# ---- 1단계: 빌드 ----
# 로컬(./gradlew)과 똑같은 결과를 보장하기 위해 프로젝트에 커밋된 Gradle Wrapper(9.5.1)를 그대로 쓴다.
# gradle 이미지가 아니라 jdk 이미지를 쓰는 이유: wrapper가 알아서 정확한 Gradle 버전을 받아오므로
# 이미지에 미리 깔린 Gradle 버전과 wrapper 버전이 어긋날 걱정이 없다.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
# Windows 호스트에서 COPY하면 실행권한이 유실될 수 있어 명시적으로 부여
RUN chmod +x gradlew
# bootJar만 실행 — plain jar(-plain.jar) 태스크는 건드리지 않아 build/libs/에 산출물이 하나만 남는다
RUN ./gradlew bootJar --no-daemon -x test

# ---- 2단계: 런타임 ----
# 컴파일 도구가 필요 없으므로 JDK 대신 JRE만 있는 가벼운 이미지 사용
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
