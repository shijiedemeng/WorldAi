import type { AgentRoleValue } from '@/types/agent'

export type ProjectMarkdownSyncMode = 'OVERWRITE' | 'CANCEL'
export type ProjectMarkdownBaseSyncMode = 'INDEPENDENT' | 'AUTO_UPDATE'
export type ProjectMarkdownFileType = 'BASE' | 'EXTRA'
export type ProjectMarkdownBaseKey = 'AGENTS' | 'SOUL' | 'USER' | 'MEMORY' | 'LEARNINGS'
export type ProjectMarkdownRoleScope = AgentRoleValue | 'COMMON'
export type ProjectDocumentUsage =
  | 'REQUIREMENT_AI_ANALYSIS'
  | 'DEFECT_AI_ANALYSIS'
  | 'AGENT_COMMON'
  | 'AGENT_MAIN'
  | 'AGENT_DEVELOPER'
  | 'AGENT_TESTER'
  | 'AGENT_OPS'
  | 'AGENT_REVIEWER'

export interface Project {
  id: number
  projectCode: string
  projectName: string
  projectDesc?: string
  businessGoal?: string
  techStack?: string
  repositoryUrl?: string
  ownerAgentCode?: string
  fileSearchMcpAgentCodes?: string
  markdownSyncMode: ProjectMarkdownSyncMode
  baseMarkdownSyncMode: ProjectMarkdownBaseSyncMode
  baseMarkdownAllowClientUpload: boolean
  status: 'ENABLED' | 'DISABLED'
  createdAt: string
  updatedAt: string
}

export interface ProjectMarkdownFile {
  id?: number
  agentRole?: ProjectMarkdownRoleScope | null
  fileType: ProjectMarkdownFileType
  baseKey?: ProjectMarkdownBaseKey
  filePath: string
  content: string
  versionNo?: number
  lastSyncSource?: string
  createdAt?: string
  updatedAt?: string
}

export interface ProjectMarkdownConfig {
  projectCode: string
  markdownSyncMode: ProjectMarkdownSyncMode
  baseMarkdownSyncMode: ProjectMarkdownBaseSyncMode
  baseMarkdownAllowClientUpload: boolean
  markdownFiles: ProjectMarkdownFile[]
  documentLinks: ProjectMarkdownDocumentLink[]
}

export interface CreateProjectPayload {
  projectCode: string
  projectName: string
  projectDesc?: string
  businessGoal?: string
  techStack?: string
  repositoryUrl?: string
  ownerAgentCode?: string
  fileSearchMcpAgentCodes?: string
  markdownSyncMode: ProjectMarkdownSyncMode
  baseMarkdownSyncMode: ProjectMarkdownBaseSyncMode
  baseMarkdownAllowClientUpload: boolean
  markdownFiles?: ProjectMarkdownFile[]
  status: 'ENABLED' | 'DISABLED'
}

export interface UpdateProjectPayload {
  projectName: string
  projectDesc?: string
  businessGoal?: string
  techStack?: string
  repositoryUrl?: string
  ownerAgentCode?: string
  fileSearchMcpAgentCodes?: string
  markdownSyncMode: ProjectMarkdownSyncMode
  baseMarkdownSyncMode: ProjectMarkdownBaseSyncMode
  baseMarkdownAllowClientUpload: boolean
  status: 'ENABLED' | 'DISABLED'
}

export interface SaveProjectMarkdownConfigPayload {
  markdownSyncMode: ProjectMarkdownSyncMode
  baseMarkdownSyncMode: ProjectMarkdownBaseSyncMode
  baseMarkdownAllowClientUpload: boolean
  markdownFiles: ProjectMarkdownFile[]
  documentLinks?: SaveProjectMarkdownDocumentLinkPayload[]
}

export interface ProjectMarkdownFileHistory {
  id: number
  projectCode: string
  agentRole?: ProjectMarkdownRoleScope | null
  baseKey: ProjectMarkdownBaseKey
  filePath: string
  content: string
  versionNo: number
  changeSource: 'SERVER' | 'CLIENT' | 'ROLLBACK'
  changeDesc?: string
  createdAt?: string
  updatedAt?: string
}

export interface ProjectMarkdownDocumentLink {
  id?: number
  usageType: ProjectDocumentUsage
  documentId: string
  title: string
  type?: string
  status?: string
  sortNo: number
  createdAt?: string
  updatedAt?: string
}

export interface SaveProjectMarkdownDocumentLinkPayload {
  usageType: ProjectDocumentUsage
  documentId: string
}
