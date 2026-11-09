<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fetchProfiles, invokeAgent } from '@/api/oryxos'
import { mapError } from '@/utils/errors'

const profiles = ref<{ name: string; description: string }[]>([])
const loading = ref(true)
const loadError = ref('')
const selectedAgent = ref('')
const message = ref('')
const reply = ref<string | null>(null)
const pending = ref(false)
const error = ref<string | null>(null)

async function refresh() {
  try {
    const result = await fetchProfiles()
    profiles.value = result
    if (result.length > 0 && !selectedAgent.value) {
      selectedAgent.value = result[0].name
    }
  } catch (err) {
    loadError.value = mapError(err).message
  } finally {
    loading.value = false
  }
}

async function invoke() {
  if (!selectedAgent.value || !message.value.trim()) return
  error.value = null
  reply.value = null
  pending.value = true
  try {
    const resp = await invokeAgent(selectedAgent.value, message.value)
    reply.value = resp.reply
  } catch (err) {
    error.value = mapError(err).message
  } finally {
    pending.value = false
  }
}

function reset() {
  reply.value = null
  error.value = null
  message.value = ''
}

onMounted(refresh)
</script>

<template>
  <div v-if="loading" role="status">正在加载 Agent 列表…</div>
  <div v-else-if="loadError" role="alert">
    <p>Agent 列表加载失败。{{ loadError }}</p>
    <button type="button" class="ghost-button" @click="refresh">重试</button>
  </div>
  <p v-else-if="!profiles.length">工作区没有可调用的 Agent。</p>
  <div v-else class="invoke-container">
    <header class="invoke-header">
      <h2>Agent 单次调用</h2>
      <label class="invoke-select-label">
        <span>选择 Agent：</span>
        <select v-model="selectedAgent">
          <option v-for="agent in profiles" :key="agent.name" :value="agent.name">{{ agent.name }}</option>
        </select>
      </label>
    </header>

    <section class="invoke-form">
      <textarea v-model="message" rows="4" placeholder="输入要发送给 Agent 的消息…"
        aria-label="消息内容" />
      <footer class="invoke-actions">
        <button type="button" class="primary-button" :disabled="pending || !message.trim()"
          @click="invoke">发送</button>
        <button type="button" class="ghost-button" :disabled="pending" @click="reset">清空</button>
      </footer>
    </section>

    <!-- Reply -->
    <section v-if="reply || error" class="invoke-result">
      <h3>回复</h3>
      <p v-if="error" role="alert" class="result-error">{{ error }}</p>
      <blockquote v-else class="result-reply" v-html="reply" />
    </section>
  </div>
</template>

<style scoped>
.invoke-container > * + * { margin-top: 0.75rem; }
.invoke-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.invoke-select-label select {
  margin-left: 0.25rem;
  padding: 0.25rem 0.5rem;
  font-size: inherit;
}
.invoke-form {
  background: var(--surface-secondary, #f9f9f9);
  padding: 1rem;
  border-radius: 4px;
}
.invoke-form textarea {
  width: 100%;
  padding: 0.5rem;
  font-size: inherit;
  font-family: inherit;
  border: 1px solid var(--border-color, #ddd);
  border-radius: 3px;
  resize: vertical;
}
.invoke-actions { display: flex; gap: 0.4rem; margin-top: 0.5rem; }
.invoke-result {
  background: var(--surface-primary, #fff);
  padding: 1rem;
  border-radius: 4px;
  border: 1px solid var(--border-color, #ddd);
}
.result-reply {
  white-space: pre-wrap;
  margin: 0;
  padding: 0.5rem;
  background: var(--surface-secondary, #f9f9f9);
  border-radius: 3px;
}
.result-error { color: #d32f2f; }
.primary-button {
  background: #0d6efd;
  color: #fff;
  border: none;
  cursor: pointer;
  padding: 0.35rem 0.75rem;
  border-radius: 3px;
}
.primary-button:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
