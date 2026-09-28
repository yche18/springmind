<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  fetchAdminUsers,
  resetAdminUserPassword,
  updateAdminUserStatus,
  type AdminUserItem,
  type UserStatus,
} from '../../../api/admin-user'
import { extractApiError } from '../../../api/http'
import { formatChinaDateTime } from '../../../utils/date-time'

const router = useRouter()
const { t } = useI18n({ useScope: 'global' })
const users = ref<AdminUserItem[]>([])
const pageError = ref('')
const pageFeedback = ref('')
const isLoading = ref(false)
const actionUserIds = ref<Set<number>>(new Set())
const statusFilter = ref<'ALL' | UserStatus>('ALL')
const searchKeyword = ref('')

const activeUsers = computed(() => users.value.filter((item) => item.status === 'ACTIVE').length)
const disabledUsers = computed(() => users.value.filter((item) => item.status === 'DISABLED').length)
const mustChangePasswordUsers = computed(() => users.value.filter((item) => item.mustChangePassword).length)
const filteredUsers = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  return users.value.filter((item) => {
    const matchesStatus = statusFilter.value === 'ALL' || item.status === statusFilter.value
    if (!matchesStatus) {
      return false
    }
    if (!keyword) {
      return true
    }
    const haystack = [item.displayName, item.username, item.email, item.userCode]
      .join(' ')
      .toLowerCase()
    return haystack.includes(keyword)
  })
})
const resultSummary = computed(() => {
  if (filteredUsers.value.length === users.value.length) {
    return t('admin.displayedUsers', { count: filteredUsers.value.length })
  }
  return t('admin.matchedUsers', { total: users.value.length, matched: filteredUsers.value.length })
})

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

async function handleStatusChange(user: AdminUserItem) {
  const nextStatus: UserStatus = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  const actionLabel = t(nextStatus === 'DISABLED' ? 'admin.disableVerb' : 'admin.enableVerb')
  if (!window.confirm(t('admin.confirmStatus', { action: actionLabel, name: user.username }))) return

  await runUserAction(user.userId, t('admin.statusChangeFailed'), async () => {
    await updateAdminUserStatus(user.userId, nextStatus)
    pageFeedback.value = t(nextStatus === 'DISABLED' ? 'admin.disabledUser' : 'admin.enabledUser', { name: user.username })
  })
}

async function handleResetPassword(user: AdminUserItem) {
  const newPassword = window.prompt(t('admin.newPasswordPrompt', { name: user.username }))
  if (newPassword === null) return

  await runUserAction(user.userId, t('admin.resetPasswordFailed'), async () => {
    await resetAdminUserPassword(user.userId, newPassword)
    pageFeedback.value = t('admin.passwordReset', { name: user.username })
  })
}

async function runUserAction(userId: number, fallbackMessage: string, action: () => Promise<void>) {
  const nextActionUserIds = new Set(actionUserIds.value)
  nextActionUserIds.add(userId)
  actionUserIds.value = nextActionUserIds
  pageError.value = ''
  pageFeedback.value = ''

  try {
    await action()
    await loadUsers()
  } catch (error) {
    pageError.value = extractApiError(error, fallbackMessage)
  } finally {
    const finalActionUserIds = new Set(actionUserIds.value)
    finalActionUserIds.delete(userId)
    actionUserIds.value = finalActionUserIds
  }
}

function formatLastLogin(value: string | null) {
  return formatChinaDateTime(value, t('common.never'))
}

function updateStatusFilter(value: 'ALL' | UserStatus) {
  statusFilter.value = value
}
</script>

<template>
  <section class="admin-page-section admin-users-page">
    <article class="admin-panel admin-panel--hero">
      <div class="admin-panel__header">
        <div>
          <p class="panel__eyebrow">{{ $t('admin.userOverview') }}</p>
          <h2>{{ $t('admin.users') }}</h2>
          <p class="admin-panel__description">
            {{ $t('admin.usersIntro') }}
          </p>
        </div>
        <div class="admin-panel__header-actions">
          <button class="ghost-button" type="button" @click="void loadUsers()">{{ $t('admin.refreshList') }}</button>
        </div>
      </div>

      <div class="admin-stats">
        <article>
          <span>{{ $t('admin.activeUsers') }}</span>
          <strong>{{ activeUsers }}</strong>
          <small>{{ $t('admin.activeUsersHint') }}</small>
        </article>
        <article>
          <span>{{ $t('admin.disabledUsers') }}</span>
          <strong>{{ disabledUsers }}</strong>
          <small>{{ $t('admin.disabledUsersHint') }}</small>
        </article>
        <article>
          <span>{{ $t('admin.passwordChangeUsers') }}</span>
          <strong>{{ mustChangePasswordUsers }}</strong>
          <small>{{ $t('admin.passwordChangeUsersHint') }}</small>
        </article>
      </div>
    </article>

    <article class="admin-panel admin-panel--table">
      <div class="admin-panel__header admin-panel__header--stacked">
        <div>
          <p class="panel__eyebrow">{{ $t('admin.filterConditions') }}</p>
          <h2>{{ $t('admin.listFilter') }}</h2>
          <p class="admin-panel__description">{{ resultSummary }}</p>
        </div>
        <div class="admin-filter-bar" :aria-label="$t('admin.userFilter')">
          <label class="admin-filter-field">
            <span>{{ $t('common.status') }}</span>
            <div class="admin-filter-pills">
              <button
                class="admin-filter-pill"
                :class="{ 'is-active': statusFilter === 'ALL' }"
                type="button"
                @click="updateStatusFilter('ALL')"
              >
                {{ $t('admin.all') }}
              </button>
              <button
                class="admin-filter-pill"
                :class="{ 'is-active': statusFilter === 'ACTIVE' }"
                type="button"
                @click="updateStatusFilter('ACTIVE')"
              >
                {{ $t('admin.active') }}
              </button>
              <button
                class="admin-filter-pill"
                :class="{ 'is-active': statusFilter === 'DISABLED' }"
                type="button"
                @click="updateStatusFilter('DISABLED')"
              >
                {{ $t('common.disabled') }}
              </button>
            </div>
          </label>

          <label class="admin-filter-field admin-filter-field--search">
            <span>{{ $t('admin.searchLabel') }}</span>
            <input
              v-model="searchKeyword"
              type="search"
              maxlength="128"
              :placeholder="$t('admin.searchUsers')"
            />
          </label>
        </div>
      </div>

      <p v-if="pageFeedback" class="feedback feedback--success">{{ pageFeedback }}</p>
      <p v-if="pageError" class="feedback feedback--error">{{ pageError }}</p>
      <p v-if="isLoading" class="placeholder-text">{{ $t('admin.loadingUsers') }}</p>

      <div v-else-if="filteredUsers.length === 0" class="admin-empty-state">
        <p class="panel__eyebrow">{{ $t('admin.noResults') }}</p>
        <h3>{{ $t('admin.noMatchingUsers') }}</h3>
        <p>{{ $t('admin.noMatchingUsersHint') }}</p>
      </div>

      <div v-else class="admin-table-wrap">
        <table>
          <thead>
            <tr>
              <th>{{ $t('admin.user') }}</th><th>{{ $t('common.role') }}</th><th>{{ $t('admin.accountStatus') }}</th>
              <th>{{ $t('admin.passwordStatus') }}</th><th>{{ $t('admin.lastLogin') }}</th><th>{{ $t('common.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in filteredUsers" :key="user.userId">
              <td>
                <div class="admin-user-cell">
                  <strong>{{ user.displayName }}</strong>
                  <span>{{ user.username }}</span>
                  <small>{{ user.email }}</small>
                  <small>{{ $t('admin.userMetaId', { id: user.userId }) }}</small>
                  <small>{{ $t('admin.accountCodeMeta', { code: user.userCode }) }}</small>
                  <button
                    class="admin-user-cell__link"
                    type="button"
                    @click="router.push(`/admin/users/${user.userId}`)"
                  >
                    {{ $t('common.details') }}
                  </button>
                </div>
              </td>
              <td>
                <span class="admin-role-pill" :data-role="user.systemRole">{{ user.systemRole }}</span>
              </td>
              <td>
                <span class="admin-status" :data-status="user.status">{{ user.status }}</span>
              </td>
              <td>
                <span
                  class="admin-security-pill"
                  :data-tone="user.mustChangePassword ? 'warning' : 'normal'"
                >
                  {{ user.mustChangePassword ? $t('admin.pendingPassword') : $t('common.normal') }}
                </span>
              </td>
              <td>{{ formatLastLogin(user.lastLoginAt) }}</td>
              <td>
                <div class="admin-actions">
                  <button
                    class="ghost-button"
                    type="button"
                    :disabled="actionUserIds.has(user.userId)"
                    @click="handleStatusChange(user)"
                  >
                    {{ user.status === 'ACTIVE' ? $t('admin.disableVerb') : $t('admin.enableVerb') }}
                  </button>
                  <button
                    class="ghost-button"
                    type="button"
                    :disabled="actionUserIds.has(user.userId)"
                    @click="handleResetPassword(user)"
                  >
                    {{ $t('admin.resetPassword') }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>
  </section>
</template>
