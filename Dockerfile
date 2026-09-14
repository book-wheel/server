FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon || true

COPY src src
RUN ./gradlew clean bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app

# JVM 기본 시간대를 KST로 고정한다. 미지정 시 컨테이너 기본값(UTC)을 따라
# LocalDateTime.now() 직접 호출부와 로그 타임스탬프가 9시간 이르게 찍힌다.
ENV TZ=Asia/Seoul

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
