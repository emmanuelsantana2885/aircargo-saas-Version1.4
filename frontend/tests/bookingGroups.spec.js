import { describe, it, expect } from 'vitest'
import { dupGroupKey, sameDupGroup } from '@/utils/bookingGroups'

describe('bookingGroups', () => {
  it('la MAWB manda sobre el AWB', () => {
    expect(dupGroupKey({ id: 'a', mawbId: 'm1', awbNumber: '406-01' })).toBe('m1')
  })

  it('sin MAWB cae al AWB', () => {
    expect(dupGroupKey({ id: 'a', mawbId: null, awbNumber: '406-01' })).toBe('406-01')
  })

  it('sin MAWB ni AWB cae al id (grupo propio de tamaño 1)', () => {
    expect(dupGroupKey({ id: 'a', mawbId: null, awbNumber: null })).toBe('a')
  })

  // Regresión: el filtro anterior de `removeBooking` era
  // `(x.mawbId === b.mawbId) || (!b.mawbId && x.awbNumber === b.awbNumber)`.
  // Con `null === null` la primera cláusula daba `true` para TODOS los bookings
  // sin `mawbId`, así que borrar un grupo de 2 duplicados arrastraba también
  // bookings de otros AWB y los que no tienen AWB.
  it('NO junta bookings sin mawbId que tienen AWB distinto', () => {
    const a = { id: 'a1', mawbId: null, awbNumber: '406-0585' }
    const b = { id: 'a2', mawbId: null, awbNumber: '406-0585' }
    const otroAwb = { id: 'b1', mawbId: null, awbNumber: '406-0586' }
    const sinAwb = { id: 'c1', mawbId: null, awbNumber: null }

    const grupo = [a, b, otroAwb, sinAwb].filter(x => sameDupGroup(x, a))
    expect(grupo.map(x => x.id)).toEqual(['a1', 'a2'])
  })

  it('el grupo coincide con el que produce la clave de agrupado', () => {
    const bookings = [
      { id: 'a1', mawbId: null, awbNumber: '406-0585' },
      { id: 'a2', mawbId: null, awbNumber: '406-0585' },
      { id: 'm1', mawbId: 'm9', awbNumber: '406-9' },
      { id: 'm2', mawbId: 'm9', awbNumber: '406-9' },
    ]
    const conteo = {}
    for (const b of bookings) {
      const k = dupGroupKey(b)
      conteo[k] = (conteo[k] || 0) + 1
    }
    // cada booking debe pertenecer a un grupo del tamaño que declara su conteo
    for (const b of bookings) {
      const members = bookings.filter(x => sameDupGroup(x, b))
      expect(members.length).toBe(conteo[dupGroupKey(b)])
    }
  })

  it('un grupo de tamaño 1 nunca entra por la ruta de borrado en grupo', () => {
    const b = { id: 'solo', mawbId: null, awbNumber: null }
    const otros = [
      { id: 'x', mawbId: null, awbNumber: '406-1' },
      { id: 'y', mawbId: 'm1', awbNumber: '406-2' },
    ]
    for (const o of otros) expect(sameDupGroup(o, b)).toBe(false)
  })

  it('una entrada nula o ausente no forma grupo', () => {
    expect(dupGroupKey(null)).toBeUndefined()
    expect(sameDupGroup({ id: 'a' }, null)).toBe(false)
    expect(sameDupGroup(null, { id: 'a' })).toBe(false)
  })
})