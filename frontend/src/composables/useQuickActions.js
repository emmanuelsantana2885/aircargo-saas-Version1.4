import { computed } from 'vue'
import { useAuthStore } from '../stores/auth'

const ACTION_DEFS = [
  {
    key: 'receive',
    perm: 'CAN_CREATE_RECEIPT',
    view: 'RECEIPTS',
    icon: 'FileInvoice',
    color: 'emerald',
    to: { name: 'receipts' },
  },
  {
    key: 'flight',
    perm: 'CAN_CREATE_FLIGHT',
    view: 'FLIGHTS',
    icon: 'PlaneDeparture',
    color: 'indigo',
    to: { name: 'flights', query: { new: '1' } },
  },
  {
    key: 'uld',
    perm: 'CAN_CREATE_ULD',
    view: 'ULDS',
    icon: 'Package',
    color: 'slate',
    to: { name: 'ulds', query: { new: '1' } },
  },
  {
    key: 'export',
    perm: 'CAN_READ_BI',
    view: 'EXPORTS',
    icon: 'Download',
    color: 'cyan',
    to: { name: 'exports' },
  },
]

export function useQuickActions() {
  const auth = useAuthStore()

  const actions = computed(() =>
    ACTION_DEFS.filter(a => auth.can(a.perm) && auth.canView(a.view)).map(a => ({ ...a }))
  )

  const hasActions = computed(() => actions.value.length > 0)

  return { actions, hasActions }
}