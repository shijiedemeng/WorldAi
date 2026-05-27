import request from './request'
import type {
  AnalyzeRequirementPayload,
  CreateRequirementPayload,
  Requirement,
  RequirementTreeNode,
  RequirementWorkflow,
  RequirementWorkflowResult,
  SaveRequirementWorkflowPayload,
  UpdateRequirementPayload,
  UpdateRequirementDescriptionPayload,
  UpdateRequirementStatusPayload,
} from '@/types/requirement'

export function fetchRequirements(projectCode?: string) {
  return request.get<never, Requirement[]>('/requirements', {
    params: projectCode ? { projectCode } : undefined,
  })
}

export function fetchRequirement(requirementNo: string) {
  return request.get<never, Requirement>(`/requirements/${requirementNo}`)
}

export function fetchRequirementTree(projectCode?: string) {
  return request.get<never, RequirementTreeNode[]>('/requirements/tree', {
    params: projectCode ? { projectCode } : undefined,
  })
}

export function fetchRequirementChildren(masterRequirementNo: string) {
  return request.get<never, Requirement[]>(`/requirements/${masterRequirementNo}/children`)
}

export function fetchRequirementWorkflowResults(requirementNo: string) {
  return request.get<never, RequirementWorkflowResult>(`/requirements/${requirementNo}/workflow-results`)
}

export function fetchRequirementWorkflow(requirementNo: string) {
  return request.get<never, RequirementWorkflow>(`/requirements/${requirementNo}/workflow`)
}

export function saveRequirementWorkflow(requirementNo: string, payload: SaveRequirementWorkflowPayload) {
  return request.put<never, RequirementWorkflow>(`/requirements/${requirementNo}/workflow`, payload)
}

export function dispatchReadyWorkflowTasks(requirementNo: string) {
  return request.post<never, unknown[]>(`/requirements/${requirementNo}/workflow/dispatch-ready`)
}

export function createRequirement(payload: CreateRequirementPayload) {
  return request.post<never, Requirement>('/requirements', payload)
}

export function createMasterRequirement(payload: CreateRequirementPayload) {
  return request.post<never, Requirement>('/requirements/masters', payload)
}

export function createChildRequirement(masterRequirementNo: string, payload: CreateRequirementPayload) {
  return request.post<never, Requirement>(`/requirements/${masterRequirementNo}/children`, payload)
}

export function updateRequirement(requirementNo: string, payload: UpdateRequirementPayload) {
  return request.put<never, Requirement>(`/requirements/${requirementNo}`, payload)
}

export function updateRequirementDescription(requirementNo: string, payload: UpdateRequirementDescriptionPayload) {
  return request.put<never, Requirement>(`/requirements/${requirementNo}/description`, payload)
}

export function updateRequirementStatus(
  requirementNo: string,
  payload: UpdateRequirementStatusPayload,
) {
  return request.put<never, Requirement>(`/requirements/${requirementNo}/status`, payload)
}

export function resetRequirementExecution(requirementNo: string) {
  return request.post<never, Requirement>(`/requirements/${requirementNo}/reset-execution`)
}

export function approveRequirementReview(requirementNo: string) {
  return request.post<never, Requirement>(`/requirements/${requirementNo}/review-approve`)
}

export function deleteRequirement(requirementNo: string) {
  return request.delete<never, void>(`/requirements/${requirementNo}`)
}

export function analyzeRequirement(requirementNo: string, payload: AnalyzeRequirementPayload) {
  return request.post<never, Requirement>(`/requirements/${requirementNo}/ai-analysis`, payload, {
    timeout: 120000,
  })
}
