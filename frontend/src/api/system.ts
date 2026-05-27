import request from './request'
import type { AiModelSetting, AiModelSettingQuery } from '@/types/system'

export function fetchAiSettings(query?: AiModelSettingQuery) {
  return request.get<never, AiModelSetting[]>('/system/ai-settings', {
    params: query,
  })
}

export function saveAiSetting(payload: AiModelSetting) {
  return request.post<never, AiModelSetting>('/system/ai-settings', payload)
}
