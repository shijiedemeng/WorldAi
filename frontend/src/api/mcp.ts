import request from './request'
import type { McpFileRequestPayload, McpFileResponse, McpFileTool } from '@/types/mcp'

export function fetchMcpFileTools(agentCode: string) {
  return request.get<never, McpFileTool[]>(`/mcp/agents/${encodeURIComponent(agentCode)}/tools`)
}

export function executeMcpFileTool(agentCode: string, payload: McpFileRequestPayload) {
  return request.post<never, McpFileResponse>(`/mcp/agents/${encodeURIComponent(agentCode)}/files`, payload, {
    timeout: 35000,
  })
}
