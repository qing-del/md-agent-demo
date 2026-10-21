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
