export type ImagePromptTemplateType = 'POSITIVE' | 'NEGATIVE'
export type ImageGenerationType = 'TEXT_TO_IMAGE' | 'IMAGE_EDIT'
export type ImageGenerationStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'

export interface ImagePromptTemplate {
  id?: number
  templateCode: string
  templateName: string
  templateType: ImagePromptTemplateType
  contentText: string
  enabledFlag: boolean
  sortOrder: number
  createdAt?: string
  updatedAt?: string
}

export interface SaveImagePromptTemplatePayload {
  templateCode: string
  templateName: string
  templateType: ImagePromptTemplateType
  contentText: string
  enabledFlag?: boolean
  sortOrder?: number
}

export interface ImagePromptTemplateQuery {
  templateType?: string
  enabledFlag?: boolean
  keyword?: string
  page?: number
  pageSize?: number
}

export interface ImagePromptTemplatePage {
  items: ImagePromptTemplate[]
  page: number
  pageSize: number
  total: number
  totalPages: number
}

export interface ImageGenerationRecord {
  id: number
  generationType: ImageGenerationType
  aiSettingKey: string
  providerName?: string
  modelName?: string
  promptTemplateCodes?: string
  negativeTemplateCodes?: string
  positivePromptText?: string
  negativePromptText?: string
  finalPrompt: string
  imageSize?: string
  quality?: string
  outputFormat?: string
  background?: string
  moderation?: string
  responseFormat?: string
  inputFidelity?: string
  userText?: string
  imageCount?: number
  sourceImageFileName?: string
  sourceImagePath?: string
  sourceImageContentType?: string
  sourceImageSize?: number
  sourceImageUrl?: string
  maskImageFileName?: string
  maskImagePath?: string
  maskImageContentType?: string
  maskImageSize?: number
  maskImageUrl?: string
  resultFileName?: string
  resultFilePath?: string
  resultFileContentType?: string
  resultFileSize?: number
  resultFileUrl?: string
  status: ImageGenerationStatus
  requestPayload?: string
  responsePayload?: string
  errorMessage?: string
  startedAt?: string
  finishedAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface ImageGenerationRecordQuery {
  status?: string
  generationType?: string
  aiSettingKey?: string
  keyword?: string
  page?: number
  pageSize?: number
}

export interface ImageGenerationRecordPage {
  items: ImageGenerationRecord[]
  page: number
  pageSize: number
  total: number
  totalPages: number
}

export interface CreateImageGenerationPayload {
  aiSettingKey: string
  promptTemplateCodes?: string[]
  negativeTemplateCodes?: string[]
  positivePromptLines?: string[]
  negativePromptLines?: string[]
  size?: string
  quality?: string
  outputFormat?: string
  background?: string
  moderation?: string
  responseFormat?: string
  n?: number
  user?: string
}

export interface CreateImageEditPayload extends CreateImageGenerationPayload {
  inputFidelity?: string
  image: File
  mask?: File
}
