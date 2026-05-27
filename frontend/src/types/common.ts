export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export type StatusType =
  | 'PENDING'
  | 'ANALYZING'
  | 'IN_PROGRESS'
  | 'TESTING'
  | 'BLOCKED'
  | 'DONE'
  | 'CLOSED'
  | 'TODO'
  | 'DOING'
  | 'SKIPPED'
  | 'ONLINE'
  | 'OFFLINE'
  | 'DISABLED'
  | 'PASS'
  | 'FAIL'
  | 'ENABLED'
  | 'SUCCESS'
  | 'FAILED'
  | 'FALLBACK'
  | 'RUNNING'

export interface OptionItem {
  label: string
  value: string
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}
