<template>
  <div
    ref="hostRef"
    class="text-code-editor"
    :class="{ 'text-code-editor--readonly': readonly }"
    :style="{ height, minHeight }"
  />
</template>

<script setup lang="ts">
import { Compartment, EditorState } from '@codemirror/state'
import { EditorView, placeholder as placeholderExtension } from '@codemirror/view'
import { markdown } from '@codemirror/lang-markdown'
import { basicSetup } from 'codemirror'
import { nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'

const props = withDefaults(defineProps<{
  modelValue?: string
  placeholder?: string
  readonly?: boolean
  autofocus?: boolean
  height?: string
  minHeight?: string
}>(), {
  modelValue: '',
  placeholder: '请输入内容',
  readonly: false,
  autofocus: false,
  height: '100%',
  minHeight: '360px',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const hostRef = ref<HTMLElement>()
const editorView = shallowRef<EditorView>()
const editableCompartment = new Compartment()
const placeholderCompartment = new Compartment()

onMounted(() => {
  if (!hostRef.value) {
    return
  }
  const state = EditorState.create({
    doc: props.modelValue || '',
    extensions: [
      basicSetup,
      markdown(),
      EditorView.lineWrapping,
      EditorView.theme({
        '&': {
          backgroundColor: '#ffffff',
          border: '1px solid #cbd5e1',
          borderRadius: '12px',
          color: '#0f172a',
          fontSize: '13px',
          height: '100%',
        },
        '&.cm-focused': {
          borderColor: '#2563eb',
          outline: 'none',
        },
        '.cm-scroller': {
          fontFamily: '"SFMono-Regular", "Menlo", "Consolas", monospace',
          lineHeight: '1.65',
        },
        '.cm-content': {
          padding: '14px 0',
        },
        '.cm-line': {
          padding: '0 12px',
        },
        '.cm-gutters': {
          backgroundColor: '#f8fafc',
          borderRight: '1px solid #e2e8f0',
          color: '#94a3b8',
        },
        '.cm-activeLine': {
          backgroundColor: '#eff6ff',
        },
        '.cm-activeLineGutter': {
          backgroundColor: '#dbeafe',
          color: '#1d4ed8',
        },
      }),
      editableCompartment.of([
        EditorState.readOnly.of(props.readonly),
        EditorView.editable.of(!props.readonly),
      ]),
      placeholderCompartment.of(placeholderExtension(props.placeholder)),
      EditorView.updateListener.of((update) => {
        if (update.docChanged) {
          emit('update:modelValue', update.state.doc.toString())
        }
      }),
    ],
  })
  editorView.value = new EditorView({
    state,
    parent: hostRef.value,
  })
  if (props.autofocus) {
    void nextTick(() => editorView.value?.focus())
  }
})

watch(() => props.modelValue, (value) => {
  const view = editorView.value
  if (!view) {
    return
  }
  const nextValue = value || ''
  const currentValue = view.state.doc.toString()
  if (nextValue === currentValue) {
    return
  }
  view.dispatch({
    changes: {
      from: 0,
      to: view.state.doc.length,
      insert: nextValue,
    },
  })
})

watch(() => props.readonly, (readonly) => {
  editorView.value?.dispatch({
    effects: editableCompartment.reconfigure([
      EditorState.readOnly.of(readonly),
      EditorView.editable.of(!readonly),
    ]),
  })
})

watch(() => props.placeholder, (placeholder) => {
  editorView.value?.dispatch({
    effects: placeholderCompartment.reconfigure(placeholderExtension(placeholder)),
  })
})

onBeforeUnmount(() => {
  editorView.value?.destroy()
  editorView.value = undefined
})
</script>

<style scoped>
.text-code-editor {
  width: 100%;
}

.text-code-editor :deep(.cm-editor) {
  min-height: inherit;
}

.text-code-editor :deep(.cm-scroller) {
  border-radius: 12px;
}

.text-code-editor--readonly {
  opacity: 0.82;
}
</style>
