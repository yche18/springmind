<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  fetchAdminUserDetail,
  resetAdminUserPassword,
  updateAdminUserStatus,
  type AdminUserItem,
  type UserStatus,
} from '../../../api/admin-user'
import { extractApiError } from '../../../api/http'
import { formatChinaDateTime } from '../../../utils/date-time'

const route = useRoute()
const router = useRouter()
const { t } = useI18n({ useScope: 'global' })

const user = ref<AdminUserItem | null>(null)
const pageError = ref('')
const pageFeedback = ref('')
const isLoading = ref(false)
const isActing = ref(false)

const userId = computed(() => Number(route.params.userId))
const detailRows = computed(() => {
  if (user.value === null) {
    return []
  }
  return [
    { label: t('common.userId'), value: String(user.value.userId) },
    { label: t('admin.accountCode'), value: user.value.userCode },
    { label: t('common.username'), value: user.value.username },
    { label: t('common.email'), value: user.value.email },
    { label: t('common.displayName'), value: user.value.displayName },
    { label: t('admin.systemRole'), value: user.value.systemRole },
    { label: t('admin.accountStatus'), value: user.value.status },
    { label: t('admin.passwordStatus'), value: user.value.mustChangePassword ? t('admin.pendingPassword') : t('common.normal') },
    { label: t('admin.lastLogin'), value: formatLastLogin(user.value.lastLoginAt) },
  ]
})

onMounted(() => {
  void loadUser()
})

async function loadUser() {
  if (!Number.isInteger(userId.value) || userId.value <= 0) {
    pageError.value = t('admin.invalidUserId')
    return
  }
  isLoading.value = true
  pageError.value = ''
  try {
    user.value = await fetchAdminUserDetail(userId.value)
  } catch (error) {
    pageError.value = extractApiError(error, t('errors.loadUser'))
  } finally {
    isLoading.value = false
  }
}

function formatLastLogin(value: string | null) {
  return formatChinaDateTime(value, t('common.never'))
}

async function handleStatusChange() {
  if (user.value === null) {
    return
  }
  const nextStatus: UserStatus = user.value.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  const actionLabel = t(nextStatus === 'DISABLED' ? 'admin.disableVerb' : 'admin.enableVerb')
  if (!window.confirm(t('admin.confirmStatus', { action: actionLabel, name: user.value.username }))) {
    return
  }

  await runDetailAction(t('admin.statusChangeFailed'), async () => {
    await updateAdminUserStatus(user.value!.userId, nextStatus)
    pageFeedback.value = t(nextStatus === 'DISABLED' ? 'admin.disabledUser' : 'admin.enabledUser', { name: user.value!.username })
  })
}

async function handleResetPassword() {
  if (user.value === null) {
    return
  }
  const newPassword = window.prompt(t('admin.newPasswordPrompt', { name: user.value.username }))
  if (newPassword === null) {
    return
  }

  await runDetailAction(t('admin.resetPasswordFailed'), async () => {
    await resetAdminUserPassword(user.value!.userId, newPassword)
    pageFeedback.value = t('admin.passwordReset', { name: user.value!.username })
  })
}

async function runDetailAction(fallbackMessage: string, action: () => Promise<void>) {
  isActing.value = true
  pageError.value = ''
  pageFeedback.value = ''
  try {
    await action()
    await loadUser()
  } catch (error) {
    pageError.value = extractApiError(error, fallbackMessage)
  } finally {
    isActing.value = false
  }
}
</script>

<template>
  <section class="admin-page-section admin-user-detail-page">
    <article class="admin-panel admin-panel--detail">
      <div class="admin-panel__header">
        <div>
          <p class="panel__eyebrow">{{ $t('admin.userDetails') }}</p>
          <h2>{{ user?.displayName ?? $t('admin.viewAccountDetails') }}</h2>
          <p class="admin-panel__description">
            {{ $t('admin.userDetailIntro') }}
          </p>
        </div>
        <div class="admin-panel__header-actions">
          <button class="ghost-button" type="button" @click="void loadUser()">{{ $t('admin.refreshDetails') }}</button>
          <button class="primary-button" type="button" @click="router.push('/admin/users')">{{ $t('admin.backToList') }}</button>
        </div>
      </div>

      <p v-if="pageError" class="feedback feedback--error">{{ pageError }}</p>
      <p v-if="pageFeedback" class="feedback feedback--success">{{ pageFeedback }}</p>
      <p v-else-if="isLoading" class="placeholder-text">{{ $t('admin.loadingDetails') }}</p>
      <div v-else-if="user === null" class="admin-empty-state">
        <p class="panel__eyebrow">{{ $t('admin.emptyData') }}</p>
        <h3>{{ $t('admin.noUserDetails') }}</h3>
        <p>{{ $t('admin.selectAgain') }}</p>
      </div>
      <div v-else class="admin-detail-grid">
        <article class="admin-detail-hero">
          <span>{{ $t('admin.accountIdentity') }}</span>
          <strong>{{ user.displayName }}</strong>
          <small>{{ user.email }}</small>
          <div class="admin-detail-hero__badges">
            <span class="admin-role-pill" :data-role="user.systemRole">{{ user.systemRole }}</span>
            <span class="admin-status" :data-status="user.status">{{ user.status }}</span>
            <span class="admin-security-pill" :data-tone="user.mustChangePassword ? 'warning' : 'normal'">
              {{ user.mustChangePassword ? $t('admin.pendingPassword') : $t('common.normal') }}
            </span>
          </div>
          <div class="admin-detail-hero__actions">
            <button class="ghost-button" type="button" :disabled="isActing" @click="handleStatusChange">
              {{ user.status === 'ACTIVE' ? $t('admin.disableAccount') : $t('admin.enableAccount') }}
            </button>
            <button class="primary-button" type="button" :disabled="isActing" @click="handleResetPassword">
              {{ isActing ? $t('common.processing') : $t('admin.resetPassword') }}
            </button>
          </div>
        </article>

        <article class="admin-detail-card">
          <div class="admin-detail-list">
            <div v-for="item in detailRows" :key="item.label" class="admin-detail-list__row">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </article>
      </div>
    </article>
  </section>
</template>
