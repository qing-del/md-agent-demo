# Markdown Agent 前端

这是一个独立的 Vite + React 前端工作台，面向当前仓库中的 Markdown Agent Spring Boot 后端。

## 本地运行

先启动后端（默认监听 `http://localhost:8080`），再执行：

```bash
cd frontend
npm install
npm run dev
```

打开终端输出的本地地址，通常是 `http://localhost:5173`。

开发环境下浏览器请求的 `/api` 会由 Vite 代理到 `http://localhost:8080`，不需要修改后端跨域配置。可以复制 `.env.example` 为 `.env.local`，通过 `VITE_API_PROXY` 调整代理目标；只有部署到已经配置跨域的独立 API 地址时，才需要设置 `VITE_API_BASE`。

## 已对接接口

- `GET/POST /api/documents`、`GET/PUT/DELETE /api/documents/{documentId}`
- `GET /api/sessions`、`GET /api/sessions/{sessionKey}`
- `POST /api/chat`

替换提案只在前端当前草稿中应用，保存时统一通过 `PUT /api/documents/{documentId}` 同步；前端不会调用提案的 confirm/cancel 接口。

## 构建验证

```bash
npm run build
```
