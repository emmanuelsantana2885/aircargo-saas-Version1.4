/**
 * Agrupación de bookings duplicados — única fuente de verdad.
 *
 * Varios bookings pueden apuntar a la misma MAWB (reservas duplicadas tras
 * reintentos de importación). La vista los colapsa en una fila con un contador
 * `Nx` y, al borrar, ofrece eliminarlos **en grupo**.
 *
 * El agrupado y el borrado deben compartir EXACTAMENTE el mismo criterio. Ya
 * no lo hacen: el agrupado usa `mawbId || awbNumber || id` y el borrado filtraba
 * por `(x.mawbId === b.mawbId) || (!b.mawbId && x.awbNumber === b.awbNumber)`.
 * Como `null === null` es `true`, ese filtro coincidía con TODOS los bookings sin
 * `mawbId`, así que borrar un grupo de 2 duplicados también eliminaba bookings de
 * otros AWB e incluso los que no tienen AWB. Se centraliza la clave para que las
 * dos rutas no puedan divergir de nuevo.
 */

/**
 * Clave de grupo de un booking: la MAWB manda; sin MAWB, el AWB; sin ninguno,
 * el id (que siempre es único, así que ese booking queda en un grupo propio de
 * tamaño 1 y nunca entra por la ruta de borrado en grupo).
 */
export function dupGroupKey(b) {
  if (!b) return undefined
  return b.mawbId || b.awbNumber || b.id
}

/** ¿`x` pertenece al mismo grupo de duplicados que `b`? */
export function sameDupGroup(x, b) {
  const kx = dupGroupKey(x)
  return kx !== undefined && kx === dupGroupKey(b)
}