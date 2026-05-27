import request from './request'
import type {
  DefectRemoteListQuery,
  AnalyzeDefectPayload,
  DefectAnalysis,
  DefectSourceConfig,
  FetchYunxiaoOrganizationsPayload,
  FetchYunxiaoProjectMembersPayload,
  FetchYunxiaoProjectsPayload,
  PushDefectToRequirementPayload,
  RemoteDefectPage,
  RemoteDefect,
  SaveDefectSourceConfigPayload,
  YunxiaoProject,
  YunxiaoProjectMember,
  YunxiaoOrganization,
} from '@/types/defect'

export function fetchDefectSources(projectCode?: string) {
  return request.get<never, DefectSourceConfig[]>('/defects/sources', { params: { projectCode } })
}

export function saveDefectSource(payload: SaveDefectSourceConfigPayload) {
  return request.post<never, DefectSourceConfig>('/defects/sources', payload)
}

export function fetchYunxiaoOrganizations(payload: FetchYunxiaoOrganizationsPayload) {
  return request.post<never, YunxiaoOrganization[]>('/defects/yunxiao/organizations', payload)
}

export function fetchYunxiaoProjects(payload: FetchYunxiaoProjectsPayload) {
  return request.post<never, YunxiaoProject[]>('/defects/yunxiao/projects', payload)
}

export function fetchYunxiaoProjectMembers(payload: FetchYunxiaoProjectMembersPayload) {
  return request.post<never, YunxiaoProjectMember[]>('/defects/yunxiao/project-members', payload)
}

export function fetchSourceYunxiaoMembers(sourceCode: string, params?: { name?: string; roleId?: string }) {
  return request.get<never, YunxiaoProjectMember[]>(`/defects/sources/${encodeURIComponent(sourceCode)}/yunxiao/members`, {
    params,
  })
}

export function fetchDefectSource(sourceCode: string) {
  return request.get<never, DefectSourceConfig>(`/defects/sources/${encodeURIComponent(sourceCode)}`)
}

export function fetchRemoteDefects(sourceCode: string, query?: DefectRemoteListQuery) {
  return request.get<never, RemoteDefectPage>(`/defects/sources/${encodeURIComponent(sourceCode)}/remote-defects`, {
    params: query,
  })
}

export function viewSourceDefects(sourceCode: string, query?: DefectRemoteListQuery) {
  return fetchRemoteDefects(sourceCode, query)
}

export function viewSourceDefectDetail(sourceCode: string, externalDefectId: string) {
  return request.get<never, RemoteDefect>(
    `/defects/sources/${encodeURIComponent(sourceCode)}/remote-defects/${encodeURIComponent(externalDefectId)}`,
  )
}

export function analyzeDefect(payload: AnalyzeDefectPayload) {
  return request.post<never, DefectAnalysis>('/defects/analyze', payload, {
    timeout: 120000,
  })
}

export function fetchDefectAnalyses(projectCode: string) {
  return request.get<never, DefectAnalysis[]>('/defects/analyses', { params: { projectCode } })
}

export function pushDefectToRequirement(analysisNo: string, payload: PushDefectToRequirementPayload) {
  return request.post<never, DefectAnalysis>(`/defects/analyses/${analysisNo}/push`, payload)
}
