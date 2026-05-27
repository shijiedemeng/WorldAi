import request from './request'
import type { Agent, CreateAgentPayload, UpdateAgentPayload } from '@/types/agent'
import type { RequirementLink } from '@/types/link'

export function fetchAgents(projectCode?: string) {
  return request.get<never, Agent[]>('/agents', {
    params: projectCode ? { projectCode } : undefined,
  })
}

export function fetchAgent(agentCode: string) {
  return request.get<never, Agent>(`/agents/${agentCode}`)
}

export function createAgent(payload: CreateAgentPayload) {
  return request.post<never, Agent>('/agents', payload)
}

export function updateAgent(agentCode: string, payload: UpdateAgentPayload) {
  return request.put<never, Agent>(`/agents/${agentCode}`, payload)
}

export function deleteAgent(agentCode: string) {
  return request.delete<never, void>(`/agents/${agentCode}`)
}

export function heartbeatAgent(agentCode: string) {
  return request.post<never, Agent>(`/agents/${agentCode}/heartbeat`)
}

export function fetchAgentTasks(agentCode: string) {
  return request.get<never, RequirementLink[]>(`/agents/${agentCode}/tasks`)
}
