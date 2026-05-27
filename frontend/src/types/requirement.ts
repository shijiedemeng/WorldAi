export type RequirementTypeValue = 'MASTER' | 'SUB'
export type SessionStrategyValue = 'NEW' | 'REUSE_SELECTED' | 'REUSE_LATEST'
export type RequirementExecutionModeValue = 'NORMAL' | 'WORKFLOW'

export interface Requirement {
  id: number
  requirementNo: string
  projectCode: string
  title: string
  requirementDesc?: string
  priority?: string
  requirementType: RequirementTypeValue
  parentRequirementNo?: string
  rootRequirementNo?: string
  sortNo?: number
  status: 'PENDING' | 'ANALYZING' | 'IN_PROGRESS' | 'TESTING' | 'BLOCKED' | 'DONE' | 'CLOSED'
  source?: string
  mainAgentCode?: string
  sessionStrategy?: SessionStrategyValue
  preferredSessionCode?: string
  currentStage?: string
  expectedDeadline?: string
  createdBy?: string
  executionSteps?: string
  autoExecuteFlag?: boolean
  fileSearchMcpAgentCodes?: string
  mcpFileSearchEnabledFlag?: boolean
  documentIds?: string
  projectKnowledgeSearchEnabledFlag?: boolean
  projectKnowledgeSearchLimit?: number
  projectKnowledgeSearchMinScore?: number
  executionMode?: RequirementExecutionModeValue
  resultExtractableFlag?: boolean
  reviewRequiredFlag?: boolean
  reviewApprovedFlag?: boolean
  executionMarker?: string
  executionClientCode?: string
  executionCommandId?: string
  executionSessionId?: string
  executionAgentCode?: string
  executionAcpFlag?: boolean
  createdAt: string
  updatedAt: string
}

export interface CreateRequirementPayload {
  requirementNo: string
  projectCode: string
  title: string
  requirementDesc?: string
  priority?: string
  requirementType?: RequirementTypeValue
  sortNo?: number
  status: Requirement['status']
  source?: string
  mainAgentCode?: string
  sessionStrategy?: SessionStrategyValue
  preferredSessionCode?: string
  currentStage?: string
  expectedDeadline?: string
  createdBy?: string
  executionSteps?: string
  autoExecuteFlag?: boolean
  fileSearchMcpAgentCodes?: string
  mcpFileSearchEnabledFlag?: boolean
  documentIds?: string
  projectKnowledgeSearchEnabledFlag?: boolean
  projectKnowledgeSearchLimit?: number
  projectKnowledgeSearchMinScore?: number
  executionMode?: RequirementExecutionModeValue
  resultExtractableFlag?: boolean
  reviewRequiredFlag?: boolean
}

export interface UpdateRequirementPayload {
  title: string
  requirementDesc?: string
  priority?: string
  sortNo?: number
  status: Requirement['status']
  source?: string
  mainAgentCode?: string
  sessionStrategy?: SessionStrategyValue
  preferredSessionCode?: string
  currentStage?: string
  expectedDeadline?: string
  createdBy?: string
  executionSteps?: string
  autoExecuteFlag?: boolean
  fileSearchMcpAgentCodes?: string
  mcpFileSearchEnabledFlag?: boolean
  documentIds?: string
  projectKnowledgeSearchEnabledFlag?: boolean
  projectKnowledgeSearchLimit?: number
  projectKnowledgeSearchMinScore?: number
  executionMode?: RequirementExecutionModeValue
  resultExtractableFlag?: boolean
  reviewRequiredFlag?: boolean
}

export interface UpdateRequirementDescriptionPayload {
  requirementDesc?: string
}

export interface UpdateRequirementStatusPayload {
  status: Requirement['status']
}

export interface AnalyzeRequirementPayload {
  aiSettingKey?: string
  promptText?: string
}

export interface RequirementTreeNode {
  requirement: Requirement
  children: RequirementTreeNode[]
}

export interface RequirementWorkflowResultItem {
  requirementNo: string
  title: string
  agentCode?: string
  requirementStatus: Requirement['status']
  linkId?: number
  linkStatus?: string
  resultSummary?: string
  executionDetails?: string
  deliverablePath?: string
  finishedAt?: string
}

export interface RequirementWorkflowResult {
  rootRequirementNo: string
  requirementNo: string
  items: RequirementWorkflowResultItem[]
}

export interface RequirementWorkflowEdge {
  id?: number
  fromRequirementNo: string
  toRequirementNo: string
}

export interface RequirementWorkflow {
  rootRequirementNo: string
  nodes: Requirement[]
  edges: RequirementWorkflowEdge[]
  startRequirementNos: string[]
  readyRequirementNos: string[]
}

export interface SaveRequirementWorkflowPayload {
  edges: RequirementWorkflowEdge[]
}
