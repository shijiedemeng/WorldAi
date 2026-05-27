export type AiAnalysisLogStatus = 'SUCCESS' | 'FAILED' | 'FALLBACK'
export type AiAnalysisLogSourceType = 'REQUIREMENT_AI_SPLIT' | 'DEFECT_AI_ANALYSIS' | 'PROJECT_KNOWLEDGE_ORGANIZE'

export interface AiAnalysisLog {
  id: number
  sourceType: AiAnalysisLogSourceType | string
  businessNo: string
  projectCode?: string
  aiSettingKey?: string
  modelName?: string
  status: AiAnalysisLogStatus | string
  requestPayload?: string
  responsePayload?: string
  errorMessage?: string
  createdAt?: string
  updatedAt?: string
}

export interface AiAnalysisLogPage {
  items: AiAnalysisLog[]
  page: number
  pageSize: number
  total: number
  totalPages: number
}

export interface AiAnalysisLogQuery {
  page?: number
  pageSize?: number
  sourceType?: string
  projectCode?: string
  businessNo?: string
  status?: string
  keyword?: string
}
