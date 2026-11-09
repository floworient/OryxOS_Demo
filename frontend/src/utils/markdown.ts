import { marked } from 'marked'
import DOMPurify from 'dompurify'

// GFM（表格、删除线、任务列表等）；不自动把裸 URL 转链接，保持输出可预期
marked.setOptions({ gfm: true, breaks: true })

/**
 * 将 Markdown 文本渲染为消毒后的 HTML 片段。
 * DOMPurify 拦截 script/事件属性/javascript: 协议等注入向量，v-html 安全。
 */
export function renderMarkdown(text: string): string {
  const raw = marked.parse(text, { async: false })
  return DOMPurify.sanitize(raw)
}
