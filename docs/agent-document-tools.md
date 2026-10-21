# Agent 文档工具

## 原文搜索

`search_document_content(documentId, query, nodeNumber?, cursor?)` 对 Markdown 原文执行区分大小写的字面搜索。
省略章节编号时搜索全文；指定编号时包括其子章节。查询非空白，最多 256 个 Unicode 码点。
每页最多 10 项，包括重叠命中，按原文顺序返回。代码块也参与搜索，不使用正则或语义检索。

返回 `documentId`、内容 SHA-256 `revision`、`matches`、`nextCursor`。
每项含 `nodeNumber`、`sectionPath`、`locationKind`（`heading`/`body`/`preamble`）、
`matchedText`、`context`、`contextTruncated`。标题前的内容及无标题文档的章节编号为空。
上下文前后各最多 80 个 Unicode 码点，保留换行及原始格式。片段不完整时需读取章节；
不要把展示片段或省略号当成精确修改锚点。没有命中返回空列表。

```json
{"documentId":42,"query":"重试次数","nodeNumber":null,"cursor":null}
```

将返回的 `nextCursor` 原样用于同一查询的下一页；为空时结束。游标绑定文档、revision、
查询和章节范围，版本或参数变化后须重新开始。游标不是权限凭证。

`get_article_overview` 和 `get_section_preview` 在原有字符串前增加 `revision: <SHA-256>\n`；
`get_section_content` 增加 `revision` 字段，`content` 仍是原始分页文本。
读取结果的版本用于后续提案，章节编号只在该版本内有效。

## 插入和版本化提案

`propose_section_insert(documentId, baseRevision, nodeNumber, position, anchorText?, newText)`
只创建 `PENDING` 提案，不更新文档。`baseRevision` 必须来自读取结果，新增文本非空且原样保留，
包括换行；不会自动添加空行或重排 Markdown。

`body_start`/`body_end` 分别位于章节直属正文开头/末尾，不能提供锚点；
`body_end` 在第一个子章节之前，不是整棵章节树末尾。
`before`/`after` 必须提供在直属正文中唯一出现的非空 `anchorText`。空章节也可插入。

```json
{"documentId":42,"baseRevision":"读取结果中的SHA-256","nodeNumber":5,"position":"after","anchorText":"最多重试三次。","newText":"\n\n建议使用退避策略。"}
```

提案与聊天响应增加 `kind`（`replace`/`insert`）、`baseRevision` 和 `edits`。
`edits` 每项包括 `type`、`nodeNumber`、创建时的 `sectionText`、`position`、`anchorText`、
实际变更的 `originalText` 和 `newText`；插入的 `originalText` 为空。
原有替换字段和入参保留，旧替换提案同样由服务端记录基础版本。
删除整行时沿用旧行为，包括移除该行行尾换行，差异中会展示实际删除内容。

接口：

- `GET /api/markdown-operations/{opId}`：查询提案与当前状态。
- `POST /api/markdown-operations/{opId}/preview`，请求 `{"content":"当前完整草稿"}`：
  计算草稿 SHA-256，严格匹配基础版本后返回 `documentId/baseRevision/revision/content/edits`。
  `content` 是完整候选草稿；不会写数据库、发布内存快照或推进提案状态。
- `POST /api/markdown-operations/{opId}/confirm`：校验当前保存版本后单次写入，返回结果版本与状态。
- `POST /api/markdown-operations/{opId}/cancel`：取消待处理提案。

试应用只校验传入草稿，确认校验当前后端快照。版本冲突返回 HTTP 409，提案仍为 `PENDING`；
需要读取新版本并重新生成，不做自动合并。未知操作为 404，无效编辑为 400。
执行或存储失败进入 `FAILED`，持久化失败不发布新快照。重复确认被拒绝。
聊天失败会丢弃本轮提案，提案仅在内存中保留，重启后失效。

本次仅交付后端，前端尚不能展示或应用新增提案类型。后续客户端应使用试应用接口，
并在接收结果时检查本地草稿未发生变化，再整体更新草稿，由用户保存。
现有草稿保存协议没有加入版本条件更新；本轮不解决旧客户端覆盖保存的问题。
同一 JVM 的上传覆盖、草稿同步、删除、SDK 替换和提案确认共用文档锁；不提供多实例协调。
