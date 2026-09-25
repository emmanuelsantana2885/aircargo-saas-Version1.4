import { onUnmounted } from 'vue'

/**
 * Document-level drag sessions (column resize, range handles, panel splitters).
 *
 * The manual `addEventListener`/`removeEventListener` pairs this replaces leaked
 * their closures whenever the component unmounted before pointerup — navigating
 * away mid-drag left a live handler mutating a dead component's state forever.
 *
 * Owning the listeners here makes the teardown automatic and per-component: each
 * instance keeps its own active set, so one component unmounting can never cancel
 * a session belonging to another.
 *
 * Usage (must be called from setup):
 *   const drag = useDragSession()
 *   function startDrag(e) {
 *     drag.begin({
 *       onMove: (ev) => { ... },   // receives the move event
 *       onEnd:  () => { ... },    // commit; NOT called on unmount
 *     })
 *   }
 *
 * @param {{moveEvents?: string[], endEvents?: string[], onMove?: Function, onEnd?: Function}} cfg
 * @returns {() => void} cancel — abort this session without running `onEnd`
 */
export function useDragSession() {
  const active = new Set()

  function begin({ moveEvents = ['pointermove'], endEvents = ['pointerup'], onMove, onEnd } = {}) {
    // A new gesture supersedes any in-flight one: never leave orphaned listeners.
    cancelAll()

    const move = (ev) => onMove?.(ev)
    const detach = () => {
      for (const type of moveEvents) document.removeEventListener(type, move)
      for (const type of endEvents) document.removeEventListener(type, end)
      active.delete(detach)
    }
    const end = () => {
      detach()
      onEnd?.()
    }

    for (const type of moveEvents) document.addEventListener(type, move)
    for (const type of endEvents) document.addEventListener(type, end)
    active.add(detach)
    return detach
  }

  /** Detach every in-flight session WITHOUT running its `onEnd` (no commits during teardown). */
  function cancelAll() {
    for (const detach of Array.from(active)) detach()
  }

  onUnmounted(cancelAll)

  return { begin, cancelAll }
}
