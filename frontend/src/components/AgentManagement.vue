<script setup lang="ts">
import { fetchProfiles } from '@/api/oryxos'
import ManagementQuery from './ManagementQuery.vue'

defineEmits<{ chat: [name: string] }>()
</script>

<template>
  <section class="agents-overview">
    <header class="agents-head">
      <h2>Agent 管理</h2>
      <p class="agents-hint">管理工作区的 Agent 配置与会话</p>
    </header>

    <ManagementQuery :load="fetchProfiles" v-slot="{ data }">
      <p v-if="!data.length" class="agents-empty">工作区没有 Agent。</p>
      <ul v-else class="agents-grid">
        <li v-for="agent in data" :key="agent.name" class="agent-card">
          <header class="agent-head">
            <span class="agent-avatar">🤖</span>
            <div class="agent-title-group">
              <h3>{{ agent.name }}</h3>
              <p class="agent-desc">{{ agent.description || '暂无描述' }}</p>
            </div>
          </header>

          <dl class="agent-info">
            <dt>Provider</dt>
            <dd>{{ agent.providerName }}</dd>
            <dt>Model</dt>
            <dd>{{ agent.model }}</dd>
            <dt>工具</dt>
            <dd>{{ agent.tools.length > 0 ? agent.tools.join(', ') : '无' }}</dd>
          </dl>

          <footer class="agent-actions">
            <button type="button" class="primary-button" :data-agent="agent.name"
              @click="$emit('chat', agent.name)">进入会话</button>
          </footer>
        </li>
      </ul>
    </ManagementQuery>
  </section>
</template>

<style scoped>
.agents-overview {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.agents-head {
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line);
}

.agents-head h2 {
  margin: 0 0 6px;
  font-size: 18px;
  font-weight: 600;
}

.agents-hint {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
}

.agents-empty {
  margin: 40px 0;
  padding: 24px;
  text-align: center;
  color: var(--muted);
  font-size: 14px;
  background: var(--white);
  border: 1px dashed var(--line);
  border-radius: 12px;
}

.agents-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.agent-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px 20px;
  background: var(--white);
  border: 1px solid var(--line);
  border-radius: 12px;
  transition: border-color 120ms ease, box-shadow 120ms ease, transform 120ms ease;
}

.agent-card:hover {
  border-color: var(--teal-light);
  box-shadow: 0 6px 20px rgba(15, 39, 64, 0.08);
  transform: translateY(-2px);
}

.agent-head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.agent-avatar {
  font-size: 28px;
  line-height: 1;
}

.agent-title-group {
  flex: 1;
  min-width: 0;
}

.agent-title-group h3 {
  margin: 0 0 4px;
  font-size: 16px;
  font-weight: 600;
  color: var(--ink);
}

.agent-desc {
  margin: 0;
  font-size: 13px;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-info {
  display: grid;
  grid-template-columns: 70px 1fr;
  gap: 6px 12px;
  margin: 0;
  padding: 12px;
  background: var(--paper);
  border-radius: 8px;
  font-size: 13px;
}

.agent-info dt {
  color: var(--muted);
  font-weight: 500;
}

.agent-info dd {
  margin: 0;
  color: var(--ink);
  word-break: break-word;
}

.agent-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 8px;
  border-top: 1px solid var(--line);
}
</style>
