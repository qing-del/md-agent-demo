package com.jacolp;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** 分模式、分场景、分阶段的报告；所有比率按有效样本的 token 总和计算。 */
final class CacheExperimentReport {
    record Sample(String scenario, String mode, int round, String phase, int status, long prompt, long hit,
            long miss, long elapsedMillis, String responseModel, String finishReason, String error) {
        boolean valid() { return error.isEmpty(); }
    }

    record Stats(long count, long hits, long prompt, long hit, long elapsedMillis) {
        String tokenRate() { return percent(hit, prompt); }
        String requestRate() { return hits + "/" + count + " (" + percent(hits, count) + ")"; }
        String averageMillis() { return count == 0 ? "N/A" : Long.toString(elapsedMillis / count); }
    }

    record Preview(String mode, CacheExperimentCases.Definition definition, ObjectNode request) { }
    record Run(String id, String model, List<String> modes, List<CacheExperimentCases.Definition> scenarios,
            int repeats, int pauseMillis, List<Preview> previews) {
        int plannedRequests() { return modes.size() * scenarios.size() * repeats; }
    }

    static Sample usage(String scenario, String mode, int round, String phase, int status, long elapsed, JsonNode response) {
        JsonNode usage = response.path("usage");
        long prompt = tokenCount(usage, "prompt_tokens");
        long hit = tokenCount(usage, "prompt_cache_hit_tokens");
        long miss = tokenCount(usage, "prompt_cache_miss_tokens");
        if (prompt <= 0 || hit > prompt || miss != prompt - hit) {
            throw new IllegalArgumentException("usage 输入 token 统计不一致，不能计为缓存命中或未命中");
        }
        return new Sample(scenario, mode, round, phase, status, prompt, hit, miss, elapsed,
                response.path("model").asText("unknown"),
                response.path("choices").path(0).path("finish_reason").asText("unknown"), "");
    }

    private static long tokenCount(JsonNode usage, String field) {
        JsonNode node = usage.path(field);
        if (!node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() < 0) {
            throw new IllegalArgumentException("响应缺少有效 usage." + field + "，不能将缺失值当成零命中");
        }
        return node.longValue();
    }

    static Stats summarize(List<Sample> samples) {
        List<Sample> valid = samples.stream().filter(Sample::valid).toList();
        return new Stats(valid.size(), valid.stream().filter(s -> s.hit() > 0).count(),
                valid.stream().mapToLong(Sample::prompt).sum(), valid.stream().mapToLong(Sample::hit).sum(),
                valid.stream().mapToLong(Sample::elapsedMillis).sum());
    }

    static Stats stats(List<Sample> samples, String mode, String scenario, Predicate<Sample> phase) {
        return summarize(samples.stream().filter(s -> s.mode().equals(mode) && s.scenario().equals(scenario))
                .filter(phase).toList());
    }

    static void write(Path directory, Run run, List<Sample> samples, List<Map<String, Object>> details, boolean complete)
            throws Exception {
        Files.createDirectories(directory);
        if (!Files.exists(directory.resolve("cases.md"))) {
            save(directory.resolve("cases.md"), cases(run));
        }
        save(directory.resolve("report.md"), report(run, samples, complete));
        save(directory.resolve("samples.csv"), csv(samples));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", run.id());
        data.put("requestedModel", run.model());
        data.put("modes", run.modes());
        data.put("repeats", run.repeats());
        data.put("pauseMillis", run.pauseMillis());
        data.put("maxTokens", 1024);
        data.put("enabledReasoningEffort", "high");
        data.put("plannedRequests", run.plannedRequests());
        data.put("attemptedRequests", samples.size());
        data.put("complete", complete);
        data.put("harnessReference", CacheExperimentCases.HARNESS_REF);
        data.put("requests", details);
        save(directory.resolve("details.json"), CacheExperimentCases.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(data));
    }

    private static String report(Run run, List<Sample> samples, boolean complete) {
        StringBuilder text = new StringBuilder("# DeepSeek 缓存实验报告\n\n");
        text.append("运行：").append(run.id()).append("；请求模型：").append(run.model())
                .append("；模式：").append(String.join(", ", run.modes())).append("。\n\n")
                .append("状态：").append(complete ? "完成" : "未完成（进行中或已停止）").append("；请求尝试 ")
                .append(samples.size()).append(" / ").append(run.plannedRequests()).append("；有效 ")
                .append(samples.stream().filter(Sample::valid).count()).append("；失败 ")
                .append(samples.stream().filter(s -> !s.valid()).count()).append("。每组 ").append(run.repeats())
                .append(" 次，间隔 ").append(run.pauseMillis()).append(" ms；max_tokens=1024，开启思考时 effort=high。\n\n")
                .append("首轮为首次观察，不保证冷缓存。token 命中率 = Σ命中 tokens / Σ输入 tokens；")
                .append("命中请求比例表示 hit>0 的有效请求占比，失败样本不进入统计分母。两种模式分别统计。")
                .append("除短问候外，每组每种模式使用不同且组内固定的实验标识，降低跨组预热。")
                .append("中文与英文未对齐 token 长度；不能用本轮比较证明语言优劣或推导固定缓存门槛。\n\n");
        for (String mode : run.modes()) {
            text.append("## 思考模式：").append(mode).append("\n\n")
                    .append("| 场景 | 分类 | 有效/计划 | 输入 tokens 范围 | 首轮命中率 | 第二轮命中率 | 第3轮起 token 命中率 | 第3轮起命中请求 | 第3轮起平均耗时 ms |\n")
                    .append("|---|---|---:|---:|---:|---:|---:|---:|---:|\n");
            for (CacheExperimentCases.Definition scenario : run.scenarios()) {
                List<Sample> group = samples.stream().filter(s -> s.mode().equals(mode) && s.scenario().equals(scenario.id())
                        && s.valid()).toList();
                Stats first = summarize(group.stream().filter(s -> s.round() == 1).toList());
                Stats second = summarize(group.stream().filter(s -> s.round() == 2).toList());
                Stats later = summarize(group.stream().filter(s -> s.round() >= 3).toList());
                String range = group.isEmpty() ? "N/A" : group.stream().mapToLong(Sample::prompt).min().orElseThrow()
                        + "–" + group.stream().mapToLong(Sample::prompt).max().orElseThrow();
                text.append("| ").append(scenario.id()).append(" | ").append(scenario.category()).append(" | ")
                        .append(group.size()).append('/').append(run.repeats()).append(" | ").append(range).append(" | ")
                        .append(first.tokenRate()).append(" | ").append(second.tokenRate()).append(" | ")
                        .append(later.tokenRate()).append(" | ").append(later.requestRate()).append(" | ")
                        .append(later.averageMillis()).append(" |\n");
            }
            if (run.scenarios().stream().anyMatch(s -> s.id().equals("history-summary"))) {
                text.append("\n### 历史压缩阶段（不同输入长度，不与普通重复请求合并解释）\n\n")
                        .append("| 阶段 | 有效请求 | 输入 tokens | 命中 tokens | token 命中率 | 命中请求比例 |\n")
                        .append("|---|---:|---:|---:|---:|---:|\n");
                for (String phase : List.of("before-summary", "summary", "after-summary")) {
                    Stats stats = stats(samples, mode, "history-summary", s -> s.phase().equals(phase));
                    text.append("| ").append(switch (phase) {
                        case "before-summary" -> "压缩前（第1、2次，含首次观察）";
                        case "summary" -> "压缩当轮（第3次）";
                        default -> "压缩后（第4次及以后）";
                    }).append(" | ").append(stats.count()).append(" | ").append(stats.prompt()).append(" | ")
                            .append(stats.hit()).append(" | ").append(stats.tokenRate()).append(" | ")
                            .append(stats.requestRate()).append(" |\n");
                }
            }
            text.append("\n### 实测对照与写法建议\n\n");
            List<String> observations = new ArrayList<>();
            compare(observations, samples, mode, "dynamic-system-head", "dynamic-system-middle", "将动态数据从开头移到中间");
            compare(observations, samples, mode, "dynamic-system-head", "dynamic-system-tail", "将动态数据移到 System 末尾");
            compare(observations, samples, mode, "dynamic-system-head", "dynamic-user", "System 固定、动态业务数据放 User");
            compare(observations, samples, mode, "reordered-system", "fixed-en-16", "固定规则排列顺序");
            compare(observations, samples, mode, "whitespace-head", "whitespace-tail", "避免改变开头空白，将变化留在尾部");
            compare(observations, samples, mode, "question-first", "document-first", "固定文档在前、变化问题在后");
            compare(observations, samples, mode, "history-edit", "history-append", "保留已发送历史，只追加新消息");
            compare(observations, samples, mode, "tools-reordered", "tools-fixed", "固定工具列表顺序");
            compare(observations, samples, mode, "tools-description-change", "tools-fixed", "固定工具描述");
            if (observations.isEmpty()) text.append("尚无足够的配对有效样本，不生成写法结论。\n");
            observations.forEach(line -> text.append("- ").append(line).append('\n'));
        }
        List<Sample> failed = samples.stream().filter(s -> !s.valid()).toList();
        if (!failed.isEmpty()) {
            text.append("\n## 停止原因\n\n");
            failed.forEach(s -> text.append("- ").append(s.mode()).append('/').append(s.scenario()).append(" #")
                    .append(s.round()).append("：").append(s.error().replace("|", "\\|")).append('\n'));
        }
        text.append("\n## 解释边界\n\n")
                .append("- 结论只基于本次模式和模型，第3轮起仍可能包含预热；比较表给出原始比例，不保证未来命中。\n")
                .append("- examples、selections 和工具交换可能在后续轮次重复早先变体，命中不一定来自紧邻的上一轮；用 details.json 的请求指纹辨别。\n")
                .append("- 三个工具定义对照使用 tool_choice=none，提供方可能不把工具定义注入模型上下文；其结果不能直接泛化到 auto 工具调用。\n")
                .append("- 保留角色、长期规则与安全约束的指令优先级；仅将动态业务数据移入 User，不为缓存降低约束权限。\n")
                .append("- 不为命中率堆砌无用文字，不按字符数设置统一长度门槛；本实验不评估回答质量。\n")
                .append("- 耗时为读取完整 HTTP 响应的耗时，不是首 token 延迟，不能单独证明缓存加速。\n")
                .append("- cases.md 包含具体案例，samples.csv 包含逐次数据，details.json 保留完整请求、原始响应、实际返回模型与阶段。\n\n")
                .append("参考：[Harness 缓存测试](").append(CacheExperimentCases.HARNESS)
                .append("/packages/core/agent-loop/tests/request-cache.e2e.ts)、[提示词组装](")
                .append(CacheExperimentCases.HARNESS).append("/packages/core/system-prompt/README.md)、")
                .append("[DeepSeek 缓存规则](https://api-docs.deepseek.com/zh-cn/guides/kv_cache/)。\n");
        return text.toString();
    }

    private static void compare(List<String> observations, List<Sample> samples, String mode, String from, String to, String rule) {
        Stats a = stats(samples, mode, from, s -> s.round() >= 3);
        Stats b = stats(samples, mode, to, s -> s.round() >= 3);
        if (a.count() < 2 || b.count() < 2) return;
        double difference = 100.0 * b.hit() / b.prompt() - 100.0 * a.hit() / a.prompt();
        observations.add("`" + from + "` " + a.tokenRate() + " → `" + to + "` " + b.tokenRate()
                + "（" + String.format(Locale.ROOT, "%+.2f", difference) + " 个百分点）。"
                + (difference > 0 ? "本轮观察支持：" + rule + "。" : "本轮未观察到该调整提高 token 命中率，不据此设置强制写法规则。"));
    }

    private static String cases(Run run) throws Exception {
        StringBuilder text = new StringBuilder("# DeepSeek 缓存实验案例\n\n");
        text.append("运行：").append(run.id()).append("；模型：").append(run.model()).append("；模式：")
                .append(String.join(", ", run.modes())).append("；每组请求 ").append(run.repeats())
                .append(" 次；间隔 ").append(run.pauseMillis()).append(" ms；max_tokens=1024；enabled effort=high。\n\n")
                .append("下面的首轮请求来自此次运行的第一种模式，含实际固定实验标识；另一模式及所有后续实际请求见 details.json。")
                .append("每个长输入组、每种模式分别隔离实验标识，不据此宣称首轮绝对冷缓存。\n\n")
                .append("文档和规则为测试自编合成素材。参考官方 Harness 的历史追加、真实工具结果回传和固定组装顺序，未引入其运行依赖。\n\n");
        for (Preview preview : run.previews()) {
            if (!preview.mode().equals(run.modes().get(0))) continue;
            CacheExperimentCases.Definition definition = preview.definition();
            text.append("## ").append(definition.id()).append("\n\n分类：").append(definition.category())
                    .append("。测试内容：").append(definition.description()).append("。对照：`")
                    .append(definition.control()).append("`。\n\n");
            if (definition.id().startsWith("history-")) {
                text.append("后续请求保留实际助手回答，再追加固定问题；history-edit 改写最早用户消息，")
                        .append("history-summary 仅第3次执行摘要替换，第4次起正常追加。\n\n");
            }
            if (definition.id().equals("tool-roundtrip")) {
                text.append("五步：真实 get_section_preview 调用 → 追加原始助手消息及匹配 tool_call_id 的固定结果 → ")
                        .append("概括章节 → 指出一个步骤 → 确认校验标识。超过5次时重复最后追问。工具只访问测试内存。\n\n")
                        .append("固定工具结果：\n\n```text\n").append(CacheExperimentCases.SECTION).append("\n```\n\n");
            }
            text.append("首轮请求：\n\n```json\n")
                    .append(CacheExperimentCases.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(preview.request()))
                    .append("\n```\n\n");
        }
        text.append("## 后续文档问题列表\n\n");
        for (int i = 0; i < run.repeats(); i++) text.append(i + 1).append(". ").append(CacheExperimentCases.QUESTIONS[i]).append('\n');
        return text.toString();
    }

    private static String csv(List<Sample> samples) {
        StringBuilder csv = new StringBuilder("\uFEFFscenario,mode,round,phase,status,prompt_tokens,hit_tokens,miss_tokens,hit_ratio,elapsed_ms,response_model,finish_reason,error\n");
        for (Sample s : samples) {
            csv.append(s.scenario()).append(',').append(s.mode()).append(',').append(s.round()).append(',').append(s.phase())
                    .append(',').append(s.status()).append(',').append(s.prompt()).append(',').append(s.hit()).append(',')
                    .append(s.miss()).append(',').append(s.valid() ? percent(s.hit(), s.prompt()) : "N/A")
                    .append(',').append(s.elapsedMillis()).append(',').append(quoted(s.responseModel())).append(',')
                    .append(quoted(s.finishReason())).append(',').append(quoted(s.error())).append('\n');
        }
        return csv.toString();
    }

    private static String quoted(String value) { return '"' + value.replace("\"", "\"\"") + '"'; }

    static String percent(long numerator, long denominator) {
        return denominator == 0 ? "N/A" : String.format(Locale.ROOT, "%.2f%%", 100.0 * numerator / denominator);
    }

    private static void save(Path file, String content) throws Exception {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    }

    private CacheExperimentReport() { }
}
