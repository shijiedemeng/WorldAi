import request from './request'
import type { CreateTestRecordPayload, TestRecord } from '@/types/testRecord'

export function fetchRequirementTests(requirementNo: string) {
  return request.get<never, TestRecord[]>(`/requirements/${requirementNo}/tests`)
}

export function createTestRecord(
  requirementNo: string,
  payload: CreateTestRecordPayload,
) {
  return request.post<never, TestRecord>(`/requirements/${requirementNo}/tests`, payload)
}
