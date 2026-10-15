import { describe, it, expect } from 'vitest'
import {
  activeReceiptForMawb,
  dispatchedPiecesForMawb,
  mawbDispatchedWeightLbs,
} from '@/utils/mawbDispatch'

// peso declarado en kg: es el proxy que se usa mientras no exista recibo
const mawb = (over = {}) => ({
  id: 'm1', awbNumber: '406-05857585', pieces: 10, reportedWeightKg: 100, ...over,
})
const link = (over = {}) => ({ uldId: 'u1', mawbId: 'm1', mawbLabel: '406-05857585', pieces: 4, ...over })

describe('activeReceiptForMawb', () => {
  it('encuentra el recibo vigente por mawbId', () => {
    const r = { id: 'r1', mawbId: 'm1', pieceCount: 10 }
    expect(activeReceiptForMawb(mawb(), [r])).toBe(r)
  })

  it('encuentra el recibo aunque mawbId venga nulo, por AWB normalizado', () => {
    // caso histórico: el recibo trae mawbNumber pero no mawbId, y el AWB está
    // sin guion. Antes sólo se buscaba por mawbId → la MAWB salía "sin recibir"
    const r = { id: 'r1', mawbId: null, mawbNumber: '40605857585', pieceCount: 10 }
    expect(activeReceiptForMawb(mawb(), [r])).toBe(r)
  })

  it('ignora los recibos superseded', () => {
    const old = { id: 'r0', mawbId: 'm1', pieceCount: 3, superseded: true }
    const cur = { id: 'r1', mawbId: 'm1', pieceCount: 10 }
    expect(activeReceiptForMawb(mawb(), [old, cur])).toBe(cur)
  })

  it('devuelve null cuando no hay recibo', () => {
    expect(activeReceiptForMawb(mawb(), [])).toBeNull()
    expect(activeReceiptForMawb(mawb(), undefined)).toBeNull()
  })

  it('no inventa coincidencias con lista vacía de MAWB', () => {
    expect(activeReceiptForMawb({ id: null, awbNumber: '' }, [])).toBeNull()
  })
})

describe('dispatchedPiecesForMawb', () => {
  it('empareja por mawbId y por AWB normalizado', () => {
    const byLabel = link({ mawbId: null, mawbLabel: '40605857585' })
    expect(dispatchedPiecesForMawb(mawb(), [byLabel], new Set(['u1']), 10)).toBe(4)
  })

  it('cuenta varios links de la misma MAWB', () => {
    const links = [link({ uldId: 'u1' }), link({ uldId: 'u2', pieces: 6 })]
    expect(dispatchedPiecesForMawb(mawb(), links, new Set(['u1', 'u2']), 10)).toBe(10)
  })

  it('NO cuenta links de otros vuelos cuando se pasa el conjunto de ULDs', () => {
    // u1 pertenece al vuelo, u2 no: contar ambos inflaría el peso del vuelo
    const links = [link({ uldId: 'u1', pieces: 4 }), link({ uldId: 'u9', pieces: 6 })]
    const uldIds = new Set(['u1'])
    expect(dispatchedPiecesForMawb(mawb(), links, uldIds, 10)).toBe(4)
  })

  it('topa a las piezas recibidas: no se despacha más de lo recibido', () => {
    const links = [link({ pieces: 4 }), link({ uldId: 'u2', pieces: 40 })]
    expect(dispatchedPiecesForMawb(mawb(), links, new Set(['u1', 'u2']), 10)).toBe(10)
  })

  it('ignora links sin piezas informadas', () => {
    expect(dispatchedPiecesForMawb(mawb(), [link({ pieces: null })], new Set(['u1']), 10)).toBe(0)
  })

  it('falla cerrado sin alcance de vuelo, en vez de sumar otros vuelos', () => {
    const links = [link({ uldId: 'u1', pieces: 4 }), link({ uldId: 'u9', pieces: 6 })]
    // sin conjunto de ULDs no se puede saber si el link es de este vuelo:
    // devolver 10 inflaría el peso del vuelo con piezas de otro
    expect(dispatchedPiecesForMawb(mawb(), links, null, 10)).toBe(0)
    expect(dispatchedPiecesForMawb(mawb(), links, new Set(), 10)).toBe(0)
  })

  it('devuelve 0 sin links', () => {
    expect(dispatchedPiecesForMawb(mawb(), [], new Set(['u1']), 10)).toBe(0)
    expect(dispatchedPiecesForMawb(mawb(), undefined, new Set(['u1']), 10)).toBe(0)
  })
})

describe('mawbDispatchedWeightLbs', () => {
  it('prorratea con el peso REAL del recibo vigente', () => {
    // (peso recibido / piezas recibidas) x piezas despachadas
    const r = { id: 'r1', mawbId: 'm1', pieceCount: 10, actualWeightLbs: 1000 }
    expect(mawbDispatchedWeightLbs(mawb(), [link({ pieces: 5 })], new Set(['u1']), [r])).toBe(500)
  })

  it('cae a los kg declarados de la MAWB si no hay recibo', () => {
    // 100 kg * 2.20462 = 220.46 lbs por pieza, 5 piezas
    const expected = (100 * 2.20462 / 10) * 5 // 220.46 lbs / 10 piezas x 5
    expect(mawbDispatchedWeightLbs(mawb(), [link({ pieces: 5 })], new Set(['u1']), [])).toBeCloseTo(expected, 2)
  })

  it('ignora un recibo superseded y usa la MAWB declarada', () => {
    const old = { id: 'r0', mawbId: 'm1', pieceCount: 10, actualWeightLbs: 99999, superseded: true }
    const expected = (100 * 2.20462 / 10) * 5 // 220.46 lbs / 10 piezas x 5
    expect(mawbDispatchedWeightLbs(mawb(), [link({ pieces: 5 })], new Set(['u1']), [old])).toBeCloseTo(expected, 2)
  })

  it('devuelve 0 si no hay nada despachado', () => {
    const r = { id: 'r1', mawbId: 'm1', pieceCount: 10, actualWeightLbs: 1000 }
    expect(mawbDispatchedWeightLbs(mawb(), [], new Set(['u1']), [r])).toBe(0)
  })

  it('usa las piezas de la MAWB si el recibo trae peso pero no pieceCount', () => {
    // pieceCount 0 es falsy: en vez de devolver 0 (que ocultaría el despacho)
    // prorratea sobre las piezas declaradas de la MAWB: 1000/10 x 4
    const r = { id: 'r1', mawbId: 'm1', pieceCount: 0, actualWeightLbs: 1000 }
    expect(mawbDispatchedWeightLbs(mawb(), [link({ pieces: 4 })], new Set(['u1']), [r])).toBe(400)
  })

  it('devuelve 0 si no hay peso ni declarado ni en recibo', () => {
    const sinPeso = mawb({ reportedWeightKg: 0, chargeableWeightKg: 0 })
    expect(mawbDispatchedWeightLbs(sinPeso, [link({ pieces: 4 })], new Set(['u1']), [])).toBe(0)
  })
})