<script setup lang="ts">
import { fetchInfo } from '@/api/oryxos'
import ManagementQuery from './ManagementQuery.vue'
</script>

<template>
  <section class="providers-overview">
    <header class="providers-head">
      <h2>Provider 管理</h2>
      <p class="providers-hint">查看已配置的模型 Provider</p>
    </header>

    <ManagementQuery :load="fetchInfo" v-slot="{ data }">
      <p v-if="!data.providers.length" class="providers-empty">当前没有已配置的 Provider。</p>
      <ul v-else class="providers-grid">
        <li v-for="name in data.providers" :key="name" class="provider-card">
          <span class="provider-icon">🔌</span>
          <div class="provider-info">
            <h3>{{ name }}</h3>
            <p class="provider-status">已配置</p>
          </div>
          <span class="provider-badge">可用</span>
        </li>
      </ul>
    </ManagementQuery>
  </section>
</template>

<style scoped>
.providers-overview {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.providers-head {
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line);
}

.providers-head h2 {
  margin: 0 0 6px;
  font-size: 18px;
  font-weight: 600;
}

.providers-hint {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
}

.providers-empty {
  margin: 40px 0;
  padding: 24px;
  text-align: center;
  color: var(--muted);
  font-size: 14px;
  background: var(--white);
  border: 1px dashed var(--line);
  border-radius: 12px;
}

.providers-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 14px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.provider-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  background: var(--white);
  border: 1px solid var(--line);
  border-radius: 12px;
  transition: border-color 120ms ease, box-shadow 120ms ease, transform 120ms ease;
}

.provider-card:hover {
  border-color: var(--teal-light);
  box-shadow: 0 6px 20px rgba(15, 39, 64, 0.08);
  transform: translateY(-2px);
}

.provider-icon {
  font-size: 24px;
  line-height: 1;
}

.provider-info {
  flex: 1;
  min-width: 0;
}

.provider-info h3 {
  margin: 0 0 2px;
  font-size: 15px;
  font-weight: 600;
  color: var(--ink);
}

.provider-status {
  margin: 0;
  font-size: 12px;
  color: var(--muted);
}

.provider-badge {
  padding: 4px 10px;
  background: rgba(63, 208, 163, 0.15);
  color: #1f7c75;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 600;
}
</style>
