import request from './request'
import type {
  ConfirmProjectKnowledgePayload,
  CreateKnowledgeFromRequirementPayload,
  OrganizeProjectKnowledgePayload,
  ProjectKnowledge,
  ProjectKnowledgePage,
  ProjectKnowledgeQuery,
  ProjectKnowledgeSearchResponse,
  SaveProjectKnowledgePayload,
  SearchProjectKnowledgePayload,
} from '@/types/projectKnowledge'

export function fetchProjectKnowledgePage(query?: ProjectKnowledgeQuery) {
  return request.get<never, ProjectKnowledgePage>('/project-knowledge', { params: query })
}

export function fetchProjectKnowledge(id: number) {
  return request.get<never, ProjectKnowledge>(`/project-knowledge/${id}`)
}

export function fetchProjectKnowledgeByRequirement(requirementNo: string) {
  return request.get<never, ProjectKnowledge[]>(`/project-knowledge/by-requirement/${encodeURIComponent(requirementNo)}`)
}

export function createProjectKnowledge(payload: SaveProjectKnowledgePayload) {
  return request.post<never, ProjectKnowledge>('/project-knowledge', payload)
}

export function createProjectKnowledgeFromRequirement(requirementNo: string, payload: CreateKnowledgeFromRequirementPayload) {
  return request.post<never, ProjectKnowledge>(`/project-knowledge/from-requirements/${encodeURIComponent(requirementNo)}`, payload)
}

export function updateProjectKnowledge(id: number, payload: SaveProjectKnowledgePayload) {
  return request.put<never, ProjectKnowledge>(`/project-knowledge/${id}`, payload)
}

export function organizeProjectKnowledge(id: number, payload: OrganizeProjectKnowledgePayload) {
  return request.post<never, ProjectKnowledge>(`/project-knowledge/${id}/organize`, payload, {
    timeout: 180000,
  })
}

export function confirmProjectKnowledge(id: number, payload: ConfirmProjectKnowledgePayload) {
  return request.post<never, ProjectKnowledge>(`/project-knowledge/${id}/confirm`, payload, {
    timeout: 180000,
  })
}

export function searchSimilarProjectKnowledge(id: number, embeddingSettingKey?: string, limit = 5) {
  return request.post<never, ProjectKnowledgeSearchResponse>(`/project-knowledge/${id}/similar`, undefined, {
    params: { embeddingSettingKey, limit },
    timeout: 120000,
  })
}

export function searchProjectKnowledge(payload: SearchProjectKnowledgePayload) {
  return request.post<never, ProjectKnowledgeSearchResponse>('/project-knowledge/search', payload, {
    timeout: 120000,
  })
}

export function deleteProjectKnowledge(id: number) {
  return request.delete<never, void>(`/project-knowledge/${id}`)
}
