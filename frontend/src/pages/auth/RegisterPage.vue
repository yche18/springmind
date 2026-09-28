<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { extractApiError } from '../../api/http'
import { register } from '../../api/auth'
import '../../assets/login-page.css'
import AuthSplitShell from '../../components/auth/AuthSplitShell.vue'

const router = useRouter()
const { t } = useI18n({ useScope: 'global' })

const form = reactive({
  username: '',
  email: '',
  displayName: '',
  password: '',
  confirmPassword: '',
})

const pageError = ref('')
const isSubmitting = ref(false)

async function handleSubmit() {
  pageError.value = ''

  if (
    form.username.trim().length === 0 ||
    form.email.trim().length === 0 ||
    form.displayName.trim().length === 0 ||
    form.password.length === 0
  ) {
    pageError.value = t('auth.registerRequired')
    return
  }

  if (form.password !== form.confirmPassword) {
    pageError.value = t('auth.passwordMismatch')
    return
  }

  isSubmitting.value = true
  try {
    await register({
      username: form.username.trim(),
      email: form.email.trim(),
      displayName: form.displayName.trim(),
      password: form.password,
    })
    await router.replace({ path: '/login', query: { registered: '1' } })
  } catch (error) {
    pageError.value = extractApiError(error, t('errors.requestFailed'))
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <AuthSplitShell
    class="login-page auth-page--register"
    eyebrow="Create business account"
    :title="$t('auth.createBusinessAccount')"
    :description="$t('auth.registerDescription')"
  >
    <template #brand>
      <div class="auth-brand-stack">
        <ul class="auth-brand-list" :aria-label="$t('auth.registerRuleTitle')">
          <li>
            <strong>{{ $t('auth.registerRuleDefaultRoleTitle') }}</strong>
            <span>{{ $t('auth.registerRuleDefaultRoleBody') }}</span>
          </li>
          <li>
            <strong>{{ $t('auth.registerRuleNoGroupTitle') }}</strong>
            <span>{{ $t('auth.registerRuleNoGroupBody') }}</span>
          </li>
          <li>
            <strong>{{ $t('auth.registerRulePasswordTitle') }}</strong>
            <span>{{ $t('auth.registerRulePasswordBody') }}</span>
          </li>
        </ul>
      </div>
    </template>

    <section class="auth-panel" aria-labelledby="register-title">
      <div class="auth-panel__header">
        <p class="auth-panel__eyebrow">Create account</p>
        <h2 id="register-title" class="auth-panel__title">{{ $t('auth.register') }}</h2>
        <p class="auth-panel__hint">{{ $t('auth.registerHint') }}</p>
      </div>

      <form class="auth-form" @submit.prevent="handleSubmit">
        <label class="auth-form__field">
          <span>{{ $t('common.username') }}</span>
          <input v-model="form.username" type="text" autocomplete="username" maxlength="64" :placeholder="$t('auth.usernameExample')" />
        </label>

        <label class="auth-form__field">
          <span>{{ $t('common.email') }}</span>
          <input v-model="form.email" type="email" autocomplete="email" maxlength="128" placeholder="user001@example.com" />
        </label>

        <label class="auth-form__field">
          <span>{{ $t('common.displayName') }}</span>
          <input v-model="form.displayName" type="text" maxlength="128" :placeholder="$t('auth.displayNameExample')" />
        </label>

        <label class="auth-form__field">
          <span>{{ $t('auth.password') }}</span>
          <input v-model="form.password" type="password" autocomplete="new-password" maxlength="128" :placeholder="$t('auth.passwordExample')" />
        </label>

        <label class="auth-form__field">
          <span>{{ $t('auth.confirmPassword') }}</span>
          <input v-model="form.confirmPassword" type="password" autocomplete="new-password" maxlength="128" :placeholder="$t('auth.confirmPasswordPlaceholder')" />
        </label>

        <p v-if="pageError" class="auth-form__error" role="alert">
          {{ pageError }}
        </p>

        <button class="auth-form__submit" type="submit" :disabled="isSubmitting">
          {{ isSubmitting ? $t('auth.registering') : $t('auth.register') }}
        </button>
      </form>

      <div class="auth-panel__footer">
        <span>{{ $t('auth.alreadyRegistered') }}</span>
        <RouterLink class="auth-page-link" to="/login">{{ $t('auth.backToLogin') }}</RouterLink>
      </div>
    </section>
  </AuthSplitShell>
</template>
