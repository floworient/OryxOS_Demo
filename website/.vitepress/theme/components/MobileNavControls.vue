<template>
  <div class="mobile-nav-controls" aria-label="Display preferences">
    <a
      class="mobile-nav-button language-button"
      :href="languageLink"
      :aria-label="languageTitle"
      :title="languageTitle"
    >
      <span class="vpi-languages" aria-hidden="true"></span>
      <span class="language-label">{{ languageLabel }}</span>
    </a>

    <button
      class="mobile-nav-button theme-button"
      type="button"
      :aria-label="themeTitle"
      :title="themeTitle"
      @click="isDark = !isDark"
    >
      <span :class="isDark ? 'vpi-sun' : 'vpi-moon'" aria-hidden="true"></span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useData, useRoute, withBase } from 'vitepress'

const { isDark, lang, site } = useData()
const route = useRoute()

const isChinese = computed(() => lang.value.toLowerCase().startsWith('zh'))

const languageLabel = computed(() => (isChinese.value ? 'EN' : '中'))
const languageTitle = computed(() =>
  isChinese.value ? 'Switch to English' : '切换到中文'
)
const themeTitle = computed(() =>
  isDark.value ? '切换到浅色主题' : '切换到深色主题'
)

const languageLink = computed(() => {
  const base = site.value.base.replace(/\/$/, '')
  const routePath = route.path || '/'
  const currentPath = base && routePath.startsWith(`${base}/`)
    ? routePath.slice(base.length)
    : routePath

  if (isChinese.value) {
    const englishPath = currentPath.replace(/^\/zh(?=\/|$)/, '') || '/'
    return withBase(englishPath)
  }

  return withBase(currentPath === '/' ? '/zh/' : `/zh${currentPath}`)
})
</script>

<style scoped>
.mobile-nav-controls {
  display: none;
}

@media (max-width: 767px) {
  .mobile-nav-controls {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .mobile-nav-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    flex: none;
    height: 36px;
    border: 1px solid var(--vp-c-divider);
    border-radius: 6px;
    color: var(--vp-c-text-1);
    background: var(--vp-c-bg-soft);
  }

  .mobile-nav-button:active {
    background: var(--vp-c-bg-mute);
  }

  .language-button {
    gap: 4px;
    min-width: 48px;
    padding: 0 8px;
    font-size: 13px;
    font-weight: 700;
  }

  .theme-button {
    width: 36px;
    font-size: 17px;
  }

  .vpi-languages {
    font-size: 16px;
  }
}
</style>
