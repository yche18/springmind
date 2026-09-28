<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import PageHeaderHero from '../../components/layout/PageHeaderHero.vue'
import WorkbenchShell from '../../components/layout/WorkbenchShell.vue'
import WorkbenchSidebar from '../../components/layout/WorkbenchSidebar.vue'
import { useAuthStore } from '../../stores/auth'
import '../../assets/page-shell.css'
import '../../assets/admin-page.css'

const authStore = useAuthStore()
const route = useRoute()
const { t } = useI18n({ useScope: 'global' })

const currentUserName = computed(() => authStore.currentUser?.displayName ?? t('nav.systemAdmin'))
const currentUserCode = computed(() => authStore.currentUser?.userCode ?? 'ADMIN')
const heroDescription = computed(() =>
  route.path === '/admin/overview'
    ? t('admin.overviewDescription')
    : route.path.startsWith('/admin/users/') && route.path !== '/admin/users'
      ? t('admin.detailDescription')
      : t('admin.managementDescription'),
)
const activeSectionLabel = computed(() =>
  route.path === '/admin/overview'
    ? t('admin.overview')
    : route.path.startsWith('/admin/users/') && route.path !== '/admin/users'
      ? t('admin.userDetails')
      : t('admin.users'),
)
</script>

<template>
  <WorkbenchShell class="admin-workbench">
    <template #sidebar>
      <WorkbenchSidebar />
    </template>

    <template #main>
      <main class="admin-layout">
        <PageHeaderHero :eyebrow="$t('admin.title')" :title="$t('admin.governance')" :description="heroDescription">
          <template #actions>
            <div class="admin-hero-actions">
              <nav class="admin-local-nav" :aria-label="$t('admin.adminNavigation')">
                <RouterLink to="/admin/overview" active-class="is-active">{{ $t('admin.overview') }}</RouterLink>
                <RouterLink to="/admin/users" active-class="is-active">{{ $t('admin.users') }}</RouterLink>
                <RouterLink to="/account/security" active-class="is-active">{{ $t('auth.securityTitle') }}</RouterLink>
              </nav>
            </div>
          </template>
        </PageHeaderHero>

        <section class="admin-identity-strip" :aria-label="$t('admin.adminContext')">
          <article class="admin-identity-card">
            <span>{{ $t('admin.currentAdmin') }}</span>
            <strong>{{ currentUserName }}</strong>
            <small>{{ currentUserCode }}</small>
          </article>
          <article class="admin-identity-card">
            <span>{{ $t('admin.currentArea') }}</span>
            <strong>{{ activeSectionLabel }}</strong>
            <small>{{ $t('admin.visualBoundary') }}</small>
          </article>
          <article class="admin-identity-card">
            <span>{{ $t('admin.governancePrinciple') }}</span>
            <strong>Fail-fast</strong>
            <small>{{ $t('admin.backendRules') }}</small>
          </article>
        </section>

        <RouterView />
      </main>
    </template>
  </WorkbenchShell>
</template>
