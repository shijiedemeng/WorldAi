import request from './request'
import type { SaveSkillPayload, Skill, SkillPage, SkillPageQuery } from '@/types/skill'

export function fetchSkills(enabledFlag?: boolean) {
  return request.get<never, Skill[]>('/skills', {
    params: enabledFlag === undefined ? undefined : { enabledFlag },
  })
}

export function fetchSkillPage(query?: SkillPageQuery) {
  return request.get<never, SkillPage>('/skills/page', {
    params: query,
  })
}

export function fetchSkill(skillCode: string) {
  return request.get<never, Skill>(`/skills/${encodeURIComponent(skillCode)}`)
}

export function createSkill(payload: SaveSkillPayload) {
  return request.post<never, Skill>('/skills', payload)
}

export function createSkillPackage(payload: FormData) {
  return request.post<never, Skill>('/skills', payload)
}

export function updateSkill(skillCode: string, payload: SaveSkillPayload) {
  return request.put<never, Skill>(`/skills/${encodeURIComponent(skillCode)}`, payload)
}

export function deleteSkill(skillCode: string) {
  return request.delete<never, void>(`/skills/${encodeURIComponent(skillCode)}`)
}

export function uploadSkillArchive(skillCode: string, file: File) {
  const formData = new FormData()
  formData.append('file', file, file.name)
  return request.post<never, Skill>(`/skills/${encodeURIComponent(skillCode)}/archive`, formData)
}
