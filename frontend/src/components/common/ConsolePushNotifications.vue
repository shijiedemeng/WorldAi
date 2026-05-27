<template>
  <Teleport to="body">
    <TransitionGroup name="console-toast" tag="div" class="console-toast-stack">
      <article
        v-for="item in appStore.consoleToasts"
        :key="item.id"
        class="console-toast-card"
        :class="`is-${item.tone}`"
      >
        <div class="console-toast-card__dot" />
        <div class="console-toast-card__body">
          <strong>{{ item.title }}</strong>
          <span>{{ item.message }}</span>
        </div>
        <time>{{ formatTime(item.createdAt) }}</time>
      </article>
    </TransitionGroup>
  </Teleport>
</template>

<script setup lang="ts">
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()

function formatTime(value?: string) {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleTimeString('zh-CN', { hour12: false })
}
</script>

<style scoped>
.console-toast-stack {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 3000;
  display: flex;
  flex-direction: column-reverse;
  gap: 12px;
  pointer-events: none;
}

.console-toast-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: start;
  gap: 10px;
  width: min(390px, calc(100vw - 48px));
  padding: 14px 16px;
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.16);
  backdrop-filter: blur(14px);
}

.console-toast-card__dot {
  width: 10px;
  height: 10px;
  margin-top: 5px;
  border-radius: 999px;
  background: #2563eb;
}

.console-toast-card.is-success .console-toast-card__dot {
  background: #16a34a;
}

.console-toast-card.is-warning .console-toast-card__dot {
  background: #f59e0b;
}

.console-toast-card.is-danger .console-toast-card__dot {
  background: #dc2626;
}

.console-toast-card__body {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.console-toast-card__body strong {
  color: #0f172a;
  font-size: 14px;
}

.console-toast-card__body span {
  color: #475569;
  font-size: 13px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.console-toast-card time {
  color: #94a3b8;
  font-size: 12px;
  white-space: nowrap;
}

.console-toast-enter-active,
.console-toast-leave-active,
.console-toast-move {
  transition: opacity 0.28s ease, transform 0.28s ease;
}

.console-toast-enter-from {
  opacity: 0;
  transform: translateY(18px) scale(0.98);
}

.console-toast-leave-to {
  opacity: 0;
  transform: translateY(-18px) scale(0.98);
}
</style>
