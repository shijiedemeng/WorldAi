export type AgentRuntimeTypeValue = 'CODEX_CLI' | 'QODER_CLI' | 'CLAUDE_CODE'

export type AgentSessionStatusValue = 'IDLE' | 'RUNNING' | 'BLOCKED' | 'CLOSED' | 'ARCHIVED'

export interface AgentSession {
  id: number
  sessionCode: string
  sessionName?: string
  sessionType: AgentRuntimeTypeValue
  projectCode: string
  requirementNo?: string
  rootRequirementNo?: string
  agentCode: string
  clientCode: string
  externalSessionId?: string
  status: AgentSessionStatusValue
  reusableFlag: boolean
  lastActiveTime?: string
  createdAt: string
  updatedAt: string
}

export interface FetchAgentSessionsParams {
  projectCode?: string
  agentCode?: string
  clientCode?: string
  rootRequirementNo?: string
  reusableFlag?: boolean
  status?: AgentSessionStatusValue
}
