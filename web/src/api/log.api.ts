import request, { get } from '@/utils/request'
import type { PageResult } from '@/types/common'
import type { LogEntry, LogQuery } from '@/types/log.types'

export function listLogs(query: LogQuery): Promise<PageResult<LogEntry>> {
  return get<PageResult<LogEntry>>('/logs', query)
}

/** 导出筛选后的审计日志 CSV。 */
export function exportLogs(query: Omit<LogQuery, 'page' | 'size'>): Promise<Blob> {
  return request.get('/logs/export', {
    params: query,
    responseType: 'blob',
  }) as Promise<Blob>
}
