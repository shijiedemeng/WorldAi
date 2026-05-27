import request from './request'
import type {
  ClientAgentSession,
  ClientCommand,
  ClientNode,
  CreateClientAgentSessionPayload,
  DispatchClientCommandPayload,
} from '@/types/client'

export function fetchClientNodes() {
  return request.get<never, ClientNode[]>('/clients')
}

export function fetchClientNode(clientCode: string) {
  return request.get<never, ClientNode>(`/clients/${clientCode}`)
}

export function fetchClientSessions(clientCode: string, agentCode?: string) {
  return request.get<never, ClientAgentSession[]>(`/clients/${clientCode}/sessions`, {
    params: agentCode ? { agentCode } : undefined,
  })
}

export function createClientSession(clientCode: string, payload: CreateClientAgentSessionPayload) {
  return request.post<never, ClientAgentSession>(`/clients/${clientCode}/sessions`, payload)
}

export function setDefaultClientSession(clientCode: string, sessionId: string) {
  return request.put<never, ClientAgentSession>(`/clients/${clientCode}/sessions/${sessionId}/default`)
}

export function dispatchClientCommand(payload: DispatchClientCommandPayload) {
  return request.post<never, ClientCommand>('/clients/dispatch', payload)
}

export function fetchClientCommands(clientCode: string) {
  return request.get<never, ClientCommand[]>(`/clients/${clientCode}/commands`)
}
