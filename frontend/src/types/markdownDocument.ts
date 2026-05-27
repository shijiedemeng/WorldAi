export type MarkdownDocumentNodeType = 'DOCUMENT' | 'FOLDER'

export interface MarkdownDocument {
  id?: number
  documentId: string
  title: string
  nodeType: MarkdownDocumentNodeType
  type: string
  status: string
  parentId?: string
  refs: string[]
  content: string
  createdAt?: string
  updatedAt?: string
}

export interface MarkdownDocumentTreeNode {
  documentId: string
  title: string
  nodeType: MarkdownDocumentNodeType
  type: string
  status: string
  parentId?: string
  refs: string[]
  children: MarkdownDocumentTreeNode[]
}

export interface MarkdownDocumentPage {
  items: MarkdownDocument[]
  page: number
  pageSize: number
  total: number
  totalPages: number
}

export interface MarkdownDocumentRefs {
  documentId: string
  refs: string[]
  missingRefs: string[]
  outgoingDocuments: MarkdownDocument[]
  incomingDocuments: MarkdownDocument[]
}

export interface SaveMarkdownDocumentPayload {
  documentId?: string
  title?: string
  nodeType?: MarkdownDocumentNodeType
  type?: string
  status?: string
  parentId?: string
  refs?: string[]
  body?: string
  content?: string
}

export interface MarkdownDocumentQuery {
  keyword?: string
  type?: string
  status?: string
  nodeType?: MarkdownDocumentNodeType
  parentId?: string
  page?: number
  pageSize?: number
}
