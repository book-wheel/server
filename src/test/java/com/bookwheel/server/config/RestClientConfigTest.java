package com.bookwheel.server.config;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.UnknownContentTypeException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

// 알라딘 미디어 타입 설정이 실제 빈 배선까지 반영되는지 검증한다.
// 테스트에서 RestClient.Builder를 직접 만들면 Spring Boot의 자동 구성을 타지 않아
// 설정을 복제하게 되고, RestClientConfig가 사라져도 통과하는 테스트가 된다.
class RestClientConfigTest {

    private static final String RESPONSE_BODY = "{\"title\":\"Clean Code\"}";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            HttpMessageConvertersAutoConfiguration.class,
            RestClientAutoConfiguration.class))
        .withUserConfiguration(RestClientConfig.class);

    private HttpServer server;
    private String url;

    // 알라딘처럼 JSON 본문을 text/javascript로 내려주는 서버를 흉내 낸다.
    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/book", exchange -> {
            byte[] body = RESPONSE_BODY.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/javascript;charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(body);
            }
        });
        server.start();
        url = "http://localhost:" + server.getAddress().getPort() + "/book";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void aladinRestClientReadsTextJavascriptResponse() {
        contextRunner.run(context -> {
            RestClient aladinRestClient = context.getBean("aladinRestClient", RestClient.class);

            Map<?, ?> body = aladinRestClient.get()
                .uri(url)
                .retrieve()
                .body(Map.class);

            assertThat(body.get("title")).isEqualTo("Clean Code");
        });
    }

    // 알라딘용 설정이 다른 클라이언트로 새지 않아야 한다.
    @Test
    void otherRestClientsDoNotReadTextJavascriptResponse() {
        contextRunner.run(context -> {
            for (String beanName : List.of("restClient", "naruRestClient", "naruPopularLoanRestClient")) {
                RestClient restClient = context.getBean(beanName, RestClient.class);

                assertThatExceptionOfType(UnknownContentTypeException.class)
                    .as("%s", beanName)
                    .isThrownBy(() -> restClient.get().uri(url).retrieve().body(Map.class))
                    .withMessageContaining("text/javascript");
            }
        });
    }

    // MVC 응답 컨버터도 같은 인스턴스를 공유하므로, 함께 바뀌지 않았는지 확인한다.
    @Test
    void sharedMessageConvertersAreNotModified() {
        contextRunner.run(context -> {
            HttpMessageConverters converters = context.getBean(HttpMessageConverters.class);

            List<MediaType> mediaTypes = converters.getConverters().stream()
                .filter(MappingJackson2HttpMessageConverter.class::isInstance)
                .map(HttpMessageConverter::getSupportedMediaTypes)
                .flatMap(List::stream)
                .toList();

            assertThat(mediaTypes).doesNotContain(
                MediaType.valueOf("text/javascript"),
                MediaType.valueOf("application/javascript")
            );
        });
    }
}
