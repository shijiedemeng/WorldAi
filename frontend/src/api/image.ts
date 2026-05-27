import request from './request'
import type {
  CreateImageEditPayload,
  CreateImageGenerationPayload,
  ImageGenerationRecord,
  ImageGenerationRecordPage,
  ImageGenerationRecordQuery,
  ImagePromptTemplate,
  ImagePromptTemplatePage,
  ImagePromptTemplateQuery,
  SaveImagePromptTemplatePayload,
} from '@/types/image'

export function fetchImagePromptTemplates(query?: Omit<ImagePromptTemplateQuery, 'page' | 'pageSize'>) {
  return request.get<never, ImagePromptTemplate[]>('/image-prompt-templates', {
    params: query,
  })
}

export function fetchImagePromptTemplatePage(query?: ImagePromptTemplateQuery) {
  return request.get<never, ImagePromptTemplatePage>('/image-prompt-templates/page', {
    params: query,
  })
}

export function fetchImagePromptTemplate(templateCode: string) {
  return request.get<never, ImagePromptTemplate>(`/image-prompt-templates/${encodeURIComponent(templateCode)}`)
}

export function saveImagePromptTemplate(payload: SaveImagePromptTemplatePayload) {
  return request.post<never, ImagePromptTemplate>('/image-prompt-templates', payload)
}

export function deleteImagePromptTemplate(templateCode: string) {
  return request.delete<never, void>(`/image-prompt-templates/${encodeURIComponent(templateCode)}`)
}

export function fetchImageGenerationRecords(query?: ImageGenerationRecordQuery) {
  return request.get<never, ImageGenerationRecordPage>('/image-generations/page', {
    params: query,
  })
}

export function fetchImageGenerationRecord(recordId: number) {
  return request.get<never, ImageGenerationRecord>(`/image-generations/${recordId}`)
}

export function createImageGeneration(payload: CreateImageGenerationPayload) {
  return request.post<never, ImageGenerationRecord>('/image-generations', payload)
}

export function retryImageGeneration(recordId: number) {
  return request.post<never, ImageGenerationRecord>(`/image-generations/${recordId}/retry`)
}

export function createImageEdit(payload: CreateImageEditPayload) {
  const formData = new FormData()
  appendFormValue(formData, 'aiSettingKey', payload.aiSettingKey)
  appendArray(formData, 'promptTemplateCodes', payload.promptTemplateCodes)
  appendArray(formData, 'negativeTemplateCodes', payload.negativeTemplateCodes)
  appendArray(formData, 'positivePromptLines', payload.positivePromptLines)
  appendArray(formData, 'negativePromptLines', payload.negativePromptLines)
  appendFormValue(formData, 'size', payload.size)
  appendFormValue(formData, 'quality', payload.quality)
  appendFormValue(formData, 'outputFormat', payload.outputFormat)
  appendFormValue(formData, 'background', payload.background)
  appendFormValue(formData, 'moderation', payload.moderation)
  appendFormValue(formData, 'responseFormat', payload.responseFormat)
  appendFormValue(formData, 'inputFidelity', payload.inputFidelity)
  appendFormValue(formData, 'n', payload.n === undefined ? undefined : String(payload.n))
  appendFormValue(formData, 'user', payload.user)
  formData.append('image', payload.image, payload.image.name)
  if (payload.mask) {
    formData.append('mask', payload.mask, payload.mask.name)
  }
  return request.post<never, ImageGenerationRecord>('/image-generations/edits', formData)
}

function appendArray(formData: FormData, key: string, values?: string[]) {
  values?.filter(Boolean).forEach((value) => formData.append(key, value))
}

function appendFormValue(formData: FormData, key: string, value?: string) {
  if (value !== undefined && value !== null && value !== '') {
    formData.append(key, value)
  }
}
