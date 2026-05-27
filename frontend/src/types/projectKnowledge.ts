export type ProjectKnowledgeTypeValue = 'COMMON_ISSUE' | 'PROCESS_GUIDE'
export type ProjectKnowledgeStatusValue = 'DRAFT' | 'AI_ORGANIZING' | 'AI_READY' | 'CONFIRMED'
export type ProjectKnowledgeVectorStatusValue = 'PENDING' | 'READY' | 'STALE' | 'FAILED'

export interface ProjectKnowledge {
  id: number
  projectCode: string
  knowledgeType: ProjectKnowledgeTypeValue
  title: string
  simpleDesc?: string
  detailContent?: string
  organizedContent?: string
  confirmedContent?: string
  sourceRequirementNo?: string
  sourceSummary?: string
  aiSettingKey?: string
  embeddingSettingKey?: string
  documentIds?: string
  selectedSimilarKnowledgeId?: number
  selectedSimilarScore?: number
  status: ProjectKnowledgeStatusValue
  vectorStatus: ProjectKnowledgeVectorStatusValue
  vectorDirtyFlag: boolean
  vectorCollection?: string
  vectorId?: string
  vectorChunkCount?: number
  vectorFilePath?: string
  vectorUpdatedAt?: string
  errorMessage?: string
  createdAt: string
  updatedAt: string
}

export interface ProjectKnowledgePage {
  items: ProjectKnowledge[]
  page: number
  pageSize: number
  total: number
  totalPages: number
}

export interface SaveProjectKnowledgePayload {
  projectCode?: string
  knowledgeType: ProjectKnowledgeTypeValue
  title: string
  simpleDesc?: string
  detailContent?: string
  organizedContent?: string
  confirmedContent?: string
  aiSettingKey?: string
  embeddingSettingKey?: string
  documentIds?: string
  selectedSimilarKnowledgeId?: number
}

export interface CreateKnowledgeFromRequirementPayload {
  knowledgeType: ProjectKnowledgeTypeValue
  title: string
  simpleDesc?: string
  aiSettingKey?: string
  embeddingSettingKey?: string
  documentIds?: string
  receiptRequirementNos?: string
  selectedSimilarKnowledgeId?: number
}

export interface OrganizeProjectKnowledgePayload {
  aiSettingKey?: string
  documentIds?: string
  selectedSimilarKnowledgeId?: number
  extraPrompt?: string
}

export interface ConfirmProjectKnowledgePayload {
  confirmedContent?: string
  embeddingSettingKey?: string
}

export interface ProjectKnowledgeQuery {
  projectCode?: string
  knowledgeType?: ProjectKnowledgeTypeValue
  status?: ProjectKnowledgeStatusValue
  vectorStatus?: ProjectKnowledgeVectorStatusValue
  keyword?: string
  page?: number
  pageSize?: number
}

export interface ProjectKnowledgeSearchItem {
  id?: number
  title: string
  simpleDesc?: string
  content?: string
  knowledgeType?: ProjectKnowledgeTypeValue
  score?: number
  matchScore?: number
  bestChunkScore?: number
  matchedChunkCount?: number
}

export interface ProjectKnowledgeSearchResponse {
  projectCode: string
  query: string
  items: ProjectKnowledgeSearchItem[]
}

export interface SearchProjectKnowledgePayload {
  projectCode: string
  query: string
  knowledgeType?: ProjectKnowledgeTypeValue
  embeddingSettingKey?: string
  limit?: number
  minMatchScore?: number
}
