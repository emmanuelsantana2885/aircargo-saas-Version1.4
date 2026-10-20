<template>
  <div class="auth-shell">
    <div class="auth-card">
      <div v-if="route.query.idle === '1'" class="mb-4 p-3 rounded-xl text-sm" style="background: var(--warn-bg); color: var(--warn); border: 1px solid var(--warn)">
          {{ t('idle.expired') }}
        </div>
        <template v-if="step === 'credentials'">
        <div class="text-center mb-6">
          <div class="auth-logo flex items-center justify-center mx-auto mb-3">
            <component :is="icons.PlaneDeparture" :size="30" color="white" :stroke-width="2" />
          </div>
          <h1 class="auth-title font-bold" style="color: var(--text)">{{ t('login.brand') }}</h1>
          <p class="text-base mt-1" style="color: var(--muted)">{{ t('login.tagline') }}</p>
        </div>

        <form @submit.prevent="handleLogin" class="space-y-4" novalidate>
          <div class="auth-field">
            <label for="login-email" class="block text-sm font-medium mb-1" style="color: var(--text)">{{ t('login.email') }}</label>
            <div class="auth-control">
              <span class="auth-control-icon" aria-hidden="true">
                <component :is="icons.Mail" :size="16" :stroke-width="2" />
              </span>
              <input
                id="login-email"
                v-model="loginEmail"
                type="email"
                required
                name="email"
                autocomplete="username"
                autocapitalize="none"
                spellcheck="false"
                :placeholder="t('login.emailPlaceholder')"
                class="w-full rounded text-base outline-none transition-all"
                style="background: var(--bg); color: var(--text)"
                :disabled="loading"
              />
            </div>
          </div>

          <div v-if="needsPassword" class="auth-field">
            <label for="login-password" class="block text-sm font-medium mb-1" style="color: var(--text)">{{ t('login.password') }}</label>
            <div class="auth-control">
              <span class="auth-control-icon" aria-hidden="true">
                <component :is="icons.Lock" :size="16" :stroke-width="2" />
              </span>
              <input
                id="login-password"
                v-model="password"
                :type="showPassword ? 'text' : 'password'"
                required
                name="password"
                autocomplete="current-password"
                :placeholder="t('login.passwordPlaceholder')"
                class="w-full rounded text-base outline-none transition-all"
                style="background: var(--bg); color: var(--text); padding-right: 42px"
                :disabled="loading"
                @keyup="detectCapsLock"
                @keydown="detectCapsLock"
              />
              <button
                type="button"
                class="auth-control-btn"
                :aria-label="showPassword ? t('login.hidePassword') : t('login.showPassword')"
                :title="showPassword ? t('login.hidePassword') : t('login.showPassword')"
                @click="showPassword = !showPassword"
              >
                <component :is="showPassword ? icons.EyeOff : icons.Eye" :size="16" :stroke-width="2" />
              </button>
            </div>
            <p v-if="capsLockOn" class="auth-hint auth-hint-warn mt-1.5">
              <component :is="icons.ShieldAlert" :size="13" :stroke-width="2" class="shrink-0" />
              {{ t('login.capsLock') }}
            </p>
          </div>

          <label v-if="needsPassword" class="auth-remember">
            <input type="checkbox" v-model="rememberEmail" />
            <span>{{ t('login.rememberEmail') }}</span>
          </label>

          <p v-if="errorMsg" class="auth-alert" role="alert">
            <component :is="icons.ShieldAlert" :size="15" :stroke-width="2" class="shrink-0 mt-px" />
            <span>{{ errorMsg }}</span>
          </p>

          <button
            type="submit"
            :disabled="loading || !loginEmail || (needsPassword && !password)"
            class="w-full py-2.5 rounded text-base font-semibold transition-all"
            :class="loading ? 'opacity-60' : 'hover:brightness-110 active:scale-[0.98]'"
            style="background: var(--accent); color: white"
          >
            <span v-if="loading" class="inline-flex items-center gap-2">
              <span class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
              {{ t('login.signingIn') }}
            </span>
            <span v-else>{{ t('login.loginBtn') }}</span>
          </button>

          <p v-if="showSetupLink" class="text-xs text-center">
            <a href="#" @click.prevent="goToSetPassword" style="color: var(--accent)" class="underline">
              {{ t('login.setPasswordLink') }}
            </a>
          </p>
        </form>
        <p class="text-center text-[11px] mt-4" style="color: var(--muted)">
          <router-link to="/privacy" class="hover:underline">{{ t('privacy.link') }}</router-link>
        </p>
      </template>

      <template v-if="step === 'mfa'">
        <div class="text-center mb-6">
          <div class="w-12 h-12 rounded-lg flex items-center justify-center mx-auto mb-3" style="background: var(--accent)">
            <component :is="icons.ShieldLock" :size="28" color="white" :stroke-width="2" />
          </div>
          <h1 class="text-xl font-bold" style="color: var(--text)">{{ t('login.mfa.title') }}</h1>
          <p class="text-base mt-1" style="color: var(--muted)">{{ t('login.mfa.subtitle') }}</p>
        </div>

        <div
          v-if="mfaReenrollmentNeeded"
          class="mb-4 px-3 py-2.5 rounded-lg border text-xs leading-relaxed"
          :style="{
            background: 'var(--warn-bg, rgba(245,158,11,0.12))',
            borderColor: 'rgba(245,158,11,0.4)',
            color: 'var(--warn-text, #92610a)'
          }"
        >
          <div class="flex items-start gap-2">
            <component :is="icons.ShieldAlert" :size="18" color="#d97706" :stroke-width="2" class="shrink-0 mt-px" />
            <div>
              <p class="font-semibold mb-0.5" style="color: #92400e">{{ t('login.mfa.reenrollWarning') }}</p>
              <p>
                {{ mfaReason === 'reset' ? t('login.mfa.reasonReset') : t('login.mfa.reasonExpired') }}
              </p>
            </div>
          </div>
        </div>

        <form @submit.prevent="handleMfa" class="space-y-4">
          <div>
            <label class="block text-sm font-medium mb-1" style="color: var(--text)">{{ t('login.mfa.codeLabel') }}</label>
            <input
              v-model="totpCode"
              type="text"
              inputmode="numeric"
              pattern="[0-9]*"
              maxlength="6"
              required
              placeholder="000000"
              class="w-full px-3 py-2.5 rounded text-base text-center font-mono tracking-[0.5em] outline-none transition-all border-slate-300"
              style="background: var(--bg); color: var(--text); font-size: 18px"
              :disabled="loading"
              autofocus
            />
          </div>

          <button
            type="submit"
            :disabled="loading || totpCode.length !== 6"
            class="w-full py-2.5 rounded text-base font-semibold transition-all"
            :class="loading ? 'opacity-60' : 'hover:brightness-110 active:scale-[0.98]'"
            style="background: var(--accent); color: white"
          >
            <span v-if="loading" class="inline-flex items-center gap-2">
              <span class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
              {{ t('login.mfa.verifying') }}
            </span>
            <span v-else>{{ t('login.mfa.verify') }}</span>
          </button>

          <p v-if="errorMsg" class="text-sm text-center" style="color: var(--muted)">{{ errorMsg }}</p>

          <button
            @click="openMfaRecovery"
            type="button"
            class="w-full py-1 rounded text-xs font-medium transition-all hover:brightness-110"
            style="background: transparent; color: var(--accent)"
          >
            {{ t('login.mfa.lostDevice') }}
          </button>

          <button
            @click="handleBackToLogin"
            type="button"
            class="w-full py-1.5 rounded text-xs font-medium transition-all hover:brightness-110"
            style="background: transparent; color: var(--muted)"
          >
            {{ t('common.back') }}
          </button>
        </form>
      </template>

      <template v-if="step === 'mfa-recovery'">
        <div class="text-center mb-6">
          <div class="w-12 h-12 rounded-lg flex items-center justify-center mx-auto mb-3" style="background: var(--accent)">
            <component :is="icons.ShieldAlert" :size="28" color="white" :stroke-width="2" />
          </div>
          <h1 class="text-xl font-bold" style="color: var(--text)">{{ t('login.mfa.recovery.title') }}</h1>
          <p class="text-base mt-1" style="color: var(--muted)">{{ t('login.mfa.recovery.subtitle') }}</p>
        </div>

        <form v-if="mfaRecoveryPhase === 'request'" @submit.prevent="handleRecoveryRequest" class="space-y-4">
          <div>
            <label class="block text-sm font-medium mb-1" style="color: var(--text)">{{ t('login.mfa.recovery.emailLabel') }}</label>
            <input
              v-model="mfaRecoveryEmail"
              type="email"
              required
              :placeholder="t('login.emailPlaceholder')"
              class="w-full px-3 py-2.5 rounded text-base outline-none transition-all border-slate-300"
              style="background: var(--bg); color: var(--text)"
              :disabled="loading"
              autofocus
            />
          </div>

          <button
            type="submit"
            :disabled="loading || !mfaRecoveryEmail"
            class="w-full py-2.5 rounded text-base font-semibold transition-all"
            :class="loading ? 'opacity-60' : 'hover:brightness-110 active:scale-[0.98]'"
            style="background: var(--accent); color: white"
          >
            <span v-if="loading" class="inline-flex items-center gap-2">
              <span class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
              {{ t('login.mfa.recovery.sending') }}
            </span>
            <span v-else>{{ t('login.mfa.recovery.sendCode') }}</span>
          </button>

          <p v-if="errorMsg" class="text-sm text-center" style="color: var(--muted)">{{ errorMsg }}</p>

          <div
            v-if="!mfaRecoveryMailAvailable"
            class="px-3 py-2.5 rounded-lg border text-xs leading-relaxed"
            style="background: #fffbeb; border-color: #fcd34d; color: #92400e"
          >
            {{ t('login.mfa.recovery.mailDisabled') }}
          </div>

          <button
            @click="backToMfa"
            type="button"
            class="w-full py-1.5 rounded text-xs font-medium transition-all hover:brightness-110"
            style="background: transparent; color: var(--muted)"
          >
            {{ t('login.mfa.recovery.backToMfa') }}
          </button>
        </form>

        <form v-else @submit.prevent="handleRecoveryConfirm" class="space-y-4">
          <div
            v-if="!mfaRecoveryMailAvailable"
            class="px-3 py-2.5 rounded-lg border text-xs leading-relaxed"
            style="background: #fffbeb; border-color: #fcd34d; color: #92400e"
          >
            {{ t('login.mfa.recovery.mailDisabled') }}
          </div>
          <div
            v-else
            class="px-3 py-2.5 rounded-lg border text-xs leading-relaxed"
            style="background: var(--bg); border-color: var(--border); color: var(--muted)"
          >
            {{ t('login.mfa.recovery.sentMessage') }}
          </div>

          <div>
            <label class="block text-sm font-medium mb-1" style="color: var(--text)">{{ t('login.mfa.recovery.codeLabel') }}</label>
            <input
              v-model="mfaRecoveryCode"
              type="text"
              inputmode="numeric"
              pattern="[0-9]*"
              maxlength="6"
              required
              placeholder="000000"
              class="w-full px-3 py-2.5 rounded text-base text-center font-mono tracking-[0.5em] outline-none transition-all border-slate-300"
              style="background: var(--bg); color: var(--text); font-size: 18px"
              :disabled="loading"
              autofocus
            />
          </div>

          <button
            type="submit"
            :disabled="loading || mfaRecoveryCode.length !== 6"
            class="w-full py-2.5 rounded text-base font-semibold transition-all"
            :class="loading ? 'opacity-60' : 'hover:brightness-110 active:scale-[0.98]'"
            style="background: var(--accent); color: white"
          >
            <span v-if="loading" class="inline-flex items-center gap-2">
              <span class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
              {{ t('login.mfa.recovery.confirming') }}
            </span>
            <span v-else>{{ t('login.mfa.recovery.confirm') }}</span>
          </button>

          <p v-if="errorMsg" class="text-sm text-center" style="color: var(--muted)">{{ errorMsg }}</p>

          <button
            @click="backToMfa"
            type="button"
            class="w-full py-1.5 rounded text-xs font-medium transition-all hover:brightness-110"
            style="background: transparent; color: var(--muted)"
          >
            {{ t('login.mfa.recovery.backToMfa') }}
          </button>
        </form>
      </template>

      <template v-if="step === 'mfa-enroll'">
        <div class="text-center mb-6">
          <div class="w-12 h-12 rounded-lg flex items-center justify-center mx-auto mb-3" style="background: var(--accent)">
            <component :is="icons.ShieldLock" :size="28" color="white" :stroke-width="2" />
          </div>
          <h1 class="text-xl font-bold" style="color: var(--text)">{{ t('login.mfaEnroll.title') }}</h1>
          <p class="text-base mt-1" style="color: var(--muted)">{{ t('login.mfaEnroll.subtitle') }}</p>
        </div>

        <div
          v-if="mfaReason !== 'required'"
          class="mb-4 px-3 py-2.5 rounded-lg border text-xs leading-relaxed"
          :style="{
            background: 'var(--warn-bg, rgba(245,158,11,0.12))',
            borderColor: 'rgba(245,158,11,0.4)',
            color: 'var(--warn-text, #92610a)'
          }"
        >
          <div class="flex items-start gap-2">
            <component :is="icons.ShieldAlert" :size="18" color="#d97706" :stroke-width="2" class="shrink-0 mt-px" />
            <div>
              <p class="font-semibold mb-0.5" style="color: #92400e">{{ t('login.mfaEnroll.securityWarning') }}</p>
              <p>
                {{ mfaReason === 'reset' ? t('login.mfaEnroll.reasonReset') : t('login.mfaEnroll.reasonExpired') }}
              </p>
            </div>
          </div>
        </div>

        <form @submit.prevent="handleEnrollEnable" class="space-y-4">
          <div class="text-center">
            <div class="inline-block p-3 rounded-lg border border-strong bg-surface">
              <img v-if="enrollOtpAuthUrl"
                :src="`https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=${encodeURIComponent(enrollOtpAuthUrl)}`"
                alt="QR Code" class="w-[180px] h-[180px]" />
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium mb-1" style="color: var(--text)">{{ t('login.mfaEnroll.secret') }}</label>
            <code class="block w-full px-3 py-2.5 rounded text-base font-mono break-all text-center"
              style="background: var(--bg); color: var(--text); border: 1px solid var(--border)">{{ enrollSecret }}</code>
          </div>

          <div>
            <label class="block text-sm font-medium mb-1" style="color: var(--text)">{{ t('login.mfa.codeLabel') }}</label>
            <input
              v-model="enrollCode"
              type="text"
              inputmode="numeric"
              pattern="[0-9]*"
              maxlength="6"
              required
              placeholder="000000"
              class="w-full px-3 py-2.5 rounded text-base text-center font-mono tracking-[0.5em] outline-none transition-all border-slate-300"
              style="background: var(--bg); color: var(--text); font-size: 18px"
              :disabled="loading"
              autofocus
            />
          </div>

          <p v-if="errorMsg" class="text-sm text-center" style="color: var(--muted)">{{ errorMsg }}</p>

          <button
            type="submit"
            :disabled="loading || enrollCode.length !== 6"
            class="w-full py-2.5 rounded text-base font-semibold transition-all"
            :class="loading ? 'opacity-60' : 'hover:brightness-110 active:scale-[0.98]'"
            style="background: var(--accent); color: white"
          >
            <span v-if="loading" class="inline-flex items-center gap-2">
              <span class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
              {{ t('login.mfaEnroll.enabling') }}
            </span>
            <span v-else>{{ t('login.mfaEnroll.enable') }}</span>
          </button>

          <button
            @click="handleBackToLogin"
            class="w-full py-1.5 rounded text-xs font-medium transition-all hover:brightness-110"
            style="background: transparent; color: var(--muted)"
          >
            {{ t('common.back') }}
          </button>
        </form>
      </template>

      <template v-if="step === 'site-select'">
        <div class="text-center mb-6">
          <div class="w-12 h-12 rounded-lg flex items-center justify-center mx-auto mb-3" style="background: var(--accent)">
            <component :is="icons.BuildingStore" :size="28" color="white" :stroke-width="2" />
          </div>
          <h1 class="text-xl font-bold" style="color: var(--text)">{{ t('login.stepSite.title') }}</h1>
          <p class="text-base mt-1" style="color: var(--muted)">{{ t('login.stepSite.subtitle') }}</p>
        </div>

        <div class="space-y-3">
          <div>
            <label class="block text-sm font-medium mb-1" style="color: var(--text)">{{ t('sidebar.siteLabel') }}</label>
            <select
              v-model="selectedSite"
              class="w-full px-3 py-2.5 rounded text-base outline-none border-slate-300"
              style="background: var(--bg); color: var(--text)"
            >
              <option v-for="site in auth.sites" :key="site.id" :value="site.id">
                {{ site.name }} ({{ site.code }})
              </option>
            </select>
          </div>

          <button
            @click="handleSiteConfirm"
            :disabled="!selectedSite"
            class="w-full py-2.5 rounded text-base font-semibold transition-all"
            :class="!selectedSite ? 'opacity-60' : 'hover:brightness-110 active:scale-[0.98]'"
            style="background: var(--accent); color: white"
          >
            {{ t('login.stepSite.continue') }} {{ selectedSiteLabel }}
          </button>

          <button
            @click="handleBackToLogin"
            class="w-full py-1.5 rounded text-xs font-medium transition-all hover:brightness-110"
            style="background: transparent; color: var(--muted)"
          >
            {{ t('common.back') }}
          </button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { authApi } from '../api/auth'
import { useIcons } from '../composables/useIcons'
import { useToastStore } from '../stores/toast'

const icons = useIcons()
import { extractError } from '../utils/error'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const toast = useToastStore()
const auth = useAuthStore()

const FLOW_KEY = 'aircargo_login_flow'

// El login es multi-step (credentials → mfa → mfa-enroll → site-select).
// Persistimos el paso en sessionStorage (sobrevive recargas y la navegación
// a /login) para que NUNCA se vuelva a la pantalla de contraseña tras pasar
// el MFA: si el flujo se interrumpe (recarga, 401+refresh fallido, guard del
// router), LoginView retoma el paso exacto (site-select / mfa) en vez de
// reiniciar en 'credentials'.
function saveFlow(step, email) {
  try {
    sessionStorage.setItem(FLOW_KEY, JSON.stringify({ step, email: email || '' }))
  } catch {}
}

function loadFlow() {
  try {
    const raw = sessionStorage.getItem(FLOW_KEY)
    if (!raw) return null
    const f = JSON.parse(raw)
    return f && f.step ? f : null
  } catch {
    return null
  }
}

function clearFlow() {
  try {
    sessionStorage.removeItem(FLOW_KEY)
  } catch {}
}

const REMEMBER_KEY = 'aircargo_login_remember'

const loginEmail = ref('')
const password = ref('')
const totpCode = ref('')
const loading = ref(false)
const errorMsg = ref('')
const needsPassword = ref(false)
const showPassword = ref(false)
const rememberEmail = ref(true)
const capsLockOn = ref(false)
const showSetupLink = ref(false)
const step = ref('credentials')
const selectedSite = ref(null)
const pendingEmail = ref('')
const pendingPassword = ref('')
const enrollToken = ref('')
const enrollSecret = ref('')
const enrollOtpAuthUrl = ref('')
const enrollCode = ref('')
const mfaReason = ref('required')
const mfaReenrollmentNeeded = ref(false)
const mfaRecoveryEmail = ref('')
const mfaRecoveryCode = ref('')
const mfaRecoveryPhase = ref('request')
const mfaRecoveryMailAvailable = ref(true)

const selectedSiteLabel = computed(() => {
  if (!selectedSite.value) return ''
  const site = auth.sites.find(s => s.id === selectedSite.value)
  return site ? `${site.name}` : ''
})

function detectCapsLock(e) {
  if (typeof e?.getModifierState === 'function') {
    capsLockOn.value = e.getModifierState('CapsLock')
  }
}

function persistRememberedEmail() {
  try {
    if (rememberEmail.value && loginEmail.value) {
      localStorage.setItem(REMEMBER_KEY, loginEmail.value)
    } else {
      localStorage.removeItem(REMEMBER_KEY)
    }
  } catch {}
}

async function handleLogin() {
  errorMsg.value = ''
  showSetupLink.value = false
  needsPassword.value = false
  loading.value = true
  try {
    await auth.login(loginEmail.value, password.value)
    persistRememberedEmail()
    if (auth.mustChangePassword) {
      router.push('/change-password')
      return
    }
    proceedAfterLogin()
  } catch (e) {
    const status = e.response?.status
    const data = e.response?.data
    if (status === 428 && data?.mfaRequired) {
      pendingEmail.value = loginEmail.value
      pendingPassword.value = password.value
      mfaReenrollmentNeeded.value = !!data?.mfaReenrollmentNeeded
      mfaReason.value = data?.mfaReason || 'required'
      step.value = 'mfa'
      totpCode.value = ''
      errorMsg.value = ''
      saveFlow('mfa', loginEmail.value)
      loading.value = false
      return
    }
    if (status === 428 && data?.mfaEnrollmentRequired) {
      mfaReason.value = data?.mfaReason || 'required'
      startMfaEnrollment(data.enrollToken)
      return
    }
    toast.error(extractError(e))
    if (status === 428) {
      needsPassword.value = true
      showSetupLink.value = true
      errorMsg.value = t('login.error.passwordRequired')
    } else if (status === 401) {
      if (step.value === 'mfa' || data?.error) {
        errorMsg.value = data?.error || t('login.error.invalidCode')
      } else if (needsPassword.value) {
        errorMsg.value = t('login.error.wrongPassword')
      } else {
        errorMsg.value = t('login.error.invalidCredentials')
      }
    } else if (status === 403) {
      errorMsg.value = data?.error || t('login.error.inactive')
    } else {
      errorMsg.value = t('login.error.generic')
    }
  } finally {
    loading.value = false
  }
}

async function handleMfa() {
  errorMsg.value = ''
  loading.value = true
  try {
    const data = await auth.login(pendingEmail.value, pendingPassword.value, totpCode.value)
    if (data?.mfaReenrollmentNeeded) {
      // MFA válido pero política exige re-enrolamiento → iniciar flujo de enrolamiento
      mfaReason.value = data.mfaReason || 'required'
      startMfaEnrollment(data.enrollToken)
      return
    }
    if (auth.mustChangePassword) {
      router.push('/change-password')
      return
    }
    proceedAfterLogin()
  } catch (e) {
    const status = e.response?.status
    const data = e.response?.data
    toast.error(extractError(e))
    if (status === 401) {
      errorMsg.value = data?.error || t('login.error.invalidCode')
      totpCode.value = ''
    } else if (status === 403) {
      errorMsg.value = data?.error || t('login.error.locked')
    } else {
      errorMsg.value = t('login.error.generic')
    }
  } finally {
    loading.value = false
  }
}

function proceedAfterLogin() {
  pendingEmail.value = ''
  pendingPassword.value = ''
  if (auth.sites.length === 0) {
    errorMsg.value = t('login.error.noSites')
    return
  }
  if (auth.sites.length === 1) {
    clearFlow()
    selectedSite.value = auth.sites[0].id
    auth.confirmSite(selectedSite.value)
    navigateAfterSiteConfirm()
    return
  }
  selectedSite.value = auth.sites[0].id
  step.value = 'site-select'
  saveFlow('site-select', auth.email || loginEmail.value)
}

async function startMfaEnrollment(token) {
  enrollToken.value = token
  pendingEmail.value = loginEmail.value
  pendingPassword.value = password.value || ''
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await authApi.mfaEnrollSetup(token)
    enrollSecret.value = res.data.secret
    enrollOtpAuthUrl.value = res.data.otpAuthUrl
    step.value = 'mfa-enroll'
    enrollCode.value = ''
  } catch (err) {
    toast.error(extractError(err))
    errorMsg.value = t('login.mfaEnroll.failed')
    step.value = 'credentials'
  } finally {
    loading.value = false
  }
}

async function handleEnrollEnable() {
  errorMsg.value = ''
  loading.value = true
  try {
    await authApi.mfaEnrollEnable(enrollToken.value, enrollSecret.value, enrollCode.value)
    // MFA habilitado → re-login con password + código TOTP para obtener sesión
    await auth.login(pendingEmail.value, pendingPassword.value, enrollCode.value)
    if (auth.mustChangePassword) {
      router.push('/change-password')
      return
    }
    proceedAfterLogin()
  } catch (e) {
    const status = e.response?.status
    const data = e.response?.data
    toast.error(extractError(e))
    if (status === 401 || status === 428) {
      errorMsg.value = data?.error || t('login.mfaEnroll.failed')
      enrollCode.value = ''
    } else if (status === 403) {
      errorMsg.value = data?.error || t('login.error.locked')
      step.value = 'credentials'
    } else {
      errorMsg.value = t('login.mfaEnroll.failed')
      enrollCode.value = ''
    }
  } finally {
    loading.value = false
  }
}

import { popReturnTo, loadDraft, restoreForms, clearDraft } from '@/utils/formDraft'

function navigateAfterSiteConfirm() {
  const draft = loadDraft()
  if (draft && draft.route && draft.forms?.length) {
    router.push(draft.route)
    setTimeout(() => {
      const n = restoreForms(draft.forms)
      clearDraft()
      if (n > 0) toast.success(t('idle.restored', { n }))
    }, 600)
    return
  }
  // Sesión expirada: se devuelve al usuario a la vista donde se cortó, para
  // que recupere el contexto (y los borradores que esa vista restaura sola al
  // inicializar sus formularios). Antes `?expired=1` se perdía aquí y todo el
  // flujo terminaba en `/`, dejando al usuario sin pista de qué estaba Doing.
  if (route.query.expired === '1') {
    let expiredRoute = ''
    try {
      expiredRoute = sessionStorage.getItem('aircargo_expired_route') || ''
      sessionStorage.removeItem('aircargo_expired_route')
    } catch {}
    // Sólo se acepta una ruta interna: nunca absoluta ni de otro origen.
    const safe = expiredRoute.startsWith('/') && !expiredRoute.startsWith('//')
      ? expiredRoute
      : ''
    router.push(safe || popReturnTo() || '/')
    return
  }
  router.push(popReturnTo() || '/')
}

function handleSiteConfirm() {
  if (!selectedSite.value) return
  clearFlow()
  auth.confirmSite(selectedSite.value)
  navigateAfterSiteConfirm()
}

function handleBackToLogin() {
  clearFlow()
  step.value = 'credentials'
  pendingEmail.value = ''
  pendingPassword.value = ''
  totpCode.value = ''
  enrollToken.value = ''
  enrollSecret.value = ''
  enrollOtpAuthUrl.value = ''
  enrollCode.value = ''
  mfaReason.value = 'required'
  mfaRecoveryEmail.value = ''
  mfaRecoveryCode.value = ''
  mfaRecoveryPhase.value = 'request'
  errorMsg.value = ''
  needsPassword.value = false
  showSetupLink.value = false
  auth.logout()
}

function goToSetPassword() {
  router.push(`/set-password?email=${encodeURIComponent(loginEmail.value)}`)
}

function openMfaRecovery() {
  mfaRecoveryEmail.value = loginEmail.value || pendingEmail.value || ''
  mfaRecoveryCode.value = ''
  mfaRecoveryPhase.value = 'request'
  mfaRecoveryMailAvailable.value = true
  errorMsg.value = ''
  step.value = 'mfa-recovery'
}

function backToMfa() {
  mfaRecoveryCode.value = ''
  errorMsg.value = ''
  step.value = (pendingEmail.value || loginEmail.value) ? 'mfa' : 'credentials'
}

async function handleRecoveryRequest() {
  errorMsg.value = ''
  loading.value = true
  try {
    const res = await authApi.mfaRecoveryRequest(mfaRecoveryEmail.value)
    const mailEnabled = res.data?.mailEnabled !== false
    const devCode = res.data?.devCode || ''
    mfaRecoveryMailAvailable.value = mailEnabled
    if (devCode) {
      mfaRecoveryCode.value = devCode
    }
    if (mailEnabled || devCode) {
      mfaRecoveryPhase.value = 'confirm'
    }
  } catch (e) {
    toast.error(extractError(e))
    errorMsg.value = t('login.mfa.recovery.failed')
  } finally {
    loading.value = false
  }
}

async function handleRecoveryConfirm() {
  errorMsg.value = ''
  loading.value = true
  try {
    const res = await authApi.mfaRecoveryConfirm(mfaRecoveryEmail.value, mfaRecoveryCode.value)
    const token = res.data?.enrollToken
    if (!token) {
      errorMsg.value = t('login.mfa.recovery.invalidCode')
      mfaRecoveryCode.value = ''
      return
    }
    loginEmail.value = mfaRecoveryEmail.value
    await startMfaEnrollment(token)
  } catch (e) {
    const status = e.response?.status
    toast.error(extractError(e))
    if (status === 400) {
      errorMsg.value = t('login.mfa.recovery.invalidCode')
      mfaRecoveryCode.value = ''
    } else {
      errorMsg.value = t('login.mfa.recovery.failed')
    }
  } finally {
    loading.value = false
  }
}

// Al montar, restauramos el paso del flujo de login guardado.
// Esto garantiza que tras pasar el MFA (sessionStorage preserve) la app JAMÁS
// vuelva a la pantalla de contraseña por una recarga/navegación intermedia:
// · si el usuario ya tenía userId pero no sitio confirmado → site-select
// · si venía de MFA → de vuelta al paso MFA con el email precargado
onMounted(() => {
  try {
    const savedEmail = localStorage.getItem(REMEMBER_KEY)
    if (savedEmail && !loginEmail.value) loginEmail.value = savedEmail
  } catch {}

  const flow = loadFlow()
  if (!flow || !flow.step || flow.step === 'credentials') return

  // El usuario ya completó el login (cookies/tokens emitidos) y quedó pendiente
  // elegir el sitio → retomar exactamente ahí, sin pedir contraseña otra vez.
  if (flow.step === 'site-select' && auth.hasSession && !auth.selectedSiteId) {
    if (auth.sites.length > 0) {
      step.value = 'site-select'
      selectedSite.value = auth.sites[0].id
      loginEmail.value = flow.email || auth.email
      return
    }
    clearFlow()
    return
  }

  // Venía del paso MFA (aún sin sesión): retoma el MFA con el email precargado.
  if ((flow.step === 'mfa' || flow.step === 'mfa-enroll') && flow.email) {
    loginEmail.value = flow.email
  }
})
</script>

<style scoped>
.auth-logo {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  background: linear-gradient(135deg, var(--accent-strong), var(--accent));
  box-shadow: var(--shadow-glow);
}

.auth-title {
  font-size: 1.4rem;
  letter-spacing: -0.01em;
}

.auth-field {
  display: flex;
  flex-direction: column;
}

.auth-control {
  position: relative;
  display: flex;
  align-items: center;
}

.auth-control-icon {
  position: absolute;
  left: 11px;
  display: inline-flex;
  color: var(--text-3);
  pointer-events: none;
}

.auth-control input {
  padding-left: 34px;
  padding-top: 10px;
  padding-bottom: 10px;
  border: 1px solid var(--border);
  font-size: 19px;
}

.auth-control input:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
}

.auth-control input:disabled {
  opacity: 0.6;
}

.auth-control-btn {
  position: absolute;
  right: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  color: var(--text-3);
  background: transparent;
  transition: color 0.15s, background 0.15s;
}

.auth-control-btn:hover {
  color: var(--text);
  background: var(--surface-hover);
}

.auth-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  line-height: 1.3;
}

.auth-hint-warn {
  color: var(--warn);
}

.auth-remember {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--text-2);
  cursor: pointer;
  user-select: none;
}

.auth-remember input {
  width: 15px;
  height: 15px;
  accent-color: var(--accent);
  cursor: pointer;
}

.auth-alert {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 9px 11px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.35;
  color: var(--warn);
  background: var(--warn-bg);
  border: 1px solid var(--warn-soft);
}
</style>

