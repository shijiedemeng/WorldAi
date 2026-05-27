import request from './request'
import type {
  CreateProjectPayload,
  Project,
  ProjectMarkdownConfig,
  ProjectMarkdownFile,
  ProjectMarkdownFileHistory,
  SaveProjectMarkdownConfigPayload,
  UpdateProjectPayload,
} from '@/types/project'

export function fetchProjects() {
  return request.get<never, Project[]>('/projects')
}

export function fetchProject(projectCode: string) {
  return request.get<never, Project>(`/projects/${projectCode}`)
}

export function createProject(payload: CreateProjectPayload) {
  return request.post<never, Project>('/projects', payload)
}

export function updateProject(projectCode: string, payload: UpdateProjectPayload) {
  return request.put<never, Project>(`/projects/${projectCode}`, payload)
}

export function deleteProject(projectCode: string) {
  return request.delete<never, void>(`/projects/${projectCode}`)
}

export function fetchProjectMarkdownConfig(projectCode: string) {
  return request.get<never, ProjectMarkdownConfig>(`/projects/${projectCode}/markdown-config`)
}

export function saveProjectMarkdownConfig(projectCode: string, payload: SaveProjectMarkdownConfigPayload) {
  return request.put<never, ProjectMarkdownConfig>(`/projects/${projectCode}/markdown-config`, payload)
}

export function fetchProjectMarkdownHistory(projectCode: string, fileId: number) {
  return request.get<never, ProjectMarkdownFileHistory[]>(`/projects/${projectCode}/markdown-files/${fileId}/history`)
}

export function rollbackProjectMarkdownFile(projectCode: string, fileId: number, historyId: number) {
  return request.post<never, ProjectMarkdownFile>(`/projects/${projectCode}/markdown-files/${fileId}/rollback/${historyId}`)
}
