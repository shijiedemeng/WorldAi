export type AiModelPurpose = 'LANGUAGE' | 'VECTOR' | 'IMAGE'

export interface AiModelSetting {
  id?: number
  settingKey: string
  providerName: string
  baseUrl?: string
  apiKey?: string
  modelName: string
  modelPurpose?: AiModelPurpose
  vectorChunkSize?: number
  vectorChunkOverlap?: number
  supportImageFlag: boolean
  promptTemplate?: string
  enabledFlag: boolean
  createdAt?: string
  updatedAt?: string
}

export interface AiModelSettingQuery {
  supportImageFlag?: boolean
  modelPurpose?: AiModelPurpose
  enabledFlag?: boolean
}
