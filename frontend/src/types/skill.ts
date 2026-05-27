export interface Skill {
  id: number
  skillCode: string
  skillName: string
  skillDesc?: string
  contentText?: string
  archiveFileName?: string
  archiveContentType?: string
  archiveSize?: number
  hasArchive: boolean
  enabledFlag: boolean
  lastArchiveUploadTime?: string
  createdAt: string
  updatedAt: string
}

export interface SaveSkillPayload {
  skillCode?: string
  skillName: string
  skillDesc?: string
  contentText?: string
  enabledFlag?: boolean
}

export interface SkillPage {
  items: Skill[]
  page: number
  pageSize: number
  total: number
  totalPages: number
}

export interface SkillPageQuery {
  page?: number
  pageSize?: number
  keyword?: string
  enabledFlag?: boolean
}
