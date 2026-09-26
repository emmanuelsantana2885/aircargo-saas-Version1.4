<template>
  <div class="ds-page overflow-y-auto">
    <div class="flex-1 flex items-center justify-center">
      <EmptyState :title="t('notFound.title')" :hint="t('notFound.hint')" :icon="icons.Search">
        <p class="mt-3 font-mono text-[12px] text-secondary bg-surface-hover border border-strong rounded-md px-3 py-1.5 max-w-full break-all">
          {{ route.fullPath }}
        </p>
        <div class="mt-4 flex items-center gap-2">
          <button class="ds-btn-secondary" @click="router.back()">{{ t('notFound.back') }}</button>
          <button class="ds-btn-primary" @click="goHome">{{ t('notFound.home') }}</button>
        </div>
      </EmptyState>
    </div>
  </div>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import EmptyState from '../components/EmptyState.vue'
import { useIcons } from '../composables/useIcons'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const icons = useIcons()

/* Si la sesión sigue viva, el enlace útil es volver al dashboard; si el usuario
 * perdió la sesión, el guard global ya lo mandó a /login antes de llegar aquí. */
const goHome = () => router.push('/')
</script>
