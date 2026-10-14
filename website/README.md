# OryxOS Website

OryxOS 的独立官网与双语文档站，使用 VitePress 构建。

## 本地开发

环境要求：Node.js 20 或更高版本、npm 10 或更高版本。

```bash
npm ci
npm run docs:dev
```

开发服务默认运行在 `http://localhost:5173`。

## 生产构建

```bash
npm ci
npm run docs:build
```

静态产物输出到 `.vitepress/dist/`，可以独立部署到 Nginx、对象存储或任意静态站点平台。站点当前按域名根路径 `/` 构建。

## 本地预览

```bash
npm run docs:preview
```

正式域名确定后，需要在 `.vitepress/config.mts` 中补充 canonical、Open Graph URL 和 sitemap hostname。
