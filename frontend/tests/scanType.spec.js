import { describe, it, expect } from 'vitest'
import { detectScanType, isUldCode, isSealCode, SEAL_RE } from '@/utils/scanType'

const ULD_CODES = ['PMC', 'PAH', 'PAG', 'AAY']

describe('detectScanType (SDD §5 — detección por tipo de código)', () => {
  it('detecta códigos ULD con y sin guion, en cualquier caso', () => {
    expect(detectScanType('PMC-12345', ULD_CODES)).toBe('uld')
    expect(detectScanType('PMC12345', ULD_CODES)).toBe('uld')
    expect(detectScanType('pmc 12345', ULD_CODES)).toBe('uld')
    expect(detectScanType('AAY77777MX', ULD_CODES)).toBe('uld')
  })

  it('detecta sellos con prefijo SC- / SC / SEAL-', () => {
    expect(detectScanType('SC-12345678', ULD_CODES)).toBe('seal')
    expect(detectScanType('sc12345678', ULD_CODES)).toBe('seal')
    expect(detectScanType('SEAL-99X', ULD_CODES)).toBe('seal')
    // Un sello que empieza por PMC no debe confundirse con ULD si no cumple el
    // sufijo alfanumérico largo del ULD… (aquí sí lo cumple: es ULD, por eso
    // la detección de ULD tiene prioridad).
    expect(detectScanType('SCA-0001', [...ULD_CODES, 'SCA'])).toBe('uld')
  })

  it('trata MAWB numéricos IATA y HAWBs alfanuméricos como piezas', () => {
    expect(detectScanType('406-05399391', ULD_CODES)).toBe('mawb')
    expect(detectScanType('40605399391', ULD_CODES)).toBe('mawb')
    expect(detectScanType('E2ESCAN001', ULD_CODES)).toBe('mawb')
    expect(detectScanType('ABC12345678', ULD_CODES)).toBe('mawb')
  })

  it('sin catálogo de ULD no clasifica nada como ULD', () => {
    expect(isUldCode('PMC-12345', [])).toBe(false)
    expect(detectScanType('PMC-12345', [])).toBe('mawb')
  })

  it('código vacío o nulo → null', () => {
    expect(detectScanType('', ULD_CODES)).toBeNull()
    expect(detectScanType('   ', ULD_CODES)).toBeNull()
    expect(detectScanType(null, ULD_CODES)).toBeNull()
  })

  it('el RE de sello no casca con MAWBs numéricos ni con códigos cortos', () => {
    expect(SEAL_RE.test('406-05399391')).toBe(false)
    expect(SEAL_RE.test('SC-1')).toBe(false)
    expect(isSealCode('SC-AB12')).toBe(true)
    expect(isSealCode('40605399391')).toBe(false)
  })
})
