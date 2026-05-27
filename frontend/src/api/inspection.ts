import request from './request'
import type { RequirementInspection } from '@/types/inspection'

export function fetchRequirementInspection(requirementNo: string) {
  return request.get<never, RequirementInspection>(`/requirements/${requirementNo}/inspection`)
}
