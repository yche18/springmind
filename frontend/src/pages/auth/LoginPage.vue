<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { extractApiError } from '../../api/http'
import '../../assets/login-page.css'
import AuthSplitShell from '../../components/auth/AuthSplitShell.vue'
import { useAuthStore } from '../../stores/auth'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()
const { t } = useI18n({ useScope: 'global' })

const form = reactive({
  loginId: '',
  password: '',
})

const pageError = ref('')
const pageNotice = ref('')
const isBootstrapping = ref(false)

onMounted(() => {
  pageNotice.value = route.query.registered === '1' ? t('auth.registerSuccess') : ''
  void redirectAuthenticatedUser()
})

async function redirectAuthenticatedUser() {
  isBootstrapping.value = true
  try {
    const currentUser = await authStore.bootstrap()
    if (currentUser !== null) {
      await router.replace(authStore.resolveLandingPath(readRedirectQuery()))
    }
  } finally {
    isBootstrapping.value = false
  }
}

async function handleSubmit() {
  pageError.value = ''

  if (form.loginId.trim().length === 0 || form.password.length === 0) {
    pageError.value = t('auth.loginRequired')
    return
  }

  try {
    await authStore.login({
      loginId: form.loginId,
      password: form.password,
    })
    await router.replace(authStore.resolveLandingPath(readRedirectQuery()))
  } catch (error) {
    pageError.value = extractApiError(error, t('auth.loginFailed'))
  }
}

function readRedirectQuery(): string | null {
  const redirect = route.query.redirect
  return Array.isArray(redirect) ? (redirect[0] ?? null) : (redirect ?? null)
}
</script>

<template>
  <AuthSplitShell
    class="login-page auth-page--login"
    :eyebrow="$t('auth.brandEyebrow')"
    :title="$t('auth.brandTitle')"
    :description="$t('auth.brandIntro')"
  >
    <template #brand>
      <div class="auth-brand-stack">
        <div class="auth-brand-chip-row" :aria-label="$t('auth.loginCapabilityLabel')">
          <span>JWT access token</span>
          <span>HttpOnly refresh cookie</span>
          <span>Role aware routing</span>
        </div>

        <div class="auth-brand-stat-grid">
          <article class="auth-brand-stat">
            <strong>01</strong>
            <span>{{ $t('auth.loginBenefitRouting') }}</span>
          </article>
          <article class="auth-brand-stat">
            <strong>02</strong>
            <span>{{ $t('auth.loginBenefitSession') }}</span>
          </article>
          <article class="auth-brand-stat">
            <strong>03</strong>
            <span>{{ $t('auth.loginBenefitPassword') }}</span>
          </article>
        </div>
      </div>
    </template>

    <section class="auth-panel" aria-labelledby="login-title">
      <div class="auth-panel__header">
        <p class="auth-panel__eyebrow">{{ $t('auth.secureSignIn') }}</p>
        <h2 id="login-title" class="auth-panel__title">{{ $t('auth.signIn') }}</h2>
        <p class="auth-panel__hint">{{ $t('auth.loginHint') }}</p>
      </div>

      <form class="auth-form" @submit.prevent="handleSubmit">
        <label class="auth-form__field">
          <span>{{ $t('auth.loginId') }}</span>
          <input
            v-model="form.loginId"
            type="text"
            autocomplete="username"
            maxlength="100"
            :placeholder="$t('auth.loginId')"
            :disabled="authStore.isAuthenticating || isBootstrapping"
          />
        </label>

        <label class="auth-form__field">
          <span>{{ $t('auth.password') }}</span>
          <input
            v-model="form.password"
            type="password"
            autocomplete="current-password"
            maxlength="128"
            :placeholder="$t('auth.passwordPlaceholder')"
            :disabled="authStore.isAuthenticating || isBootstrapping"
          />
        </label>

        <p v-if="pageError" class="auth-form__error" role="alert">
          {{ pageError }}
        </p>
        <p v-if="pageNotice" class="auth-form__notice">
          {{ pageNotice }}
        </p>

        <button
          class="auth-form__submit"
          type="submit"
          :disabled="authStore.isAuthenticating || isBootstrapping"
        >
          {{ authStore.isAuthenticating ? $t('auth.signingIn') : $t('auth.enterSystem') }}
        </button>
      </form>

      <div class="auth-panel__footer">
        <span>{{ $t('auth.noAccount') }}</span>
        <RouterLink class="auth-page-link" to="/register">{{ $t('auth.createNewAccount') }}</RouterLink>
      </div>
    </section>
  </AuthSplitShell>
</template>
