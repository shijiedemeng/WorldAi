export interface RequirementLink {
  id: number
  requirementNo: string
  linkType: 'BACKEND' | 'FRONTEND' | 'API' | 'DB' | 'TEST' | 'DOCS' | 'DEPLOYMENT' | 'REVIEW'
  taskTitle: string
  taskDesc?: string
  agentCode: string
  developerName?: string
  status: 'TODO' | 'DOING' | 'DONE' | 'BLOCKED' | 'SKIPPED'
  resultSummary?: string
  executionDetails?: string
  deliverablePath?: string
  dependsOnLinkId?: number
  startedAt?: string
  finishedAt?: string
  createdAt: string
  updatedAt: string
}

export interface CreateRequirementLinkPayload {
  linkType: RequirementLink['linkType']
  taskTitle: string
  taskDesc?: string
  agentCode: string
  developerName?: string
  status: RequirementLink['status']
  dependsOnLinkId?: number
}

export interface UpdateLinkProgressPayload {
  status: RequirementLink['status']
  resultSummary?: string
  executionDetails?: string
  deliverablePath?: string
  startedAt?: string
  finishedAt?: string
}
