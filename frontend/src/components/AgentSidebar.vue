<script setup lang="ts">
import type { ProfileSummary, SessionSummary } from '@/api/types'
import type { BootStatus } from '@/state/useConsole'
import type { UiError } from '@/utils/errors'
import SessionHistory from './SessionHistory.vue'

defineProps<{
  profiles: ProfileSummary[]
  activeAgent: string | null
  bootStatus: BootStatus
  sessions: SessionSummary[]
  sessionsStatus: 'loading' | 'ready' | 'empty' | 'error'
  sessionsError: UiError | null
  activeSessionId: string | null
}>()

defineEmits<{
  select: [name: string]
  refresh: []
  selectSession: [session: SessionSummary]
  retrySessions: []
}>()
</script>

<template>
  <aside class="agent-sidebar">
    <div class="sidebar-head">Agent</div>

    <div v-if="bootStatus === 'loading' && profiles.length === 0" class="sidebar-hint">
      正在加载 Agent…
    </div>

    <div v-else-if="profiles.length === 0" class="sidebar-hint">
      <p>工作区没有 Agent。</p>
      <p>
        先运行 <code>profile create</code>，或在 <code>.oryxos/agents/</code> 下创建
        <code>AGENT.md</code>。
      </p>
    </div>

    <ul v-else class="agent-list">
      <li v-for="profile in profiles" :key="profile.name">
        <button
          type="button"
          class="agent-item"
          :class="{ active: profile.name === activeAgent }"
          :aria-pressed="profile.name === activeAgent"
          @click="$emit('select', profile.name)"
        >
          <span class="agent-title">{{ profile.description || profile.name }}</span>
          <span class="agent-name">{{ profile.name }}</span>
          <span class="agent-model">{{ profile.model }}</span>
        </button>
      </li>
    </ul>

    <SessionHistory :sessions="sessions" :status="sessionsStatus" :error="sessionsError"
      :active-id="activeSessionId" @select="$emit('selectSession', $event)"
      @retry="$emit('retrySessions')" />

    <button type="button" class="ghost-button" @click="$emit('refresh')">刷新列表</button>
  </aside>
</template>
