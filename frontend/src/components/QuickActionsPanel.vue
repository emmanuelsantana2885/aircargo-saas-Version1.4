<script setup>
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useIcons } from '../composables/useIcons'
import { useQuickActions } from '../composables/useQuickActions'

const router = useRouter()
const { t } = useI18n()
const icons = useIcons()
const { actions } = useQuickActions()

function run(action) {
  router.push(action.to)
}

function colorClasses(scope, color) {
  const map = {
    'icon-bg': {
      emerald: 'bg-emerald-50 text-emerald-600',
      amber: 'bg-amber-50 text-amber-600',
      blue: 'bg-blue-50 text-blue-600',
      indigo: 'bg-indigo-50 text-indigo-600',
      slate: 'bg-slate-100 text-slate-600',
      rose: 'bg-rose-50 text-rose-600',
      cyan: 'bg-cyan-50 text-cyan-600',
    },
    'text': {
      emerald: 'text-emerald-600',
      amber: 'text-amber-600',
      blue: 'text-blue-600',
      indigo: 'text-indigo-600',
      slate: 'text-slate-600',
      rose: 'text-rose-600',
      cyan: 'text-cyan-600',
    },
  }
  return map[scope][color] || map[scope].slate
}
</script>

<template>
  <section v-if="actions.length" class="ds-card p-3" data-qa="quick-actions">
    <div class="flex items-center gap-2 mb-2">
      <span class="text-[13px] font-bold text-slate-700">{{ t('dashboard.quickActions.title') }}</span>
      <span class="text-[11px] text-slate-400">{{ t('dashboard.quickActions.hint') }}</span>
    </div>
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-1.5">
      <button
        v-for="a in actions"
        :key="a.key"
        type="button"
        class="flex items-center gap-2.5 rounded-lg border border-slate-200 bg-white px-2.5 py-2 text-left transition-colors duration-150 hover:border-slate-300 hover:bg-slate-50"
        :data-qa="`quick-action-${a.key}`"
        @click="run(a)"
      >
        <span class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg" :class="colorClasses('icon-bg', a.color)">
          <component :is="icons[a.icon]" :size="17" :stroke-width="2" />
        </span>
        <span class="min-w-0">
          <span class="block truncate text-[12px] font-bold text-slate-800">{{ t(`dashboard.quickActions.${a.key}`) }}</span>
          <span class="block truncate text-[10px] text-slate-400">{{ t(`dashboard.quickActions.${a.key}Desc`) }}</span>
        </span>
      </button>
    </div>
  </section>
</template>