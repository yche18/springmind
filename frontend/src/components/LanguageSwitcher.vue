<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { setAppLocale, type AppLocale } from '../i18n'

const { locale, t } = useI18n({ useScope: 'global' })

function selectLocale(nextLocale: AppLocale) {
  setAppLocale(nextLocale)
}
</script>

<template>
  <div class="language-switcher" :aria-label="t('common.language')">
    <button :class="{ active: locale === 'en' }" type="button" @click="selectLocale('en')">EN</button>
    <span aria-hidden="true">/</span>
    <button :class="{ active: locale === 'zh' }" type="button" @click="selectLocale('zh')">中文</button>
  </div>
</template>

<style scoped>
.language-switcher {
  position: fixed;
  top: 16px;
  right: 20px;
  z-index: 1000;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 9px;
  border: 1px solid rgba(129, 140, 248, 0.24);
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.88);
  color: #94a3b8;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.16);
  backdrop-filter: blur(12px);
}

button {
  border: 0;
  padding: 2px 4px;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font: inherit;
}

button.active {
  color: #fff;
  font-weight: 700;
}

button:focus-visible {
  outline: 2px solid #818cf8;
  outline-offset: 2px;
}
</style>
