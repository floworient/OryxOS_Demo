<script setup lang="ts">
import { computed } from 'vue'
import type { ProfileSummary } from '@/api/types'
import type { AgentConversation, BootStatus, RuntimeHealth } from '@/state/useConsole'
import type { UiError } from '@/utils/errors'
import ChatComposer from './ChatComposer.vue'
import EmptyState from './EmptyState.vue'
import MessageBubble from './MessageBubble.vue'

const props = defineProps<{
  profile: ProfileSummary | null
  conversation: AgentConversation | null
  bootStatus: BootStatus
  banner: UiError | null
  runtimeHealth: RuntimeHealth
}>()

const emit = defineEmits<{
  send: []
  'update:draft': [value: string]
  'new-conversation': []
  archive: []
  retry: []
  refresh: []
}>()

const sending = computed(() => props.conversation?.status === 'sending')
const loadingHistory = computed(() => props.conversation?.status === 'loadingHistory')
const archived = computed(() => props.conversation?.status === 'archived')
const readonly = computed(() => props.conversation?.status === 'readonly')
const composerDisabled = computed(() => sending.value || loadingHistory.value || archived.value || readonly.value)
const showWelcome = computed(
  () =>
    props.profile !== null &&
    !sending.value &&
    (props.conversation?.messages.length ?? 0) === 0 &&
    !props.conversation?.error,
)

function updateDraft(value: string): void {
  emit('update:draft', value)
}
</script>

<template>
  <main class="conversation-panel">
    <EmptyState
      v-if="bootStatus === 'loading' && profile === null"
      title="正在连接运行时"
      description="正在读取 Agent、运行状态与 Provider 信息…"
    />

    <EmptyState
      v-else-if="bootStatus === 'offline'"
      title="无法连接运行时"
      description="请确认后端已启动，并检查 /api 代理或后端端口配置。"
    >
      <button type="button" class="primary-button" @click="emit('refresh')">重新连接</button>
    </EmptyState>

    <EmptyState
      v-else-if="bootStatus === 'empty'"
      title="工作区还没有 Agent"
      description="运行 profile create，或在 .oryxos/agents/<name>/AGENT.md 创建 Agent 后刷新。"
    >
      <button type="button" class="primary-button" @click="emit('refresh')">刷新列表</button>
    </EmptyState>

    <template v-else>
      <header class="conversation-head">
        <div>
          <h1>{{ profile?.description || profile?.name || '选择 Agent' }}</h1>
          <p class="conversation-meta">
            <span v-if="profile">{{ profile.name }} · {{ profile.providerName }} / {{ profile.model }}</span>
            <span v-else>请选择左侧 Agent</span>
          </p>
        </div>
        <div class="conversation-actions">
          <button type="button" class="ghost-button" :disabled="sending" @click="emit('new-conversation')">
            新对话
          </button>
          <button type="button" class="ghost-button" :disabled="sending" @click="emit('archive')">
            归档
          </button>
        </div>
      </header>

      <div v-if="banner" class="error-bar" role="alert">
        <span>{{ banner.message }}</span>
        <button type="button" class="text-button" @click="emit('refresh')">重试</button>
      </div>

      <div v-if="runtimeHealth === 'DOWN'" class="warning-bar">
        运行时健康检查未通过，模型调用可能失败。
      </div>

      <section class="message-area" aria-live="polite">
        <div v-if="conversation?.historyTruncated" class="notice-bar">仅展示最近 100 条消息</div>

        <div v-if="archived" class="notice-bar">
          该会话已归档，只能查看历史。开始新对话以继续提问。
        </div>
        <div v-if="readonly" class="notice-bar">该会话来自其他渠道，只能查看历史。</div>

        <p v-if="loadingHistory" class="loading-hint">正在加载历史…</p>

        <EmptyState
          v-else-if="showWelcome"
          title="开始对话"
          description="发送一条消息，查看 Agent 的真实回复与本轮工具调用。"
        />

        <div v-else class="message-list">
          <MessageBubble
            v-for="message in conversation?.messages ?? []"
            :key="message.key"
            :message="message"
          />

          <div v-if="sending" class="message message-assistant message-waiting">
            <div class="message-role">Agent</div>
            <div class="message-text">
              正在思考…
              <span class="waiting-time">{{ conversation?.waitingSeconds ?? 0 }}s</span>
              <span v-if="(conversation?.waitingSeconds ?? 0) >= 55" class="waiting-warning">
                即将超时
              </span>
            </div>
          </div>
        </div>

        <div v-if="conversation?.error" class="error-bar" role="alert">
          <span>{{ conversation.error.message }}</span>
          <button
            v-if="conversation.error.retryable"
            type="button"
            class="text-button"
            @click="emit('retry')"
          >
            重试
          </button>
        </div>
      </section>

      <div v-if="archived" class="archived-actions">
        <button type="button" class="primary-button" @click="emit('new-conversation')">
          开始新对话
        </button>
      </div>

      <ChatComposer
        v-else-if="conversation && !readonly"
        :model-value="conversation.draft"
        :disabled="composerDisabled"
        :sending="sending"
        :waiting-seconds="conversation.waitingSeconds"
        :input-error="conversation.inputError"
        @update:model-value="updateDraft"
        @send="emit('send')"
      />
    </template>
  </main>
</template>
