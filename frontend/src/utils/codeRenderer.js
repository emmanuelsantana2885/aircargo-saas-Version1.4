import JsBarcode from 'jsbarcode'
import qrcode from 'qrcode-generator'
import { effectiveSize } from './labelConfig'

const SVG_NS = 'http://www.w3.org/2000/svg'

const BARCODE_FORMATS = {
  CODE128: 'CODE128',
  CODE39: 'CODE39',
  EAN13: 'EAN13',
  EAN8: 'EAN8',
  UPC: 'UPC',
  ITF: 'ITF',
  PHARMACODE: 'pharmacode',
}

export function barcodeSvg(value, format = 'CODE128', heightPx = 120, width = 2) {
  if (!value) return null
  try {
    const svg = document.createElementNS(SVG_NS, 'svg')
    JsBarcode(svg, String(value), {
      format: BARCODE_FORMATS[format] || 'CODE128',
      height: Math.max(20, heightPx),
      width,
      margin: 0,
      displayValue: false,
      background: 'transparent',
      lineColor: '#000000',
    })
    const viewW = Number(svg.getAttribute('width') || 100)
    const viewH = Number(svg.getAttribute('height') || heightPx)
    svg.setAttribute('viewBox', `0 0 ${viewW} ${viewH}`)
    svg.setAttribute('preserveAspectRatio', 'xMidYMid meet')
    return svg
  } catch {
    return null
  }
}

export function qrSvg(value, marginModules = 2) {
  if (!value) return null
  try {
    const qr = qrcode(0, 'M')
    qr.addData(String(value))
    qr.make()
    const n = qr.getModuleCount()
    let path = ''
    for (let r = 0; r < n; r++) {
      for (let c = 0; c < n; c++) {
        if (qr.isDark(r, c)) path += `M${c + marginModules} ${r + marginModules}h1v1h-1z`
      }
    }
    const svg = document.createElementNS(SVG_NS, 'svg')
    svg.setAttribute('viewBox', `0 0 ${n + marginModules * 2} ${n + marginModules * 2}`)
    svg.setAttribute('preserveAspectRatio', 'xMidYMid meet')
    svg.setAttribute('shape-rendering', 'crispEdges')
    svg.innerHTML = `<rect width="100%" height="100%" fill="white"/><path d="${path}" fill="black"/>`
    return svg
  } catch {
    return null
  }
}

export function svgToDataUrl(svg) {
  if (!svg) return null
  try {
    const xml = new XMLSerializer().serializeToString(svg)
    return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(xml)
  } catch {
    return null
  }
}

export function svgToImage(svg) {
  const url = svgToDataUrl(svg)
  if (!url) return Promise.reject(new Error('no svg'))
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error('image load failed'))
    img.src = url
  })
}

export function isValidQrPayload(value) {
  return !!value && typeof value === 'string' && value.length > 0 && value.length <= 2950
}

export function isValidBarcodePayload(value, format = 'CODE128') {
  if (value === undefined || value === null || String(value).length === 0) return false
  const s = String(value)
  switch (format) {
    case 'CODE39':
      return /^[0-9A-Z\-\. \$\/\+\%]+$/.test(s)
    case 'EAN13':
      return /^\d{12,13}$/.test(s)
    case 'EAN8':
      return /^\d{7,8}$/.test(s)
    case 'UPC':
      return /^\d{11,12}$/.test(s)
    case 'ITF':
      return /^\d{2,}$/.test(s)
    default:
      return /^[\x00-\x7f]+$/.test(s)
  }
}

export async function renderLabelCanvas({ widthInches, heightInches, orientation, dpi, elements, _elementsLabeled, resolveValue }) {
  const eff = effectiveSize({ widthInches, heightInches, orientation })
  const W = Math.max(1, Math.round(eff.w * dpi))
  const H = Math.max(1, Math.round(eff.h * dpi))
  const canvas = document.createElement('canvas')
  canvas.width = W
  canvas.height = H
  const ctx = canvas.getContext('2d')
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, W, H)
  const mm = dpi / 25.4
  ctx.fillStyle = '#000000'
  for (const el of elements || []) {
    const x = (el.x || 0) * mm
    const y = (el.y || 0) * mm
    const w = (el.w || 0) * mm
    const h = (el.h || 0) * mm
    if (el.type === 'rect') {
      ctx.strokeRect(x, y, w, h)
      continue
    }
    if (el.type === 'line') {
      ctx.beginPath()
      ctx.moveTo(x, y)
      ctx.lineTo(x + w, y)
      ctx.stroke()
      continue
    }
    if (el.type === 'text') {
      const fs = Math.max(1, (el.fontSize || 4) * mm)
      ctx.font = `${el.bold ? 'bold ' : ''}${fs}px monospace`
      ctx.textBaseline = 'top'
      ctx.textAlign = el.align === 'right' ? 'right' : el.align === 'center' ? 'center' : 'left'
      const tx = el.align === 'right' ? x + w : el.align === 'center' ? x + w / 2 : x
      ctx.fillText(String(resolveValue ? resolveValue(el) : ''), tx, y, w)
      continue
    }
    if (el.type === 'barcode' || el.type === 'qrcode') {
      const value = resolveValue ? resolveValue(el) : ''
      if (value === undefined || value === null || String(value).length === 0) continue
      const fmt = el.barcodeFormat || 'CODE128'
      const svg = el.type === 'qrcode' ? qrSvg(String(value), 2) : barcodeSvg(String(value), fmt, Math.max(20, h), 1)
      if (!svg) continue
      try {
        const img = await svgToImage(svg)
        ctx.drawImage(img, x, y, w, h)
      } catch {
        /* skip broken code */
      }
    }
  }
  return { canvas, w: W, h: H }
}