export type ClientOsTypeValue = 'MAC' | 'WINDOWS' | 'LINUX'
export type ClientNodeStatusValue = 'ONLINE' | 'OFFLINE' | 'DISABLED'
export type ConnectionProtocolValue = 'WEBSOCKET'
export type AgentRuntimeTypeValue = 'CODEX_CLI' | 'QODER_CLI' | 'CLAUDE_CODE'

export interface ClientAgentRuntime {
  id?: number
  agentType: AgentRuntimeTypeValue
  agentName: string
  agentVersion?: string
  commandPath?: string
  availableFlag: boolean
  capabilityTags?: string
  supportsSessionReuse: boolean
  defaultWorkspaceDir?: string
  lastProbeTime?: string
}

export interface ClientControllableAgent {
  id?: number
  agentCode: string
  agentName?: string
  projectCode?: string
  agentEngineType?: string
  agentRole?: string
  mcpTransportProtocol?: string
  status?: string
  enabledFlag: boolean
  skillCodes?: string[]
  skillsDir?: string
  workspaceDir?: string
  workerCommand?: string
  lastSeenTime?: string
}

export interface ClientUnlinkedAgent {
  agentCode: string
  enabledFlag: boolean
  skillsDir?: string
  workspaceDir?: string
  workerCommand?: string
  reason?: string
  lastSeenTime?: string
}

export interface ClientNode {
  id: number
  clientCode: string
  clientName: string
  osType: ClientOsTypeValue
  hostName?: string
  ipAddress?: string
  connectionProtocol: ConnectionProtocolValue
  connectionSessionId?: string
  status: ClientNodeStatusValue
  lastHeartbeatTime?: string
  supportedAgentTypes?: string
  appVersion?: string
  mcpEnabled?: boolean
  mcpServerUrl?: string
  runtimes: ClientAgentRuntime[]
  agents: ClientControllableAgent[]
  unlinkedAgents: ClientUnlinkedAgent[]
  createdAt: string
  updatedAt: string
}

export interface ClientSessionEvent {
  eventId: string
  direction: 'IN' | 'OUT' | string
  eventType: string
  payload?: string
  createdAt: string
}

export interface ClientAgentSession {
  requestId?: string
  sessionId?: string
  sessionCode?: string
  clientCode: string
  agentCode: string
  sessionName: string
  sessionType: string
  runtimeType?: string
  defaultFlag: boolean
  status: string
  workspaceDir?: string
  errorMessage?: string
  initializedSkills?: unknown[]
  events?: ClientSessionEvent[]
  createdAt: string
  updatedAt: string
}

export interface CreateClientAgentSessionPayload {
  agentCode: string
  sessionName?: string
  sessionType?: string
  runtimeType?: string
  defaultFlag?: boolean
  workspaceDir?: string
}

export type ClientCommandStatusValue =
  | 'CREATED'
  | 'DISPATCHED'
  | 'ACKED'
  | 'RUNNING'
  | 'SUCCESS'
  | 'FAILED'
  | 'BLOCKED'
  | 'CANCELLED'
  | 'TIMEOUT'

export interface ClientCommand {
  commandId: string
  clientCode: string
  agentCode: string
  sessionId?: string
  sessionCode?: string
  requirementNo: string
  linkId?: number
  title: string
  prompt: string
  status: ClientCommandStatusValue
  resultSummary?: string
  executionDetails?: string
  deliverablePath?: string
  createdAt: string
  startedAt?: string
  finishedAt?: string
  updatedAt: string
}

export interface DispatchClientCommandPayload {
  clientCode: string
  agentCode: string
  sessionId?: string
  requirementNo: string
  linkId?: number
  title?: string
  prompt?: string
}
