import type { RequirementExecutionModeValue } from './requirement'

export type DefectPlatformType = 'ZENTAO' | 'YUNXIAO'
export type DefectPushStatus = 'DRAFT' | 'ANALYZING' | 'ANALYZED' | 'PUSHED'

export interface DefectSourceConfig {
  id?: number
  sourceCode: string
  sourceName: string
  projectCode: string
  platformType: DefectPlatformType
  baseUrl: string
  username?: string
  passwordValue?: string
  accessToken?: string
  enabledFlag: boolean
  extraConfig?: string
  externalProjectKey: string
  externalProjectName: string
  createdAt?: string
  updatedAt?: string
}

export interface RemoteDefect {
  sourceCode: string
  projectCode: string
  accountCode: string
  platformType: DefectPlatformType
  persistedRecordId?: number
  externalDefectId: string
  externalDefectKey?: string
  title: string
  severity?: string
  defectStatusId?: string
  defectStatus?: string
  defectType?: string
  assignedTo?: string
  reporterName?: string
  openedAt?: string
  updatedAtRemote?: string
  hasImageFlag: boolean
  tags?: string
  summary?: string
  descriptionText?: string
  comments: DefectComment[]
  attachments: DefectAttachment[]
}

export interface RemoteDefectPage {
  items: RemoteDefect[]
  page: number
  pageSize: number
  total: number
  totalPages: number
}

export interface DefectComment {
  id?: number
  externalCommentId?: string
  authorName?: string
  commentContent?: string
  commentedAt?: string
}

export interface DefectAttachment {
  externalAttachmentId?: string
  fileId?: string
  fileName?: string
  suffix?: string
  size?: number
  url?: string
  creatorName?: string
  createdAt?: string
}

export interface YunxiaoOrganization {
  id: string
  name?: string
  description?: string
  creatorId?: string
  defaultRole?: string
  createdAt?: string
  updatedAt?: string
}

export interface FetchYunxiaoOrganizationsPayload {
  baseUrl: string
  accessToken: string
  userId?: string
}

export interface FetchYunxiaoProjectsPayload {
  baseUrl: string
  accessToken: string
  organizationId: string
}

export interface FetchYunxiaoProjectMembersPayload {
  baseUrl: string
  accessToken: string
  organizationId: string
  projectId: string
  name?: string
  roleId?: string
}

export interface DefectRemoteListQuery {
  page?: number
  pageSize?: number
  keyword?: string
  status?: string
  severity?: string
  assignedTo?: string
  reporterName?: string
  tag?: string
  orderBy?: string
  sort?: string
}

export interface YunxiaoProject {
  id: string
  name?: string
  description?: string
  customCode?: string
  scope?: string
  creatorId?: string
  creatorName?: string
  modifierId?: string
  modifierName?: string
  statusId?: string
  statusName?: string
  createdAt?: string
  updatedAt?: string
}

export interface YunxiaoProjectMember {
  roleId?: string
  roleName?: string
  userAvatar?: string
  userId: string
  userName?: string
}

export interface DefectAnalysis {
  id?: number
  analysisNo: string
  projectCode: string
  defectRecordId?: number
  sourceCode?: string
  externalDefectId?: string
  externalDefectKey?: string
  title?: string
  aiSettingKey?: string
  agentScope?: string
  promptText?: string
  editedSummary?: string
  analysisResult?: string
  nextRequirementNo?: string
  pushStatus: DefectPushStatus
  pushedAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface SaveDefectSourceConfigPayload {
  sourceCode: string
  sourceName: string
  projectCode: string
  platformType: DefectPlatformType
  baseUrl: string
  username?: string
  passwordValue?: string
  accessToken?: string
  enabledFlag: boolean
  extraConfig?: string
  externalProjectKey: string
  externalProjectName: string
}

export interface AnalyzeDefectPayload {
  analysisNo: string
  projectCode: string
  defectRecordId?: number
  sourceCode?: string
  externalDefectId?: string
  externalDefectKey?: string
  title?: string
  severity?: string
  defectStatus?: string
  defectType?: string
  assignedTo?: string
  reporterName?: string
  summary?: string
  descriptionText?: string
  aiSettingKey?: string
  agentScope?: string
  promptText?: string
  editedSummary?: string
}

export interface PushDefectToRequirementPayload {
  requirementNo?: string
  title: string
  executionMode: RequirementExecutionModeValue
  mainAgentCode?: string
  executionSteps?: string
}
