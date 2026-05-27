import type { MarkdownDocumentNodeType, MarkdownDocumentTreeNode } from '@/types/markdownDocument'

export interface MarkdownDocumentTreeSelectNode {
  value: string
  label: string
  nodeType: MarkdownDocumentNodeType
  disabled?: boolean
  children: MarkdownDocumentTreeSelectNode[]
}

export interface BuildMarkdownDocumentTreeSelectOptions {
  documentOnly?: boolean
  excludeId?: string
  promoteExcludedChildren?: boolean
}

export function buildMarkdownDocumentTreeSelectOptions(
  nodes: MarkdownDocumentTreeNode[],
  options: BuildMarkdownDocumentTreeSelectOptions = {},
): MarkdownDocumentTreeSelectNode[] {
  return nodes.flatMap((node) => {
    const children = buildMarkdownDocumentTreeSelectOptions(node.children || [], options)
    if (options.excludeId && node.documentId === options.excludeId) {
      return options.promoteExcludedChildren ? children : []
    }
    const nodeType = node.nodeType || 'DOCUMENT'
    return [{
      value: node.documentId,
      label: node.title,
      nodeType,
      disabled: Boolean(options.documentOnly && nodeType === 'FOLDER'),
      children,
    }]
  })
}
