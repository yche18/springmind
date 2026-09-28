<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { fetchAdminUsers, type AdminUserItem } from '../../api/admin-user'
import { extractApiError } from '../../api/http'
import { useAuthStore } from '../../stores/auth'
import { formatChinaDateTime } from '../../utils/date-time'

const router = useRouter()
const authStore = useAuthStore()
const { t } = useI18n({ useScope: 'global' })

const users = ref<AdminUserItem[]>([])
const pageError = ref('')
const isLoading = ref(false)

const totalUsers = computed(() => users.value.length)
const activeUsers = computed(() => users.value.filter((item) => item.status === 'ACTIVE').length)
const disabledUsers = computed(() => users.value.filter((item) => item.status === 'DISABLED').length)
const mustChangePasswordUsers = computed(() =>
  users.value.filter((item) => item.mustChangePassword).length,
)
const adminUsers = computed(() => users.value.filter((item) => item.systemRole === 'ADMIN').length)
const recentUsers = computed(() => users.value.slice(0, 5))
const currentAdminName = computed(() => authStore.currentUser?.displayName ?? t('nav.systemAdmin'))

onMounted(() => {
  void loadUsers()
})

async function loadUsers() {
  isLoading.value = true
  pageError.value = ''
  try {
    users.value = await fetchAdminUsers()
  } catch (error) {
    pageError.value = extractApiError(error, t('errors.loadUsers'))
  } finally {
    isLoading.value = false
  }
}

function goToUsers() {
  void router.push('/admin/users')
}

function formatLastLogin(value: string | null) {
  return formatChinaDateTime(value, t('common.never'))
}
</script>

<template>
  <section class="admin-page-section admin-overview-page">
    <article class="admin-panel admin-panel--hero">
      <div class="admin-panel__header">
        <div>
          <p class="panel__eyebrow">{{ $t('admin.overview') }}</p>
          <h2>{{ $t('admin.overview') }}</h2>
          <p class="admin-panel__description">
            {{ $t('admin.overviewIntro') }}
          </p>
        </div>
        <div class="admin-panel__header-actions">
          <button class="ghost-button" type="button" @click="void loadUsers()">{{ $t('admin.refreshData') }}</button>
          <button class="primary-button" type="button" @click="goToUsers">{{ $t('admin.enterUsers') }}</button>
        </div>
      </div>

      <div class="admin-stats">
        <article>
          <span>{{ $t('admin.totalAccounts') }}</span>
          <strong>{{ totalUsers }}</strong>
          <small>{{ $t('admin.totalAccountsHint') }}</small>
        </article>
        <article>
          <span>{{ $t('admin.activeAccounts') }}</span>
          <strong>{{ activeUsers }}</strong>
          <small>{{ $t('admin.activeAccountsHint') }}</small>
        </article>
        <article>
          <span>{{ $t('admin.disabledAccounts') }}</span>
          <strong>{{ disabledUsers }}</strong>
          <small>{{ $t('admin.disabledAccountsHint') }}</small>
        </article>
      </div>
    </article>

    <div class="admin-overview-grid">
      <article class="admin-panel">
        <div class="admin-panel__header">
          <div>
            <p class="panel__eyebrow">{{ $t('admin.governanceFocus') }}</p>
            <h2>{{ $t('admin.currentFocus') }}</h2>
          </div>
        </div>

        <div class="admin-overview-highlights">
          <article>
            <span>{{ $t('admin.passwordChangeAccounts') }}</span>
            <strong>{{ mustChangePasswordUsers }}</strong>
            <small>{{ $t('admin.passwordChangeHint') }}</small>
          </article>
          <article>
            <span>{{ $t('admin.adminAccounts') }}</span>
            <strong>{{ adminUsers }}</strong>
            <small>{{ $t('admin.adminAccountsHint') }}</small>
          </article>
          <article>
            <span>{{ $t('admin.onDutyAdmin') }}</span>
            <strong>{{ currentAdminName }}</strong>
            <small>{{ $t('admin.onDutyHint') }}</small>
          </article>
        </div>
      </article>

      <article class="admin-panel">
        <div class="admin-panel__header">
          <div>
            <p class="panel__eyebrow">{{ $t('admin.quickEntry') }}</p>
            <h2>{{ $t('admin.commonActions') }}</h2>
          </div>
        </div>

        <div class="admin-overview-actions">
          <button class="admin-overview-action" type="button" @click="goToUsers">
            <strong>{{ $t('admin.viewUserList') }}</strong>
            <span>{{ $t('admin.viewUserListHint') }}</span>
          </button>
          <button class="admin-overview-action" type="button" @click="router.push('/account/security')">
            <strong>{{ $t('admin.checkAccountSecurity') }}</strong>
            <span>{{ $t('admin.checkSecurityHint') }}</span>
          </button>
          <article class="admin-overview-highlights__note">
            <strong>{{ $t('admin.governanceBoundary') }}</strong>
            <span>{{ $t('admin.governanceBoundaryHint') }}</span>
          </article>
        </div>
      </article>
    </div>

    <article class="admin-panel admin-panel--table">
      <div class="admin-panel__header">
        <div>
          <p class="panel__eyebrow">{{ $t('admin.recentAccounts') }}</p>
          <h2>{{ $t('admin.recentView') }}</h2>
          <p class="admin-panel__description">{{ $t('admin.recentViewHint') }}</p>
        </div>
      </div>

      <p v-if="pageError" class="feedback feedback--error">{{ pageError }}</p>
      <p v-else-if="isLoading" class="placeholder-text">{{ $t('admin.loadingOverview') }}</p>
      <div v-else-if="recentUsers.length === 0" class="admin-empty-state">
        <p class="panel__eyebrow">{{ $t('admin.emptyData') }}</p>
        <h3>{{ $t('admin.noAccounts') }}</h3>
        <p>{{ $t('admin.noAccountsHint') }}</p>
      </div>
      <div v-else class="admin-table-wrap">
        <table>
          <thead>
            <tr>
              <th>{{ $t('admin.user') }}</th>
              <th>{{ $t('common.role') }}</th>
              <th>{{ $t('admin.accountStatus') }}</th>
              <th>{{ $t('admin.passwordStatus') }}</th>
              <th>{{ $t('admin.lastLogin') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in recentUsers" :key="user.userId">
              <td>
                <div class="admin-user-cell">
                  <strong>{{ user.displayName }}</strong>
                  <span>{{ user.username }}</span>
                  <small>{{ user.email }}</small>
                </div>
              </td>
              <td>
                <span class="admin-role-pill" :data-role="user.systemRole">{{ user.systemRole }}</span>
              </td>
              <td>
                <span class="admin-status" :data-status="user.status">{{ user.status }}</span>
              </td>
              <td>
                <span class="admin-security-pill" :data-tone="user.mustChangePassword ? 'warning' : 'normal'">
                  {{ user.mustChangePassword ? $t('admin.pendingPassword') : $t('common.normal') }}
                </span>
              </td>
              <td>{{ formatLastLogin(user.lastLoginAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>
  </section>
</template>
