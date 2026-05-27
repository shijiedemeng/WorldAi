export type AgentRoleValue = 'MAIN' | 'DEVELOPER' | 'TESTER' | 'REVIEWER' | 'OPS'
export type AgentEngineTypeValue = 'CODEX' | 'QODER' | 'CLAUDE'
export type McpTransportProtocolValue = 'NONE' | 'SSE' | 'REST'

export interface Agent {
  id: number
  agentCode: string
  agentName: string
  projectCode?: string
  agentEngineType?: AgentEngineTypeValue
  agentRole: AgentRoleValue
  agentDesc?: string
  capabilityTags?: string
  supportedLinkTypes?: string
  skillCodes?: string[]
  callbackMode?: string
  endpointUrl?: string
  mcpTransportProtocol?: McpTransportProtocolValue
  status: 'ONLINE' | 'OFFLINE' | 'DISABLED'
  lastHeartbeatTime?: string
  createdAt: string
  updatedAt: string
}

export interface CreateAgentPayload {
  agentCode: string
  agentName: string
  projectCode: string
  agentEngineType: AgentEngineTypeValue
  agentRole: AgentRoleValue
  agentDesc?: string
  capabilityTags?: string
  supportedLinkTypes?: string
  skillCodes?: string[]
  callbackMode?: string
  endpointUrl?: string
  mcpTransportProtocol?: McpTransportProtocolValue
  status: Agent['status']
}

export interface UpdateAgentPayload {
  agentName: string
  projectCode: string
  agentEngineType: AgentEngineTypeValue
  agentRole: AgentRoleValue
  agentDesc?: string
  capabilityTags?: string
  supportedLinkTypes?: string
  skillCodes?: string[]
  callbackMode?: string
  endpointUrl?: string
  mcpTransportProtocol?: McpTransportProtocolValue
  status: Agent['status']
}
