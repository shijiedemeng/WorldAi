import request from './request'
import type {
  MarkdownDocument,
  MarkdownDocumentPage,
  MarkdownDocumentQuery,
  MarkdownDocumentRefs,
  MarkdownDocumentTreeNode,
  SaveMarkdownDocumentPayload,
} from '@/types/markdownDocument'

export function fetchMarkdownDocuments(query?: MarkdownDocumentQuery) {
  return request.get<never, MarkdownDocumentPage>('/markdown-documents', { params: query })
}

export function fetchMarkdownDocumentTree() {
  return request.get<never, MarkdownDocumentTreeNode[]>('/markdown-documents/tree')
}

export function fetchMarkdownDocument(documentId: string) {
  return request.get<never, MarkdownDocument>(`/markdown-documents/${encodeURIComponent(documentId)}`)
}

export function createMarkdownDocument(payload: SaveMarkdownDocumentPayload) {
  return request.post<never, MarkdownDocument>('/markdown-documents', payload)
}

export function uploadMarkdownDocument(file: File, overwrite: boolean) {
  const formData = new FormData()
  formData.append('file', file, file.name)
  return request.post<never, MarkdownDocument>('/markdown-documents/upload', formData, {
    params: { overwrite },
  })
}

export function updateMarkdownDocument(documentId: string, payload: SaveMarkdownDocumentPayload) {
  return request.put<never, MarkdownDocument>(`/markdown-documents/${encodeURIComponent(documentId)}`, payload)
}

export function deleteMarkdownDocument(documentId: string) {
  return request.delete<never, void>(`/markdown-documents/${encodeURIComponent(documentId)}`)
}

export function fetchMarkdownDocumentPath(documentId: string) {
  return request.get<never, MarkdownDocument[]>(`/markdown-documents/${encodeURIComponent(documentId)}/path`)
}

export function fetchMarkdownDocumentRefs(documentId: string) {
  return request.get<never, MarkdownDocumentRefs>(`/markdown-documents/${encodeURIComponent(documentId)}/refs`)
}

export function markdownDocumentDownloadUrl(documentId: string) {
  return `/api/markdown-documents/${encodeURIComponent(documentId)}/download`
}
