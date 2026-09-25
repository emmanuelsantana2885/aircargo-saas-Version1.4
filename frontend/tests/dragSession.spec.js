import { describe, it, expect, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { mount } from '@vue/test-utils'

import { useDragSession } from '@/composables/useDragSession'

/** Monta un componente que expone la sesión a una variable externa. */
function mountSession() {
  const api = {}
  const wrapper = mount(defineComponent({
    setup() {
      Object.assign(api, useDragSession())
      return () => h('div')
    },
  }))
  return { wrapper, api }
}

const fire = (type) => document.dispatchEvent(new Event(type, { bubbles: true }))

describe('useDragSession', () => {
  it('adds document listeners on begin and routes move events to onMove', () => {
    const { wrapper, api } = mountSession()
    const onMove = vi.fn()

    api.begin({ onMove })
    fire('pointermove')

    expect(onMove).toHaveBeenCalledTimes(1)
    expect(onMove.mock.calls[0][0].type).toBe('pointermove')
    wrapper.unmount()
  })

  it('commits onEnd and detaches both listeners on the end event', () => {
    const { wrapper, api } = mountSession()
    const onMove = vi.fn()
    const onEnd = vi.fn()

    api.begin({ onMove, onEnd })
    fire('pointerup')
    fire('pointermove')

    expect(onEnd).toHaveBeenCalledTimes(1)
    // after the gesture ends the move handler must be gone, not merely idle
    expect(onMove).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  // ── regression guard ──────────────────────────────────────────────
  // Before this composable every drag machine paired add/removeEventListener
  // by hand and only removed them from its own end handler. Navigating away
  // mid-drag left a live handler mutating an unmounted component forever.
  it('stops delivering events after unmount (no listener leak)', () => {
    const { wrapper, api } = mountSession()
    const onMove = vi.fn()

    api.begin({ onMove })
    fire('pointermove')
    expect(onMove).toHaveBeenCalledTimes(1)

    wrapper.unmount()
    fire('pointermove')
    fire('pointermove')

    expect(onMove).toHaveBeenCalledTimes(1)
  })

  it('does NOT run onEnd when the component unmounts mid-drag', () => {
    const { wrapper, api } = mountSession()
    const onEnd = vi.fn()

    api.begin({ onMove: () => {}, onEnd })
    wrapper.unmount()

    // tearing down must not commit state (e.g. a matrix rebuild + refetch)
    expect(onEnd).not.toHaveBeenCalled()
  })

  it('a new gesture supersedes the previous one (no doubled handlers)', () => {
    const { wrapper, api } = mountSession()
    const first = vi.fn()
    const second = vi.fn()
    const firstEnd = vi.fn()

    api.begin({ onMove: first, onEnd: firstEnd })
    api.begin({ onMove: second })
    fire('pointermove')

    expect(first).not.toHaveBeenCalled()
    expect(second).toHaveBeenCalledTimes(1)
    // the abandoned session must not commit either
    expect(firstEnd).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('honours custom move/end events (mousemove/mouseup splitter)', () => {
    const { wrapper, api } = mountSession()
    const onMove = vi.fn()
    const onEnd = vi.fn()

    api.begin({ moveEvents: ['mousemove'], endEvents: ['mouseup'], onMove, onEnd })
    fire('pointermove')
    expect(onMove).not.toHaveBeenCalled()

    fire('mousemove')
    expect(onMove).toHaveBeenCalledTimes(1)

    fire('mouseup')
    expect(onEnd).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('keeps sessions isolated per component instance', async () => {
    const a = mountSession()
    const b = mountSession()
    const onA = vi.fn()
    const onB = vi.fn()

    a.api.begin({ onMove: onA })
    b.api.begin({ onMove: onB })

    a.wrapper.unmount()
    await nextTick()
    fire('pointermove')

    // unmounting A must not cancel B's in-flight gesture
    expect(onA).not.toHaveBeenCalled()
    expect(onB).toHaveBeenCalledTimes(1)
    b.wrapper.unmount()
  })

  it('returns a cancel handle that detaches without committing', () => {
    const { wrapper, api } = mountSession()
    const onMove = vi.fn()
    const onEnd = vi.fn()

    const cancel = api.begin({ onMove, onEnd })
    cancel()
    fire('pointermove')

    expect(onMove).not.toHaveBeenCalled()
    expect(onEnd).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
