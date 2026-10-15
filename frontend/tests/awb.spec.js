import { describe, it, expect } from 'vitest'
import { normalizeAwb, sameAwb } from '@/utils/awb'

describe('normalizeAwb', () => {
  it('normaliza a 3 guiones + 8 dígitos', () => {
    expect(normalizeAwb('406-05857585')).toBe('406-05857585')
    expect(normalizeAwb('40605857585')).toBe('406-05857585')
  })

  it('tolera espacios, guiones, guiones bajos y barras', () => {
    expect(normalizeAwb(' 406 058 57585 ')).toBe('406-05857585')
    expect(normalizeAwb('406_05857585')).toBe('406-05857585')
    expect(normalizeAwb('406/05857585')).toBe('406-05857585')
  })

  it('sube a mayúsculas y conserva los caracteres no separadores', () => {
    // no son 11 dígitos, así que no se inserta guion: sólo se limpia y se pasa
    // a mayúsculas
    expect(normalizeAwb('aad99387ups')).toBe('AAD99387UPS')
    expect(normalizeAwb('406-05857585abc')).toBe('40605857585ABC')
  })

  it('nunca lanza ante basura; cadena vacía sólo si no hay nada', () => {
    expect(normalizeAwb(null)).toBe('')
    expect(normalizeAwb(undefined)).toBe('')
    expect(normalizeAwb('')).toBe('')
    expect(normalizeAwb('   ')).toBe('')
    // la basura es comparable consigo misma; la guarda de vacío vive en sameAwb
    expect(normalizeAwb('abc')).toBe('ABC')
    expect(normalizeAwb('123')).toBe('123')
  })
})

describe('sameAwb', () => {
  it('compara dos AWB equivalentes escritos de forma distinta', () => {
    expect(sameAwb('406-05857585', '40605857585')).toBe(true)
    expect(sameAwb('406-05857585', '406 058 57585')).toBe(true)
    expect(sameAwb('406-05857585', ' 40605857585 ')).toBe(true)
  })

  it('distingue AWB distintos', () => {
    expect(sameAwb('406-05857585', '406-05857586')).toBe(false)
    expect(sameAwb('AAD99387UPS', 'AAD99388UPS')).toBe(false)
  })

  it('nunca declara iguales dos vacíos', () => {
    // sin este guardia, un MAWB sin AWB emparejaría con cualquier otro vacío
    expect(sameAwb('', '')).toBe(false)
    expect(sameAwb(null, '')).toBe(false)
    expect(sameAwb(null, null)).toBe(false)
    expect(sameAwb('abc', '')).toBe(false)
  })

  it('es tolerante a null/undefined', () => {
    expect(sameAwb(null, '406-05857585')).toBe(false)
    expect(sameAwb('406-05857585', undefined)).toBe(false)
  })
})