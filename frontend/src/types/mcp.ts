export type McpFileOperation = 'TREE' | 'LIST' | 'READ' | 'SEARCH'

export interface McpFileTool {
  name: string
  description?: string
}

export interface McpFileRequestPayload {
  operation?: McpFileOperation | string
  path?: string
  keyword?: string
  maxDepth?: number
  limit?: number
}

export interface McpFileEntry {
  path: string
  name: string
  type: 'directory' | 'file' | string
  size?: number
  depth?: number
  preview?: string
}

export interface McpFileResponse {
  requestId?: string
  agentCode?: string
  clientCode?: string
  enabled?: boolean
  operation?: string
  basePath?: string
  path?: string
  entries?: McpFileEntry[]
  content?: string
  matches?: McpFileEntry[]
  errorMessage?: string
}
