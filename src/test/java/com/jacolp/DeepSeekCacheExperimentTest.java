package com.jacolp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import com.jacolp.CacheExperimentCases.Definition;
import com.jacolp.CacheExperimentCases.Sequence;
import com.jacolp.CacheExperimentReport.Preview;
import com.jacolp.CacheExperimentReport.Run;
import com.jacolp.CacheExperimentReport.Sample;
import com.jacolp.CacheExperimentReport.Stats;

/**
 * 只读取 src/main/resources/application-dev.yaml，不启动 Spring、不连接数据库。
 * API Key 优先取 DEEPSEEK_API_KEY，未设置时使用 YAML 占位符中的默认值。
 *
 * <p>项目根目录 PowerShell：
 * <pre>
 * mvn "-Dtest=DeepSeekCacheExperimentTest" "-DrunCacheExperiment=true" test
 * mvn "-Dtest=DeepSeekCacheExperimentTest" "-DrunCacheExperiment=true" "-DcacheExperiment.scenarios=dynamic-system-head,dynamic-system-tail,dynamic-user" "-DcacheExperiment.modes=disabled" test
 * </pre>
 * IDEA Debug：在本 JUnit 运行配置的 VM options 中加入 -DrunCacheExperiment=true，其他筛选参数同上。
 * 应用运行配置中的环境变量不会自动传给 JUnit 进程。
 *
 * <p>默认24场景×2模式×5次=240次真实请求；间隔3000ms，无重试。
 * cacheExperiment.repeats 范围3–10；cacheExperiment.pauseMillis 范围0–60000。
 * cacheExperiment.modes 支持 disabled、enabled 或二者；cacheExperiment.scenarios 为逗号分隔场景ID。
 * max_tokens=1024；开启思考时 reasoning_effort=high。各场景的 repeats 都表示 API 请求次数，非完整会话次数。
 * 输出 log/cache-experiment/&lt;运行ID&gt;/{cases.md,report.md,samples.csv,details.json}，不中途覆盖旧运行。
 * 普通执行只运行离线校验，跳过真实 API 实验；不会启用 DeepSeekApiSmokeTest。
 */
class DeepSeekCacheExperimentTest {

    @Test
    @EnabledIfSystemProperty(named = "runCacheExperiment", matches = "true")
    void comparePromptCacheStrategies() throws Exception {
        Options options = Options.from(System.getProperties());
        StandardEnvironment environment = loadDevelopmentConfig();
        String prefix = "spring.ai.openai.";
        String baseUrl = requiredConfig(environment, prefix + "base-url");
        String apiKey = requiredConfig(environment, prefix + "api-key");
        String path = requiredConfig(environment, prefix + "chat.completions-path");
        String model = requiredConfig(environment, prefix + "chat.options.model");
        String endpoint = baseUrl.replaceAll("/+$", "") + "/" + path.replaceAll("^/+", "");

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(60));
        RestClient client = RestClient.builder().requestFactory(factory).build();
        String runId = Instant.now().toString().replace(':', '-') + "-" + UUID.randomUUID().toString().substring(0, 8);
        Path directory = Path.of("log", "cache-experiment", runId);
        List<Trial> trials = new ArrayList<>();
        List<Preview> previews = new ArrayList<>();
        for (String mode : options.modes()) {
            for (Definition definition : options.scenarios()) {
                Sequence sequence = new Sequence(definition, mode);
                previews.add(new Preview(mode, definition, sequence.request(1, model)));
                trials.add(new Trial(mode, definition, sequence));
            }
        }
        Run run = new Run(runId, model, options.modes(), options.scenarios(), options.repeats(), options.pauseMillis(), previews);
        List<Sample> samples = new ArrayList<>();
        List<Map<String, Object>> details = new ArrayList<>();
        boolean complete = false;
        CacheExperimentReport.write(directory, run, samples, details, false);
        System.out.printf("Cache experiment: %d requests; output: %s%n", run.plannedRequests(), directory.toAbsolutePath());
        try {
            for (Trial trial : trials) {
                for (int round = 1; round <= options.repeats(); round++) {
                    if (!samples.isEmpty()) Thread.sleep(options.pauseMillis());
                    ObjectNode request = trial.sequence().request(round, model);
                    String body = CacheExperimentCases.JSON.writeValueAsString(request);
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("scenario", trial.definition().id());
                    detail.put("mode", trial.mode());
                    detail.put("round", round);
                    detail.put("phase", trial.sequence().phase(round));
                    detail.put("request", request);
                    detail.put("requestSha256", sha256(body));
                    long start = System.nanoTime();
                    int status = 0;
                    Sample parsed = null;
                    Sample sample;
                    try {
                        Reply reply = client.post().uri(endpoint)
                                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                                .headers(headers -> headers.setBearerAuth(apiKey)).body(body)
                                .exchange((httpRequest, httpResponse) -> new Reply(httpResponse.getStatusCode().value(),
                                        redact(StreamUtils.copyToString(httpResponse.getBody(), StandardCharsets.UTF_8), apiKey),
                                        httpResponse.getHeaders().getFirst("Content-Type")));
                        status = reply.status();
                        long elapsed = elapsedMillis(start);
                        detail.put("status", status);
                        detail.put("contentType", reply.contentType());
                        detail.put("responseBody", reply.body());
                        if (status < 200 || status >= 300) {
                            System.out.println("HTTP " + status + " response body: " + reply.body());
                            throw new IllegalStateException("HTTP " + status + "，响应体已保存到 details.json");
                        }
                        JsonNode response = CacheExperimentCases.JSON.readTree(reply.body());
                        if (response == null) throw new IllegalArgumentException("响应体为空，无法读取 usage");
                        parsed = CacheExperimentReport.usage(trial.definition().id(), trial.mode(), round,
                                trial.sequence().phase(round), status, elapsed, response);
                        trial.sequence().accept(round, response);
                        sample = parsed;
                    } catch (Exception exception) {
                        String error = redact(exception.getClass().getSimpleName() + ": " + exception.getMessage(), apiKey)
                                .replace('\n', ' ').replace('\r', ' ');
                        sample = new Sample(trial.definition().id(), trial.mode(), round, trial.sequence().phase(round), status,
                                parsed == null ? -1 : parsed.prompt(), parsed == null ? -1 : parsed.hit(),
                                parsed == null ? -1 : parsed.miss(), elapsedMillis(start),
                                parsed == null ? "unknown" : parsed.responseModel(),
                                parsed == null ? "unknown" : parsed.finishReason(), error);
                    }
                    samples.add(sample);
                    detail.put("sample", sample);
                    details.add(detail);
                    CacheExperimentReport.write(directory, run, samples, details, false);
                    System.out.printf(Locale.ROOT,
                            "[%d/%d] %s/%s #%d phase=%s HTTP=%d input=%d hit=%d miss=%d ratio=%s elapsed=%dms%s%n",
                            samples.size(), run.plannedRequests(), sample.mode(), sample.scenario(), round, sample.phase(),
                            sample.status(), sample.prompt(), sample.hit(), sample.miss(),
                            sample.valid() ? CacheExperimentReport.percent(sample.hit(), sample.prompt()) : "N/A",
                            sample.elapsedMillis(), sample.valid() ? "" : " ERROR: " + sample.error());
                    assertTrue(sample.valid(), "实验已停止，已完成记录及错误响应：" + directory.toAbsolutePath());
                }
            }
            complete = true;
        } finally {
            CacheExperimentReport.write(directory, run, samples, details, complete);
        }
        System.out.println("Cache experiment completed. Report: " + directory.resolve("report.md").toAbsolutePath());
    }

    @Test
    void statisticsExcludeFailuresAndWeightByTokens() throws Exception {
        Sample first = usage("fixed-en-16", "disabled", 3, "later", 100, 50, 100);
        Sample second = usage("fixed-en-16", "disabled", 4, "later", 900, 0, 200);
        Sample error = new Sample("fixed-en-16", "disabled", 5, "later", 500, 1000, 1000, 0, 10, "test", "stop", "HTTP 500");
        Stats stats = CacheExperimentReport.summarize(List.of(first, second, error));
        assertEquals(2, stats.count());
        assertEquals(1, stats.hits());
        assertEquals("5.00%", stats.tokenRate());
        assertEquals(300, stats.elapsedMillis());
        assertThrows(IllegalArgumentException.class, () -> CacheExperimentReport.usage("case", "disabled", 1, "first", 200, 1,
                CacheExperimentCases.JSON.readTree("{\"usage\":{}}")));
        assertThrows(IllegalArgumentException.class, () -> CacheExperimentReport.usage("case", "disabled", 1, "first", 200, 1,
                CacheExperimentCases.JSON.readTree("{\"usage\":{\"prompt_tokens\":100,\"prompt_cache_hit_tokens\":80,\"prompt_cache_miss_tokens\":50}}")));
    }

    @Test
    void modeAndSummaryPhaseStatisticsStaySeparate() throws Exception {
        List<Sample> samples = List.of(usage("history-summary", "disabled", 1, "before-summary", 100, 0, 10),
                usage("history-summary", "disabled", 3, "summary", 100, 20, 10),
                usage("history-summary", "disabled", 4, "after-summary", 100, 80, 10),
                usage("history-summary", "enabled", 4, "after-summary", 100, 0, 10));
        assertEquals("80.00%", CacheExperimentReport.stats(samples, "disabled", "history-summary",
                s -> s.phase().equals("after-summary")).tokenRate());
        assertEquals("0.00%", CacheExperimentReport.stats(samples, "enabled", "history-summary", s -> s.round() >= 3).tokenRate());
        assertEquals(1, CacheExperimentReport.stats(samples, "disabled", "history-summary", s -> s.phase().equals("summary")).count());
    }

    @Test
    void historyAppendPreservesSnapshotsAndSummaryReplacesOnlyAtThirdCall() {
        Sequence append = sequence("history-append", "disabled");
        ObjectNode first = append.request(1, "test-model");
        append.accept(1, answer("First real answer"));
        ObjectNode second = append.request(2, "test-model");
        assertEquals(2, first.path("messages").size());
        assertEquals(first.path("messages").get(1), second.path("messages").get(1));
        assertEquals("First real answer", second.path("messages").get(2).path("content").asText());
        assertEquals(4, second.path("messages").size());

        Sequence edit = sequence("history-edit", "disabled");
        ObjectNode original = edit.request(1, "test-model");
        edit.accept(1, answer("Original answer"));
        assertNotEquals(original.path("messages").get(1), edit.request(2, "test-model").path("messages").get(1));

        Sequence summary = sequence("history-summary", "disabled");
        ObjectNode before = summary.request(1, "test-model");
        summary.accept(1, answer("First"));
        summary.request(2, "test-model");
        summary.accept(2, answer("Second"));
        ObjectNode compressed = summary.request(3, "test-model");
        assertEquals(before.path("messages").get(0), compressed.path("messages").get(0));
        assertEquals(3, compressed.path("messages").size());
        assertTrue(compressed.path("messages").get(1).path("content").asText().startsWith("历史摘要："));
        assertEquals("before-summary", summary.phase(2));
        assertEquals("summary", summary.phase(3));
        assertEquals("after-summary", summary.phase(4));
    }

    @Test
    void toolRoundtripPreservesReasoningAndMatchesCallIdWithoutExecutingBusinessTools() {
        Sequence sequence = sequence("tool-roundtrip", "enabled");
        ObjectNode first = sequence.request(1, "test-model");
        ObjectNode response = answer(null);
        ObjectNode assistant = (ObjectNode) response.path("choices").get(0).path("message");
        assistant.put("reasoning_content", "Fixture reasoning required for replay.");
        ObjectNode call = assistant.putArray("tool_calls").addObject().put("id", "fixture-call-42").put("type", "function");
        call.putObject("function").put("name", "get_section_preview").put("arguments", "{\"documentId\":42,\"nodeNumber\":1}");
        sequence.accept(1, response);
        ObjectNode second = sequence.request(2, "test-model");
        assertEquals("enabled", first.path("thinking").path("type").asText());
        assertEquals("high", first.path("reasoning_effort").asText());
        assertEquals(first.path("tools"), second.path("tools"));
        assertEquals("auto", second.path("tool_choice").asText());
        assertEquals(assistant, second.path("messages").get(2));
        assertEquals("fixture-call-42", second.path("messages").get(3).path("tool_call_id").asText());
        assertEquals(CacheExperimentCases.SECTION, second.path("messages").get(3).path("content").asText());
        sequence.accept(2, answer("The marker is azure-falcon-42."));
        assertEquals(6, sequence.request(3, "test-model").path("messages").size());
        ((ObjectNode) call.path("function")).put("name", "propose_section_replace");
        assertThrows(IllegalArgumentException.class, () -> CacheExperimentCases.localToolResults(assistant));
        assertThrows(IllegalArgumentException.class, () -> sequence("tool-roundtrip", "enabled").accept(1, answer("No tool")));
    }

    @Test
    void filtersRejectTyposAndDefaultTo240Requests() {
        Options defaults = Options.from(new Properties());
        assertEquals(24, defaults.scenarios().size());
        assertEquals(240, defaults.scenarios().size() * defaults.modes().size() * defaults.repeats());
        Properties properties = new Properties();
        properties.setProperty("cacheExperiment.scenarios", "dynamic-system-head,dynamic-user");
        properties.setProperty("cacheExperiment.modes", "enabled");
        assertEquals(2, Options.from(properties).scenarios().size());
        properties.setProperty("cacheExperiment.scenarios", "unknown-case");
        assertThrows(IllegalArgumentException.class, () -> Options.from(properties));
        properties.setProperty("cacheExperiment.scenarios", "short-en");
        properties.setProperty("cacheExperiment.modes", "enabled,enabled");
        assertThrows(IllegalArgumentException.class, () -> Options.from(properties));
    }

    @Test
    void reportsPreservePartialResultsAndContainConcreteCases(@TempDir Path directory) throws Exception {
        Definition definition = definition("history-summary");
        ObjectNode request = sequence("history-summary", "disabled").request(1, "test-model");
        Run run = new Run("offline-run", "test-model", List.of("disabled", "enabled"), List.of(definition), 5, 3000,
                List.of(new Preview("disabled", definition, request)));
        Sample sample = usage("history-summary", "disabled", 1, "before-summary", 100, 0, 10);
        List<Map<String, Object>> details = List.of(Map.of("request", request, "responseBody", "{\"fixture\":true}", "sample", sample));
        CacheExperimentReport.write(directory, run, List.of(sample), details, false);
        JsonNode saved = CacheExperimentCases.JSON.readTree(Files.readString(directory.resolve("details.json")));
        assertFalse(saved.path("complete").asBoolean());
        assertEquals(1, saved.path("attemptedRequests").asInt());
        assertEquals(request, saved.path("requests").get(0).path("request"));
        String report = Files.readString(directory.resolve("report.md"));
        assertTrue(report.contains("压缩当轮"));
        assertTrue(report.contains("思考模式：enabled"));
        assertTrue(report.contains("尚无足够的配对有效样本"));
        assertTrue(Files.readString(directory.resolve("cases.md")).contains("Markdown 服务安装手册"));
        assertEquals(2, Files.readAllLines(directory.resolve("samples.csv")).size());
        assertEquals("request [REDACTED] failed", redact("request sentinel-private-value failed", "sentinel-private-value"));
    }

    private static Sample usage(String scenario, String mode, int round, String phase, long prompt, long hit, long elapsed) throws Exception {
        ObjectNode response = answer("OK");
        response.putObject("usage").put("prompt_tokens", prompt).put("prompt_cache_hit_tokens", hit).put("prompt_cache_miss_tokens", prompt - hit);
        return CacheExperimentReport.usage(scenario, mode, round, phase, 200, elapsed, response);
    }

    private static ObjectNode answer(String text) {
        ObjectNode response = CacheExperimentCases.JSON.createObjectNode().put("model", "fixture-model");
        ObjectNode choice = response.putArray("choices").addObject().put("finish_reason", "stop");
        choice.set("message", CacheExperimentCases.message("assistant", text));
        return response;
    }

    private static Sequence sequence(String id, String mode) { return new Sequence(definition(id), mode, "offline-fixed-seed"); }
    private static Definition definition(String id) {
        return CacheExperimentCases.definitions().stream().filter(d -> d.id().equals(id)).findFirst().orElseThrow();
    }

    private static StandardEnvironment loadDevelopmentConfig() throws Exception {
        FileSystemResource resource = new FileSystemResource("src/main/resources/application-dev.yaml");
        assertTrue(resource.exists(), "请在项目根目录运行，确认 application-dev.yaml 存在");
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("cache-experiment", resource).forEach(environment.getPropertySources()::addLast);
        return environment;
    }

    private static String requiredConfig(StandardEnvironment environment, String name) {
        String value;
        try {
            value = environment.getProperty(name);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("配置占位符未解析：" + name + "，请检查开发配置及当前 JUnit 进程环境变量");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("配置为空：" + name + "，API Key 可填写为 ${DEEPSEEK_API_KEY:本地密钥}，或设置当前进程的 DEEPSEEK_API_KEY");
        }
        return value.trim();
    }

    private static String sha256(String body) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
    }
    private static long elapsedMillis(long start) { return Duration.ofNanos(System.nanoTime() - start).toMillis(); }
    private static String redact(String text, String secret) { return text.replace(secret, "[REDACTED]"); }
    private record Reply(int status, String body, String contentType) { }
    private record Trial(String mode, Definition definition, Sequence sequence) { }

    private record Options(int repeats, int pauseMillis, List<String> modes, List<Definition> scenarios) {
        static Options from(Properties properties) {
            int repeats = bounded(properties, "cacheExperiment.repeats", 5, 3, 10);
            int pause = bounded(properties, "cacheExperiment.pauseMillis", 3000, 0, 60000);
            List<String> modes = selection(properties.getProperty("cacheExperiment.modes", "disabled,enabled"), List.of("disabled", "enabled"));
            List<Definition> all = CacheExperimentCases.definitions();
            List<String> ids = all.stream().map(Definition::id).toList();
            List<String> selected = selection(properties.getProperty("cacheExperiment.scenarios", String.join(",", ids)), ids);
            return new Options(repeats, pause, modes, all.stream().filter(d -> selected.contains(d.id())).toList());
        }

        private static List<String> selection(String value, List<String> allowed) {
            List<String> selected = Arrays.stream(value.split(",", -1)).map(String::trim).toList();
            if (selected.isEmpty() || selected.stream().distinct().count() != selected.size() || !allowed.containsAll(selected)) {
                throw new IllegalArgumentException("筛选项为空、重复或未知：" + value + "；允许值：" + String.join(",", allowed));
            }
            return selected;
        }

        private static int bounded(Properties properties, String name, int fallback, int min, int max) {
            int value = Integer.parseInt(properties.getProperty(name, Integer.toString(fallback)));
            if (value < min || value > max) throw new IllegalArgumentException(name + " 必须在 " + min + " 与 " + max + " 之间");
            return value;
        }
    }
}
