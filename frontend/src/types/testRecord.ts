export interface TestRecord {
  id: number
  requirementNo: string
  testType: string
  testTitle: string
  testContent?: string
  testerAgentCode?: string
  testerName?: string
  testResult: 'PASS' | 'FAIL' | 'BLOCKED'
  bugCount: number
  riskDesc?: string
  suggestion?: string
  attachments?: string
  testedAt?: string
  createdAt: string
  updatedAt: string
}

export interface CreateTestRecordPayload {
  testType: string
  testTitle: string
  testContent?: string
  testerAgentCode?: string
  testerName?: string
  testResult: TestRecord['testResult']
  bugCount: number
  riskDesc?: string
  suggestion?: string
  attachments?: string
  testedAt?: string
}
