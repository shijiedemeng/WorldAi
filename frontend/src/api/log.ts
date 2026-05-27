import request from './request'
import type { AiAnalysisLog, AiAnalysisLogPage, AiAnalysisLogQuery } from '@/types/log'

export function fetchAiAnalysisLogs(query?: AiAnalysisLogQuery) {
  return request.get<never, AiAnalysisLogPage>('/logs/ai-analysis', {
    params: query,
  })
}

export function fetchAiAnalysisLogDetail(id: number) {
  return request.get<never, AiAnalysisLog>(`/logs/ai-analysis/${id}`)
}
