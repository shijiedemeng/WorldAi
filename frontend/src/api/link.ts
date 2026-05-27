import request from './request'
import type {
  CreateRequirementLinkPayload,
  RequirementLink,
  UpdateLinkProgressPayload,
} from '@/types/link'

export function fetchRequirementLinks(requirementNo: string) {
  return request.get<never, RequirementLink[]>(`/requirements/${requirementNo}/links`)
}

export function fetchRequirementLinksWithChildren(requirementNo: string) {
  return request.get<never, RequirementLink[]>(`/requirements/${requirementNo}/links`, {
    params: { includeChildren: true },
  })
}

export function createRequirementLink(
  requirementNo: string,
  payload: CreateRequirementLinkPayload,
) {
  return request.post<never, RequirementLink>(`/requirements/${requirementNo}/links`, payload)
}

export function updateLinkProgress(linkId: number, payload: UpdateLinkProgressPayload) {
  return request.put<never, RequirementLink>(`/links/${linkId}/progress`, payload)
}
