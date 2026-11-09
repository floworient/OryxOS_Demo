/**
 * 相对时间：刚刚 / N 分钟前 / N 小时前 / N 天前；超过 7 天回落为日期。
 * 非法输入原样返回，避免渲染 NaN。
 */
export function formatRelativeTime(iso: string, now: Date = new Date()): string {
  const then = new Date(iso).getTime()
  if (Number.isNaN(then)) {
    return iso
  }
  const diffMinutes = Math.floor((now.getTime() - then) / 60000)
  if (diffMinutes < 1) {
    return '刚刚'
  }
  if (diffMinutes < 60) {
    return `${diffMinutes} 分钟前`
  }
  const diffHours = Math.floor(diffMinutes / 60)
  if (diffHours < 24) {
    return `${diffHours} 小时前`
  }
  const diffDays = Math.floor(diffHours / 24)
  if (diffDays <= 7) {
    return `${diffDays} 天前`
  }
  return new Date(then).toLocaleDateString('zh-CN')
}
