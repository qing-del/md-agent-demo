# md-agent

一个最小的 Spring AI 后端 demo，当前提供 OpenAI Chat 接口。

## 启动

```bash
export OPENAI_API_KEY=your-api-key
mvn spring-boot:run
```

也可以通过 `OPENAI_MODEL` 覆盖模型，默认是 `gpt-4o-mini`。

## 调用

```bash
curl -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"用一句话介绍 Spring AI"}'
```

返回：

```json
{"content":"..."}
```
