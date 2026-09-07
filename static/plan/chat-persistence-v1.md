# 聊天记录持久化 v1 方案

## 目标

- 使用 `chat_sessions` 保存完整聊天历史。
- 新增 `referenced_file_contents` JSON 列，保存聊天中引用过的 Markdown 文件信息。
- 选中 Markdown 片段执行 `add to chat` 时，保存有限的前后文和选中内容。
- 使用 Spring AI 的 `ChatMemory` 管理模型上下文，采用滑动窗口保留最近 10 条消息。
- 脏会话每 2 分钟刷新一次数据库。

## 接口与职责

Spring AI 1.1.8 没有 `ChatContextManage` 或 `ChatMessageCache` 接口；对应的框架接口是 `org.springframework.ai.chat.memory.ChatMemory`。

v1 直接使用 `ChatMemory` 作为上下文管理接口，不再额外定义同职责的接口。应用实现负责：

- `add`：将消息加入当前会话的内存缓存。
- `get`：只返回最近 10 条消息给模型。
- `clear`：清理会话内存状态。
- 内部同时保留完整消息列表，供定时持久化使用。

`MessageChatMemoryAdvisor` 使用 `ChatMemory` 为每次请求加载上下文，并在请求完成后加入用户消息和 AI 回复；每次调用必须传入稳定的 `conversationId`，使用前端生成并持久化在 `chat_sessions.session_key` 中的 UUID。

## 数据结构

`chat_sessions` 增加：

```sql
referenced_file_contents JSON NOT NULL DEFAULT (JSON_ARRAY())
```

同时保留数据库自增主键，并增加前端会话标识：

```sql
session_key CHAR(36) NOT NULL UNIQUE
```

推荐保存去重后的引用信息，而不是完整文件内容：

```json
[
  {
    "referenceId": "ref-uuid",
    "documentId": 12,
    "fileName": "guide.md",
    "contentSha256": "...",
    "referencedAt": "2026-09-07T12:00:00Z",
    "turnIds": ["turn-uuid"]
  }
]
```

如果发生 `add to chat`，在对应引用项中增加有限上下文：

```json
{
  "selectedContext": {
    "title": "被选中区域的标题",
    "before": "选中内容前的有限文本",
    "selected": "用户选中的文本",
    "after": "选中内容后的有限文本"
  }
}
```

`messages` 保留用户原始输入和 AI 回复，并通过 `turnId` / `referenceId` 建立关联。普通 `@file.md` 引用只保存文件身份、哈希和引用关系；选中内容需要实际保存，因为它是后续历史上下文的一部分。

v1 不保存完整文件快照，因此普通引用在文件被修改或删除后不能保证完全复现当时的文件内容；`contentSha256` 用于识别这种变化。

## 请求处理流程

1. 前端生成 UUID，并在请求中携带 `sessionKey` 和用户原始消息。
2. 从缓存获取会话；缓存未命中时按 `session_key` 从 `chat_sessions` 加载，不存在时自动创建。
3. 解析 `@xxx.md`，根据文档 ID/文件名读取 `md_documents`。
4. 将文件引用元数据、选中片段和有限前后文加入当前会话缓存。
5. 通过 `ChatMemory` 向模型提供最近 10 条消息。
6. AI 回复完成后，将用户消息和 AI 回复加入完整历史缓存。
7. 会话标记为 dirty，由定时任务刷新数据库。

用户原始消息和发送给模型的扩展上下文应分开保存，避免历史记录被完整 Markdown 内容污染。

## 刷新策略

- 定时任务每 1 分钟扫描 dirty 会话并批量写入数据库。
- 每次刷新同时更新 `messages` 和 `referenced_file_contents`，保证同一会话的数据一致。
- AI 请求进行中时不刷新该会话，待请求完成后进入下一次刷新。
- 数据库写入失败时保留 dirty 状态并重试。
- 应用正常关闭时主动刷新所有 dirty 会话。
- v1 仅考虑单实例内存缓存；应用异常退出时，最多可能丢失最近约 2 分钟未刷新的数据。

## 上下文窗口

`maxMessages = 10` 表示最近 10 条 Spring AI `Message`，用户消息和 AI 消息合计计算，约等于最近 5 轮对话。如果产品语义要求“最近 10 轮”，则应设置为 20。

完整历史不受这个窗口限制，仍保存在 `chat_sessions.messages` 中。

## v1 边界

- 暂不实现 WebSocket。
- 暂不引入 Spring AI JDBC Chat Memory Repository；它适合持久化模型记忆，不直接匹配当前 `chat_sessions` 和文件引用结构。
- 同一会话的并发请求需要串行化，避免整行 JSON 更新互相覆盖。
- `referenced_file_contents` 和选中片段需要设置大小上限。
- `static/init.sql` 中的 `CREATE TABLE IF NOT EXISTS` 只覆盖新建表；已有数据库需要单独执行迁移。

## 实施顺序

1. 确认请求中的 UUID `sessionKey`、`@` 语法和选中片段字段。
2. 增加数据库列及迁移脚本。
3. 实现基于内存缓存的 `ChatMemory` 和 10 条滑动窗口。
4. 接入 `MessageChatMemoryAdvisor`。
5. 实现 2 分钟定时刷新和失败重试。
6. 补充会话加载、历史恢复、引用文件和并发更新测试。

参考：[Spring AI 1.1.x Chat Memory](https://docs.spring.io/spring-ai/reference/1.1-SNAPSHOT/api/chat-memory.html)
