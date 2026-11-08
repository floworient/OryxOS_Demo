<script setup lang="ts">
import type { SessionSummary } from '@/api/types'
import type { UiError } from '@/utils/errors'

defineProps<{
  sessions: SessionSummary[]
  status: 'loading' | 'ready' | 'empty' | 'error'
  error: UiError | null
  activeId: string | null
}>()

defineEmits<{ select: [session: SessionSummary]; retry: [] }>()

function time(value: string): string {
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}
</script>

<template>
  <section class="session-history">
    <div class="sidebar-head">最近会话</div>
    <p v-if="status === 'loading'" class="sidebar-hint">正在加载会话…</p>
    <div v-else-if="status === 'error'" class="sidebar-hint" role="alert">
      <p>{{ error?.message ?? '会话加载失败' }}</p>
      <button type="button" class="text-button" @click="$emit('retry')">重试</button>
    </div>
    <p v-else-if="status === 'empty'" class="sidebar-hint">暂无会话。</p>
    <ul v-else class="agent-list">
      <li v-for="session in sessions" :key="session.id">
        <button type="button" class="agent-item" :class="{ active: session.id === activeId }"
          @click="$emit('select', session)">
          <span class="agent-title">{{ session.profileName }}</span>
          <span class="agent-name">{{ session.channel }} · {{ session.messageCount }} 条消息</span>
          <span class="agent-model">{{ time(session.lastActiveAt) }}</span>
        </button>
      </li>
    </ul>
  </section>
</template>
