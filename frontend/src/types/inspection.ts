export interface InspectionLinkIssue {
  linkId: number
  linkType: string
  agentCode: string
  status: string
  summary?: string
}

export interface InspectionNotifyAgent {
  agentCode: string
  reason: string
}

export interface RequirementInspection {
  requirementNo: string
  title: string
  requirementStatus: string
  existingLinks: string[]
  missingItems: string[]
  incompleteLinks: InspectionLinkIssue[]
  blockedLinks: InspectionLinkIssue[]
  suggestedAgents: InspectionNotifyAgent[]
}
