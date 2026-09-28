<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import SessionLogoutButton from '../SessionLogoutButton.vue'
import { useAuthStore } from '../../stores/auth'
import { useI18n } from 'vue-i18n'

interface NavItem {
  to: string
  label: string
  caption: string
}

const route = useRoute()
const authStore = useAuthStore()
const { t } = useI18n({ useScope: 'global' })

const navItems = computed<NavItem[]>(() => authStore.isAdmin
  ? [
      { to: '/admin/overview', label: t('nav.admin'), caption: t('nav.workbench') },
      { to: '/admin/users', label: t('nav.users'), caption: t('nav.usersHint') },
    ]
  : [
      { to: '/groups', label: t('nav.groups'), caption: t('nav.groupsHint') },
      { to: '/documents', label: t('nav.documents'), caption: t('nav.documentsHint') },
      { to: '/qa', label: t('nav.qa'), caption: t('nav.qaHint') },
    ])
const currentUser = computed(() => authStore.currentUser)
const roleLabel = computed(() => (authStore.isAdmin ? t('nav.systemAdmin') : t('nav.businessUser')))
const accountSummary = computed(() => currentUser.value?.userCode ?? t('common.unknown'))
const statusLabel = computed(() => {
  if (currentUser.value === null) {
    return t('nav.syncingAccount')
  }
  return currentUser.value.mustChangePassword ? t('auth.mustChangePassword') : t('nav.sessionReady')
})

function isActive(targetPath: string) {
  return route.path === targetPath || route.path.startsWith(`${targetPath}/`)
}
</script>

<template>
  <div class="workbench-sidebar">
    <RouterLink class="workbench-sidebar__brand" :to="authStore.homePath">
      <span class="workbench-sidebar__eyebrow">SPRINGMIND</span>
      <strong>{{ $t('common.appName') }}</strong>
      <span class="workbench-sidebar__brand-subtitle">{{ $t('nav.qa') }}</span>
      <span class="workbench-sidebar__brand-description">{{ $t('qa.chooseFirst') }}</span>
      <span class="workbench-sidebar__brand-tag">RAG · Evidence · Citations</span>
    </RouterLink>

    <nav class="workbench-sidebar__nav" :aria-label="$t('nav.workbench')">
      <RouterLink
        v-for="item in navItems"
        :key="item.to"
        :to="item.to"
        class="workbench-sidebar__nav-item"
        :class="{ 'is-active': isActive(item.to) }"
      >
        <strong>{{ item.label }}</strong>
        <span>{{ item.caption }}</span>
      </RouterLink>
    </nav>

    <div class="workbench-sidebar__user">
      <div class="workbench-sidebar__user-copy">
        <span class="workbench-sidebar__user-label">{{ roleLabel }}</span>
        <strong>{{ currentUser?.displayName ?? $t('common.unknown') }}</strong>
        <span>{{ accountSummary }}</span>
      </div>

      <div class="workbench-sidebar__status">
        <span class="workbench-sidebar__status-dot" aria-hidden="true" />
        <span>{{ statusLabel }}</span>
      </div>

      <div class="workbench-sidebar__actions">
        <RouterLink class="workbench-sidebar__security-link" to="/account/security">
          {{ $t('auth.securityTitle') }}
        </RouterLink>
        <SessionLogoutButton class="workbench-sidebar__logout" />
      </div>
    </div>
  </div>
</template>
