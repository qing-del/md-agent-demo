package com.jacolp;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

/**
 * 直接读取 src/main/resources/application-dev.yaml，发送 Hello 并查看 API 原始响应。
 * 支持解析配置中的环境变量占位符，不启动应用或连接数据库。
 * API Key 优先使用 DEEPSEEK_API_KEY，未设置时使用开发配置中占位符冒号后的本地默认值。
 * IDEA 应用启动配置中的环境变量不会自动传递给 JUnit 测试进程。
 *
 * <p>在项目根目录通过 PowerShell 手动运行：
 * <pre>
 * mvn "-Dtest=DeepSeekApiSmokeTest" "-DrunApiSmokeTest=true" test
 * </pre>
 * 普通 mvn test 默认跳过；手动启用后会发送一次真实请求。
 */
@EnabledIfSystemProperty(named = "runApiSmokeTest", matches = "true")
class DeepSeekApiSmokeTest {

    @Test
    void helloPrintsRawApiResponse() throws Exception {
        FileSystemResource resource = new FileSystemResource("src/main/resources/application-dev.yaml");
        assertTrue(resource.exists(), "请在项目根目录运行，并确认 application-dev.yaml 存在");

        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("dev-api-config", resource)
                .forEach(environment.getPropertySources()::addLast);

        String prefix = "spring.ai.openai.";
        String baseUrl = requiredConfig(environment, prefix + "base-url");
        String apiKey = requiredConfig(environment, prefix + "api-key");
        String completionsPath = requiredConfig(environment, prefix + "chat.completions-path");
        String model = requiredConfig(environment, prefix + "chat.options.model");
        String endpoint = baseUrl.replaceAll("/+$", "") + "/" + completionsPath.replaceAll("^/+", "");

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        ObjectMapper objectMapper = new ObjectMapper();
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", "你好啊")),
                "stream", false));

        RestClient.builder().requestFactory(requestFactory).build()
                .post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(apiKey))
                .body(requestBody)
                .exchange((request, response) -> {
                    String responseBody = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
                    System.out.println("HTTP status: " + response.getStatusCode());
                    System.out.println("Content-Type: " + response.getHeaders().getContentType());
                    System.out.println("Response body:");

                    JsonNode json = null;
                    try {
                        json = objectMapper.readTree(responseBody);
                    } catch (JsonProcessingException ignored) {
                        // 保留非 JSON 错误响应，方便排查接口问题。
                    }
                    System.out.println(json == null ? responseBody
                            : objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json));

                    assertTrue(response.getStatusCode().is2xxSuccessful(), "API 返回非 2xx 状态码，请查看上方响应");
                    assertNotNull(json, "成功响应应为 JSON");
                    JsonNode choices = json.path("choices");
                    assertTrue(choices.isArray() && !choices.isEmpty(), "响应应包含非空 choices 数组");
                    assertTrue(choices.get(0).path("message").isObject(), "choices[0] 应包含 message 对象");
                    return null;
                });
    }

    private static String requiredConfig(StandardEnvironment environment, String name) {
        String hint = name.equals("spring.ai.openai.api-key")
                ? "请在 application-dev.yaml 的 api-key 中填写 ${DEEPSEEK_API_KEY:你的实际Key}，"
                        + "或在当前 JUnit 运行配置中设置 DEEPSEEK_API_KEY"
                : "请检查 application-dev.yaml 及对应环境变量";
        String value;
        try {
            value = environment.getProperty(name);
        } catch (IllegalArgumentException exception) {
            return fail("配置缺失或含未解析的环境变量：" + name + "，" + hint);
        }
        assertTrue(value != null && !value.isBlank(), "配置不能为空：" + name + "，" + hint);
        return value.trim();
    }
}
