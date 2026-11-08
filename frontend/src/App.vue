<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AgentSidebar from './components/AgentSidebar.vue'
import ConsoleNavigation from './components/ConsoleNavigation.vue'
import ManagementPanel from './components/ManagementPanel.vue'
import { type ConsoleSection } from './components/consoleSections'
import ConversationPanel from './components/ConversationPanel.vue'
import StatusBar from './components/StatusBar.vue'
import { createConsole } from './state/useConsole'

const { state, activeConversation, initialize, selectAgent, selectSession, refreshSessions, send, newConversation, archiveConversation, retry } =
  createConsole()

const section = ref<ConsoleSection>('sessions')

async function openAgentConversation(name: string): Promise<void> {
  section.value = 'sessions'
  if (!state.profiles.some(profile => profile.name === name)) await initialize()
  if (section.value === 'sessions') await selectAgent(name)
}

const activeProfile = computed(
  () => state.profiles.find((profile) => profile.name === state.activeAgent) ?? null,
)

function updateDraft(value: string): void {
  if (activeConversation.value) {
    activeConversation.value.draft = value
    activeConversation.value.inputError = null
  }
}

function handleSend(): void {
  void send()
}

function handleRetry(): void {
  void retry()
}

function handleNewConversation(): void {
  void newConversation()
}

function handleArchive(): void {
  void archiveConversation()
}

function handleRefresh(): void {
  void initialize()
}

onMounted(() => {
  void initialize()
})
</script>

<template>
  <div class="console-shell">
    <StatusBar
      :health="state.runtimeHealth"
      :profile-count="state.profiles.length"
      :tool-count="state.systemInfo?.toolCount ?? 0"
      :providers="state.systemInfo?.providers ?? []"
    />

    <div class="console-layout">
      <ConsoleNavigation :active="section" @select="section = $event" />
      <div v-show="section === 'sessions'" class="console-body">
        <AgentSidebar
          :profiles="state.profiles"
          :active-agent="state.activeAgent"
          :boot-status="state.bootStatus"
          :sessions="state.sessions"
          :sessions-status="state.sessionsStatus"
          :sessions-error="state.sessionsError"
          :active-session-id="activeConversation?.sessionId ?? null"
          @select="selectAgent"
          @select-session="selectSession"
          @retry-sessions="refreshSessions"
          @refresh="handleRefresh"
        />

        <ConversationPanel
          :profile="activeProfile"
          :conversation="activeConversation"
          :boot-status="state.bootStatus"
          :banner="state.banner"
          :runtime-health="state.runtimeHealth"
          @send="handleSend"
          @update:draft="updateDraft"
          @new-conversation="handleNewConversation"
          @archive="handleArchive"
          @retry="handleRetry"
          @refresh="handleRefresh"
        />
      </div>
      <ManagementPanel v-if="section !== 'sessions'" :key="section" :section="section"
        :profiles="state.profiles" :boot-status="state.bootStatus"
        @chat="openAgentConversation" @refresh-profiles="handleRefresh" />
    </div>
  </div>
</template>
