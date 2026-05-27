<template>
  <div v-if="blocks.length" class="defect-rich-text">
    <template v-for="(block, blockIndex) in blocks" :key="`${block.type}-${blockIndex}`">
      <p v-if="block.type === 'paragraph'" class="defect-rich-text__paragraph">
        <template v-for="(part, partIndex) in block.parts" :key="partIndex">
          <a v-if="part.type === 'link'" :href="part.href" target="_blank" rel="noreferrer">{{ part.value }}</a>
          <span v-else>{{ part.value }}</span>
        </template>
      </p>
      <figure v-else class="defect-rich-text__image-wrap">
        <el-image
          class="defect-rich-text__image"
          :src="block.url"
          :preview-src-list="imagePreviewList"
          :initial-index="imagePreviewList.indexOf(block.url)"
          fit="contain"
          lazy
          preview-teleported
          @dblclick.stop="openImageDetail(block.url)"
        >
          <template #error>
            <a :href="block.url" target="_blank" rel="noreferrer">{{ block.url }}</a>
          </template>
        </el-image>
      </figure>
    </template>
  </div>
  <span v-else class="defect-rich-text__empty">{{ emptyText }}</span>

  <el-dialog v-model="imageDetailVisible" title="图片详情" width="82%" append-to-body destroy-on-close>
    <div class="defect-rich-text__detail">
      <img v-if="selectedImageUrl" :src="selectedImageUrl" alt="缺陷图片" />
      <a v-if="selectedImageUrl" :href="selectedImageUrl" target="_blank" rel="noreferrer">{{ selectedImageUrl }}</a>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

interface TextPart {
  type: 'text'
  value: string
}

interface LinkPart {
  type: 'link'
  value: string
  href: string
}

type InlinePart = TextPart | LinkPart

interface ParagraphBlock {
  type: 'paragraph'
  parts: InlinePart[]
}

interface ImageBlock {
  type: 'image'
  url: string
}

type RichTextBlock = ParagraphBlock | ImageBlock

const props = withDefaults(defineProps<{
  content?: string
  emptyText?: string
}>(), {
  content: '',
  emptyText: '-',
})

const blocks = computed(() => buildBlocks(props.content))
const imagePreviewList = computed(() => blocks.value
  .filter((block): block is ImageBlock => block.type === 'image')
  .map((block) => block.url))
const imageDetailVisible = ref(false)
const selectedImageUrl = ref('')

function openImageDetail(url: string) {
  selectedImageUrl.value = url
  imageDetailVisible.value = true
}

function buildBlocks(content?: string) {
  const normalized = content?.replace(/\r\n/g, '\n').replace(/\r/g, '\n').trim()
  if (!normalized) {
    return []
  }
  const result: RichTextBlock[] = []
  const seenImages = new Set<string>()
  for (const line of normalized.split('\n')) {
    const trimmed = line.trim()
    if (!trimmed) {
      continue
    }
    const urls = extractUrls(trimmed)
    const imageUrls = urls.filter((url) => isImageUrl(url) || isImageLine(trimmed, url) || isYunxiaoFileUrl(url))
    const textLine = stripImageUrlsFromLine(line, imageUrls)
    if (hasDisplayableText(textLine)) {
      result.push({
        type: 'paragraph',
        parts: buildInlineParts(textLine),
      })
    }
    for (const url of imageUrls) {
      if (!seenImages.has(url)) {
        seenImages.add(url)
        result.push({ type: 'image', url })
      }
    }
  }
  return result
}

function extractUrls(value: string) {
  return [...value.matchAll(/https?:\/\/[^\s<>"']+/g)]
    .map((match) => normalizeUrlToken(match[0]).url)
    .filter(Boolean)
}

function buildInlineParts(value: string): InlinePart[] {
  const parts: InlinePart[] = []
  const matcher = /https?:\/\/[^\s<>"']+/g
  let cursor = 0
  for (const match of value.matchAll(matcher)) {
    const raw = match[0]
    const index = match.index || 0
    if (index > cursor) {
      parts.push({ type: 'text', value: value.slice(cursor, index) })
    }
    const normalized = normalizeUrlToken(raw)
    parts.push({ type: 'link', value: normalized.url, href: normalized.url })
    if (normalized.trailing) {
      parts.push({ type: 'text', value: normalized.trailing })
    }
    cursor = index + raw.length
  }
  if (cursor < value.length) {
    parts.push({ type: 'text', value: value.slice(cursor) })
  }
  return parts.length ? parts : [{ type: 'text', value }]
}

function normalizeUrlToken(raw: string) {
  let url = raw
  let trailing = ''
  while (/[),.;!?，。；、）】\]]$/.test(url)) {
    trailing = `${url.slice(-1)}${trailing}`
    url = url.slice(0, -1)
  }
  return { url, trailing }
}

function isImageLine(line: string, url: string) {
  const index = line.indexOf(url)
  if (index < 0) {
    return false
  }
  return /图片\s*[:：]?\s*$/.test(line.slice(0, index).trim())
}

function isImageUrl(url: string) {
  const normalized = url.toLowerCase()
  const path = normalized.split(/[?#]/)[0]
  return /\.(png|jpe?g|gif|webp|bmp|svg|avif)$/.test(path) || normalized.includes('/image/')
}

function isYunxiaoFileUrl(url: string) {
  const normalized = url.toLowerCase()
  return normalized.includes('devops.aliyun.com/projex/api/workitem/file/url') || normalized.includes('fileidentifier=')
}

function stripImageUrlsFromLine(line: string, imageUrls: string[]) {
  let text = line
  for (const url of imageUrls) {
    text = text.replace(url, '')
  }
  return text
}

function hasDisplayableText(value: string) {
  return value
    .replace(/图片\s*[:：]?/g, '')
    .replace(/[：:，,；;。.\s-]+/g, '')
    .trim()
    .length > 0
}
</script>

<style scoped>
.defect-rich-text {
  display: grid;
  gap: 10px;
  line-height: 1.75;
  color: var(--el-text-color-primary);
}

.defect-rich-text__paragraph {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.defect-rich-text a {
  color: var(--el-color-primary);
  text-decoration: none;
  word-break: break-all;
}

.defect-rich-text a:hover {
  text-decoration: underline;
}

.defect-rich-text__image-wrap {
  display: block;
  margin: 0;
}

.defect-rich-text__image {
  width: min(100%, 760px);
  min-height: 120px;
  max-height: 460px;
  padding: 10px;
  border: 1px solid rgba(148, 163, 184, 0.32);
  border-radius: 14px;
  background: #f8fafc;
  cursor: zoom-in;
}

.defect-rich-text__image :deep(img) {
  max-height: 440px;
  object-fit: contain;
}

.defect-rich-text__empty {
  color: var(--el-text-color-secondary);
}

.defect-rich-text__detail {
  display: grid;
  gap: 12px;
}

.defect-rich-text__detail img {
  max-width: 100%;
  max-height: 72vh;
  object-fit: contain;
  justify-self: center;
  border-radius: 12px;
  background: #f8fafc;
}

.defect-rich-text__detail a {
  word-break: break-all;
}
</style>
