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

## 单文档批量修改

`propose_document_patch(documentId, baseRevision, edits)` 创建 `kind: "patch"` 的整体提案，
包含 1～50 项非空编辑，所有章节编号和原文均相对于同一个 `baseRevision`。
每项不能指定其他文档，也不能依赖前一项刚插入或替换的内容。

```json
{
  "documentId": 42,
  "baseRevision": "读取结果中的SHA-256",
  "edits": [
    {"type":"replace","nodeNumber":5,"originalText":"最多重试三次","newText":"最多重试五次"},
    {"type":"insert","nodeNumber":8,"position":"body_end","newText":"\n\n建议开启监控。"}
  ]
}
```

`replace` 的 `originalText` 必须在章节直属正文中唯一出现，`newText` 空字符串表示删除，
不接受 `position` 或 `anchorText`。`insert` 的位置及锚点规则与单项插入相同，
不接受 `originalText`。字段值区分大小写。

一次解析全部编辑为基础原文的坐标，拒绝重叠替换、替换内部或边界上的插入、同位置多项插入。
相邻但不重叠的两项替换允许执行。删除整行扩展出的换行也计入冲突检测范围。
按原始坐标从后向前生成一份候选原文，仅在最后重新解析章节树，不逐项写库。

查询、试应用、确认、取消沿用现有操作接口。`edits` 返回全部逐项差异，顺序与请求一致；
试应用返回完整候选内容，确认只持久化一次。任何一项无效时不创建提案，也不产生部分修改。
所有提案仍使用同一请求捕获与失败清理机制。

为兼容已有接口，批量提案顶层的 `nodeNumber`、`sectionText`（聊天响应中）、
`originalText`、`newText` 指第一项编辑；客户端必须按 `kind` 分支，使用完整 `edits`，
不能把顶层字段当作整个批量提案。结果中的 `revision` 是整个文档的新版本。
