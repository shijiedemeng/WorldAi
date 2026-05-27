import request from './request'
import type { AgentSession, FetchAgentSessionsParams } from '@/types/session'

export function fetchAgentSessions(params?: FetchAgentSessionsParams) {
  return request.get<never, AgentSession[]>('/sessions', { params })
}
