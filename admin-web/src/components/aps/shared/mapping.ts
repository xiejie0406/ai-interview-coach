export function parseApsDate(value: string, field: string): Date {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    throw new Error(`${field} 不是有效的 ISO 日期时间: ${value}`)
  }
  return date
}

export function assertApsInterval(start: Date, end: Date, identity: string): void {
  if (end.getTime() <= start.getTime()) {
    throw new Error(`${identity} 的结束时间必须晚于开始时间`)
  }
}

export function assertUniqueApsIds(ids: readonly string[], kind: string): void {
  const seen = new Set<string>()
  for (const id of ids) {
    if (!id) {
      throw new Error(`${kind} ID 不能为空`)
    }
    if (seen.has(id)) {
      throw new Error(`${kind} ID 重复: ${id}`)
    }
    seen.add(id)
  }
}

/** 第三方时间轴把字符串当 HTML 使用，适配层统一转义业务文本。 */
export function escapeApsHtml(value: string): string {
  return value.replace(/[&<>'"]/g, (character) => {
    const entities: Record<string, string> = {
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      "'": '&#39;',
      '"': '&quot;'
    }
    return entities[character]
  })
}
