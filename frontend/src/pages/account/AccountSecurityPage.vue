<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AccountPasswordForm from '../../components/AccountPasswordForm.vue'
import AuthSplitShell from '../../components/auth/AuthSplitShell.vue'
import SessionLogoutButton from '../../components/SessionLogoutButton.vue'
import '../../assets/account-security-page.css'
import { useAuthStore } from '../../stores/auth'

const authStore = useAuthStore()
const router = useRouter()

const currentUser = computed(() => authStore.currentUser)
const mustChangePassword = computed(() => currentUser.value?.mustChangePassword === true)
const returnPath = computed(() => authStore.resolveLandingPath())

onMounted(() => {
  void ensureAuthenticated()
})

async function ensureAuthenticated() {
  const user = await authStore.bootstrap()
  if (user === null) {
    await router.replace('/login?redirect=/account/security')
  }
}

async function handlePasswordChanged(payload: { wasMandatory: boolean }) {
  if (payload.wasMandatory) {
    await router.replace(authStore.resolveLandingPath())
  }
}
</script>

<template>
  <AuthSplitShell
    class="account-security-page"
    eyebrow="Account security"
    :title="$t('auth.securityDescription')"
    :description="$t('auth.changePassword')"
  >
    <template #brand>
      <div class="security-brand-stack">
        <div class="security-brand-points">
          <article class="security-brand-point">
            <strong>{{ $t('auth.mustChangePassword') }}</strong>
            <span>{{ $t('auth.passwordRule') }}</span>
          </article>
          <article class="security-brand-point">
            <strong>{{ $t('nav.workbench') }}</strong>
            <span>{{ $t('auth.brandIntro') }}</span>
          </article>
        </div>

        <div v-if="currentUser" class="security-brand-summary">
          <span class="security-brand-summary__eyebrow">{{ $t('common.role') }}</span>
          <strong>{{ currentUser.displayName }}</strong>
          <span>{{ currentUser.userCode }} · {{ currentUser.systemRole }}</span>
        </div>
      </div>
    </template>

    <template #actions>
      <div class="security-shell-actions">
        <RouterLink v-if="!mustChangePassword" class="security-shell-back" :to="returnPath">
          {{ $t('nav.workbench') }}
        </RouterLink>
        <SessionLogoutButton class="security-shell-logout" />
      </div>
    </template>

    <AccountPasswordForm show-user-summary @completed="handlePasswordChanged" />
  </AuthSplitShell>
</template>
