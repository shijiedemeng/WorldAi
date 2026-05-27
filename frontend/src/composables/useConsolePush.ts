import { onBeforeUnmount, onMounted } from 'vue'
import { useAppStore } from '@/stores/app'

export interface ConsolePushEvent<T = unknown> {
  eventId?: string
  type: string
  data?: T
  createdAt?: string
}

const CONSOLE_EVENT_NAME = 'ai-api-console-event'

let socket: WebSocket | null = null
let reconnectTimer: number | undefined
let connectTimeoutTimer: number | undefined
let activeUsers = 0

export function useConsolePush() {
  onMounted(() => {
    activeUsers += 1
    connect()
  })

  onBeforeUnmount(() => {
    activeUsers = Math.max(0, activeUsers - 1)
    if (activeUsers === 0) {
      close()
    }
  })
}

export function addConsolePushListener(listener: (event: ConsolePushEvent) => void) {
  const handler = (event: Event) => listener((event as CustomEvent<ConsolePushEvent>).detail)
  window.addEventListener(CONSOLE_EVENT_NAME, handler)
  return () => window.removeEventListener(CONSOLE_EVENT_NAME, handler)
}

function connect() {
  const appStore = useAppStore()
  if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
    return
  }
  window.clearTimeout(reconnectTimer)
  window.clearTimeout(connectTimeoutTimer)
  appStore.setConsolePushStatus('CONNECTING')
  try {
    socket = new WebSocket(buildConsoleWsUrl())
  } catch {
    socket = null
    appStore.setConsolePushStatus('DISCONNECTED')
    if (activeUsers > 0) {
      reconnectTimer = window.setTimeout(connect, 3000)
    }
    return
  }
  connectTimeoutTimer = window.setTimeout(() => {
    if (socket?.readyState === WebSocket.CONNECTING) {
      appStore.setConsolePushStatus('DISCONNECTED')
      socket.close()
    }
  }, 5000)
  socket.onopen = () => {
    window.clearTimeout(connectTimeoutTimer)
    connectTimeoutTimer = undefined
    appStore.setConsolePushStatus('CONNECTED')
  }
  socket.onmessage = (event) => {
    try {
      const payload = JSON.parse(event.data) as ConsolePushEvent
      window.dispatchEvent(new CustomEvent(CONSOLE_EVENT_NAME, { detail: payload }))
    } catch {
      // Ignore malformed push messages; API polling remains the fallback.
    }
  }
  socket.onclose = () => {
    window.clearTimeout(connectTimeoutTimer)
    connectTimeoutTimer = undefined
    socket = null
    appStore.setConsolePushStatus('DISCONNECTED')
    if (activeUsers > 0) {
      reconnectTimer = window.setTimeout(connect, 3000)
    }
  }
  socket.onerror = () => {
    appStore.setConsolePushStatus('DISCONNECTED')
    socket?.close()
  }
}

function close() {
  const appStore = useAppStore()
  window.clearTimeout(reconnectTimer)
  window.clearTimeout(connectTimeoutTimer)
  reconnectTimer = undefined
  connectTimeoutTimer = undefined
  socket?.close()
  socket = null
  appStore.setConsolePushStatus('DISCONNECTED')
}

function buildConsoleWsUrl() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws/console`
}
