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
  -d '{"sessionKey":"550e8400-e29b-41d4-a716-446655440000","message":"用一句话介绍 Spring AI"}'
```

`sessionKey` 必须是前端生成的标准 UUID。后端会在首次请求时创建对应会话，之后使用同一 UUID 恢复历史上下文。

已有数据库启动前，需先执行 [20260907_add_chat_session_key.sql](static/migration/20260907_add_chat_session_key.sql)；新建数据库直接执行 [init.sql](static/init.sql)。

查询历史会话摘要，可选地按标题进行模糊查询：

```bash
curl 'http://localhost:8080/api/sessions?title=Spring'
```

列表接口返回：

```json
[
  {
    "sessionKey": "550e8400-e29b-41d4-a716-446655440000",
    "title": "Spring AI",
    "createdAt": "2026-09-07T12:00:00",
    "updatedAt": "2026-09-07T12:05:00"
  }
]
```

聊天接口返回：

```json
{"content":"..."}
```
