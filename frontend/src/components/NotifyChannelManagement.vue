<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ApiError } from '@/api/http'
import {
  createNotifyChannel,
  deleteNotifyChannel,
  fetchNotifyChannels,
  updateNotifyChannel,
} from '@/api/oryxos'
import type { NotifyChannelCreateInput, NotifyChannelSummary, NotifyChannelUpdateInput } from '@/api/types'
import { mapError } from '@/utils/errors'

const channels = ref<NotifyChannelSummary[]>([])
const loading = ref(true)
const loadError = ref('')
const formOpen = ref(false)
const editingName = ref<string | null>(null)
interface FormData {
  name: string
  type: string
  url: string | undefined
  description: string | undefined
  config: Record<string, unknown> | undefined
}

const form: FormData = reactive({
  name: '',
  type: '',
  url: undefined,
  description: undefined,
  config: undefined,
})
const submitPending = ref(false)
const errorMap = reactive(new Map<string, string>())

async function refresh() {
  try {
    const result = await fetchNotifyChannels()
    channels.value = result
  } catch (error) {
    loadError.value = mapError(error).message
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.name = ''
  form.type = ''
  form.url = ''
  form.description = ''
  form.config = {}
  editingName.value = null
  formOpen.value = false
}

function startCreate() {
  editingName.value = null
  resetForm()
  formOpen.value = true
}

function startEdit(ch: NotifyChannelSummary) {
  editingName.value = ch.name
  Object.assign(form, {
    name: ch.name,
    type: ch.type,
    url: ch.url ?? '',
    description: ch.description ?? '',
    config: ch.config ?? {},
  })
  formOpen.value = true
}

async function submitForm() {
  if (!form.type.trim()) { errorMap.set('type', '类型不能为空'); return }
  errorMap.clear()
  submitPending.value = true
  try {
    if (editingName.value) {
      const input: NotifyChannelUpdateInput = {
        type: form.type || undefined,
        url: form.url || undefined,
        description: form.description || undefined,
        config: Object.keys(form.config ?? {}).length ? form.config : undefined,
      }
      await updateNotifyChannel(editingName.value, input)
    } else {
      if (!form.name.trim()) { errorMap.set('name', '名称不能为空'); return }
      const input: NotifyChannelCreateInput = {
        name: form.name,
        type: form.type,
        url: form.url || undefined,
        description: form.description || undefined,
        config: Object.keys(form.config ?? {}).length ? form.config : undefined,
      }
      await createNotifyChannel(input)
    }
    resetForm()
    await refresh()
  } catch (error) {
    errorMap.set('__form__', mapError(error).message)
  } finally {
    submitPending.value = false
  }
}

async function confirmDelete(ch: NotifyChannelSummary) {
  if (!confirm(`确定删除渠道「${ch.name}」？此操作不可撤销。`)) return
  try {
    await deleteNotifyChannel(ch.name)
    await refresh()
  } catch (error) {
    errorMap.set(ch.name, mapError(error).message)
  }
}

function actionError(code: string): string {
  if (code === 'VALIDATION_ERROR') return '请求参数不合法，请检查输入。'
  if (code === 'CHANNEL_EXISTS') return '渠道名称已存在。'
  if (code === 'CHANNEL_NOT_FOUND') return '渠道不存在。'
  return mapError(Object.assign(new ApiError(code, 0))).message
}

onMounted(refresh)
</script>

<template>
  <div v-if="loading" role="status">正在加载通知渠道…</div>
  <div v-else-if="loadError" role="alert">
    <p>通知渠道加载失败。{{ loadError }}</p>
    <button type="button" class="ghost-button" @click="refresh">重试</button>
  </div>
  <p v-else-if="!channels.length">暂无通知渠道。</p>
  <div v-else class="channel-container">
    <header class="channel-header">
      <h2>通知渠道管理</h2>
      <button type="button" class="primary-button" :disabled="submitPending" @click="startCreate">+ 新建渠道</button>
    </header>

    <!-- Form -->
    <details v-if="formOpen" open class="channel-form">
      <summary>{{ editingName ? '编辑渠道' : '新建渠道' }}</summary>
      <p v-if="errorMap.get('__form__')" role="alert">{{ errorMap.get('__form__') }}</p>
      <form @submit.prevent="submitForm">
        <div class="form-grid">
          <label v-if="!editingName">
            <span>名称 *</span>
            <input v-model="form.name" placeholder="唯一标识，如 dingtalk-critical" />
            <small v-if="errorMap.get('name')">{{ errorMap.get('name') }}</small>
          </label>
          <label>
            <span>类型 *</span>
            <select v-model="form.type">
              <option value="">选择类型</option>
              <option value="webhook">Webhook</option>
              <option value="email">Email</option>
              <option value="dingtalk">钉钉</option>
              <option value="wecom">企业微信</option>
              <option value="feishu">飞书</option>
            </select>
            <small v-if="errorMap.get('type')">{{ errorMap.get('type') }}</small>
          </label>
          <label class="full-width">
            <span>URL / 地址</span>
            <input v-model="form.url" placeholder="可选目标地址（会被脱敏显示）" />
          </label>
          <label class="full-width">
            <span>描述</span>
            <textarea v-model="form.description" rows="2" placeholder="渠道用途说明" />
          </label>
        </div>
        <footer class="form-actions">
          <button type="submit" class="primary-button" :disabled="submitPending">
            {{ editingName ? '保存修改' : '创建' }}
          </button>
          <button type="button" class="ghost-button" :disabled="submitPending" @click="resetForm">取消</button>
        </footer>
      </form>
    </details>

    <!-- List -->
    <table aria-label="通知渠道">
      <thead>
        <tr>
          <th scope="col">名称</th><th scope="col">类型</th>
          <th scope="col">描述</th><th scope="col">URL（脱敏）</th>
          <th scope="col">更新时间</th><th scope="col">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="ch in channels" :key="ch.name">
          <th scope="row">{{ ch.name }}</th>
          <td>{{ ch.type }}</td>
          <td>{{ ch.description ?? '—' }}</td>
          <td><code>{{ ch.url ?? '—' }}</code></td>
          <td>{{ new Date(ch.updatedAt).toLocaleString('zh-CN', { hour12: false }) }}</td>
          <td>
            <div class="channel-actions">
              <button type="button" class="ghost-button" @click="startEdit(ch)">编辑</button>
              <button type="button" class="ghost-button danger" @click="confirmDelete(ch)">删除</button>
              <p v-if="errorMap.has(ch.name)" role="alert" class="inline-error">{{ errorMap.get(ch.name) }}</p>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.channel-container > * + * { margin-top: 0.5rem; }
.channel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.channel-form {
  background: var(--surface-secondary, #f9f9f9);
  padding: 1rem;
  border-radius: 4px;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}
.full-width { grid-column: 1 / -1; }
.form-grid label { display: flex; flex-direction: column; gap: 0.25rem; font-size: 0.9em; }
.form-grid input, .form-grid select, .form-grid textarea {
  padding: 0.35rem 0.5rem;
  font-size: inherit;
  border: 1px solid var(--border-color, #ddd);
  border-radius: 3px;
}
.form-grid small { color: #d32f2f; font-size: 0.8em; }
.form-actions { display: flex; gap: 0.4rem; }
.channel-table { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; }
th, td { padding: 0.5rem; text-align: left; vertical-align: top; }
thead { border-bottom: 1px solid currentColor; }
.channel-actions { display: flex; flex-direction: column; gap: 0.25rem; }
.inline-error { color: #d32f2f; font-size: 0.8em; margin: 0; }
.danger { border-color: #d32f2f !important; color: #d32f2f !important; }
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
