<template>
  <div class="ds-page">
    <header class="ds-section-header">
      <div class="flex items-end gap-3 flex-1 min-w-0 flex-wrap">
        <div>
          <h1 class="ds-title">{{ t('warehouse.title') }}</h1>
          <p class="ds-subtitle hidden sm:block">{{ t('warehouse.subtitle') }}</p>
        </div>
        <div class="h-8 w-[1px] bg-[var(--border-2)] hidden sm:block"></div>
        <div class="flex flex-col gap-0.5 flex-1 min-w-[140px] max-w-[280px]">
          <span class="ds-label hidden sm:block">{{ t('common.search') }} (* < > =)</span>
          <div class="ds-search max-w-none">
            <component :is="icons.Search" :size="14" class="ds-search-icon" :stroke-width="2" />
            <input v-model="filterTextRaw" type="text" :placeholder="t('common.search')"
              class="ds-input w-full pl-9" />
          </div>
        </div>
        <div class="flex flex-col gap-0.5">
          <span class="ds-label hidden sm:block">{{ t('common.date') }}</span>
          <LocaleDatePicker v-model="filterDate" class="w-[150px]" />
        </div>
        <div class="shrink-0 flex items-center gap-2 ml-2 wrap-actions">
          <span class="vm-chip" :title="t('warehouse.updatedAt')">&#10227; {{ t('warehouse.updatedAgo', { sec: pipelineAgo }) }}</span>
          <button type="button" class="ds-btn-primary !py-1.5 !px-3 text-[12px] whitespace-nowrap" @click="newReceipt">
            &#43; {{ t('warehouse.newReceipt') }}
          </button>
        </div>
      </div>
    </header>

    <!-- Pipeline de recepción (s1 zona A) — estado operativo segmentado -->
    <section class="pipe mx-3 mb-2 rounded-lg overflow-hidden">
      <div class="pipe-hd flex items-center gap-2 px-4 py-2">
        <span class="text-[13px] font-bold">{{ t('warehouse.pipeline.title') }}</span>
        <span class="pipe-total">{{ pipeTotal }}</span>
        <span class="ml-auto text-[11px]" style="color: var(--muted)">{{ t('warehouse.pipeline.sub') }}</span>
      </div>
      <div class="pipe-segs flex px-3 pb-3">
        <button v-for="seg in pipeSegments" :key="seg.key" type="button"
          class="seg" :class="{ on: statusFilter === seg.key, empty: seg.n === 0 }"
          :style="{ flex: seg.n ? String(seg.n) : '1', minWidth: '86px' }"
          :title="t('warehouse.pipeline.hint', { s: t('warehouse.derivedStatus.' + seg.key) })"
          @click="setStatusFilter(seg.key)">
          <span class="seg-n" :style="{ color: seg.color }">{{ seg.n }}</span>
          <span class="seg-l">{{ t('warehouse.derivedStatus.' + seg.key) }}</span>
          <span class="seg-bar" :style="{ background: seg.color }"></span>
        </button>
      </div>
    </section>

    <!-- Cola “Por atender” (s1) -->
    <section v-if="attentionQueue.length" class="exq mx-3 mb-2 rounded-lg overflow-y-auto">
      <button type="button" class="exq-hd w-full flex items-center gap-2 px-4 py-2 text-left cursor-pointer"
        @click="queueExpanded = !queueExpanded">
        <span class="text-[var(--warn)] text-[14px] leading-none">&#9888;</span>
        <span class="text-[13px] font-bold text-[var(--warn)]">{{ t('warehouse.attention.title') }} · {{ attentionQueue.length }}</span>
        <span class="ml-auto text-[11px] text-[var(--warn)]">{{ queueExpanded ? t('warehouse.attention.hide') : t('warehouse.attention.expand') }}</span>
        <span class="text-[var(--warn)] text-[11px] leading-none">{{ queueExpanded ? '&#9650;' : '&#9660;' }}</span>
      </button>
      <div v-if="queueExpanded" class="exq-rows">
        <div v-for="q in attentionQueue" :key="q.mawb.id" class="exq-row flex items-center gap-3 px-4 py-1.5">
          <span class="font-mono font-bold text-[12px] text-[var(--warn)] cursor-pointer hover:underline" @click="toggleExpand(q.mawb)">{{ q.mawb.awbNumber || q.mawb.id?.slice(0, 8) }}</span>
          <span class="text-[11px] text-[var(--warn)]">{{ t(q.whyKey) }}</span>
          <span v-if="q.flight" class="text-[11px] font-mono" style="color: var(--muted)">{{ q.flight.flightNumber }} · {{ fmtFlightDate(q.flight.flightDate) }}</span>
          <button type="button" class="exq-go ml-auto rounded-md px-3 py-1 text-[12px] font-bold cursor-pointer" @click="toggleExpand(q.mawb)">{{ t('warehouse.attention.go') }}</button>
        </div>
      </div>
    </section>

    <section class="ds-table-section mb-1.5">
      <div class="vm-bar flex flex-wrap items-center gap-2 px-4 py-2 border-b shrink-0" style="border-color: var(--border)">
        <div class="flex items-center gap-1 p-0.5 rounded-lg" style="background: color-mix(in srgb, var(--accent) 10%, transparent)">
          <button type="button" class="vm-pill" :class="{ on: tableMode === 'master' }" @click="openMaster()">&#9776; {{ t('warehouse.viewMode.master') }}</button>
          <button type="button" class="vm-pill" :class="{ on: tableMode === 'byFlight' }" @click="toggleMode('byFlight')">&#9992; {{ t('warehouse.viewMode.byFlight') }}</button>
        </div>
        <span class="text-[11px] font-bold uppercase tracking-wider" style="color: var(--muted)">{{ filteredMawbs.length }} MAWB{{ filteredMawbs.length === 1 ? '' : 's' }}</span>
        <div class="flex items-center gap-1 p-0.5 rounded-lg" style="background: color-mix(in srgb, var(--accent) 10%, transparent)">
          <button type="button" class="vm-pill" :class="{ on: flightFilter === '' }" @click="flightFilter = ''">&#9992; {{ t('warehouse.allFlights') }}</button>
          <button v-for="fo in flightOptions" :key="fo.key" type="button" class="vm-pill" :class="{ on: flightFilter === fo.key }" @click="flightFilter = fo.key">{{ fo.label }}</button>
        </div>
        <span v-if="tableMode === 'byFlight'" class="text-[11px] italic" style="color: var(--muted)">{{ t('warehouse.pipeHint') }}</span>
        <div class="ml-auto flex items-center gap-1.5">
          <button type="button" class="vm-pill" @click="openQueueModal" title="Cargar un recibo desde la cola de MAWBs pendientes">
            &#9203; {{ t('warehouse.queue.load') }}
          </button>
          <button type="button" class="vm-pill" @click="openScanModal">&#128247; {{ t('warehouse.scan.button') }}</button>
        </div>
      </div>
      <div class="overflow-x-auto shrink-0">
        <div class="bg-[#0d3b37] border-b border-[#12332f] receipt-list-header sticky top-0 z-10" >
          <div class="grid grid-cols-12 gap-0">
            <div class="col-span-2 text-left px-5">
              <span class="text-[11px] font-bold uppercase tracking-wider text-white/70">MAWB</span>
            </div>
            <div class="col-span-2 text-left px-5 receipt-list-cell" data-col="shipper">
              <span class="text-[11px] font-bold uppercase tracking-wider text-white/70">Shipper</span>
            </div>
            <div class="col-span-2 text-left px-5 receipt-list-cell" data-col="flight">
              <span class="text-[11px] font-bold uppercase tracking-wider text-white/70">{{ t('common.flight') }}</span>
            </div>
            <div class="col-span-1 text-center px-2">
              <span class="text-[11px] font-bold uppercase tracking-wider text-white/70">{{ t('common.pieces') }}</span>
            </div>
            <div class="col-span-1 text-right font-mono font-bold pr-2">
              <span class="text-[11px] uppercase tracking-wider text-white/70">{{ t('common.weightKg') }}</span>
            </div>
            <div class="col-span-2 text-center">
              <span class="text-[11px] font-bold uppercase tracking-wider text-white/70">{{ t('common.status') }}</span>
            </div>
            <div class="col-span-2 text-center">
              <span class="text-[11px] font-bold uppercase tracking-wider text-white/70">{{ t('common.actions') }}</span>
            </div>
          </div>
        </div>
      </div>
      <EmptyState v-if="store.mawbs.length === 0" :title="t('warehouse.empty')" :hint="t('warehouse.emptyHint')" :icon="icons.FileInvoice" />
      <EmptyState v-else-if="renderMawbs.length === 0" :title="t('warehouse.emptyFilter')" :hint="t('warehouse.emptyHint')" :icon="icons.Search" />
      <div v-else class="divide-y divide-[var(--border-2)] text-[12.5px] text-[var(--text)] overflow-y-auto flex-1 min-h-0 thin-scrollbar table-scroll-wrapper" >
        <template v-for="m in renderMawbs" :key="m.id">
          <div v-if="tableMode === 'byFlight' && flightGroupStarts().has(m.id)" class="fl-group-hd"
            :class="{ closed: isCollapsedGroup(flightKeyOf(m)) }" @click="toggleGroup(flightKeyOf(m))">
            <span class="fl-chip">{{ fmtFlight(m) }}</span>
            <span class="fl-sub">{{ flightSub(flightKeyOf(m)) }}</span>
            <span class="fl-count">{{ groupByKey(flightKeyOf(m))?.mawbs.length || 0 }}</span>
            <span class="fl-caret">{{ isCollapsedGroup(flightKeyOf(m)) ? '&#9656;' : '&#9662;' }}</span>
          </div>
          <div v-if="tableMode === 'master' || !isCollapsedGroup(flightKeyOf(m)) || expandedId === m.id" class="flex flex-col">
          <div class="overflow-x-auto">
          <div class="grid grid-cols-12 items-center py-1 px-4 transition-all duration-150 cursor-pointer border-t hover:bg-[var(--surface-2)]"
            :class="[
              expandedId === m.id ? 'row-selected' : '',
              rowFlash.has(m.id) ? 'row-flash' : '',
            ]"style="border-color: var(--border); min-width: 0;"
             :data-awb="m.awbNumber || ''" :data-status="deriveMawbOperationalStatus(m)"
             @click="toggleExpand(m)">
             <div class="col-span-2 font-mono font-bold text-[var(--text)] relative z-10 flex items-center gap-1.5">
              <span class="text-[12px] text-[var(--text)] transition-transform duration-200" :class="{ 'rotate-90': expandedId === m.id }">&#9654;</span>
              {{ m.awbNumber || m.id?.slice(0, 8) || '—' }}
              <span v-if="receiptHawbs[m.id] && receiptHawbs[m.id].length > 1"
                class="text-[13px] font-bold text-[var(--text-2)] bg-[var(--surface-3)] px-1.5 py-0.5 rounded leading-none"
                title="Múltiples HAWBs">{{ receiptHawbs[m.id].length }} HAWBs</span>
              <span v-else-if="receiptHawbs[m.id] && receiptHawbs[m.id].length === 1"
                class="text-[13px] font-bold text-[var(--text)] bg-[var(--surface-3)] px-1.5 py-0.5 rounded leading-none">1 HAWB</span>
              <span v-if="draftForMawb(m)" class="badge-draft" :title="'DRAFT'">DRAFT</span>
            </div>
            <div class="col-span-2 text-[var(--text)] font-semibold relative z-10 truncate pr-3 receipt-list-cell" data-col="shipper">{{ m.shipperName || '—' }}</div>
            <div class="col-span-2 text-left font-mono font-bold text-[var(--text)] relative z-10 text-[12px] receipt-list-cell" data-col="flight">{{ fmtFlight(m) }}</div>
            <div class="col-span-1 text-center font-mono font-bold relative z-10"
              :class="receiptTotals[m.id]?.pieces > 0 ? 'text-[var(--text-2)]' : 'text-[var(--text)]'">
              {{ receiptTotals[m.id]?.pieces || m.pieces || '—' }}
              <span v-if="receiptTotals[m.id]?.pieces > 0 && receiptTotals[m.id]?.pieces !== m.pieces" class="text-[13px] text-[var(--muted)] block leading-tight">rec: {{ receiptTotals[m.id].pieces }}</span>
            </div>
            <div class="col-span-1 text-right font-mono font-bold relative z-10 pr-2"
              :class="receiptTotals[m.id]?.weightKg > 0 ? 'text-[var(--text-2)]' : 'text-[var(--text)]'">
              {{ receiptTotals[m.id]?.weightKg ? Number(receiptTotals[m.id].weightKg).toLocaleString() : (m.reportedWeightKg ? Number(m.reportedWeightKg).toLocaleString() : '—') }}
              <span v-if="receiptTotals[m.id]?.weightKg > 0 && receiptTotals[m.id]?.weightKg !== Number(m.reportedWeightKg)" class="text-[13px] text-[var(--muted)] block leading-tight">recibo</span>
            </div>
            <div class="col-span-2 flex items-center justify-center gap-1 relative z-10"><button v-if="draftForMawb(m)" type="button" @click.stop="continueDraft(m)" :title="'DRAFT'">DRAFT</button>
              <button type="button" @click.stop="editOrExpandReceipt(m)" :title="t('warehouse.editReceipt')"
                class="mini-act" :class="{ 'act-edit-has': receiptById[m.id] }">&#9998;</button>
              <button type="button" @click.stop="toggleMawbEvidenceManager(m)" :title="t('warehouse.evidence.title')"
                class="mini-act">&#128193;</button>
              <template v-if="receiptById[m.id]">
                <button type="button" @click.stop="downloadReceiptById(m)" :title="t('warehouse.downloadExcel')"
                  class="mini-act">&#11015;</button>
                <button type="button" @click.stop="downloadHtmlById(m)" :title="t('warehouse.downloadHtml')"
                  class="mini-act">&#128196;</button>
                <button type="button" @click.stop="downloadPdfById(m)" :title="t('warehouse.downloadPdf')"
                  class="mini-act">&#128213;</button>
              </template>
            </div>
            <div class="col-span-2 flex items-center gap-2 relative z-10">
              <div class="status-cell min-w-[196px]">
                <div class="row flex items-center gap-2">
                  <span class="badge flex items-center gap-2 px-2 py-1 rounded-lg border text-[10.5px] font-bold uppercase tracking-wider"
                    :class="statusChipClass(m)">
                    <span class="cir w-2 h-2 rounded-full" :class="statusDotClass(m)"></span>
                    {{ t(`warehouse.derivedStatus.${deriveMawbOperationalStatus(m)}`) }}
                    <span v-if="deriveMawbOperationalStatus(m) === 'CARGADA'"
                      class="flex items-center text-red-600" :title="t('warehouse.status.mountedNoReceipt')">&#9888;</span>
                  </span>
                </div>
                <div class="row flex items-center gap-2 flex-wrap mt-0.5">
                  <button v-if="deriveMawbOperationalStatus(m) === 'PENDIENTE'"
                    @click.stop="toggleExpand(m)"
                    class="btn-rec">
                    {{ 'RECEIVE' }}
                  </button>
                  <span v-else-if="deriveMawbOperationalStatus(m) === 'CARGADA'" class="flex items-center gap-1.5 flex-wrap">
                    <span class="tag-warn" :title="t('warehouse.status.mountedNoReceipt')">&#9888; {{ t('warehouse.status.mountedNoReceipt') }}</span>
                    <button @click.stop="toggleExpand(m)"
                      class="btn-rec">
                      {{ 'RECEIVE' }}
                    </button>
                  </span>
                  <span v-else-if="isReceiptComplete(m) && deriveMawbOperationalStatus(m) === 'DESPACHADA'" class="flex items-center gap-1.5">
                    <span class="tag-final" :title="t('warehouse.status.finalTooltip')">{{ t('warehouse.status.final') }}</span>
                    <span v-if="isPartialLoading(m)"
                      class="tag-warn" :title="t('warehouse.status.partialLoad')">&#9888; {{ t('warehouse.status.partialLoad') }}</span>
                  </span>
                  <span v-else-if="deriveMawbOperationalStatus(m) === 'RECIBIDA'"
                    class="tag-neutral" :title="t('warehouse.status.awaitingMount')">{{ t('warehouse.status.awaitingMount') }}</span>
                  <span v-else-if="deriveMawbOperationalStatus(m) === 'EN_PROCESO'" class="flex items-center gap-1.5">
                    <span class="spinner w-2 h-2 rounded-full border-2 border-[var(--warn)] border-t-transparent animate-spin"></span>
                    <span class="text-[9px] font-bold uppercase tracking-wider text-[var(--warn)]">{{ t('warehouse.status.loadingInProgress') }}</span>
                  </span>
                  <span v-else-if="deriveMawbOperationalStatus(m) === 'MANIFESTADA'"
                    class="tag-final" :title="t('warehouse.status.readyDispatch')">{{ t('warehouse.status.readyDispatch') }}</span>
                </div>
              </div>
          </div>
          </div>

<Teleport to="body">
  <div v-if="expandedId === m.id && receiptForms[m.id]" class="ops-overlay" @click.self="cancelForm">
            <div class="ops-modal rcp-pop" role="dialog" aria-modal="true">
              <!-- Cabecera del popup: identidad del recibo + KPIs en vivo + herramientas -->
              <header class="rcp-pop-hdr">
                <div class="rcp-pop-id">
                  <span class="rcp-pop-kicker">{{ t('warehouse.title') }} · DOCK RECEIPT</span>
                  <div class="rcp-pop-awbline">
                    <h2 class="rcp-pop-awb">{{ m.awbNumber || '—' }}</h2>
                    <span class="rcp-pop-chip">{{ receiptForms[m.id].origin || '—' }} &#8594; {{ receiptForms[m.id].destination || '—' }}</span>
                    <span class="rcp-pop-chip" :class="receiptById[m.id] ? 'ok' : 'warn'">
                      {{ receiptById[m.id] ? 'Recibo emitido' : 'Borrador' }}
                    </span>
                  </div>
                  <span class="rcp-pop-meta">{{ m.shipperName || '—' }} · {{ m.consigneeName || '—' }}</span>
                </div>

                <div class="rcp-pop-kpis">
                  <div class="rcp-pop-kpi">
                    <small>Piezas</small>
                    <strong>{{ stepRecap?.pcs ?? 0 }}</strong>
                  </div>
                  <div class="rcp-pop-kpi">
                    <small>Cobrable</small>
                    <strong>{{ stepRecap?.kg ?? 0 }}<i> kg</i></strong>
                  </div>
                  <div class="rcp-pop-kpi">
                    <small>Paso</small>
                    <strong>{{ localStep }}<i>/{{ steps.length }}</i></strong>
                  </div>
                </div>

                <div class="rcp-pop-tools">
                  <div class="rcp-viewtog" role="tablist" aria-label="Vistas del recibo">
                    <button type="button" role="tab" :aria-selected="receiptView === 'ops'"
                      :class="{ on: receiptView === 'ops' }" @click="setReceiptView('ops')">Operativa</button>
                    <button type="button" role="tab" :aria-selected="receiptView === 'skids'"
                      :class="{ on: receiptView === 'skids' }" @click="setReceiptView('skids')">Skids</button>
                    <button type="button" role="tab" :aria-selected="receiptView === 'preview'"
                      :class="{ on: receiptView === 'preview' }" @click="setReceiptView('preview')">Previa</button>
                  </div>
                  <button class="rcp-icon-btn" @click="printPreview(m)" title="Vista previa para impresión" aria-label="PDF / Imprimir">&#128424;</button>
                  <button v-if="canConfigureOptional" class="rcp-icon-btn rcp-fn-btn" :class="{ on: optionalFnOpen }"
                    @click="optionalFnOpen = !optionalFnOpen"
                    :aria-expanded="optionalFnOpen" aria-controls="rcp-fn-bar"
                    :title="t('warehouse.optionalFn.buttonTitle')" :aria-label="t('warehouse.optionalFn.buttonTitle')">&#9881;</button>
                  <button class="rcp-icon-btn x" @click="cancelForm" title="Cerrar" aria-label="Cerrar">&#10005;</button>
                </div>
              </header>

              <!-- Funciones opcionales del recibo: cada utilidad (sello, muelle…)
                   se activa por separado, aquí mismo, sin salir de la vista. -->
              <section v-if="canConfigureOptional && optionalFnOpen" id="rcp-fn-bar" class="rcp-fn-bar"
                role="region" :aria-label="t('warehouse.optionalFn.title')">
                <div class="rcp-fn-hd">
                  <div class="min-w-0">
                    <strong class="rcp-fn-tt">{{ t('warehouse.optionalFn.title') }}</strong>
                    <p class="rcp-fn-sub">{{ t('warehouse.optionalFn.hint') }}</p>
                  </div>
                  <button type="button" class="rcp-fn-close" @click="optionalFnOpen = false"
                    :aria-label="t('common.close')" :title="t('common.close')">&#10005;</button>
                </div>
                <div class="rcp-fn-rows">
                  <div v-for="fn in OPTIONAL_RECEIPT_FUNCTIONS" :key="fn.key" class="rcp-fn-row">
                    <div class="rcp-fn-lbl">
                      <strong>{{ t(fn.labelKey) }}</strong>
                      <small>{{ t(fn.helpKey) }}</small>
                    </div>
                    <button type="button" role="switch" :aria-checked="isOptionalOn(fn)"
                      :disabled="optionalFnSaving === fn.key"
                      class="rcp-switch" :class="{ on: isOptionalOn(fn), busy: optionalFnSaving === fn.key }"
                      @click="toggleOptional(fn)">
                      <span class="rcp-switch-dot" aria-hidden="true"></span>
                      <span>{{ isOptionalOn(fn) ? t('warehouse.optionalFn.on') : t('warehouse.optionalFn.off') }}</span>
                    </button>
                  </div>
                </div>
              </section>

              <div class="ops-steps">
                <div v-for="(step, si) in steps" :key="si" class="ops-step">
                  <div class="ops-sdot" :class="{ on: localStep === si + 1, dn: stepDone(si), er: stepError(si) }"
                    @click="goToStep(si + 1)">
                    <span v-if="stepDone(si)">&#10003;</span>
                    <span v-else-if="stepError(si)">&#33;</span>
                    <span v-else>{{ si + 1 }}</span>
                  </div>
                  <span class="ops-slbl" @click="goToStep(si + 1)">{{ STEP_LABELS[si] || step }}</span>
                  <div v-if="si < steps.length - 1" class="ops-sln" :class="{ dn: stepDone(si) || localStep > si + 1 }"></div>
                </div>
              </div>

              <!-- Aviso de validación (bloqueante) y advertencias de integridad (no bloquean) -->
              <div v-if="stepErrorMsg && receiptView === 'ops'" class="ops-err" role="alert">&#9888; {{ stepErrorMsg }}</div>
              <div v-else-if="stepWarnings.length && receiptView === 'ops'" class="ops-warn">
                <span v-for="(w, wi) in stepWarnings" :key="wi">&#9888; {{ w }}</span>
              </div>

              <!-- ═══ Scrollable step content ═══ -->
              <div v-if="receiptView === 'ops'" class="px-4 pb-1">
                  <!-- Cockpit de recepción: pesos, cubicaje y discrepancias en vivo -->
                  <div v-if="activeCockpit" class="rcp-cockpit">
                    <div class="rcp-cockpit-hd">
                      <span class="rcp-cockpit-tt">Cockpit de recepción</span>
                      <div class="flex items-center gap-2 min-w-0">
                        <span class="rcp-badge" :class="activeCockpit.deltaOk ? 'ok' : 'warn'">
                          {{ activeCockpit.deltaOk ? 'Dentro de reserva' : 'Discrepancia vs reserva' }}
                        </span>
                        <button type="button" class="rcp-toggle" :aria-expanded="cockpitOpen"
                          @click="cockpitOpen = !cockpitOpen"
                          :title="cockpitOpen ? 'Ocultar cockpit' : 'Mostrar cockpit'">
                          {{ cockpitOpen ? '▾' : '▸' }}
                        </button>
                      </div>
                    </div>
                    <div v-show="cockpitOpen" class="rcp-cockpit-grid">
                      <div class="rcp-kpi">
                        <span class="rcp-kpi-l">Reserva</span>
                        <strong class="rcp-kpi-v">{{ activeCockpit.reservedKg.toFixed(1) }} kg</strong>
                        <small>{{ activeCockpit.reservedLbs.toFixed(0) }} lbs</small>
                      </div>
                      <div class="rcp-kpi">
                        <span class="rcp-kpi-l">Peso báscula</span>
                        <strong class="rcp-kpi-v">{{ activeCockpit.actualKg.toFixed(1) }} kg</strong>
                        <small>{{ activeCockpit.actualLbs.toFixed(0) }} lbs</small>
                      </div>
                      <div class="rcp-kpi">
                        <span class="rcp-kpi-l">Peso volumétrico</span>
                        <strong class="rcp-kpi-v">{{ activeCockpit.dimKg.toFixed(1) }} kg</strong>
                        <small>{{ activeCockpit.dimLbs.toFixed(0) }} lbs</small>
                      </div>
                      <div class="rcp-kpi rcp-kpi-accent">
                        <span class="rcp-kpi-l">Peso cobrable</span>
                        <strong class="rcp-kpi-v">{{ activeCockpit.chargeKg.toFixed(1) }} kg</strong>
                        <small>{{ activeCockpit.chargeLbs.toFixed(0) }} lbs</small>
                      </div>
                      <div class="rcp-kpi">
                        <span class="rcp-kpi-l">Cubicaje</span>
                        <strong class="rcp-kpi-v">{{ activeCockpit.cubicFt3.toFixed(2) }} ft³</strong>
                        <small>{{ activeCockpit.cubicM3.toFixed(3) }} m³ · {{ Math.round(activeCockpit.cubicIn3).toLocaleString() }} in³</small>
                      </div>
                      <div class="rcp-kpi">
                        <span class="rcp-kpi-l">Piezas</span>
                        <strong class="rcp-kpi-v">{{ activeCockpit.pcs }}</strong>
                        <small>detalle del recibo</small>
                      </div>
                    </div>
                    <div v-show="cockpitOpen" class="rcp-delta" :class="activeCockpit.deltaOk ? 'ok' : 'bad'">
                      <span v-if="activeCockpit.reservedKg > 0">
                        Δ vs reserva:
                        <strong>{{ activeCockpit.deltaKg > 0 ? '+' : '' }}{{ activeCockpit.deltaKg.toFixed(1) }} kg</strong>
                        ({{ activeCockpit.deltaPct > 0 ? '+' : '' }}{{ activeCockpit.deltaPct.toFixed(1) }}%)
                      </span>
                      <span v-else>Sin reserva registrada: se compara con el peso reportado del MAWB ({{ activeCockpit.reservedKg.toFixed(1) }} kg).</span>
                      <span v-if="activeCockpit.volumeWarning">⚠ Cobrable volumétrico ({{ activeCockpit.dimKg.toFixed(1) }} kg &gt; {{ activeCockpit.actualKg.toFixed(1) }} kg).</span>
                      <span v-if="activeCockpit.excessWarning">⚠ Excede la reserva en más del 5%.</span>
                    </div>
                  </div>
              </div>

              <div v-if="receiptView === 'ops'" class="ops-body thin-scrollbar overscroll-contain">
              <div v-if="localStep === 1" class="space-y-1.5">
                <div class="grid grid-cols-1 lg:grid-cols-2 gap-x-6 gap-y-1.5">
                  <!-- Left column -->
                  <div class="space-y-2">
                    <div>
                      <label class="ds-label">Gateway / CFS Name</label>
                      <input v-model="receiptForms[m.id].gatewayCfs" type="text" placeholder="SDQ"
                        class="ds-input uppercase" />
                    </div>
                    <div>
                      <label class="ds-label">{{ t('warehouse.form.shipperName') }} <span class="req">*</span></label>
                      <div class="flex gap-2 items-center">
                        <input v-model="receiptForms[m.id].shipperName" type="text" placeholder="Shipper"
                          class="ds-input flex-1"
                          @blur="syncMawbName(m, 'shipperName')" />
                        <span class="text-[12px] text-primary font-mono shrink-0">MAWB: {{ m.shipperName || '—' }}</span>
                      </div>
                    </div>
                    <div>
                      <label class="ds-label">Consignee Name</label>
                      <div class="flex gap-2 items-center">
                        <input v-model="receiptForms[m.id].consigneeName" type="text" placeholder="Consignee"
                          class="ds-input flex-1"
                          @blur="syncMawbName(m, 'consigneeName')" />
                        <span class="text-[12px] text-primary font-mono shrink-0">MAWB: {{ m.consigneeName || '—' }}</span>
                      </div>
                    </div>
                    <div>
                      <label class="ds-label">MAWB Number</label>
                      <input :value="m.awbNumber || ''" readonly
                        class="ds-input bg-surface-hover text-primary" />
                    </div>
                    <div class="grid grid-cols-2 gap-2">
                      <div>
                        <label class="ds-label">Origin</label>
                        <input v-model="receiptForms[m.id].origin" type="text" maxlength="3" placeholder="SDQ"
                          class="ds-input uppercase" />
                      </div>
                      <div>
                        <label class="ds-label">Destination</label>
                        <input v-model="receiptForms[m.id].destination" type="text" maxlength="3" placeholder="MIA"
                          class="ds-input uppercase" />
                      </div>
                    </div>
                    <div>
                      <label class="ds-label">Commodity <span class="rcp-inh">heredado del booking</span></label>
                      <div class="rcp-inh-field">
                        <span class="rcp-inh-val">{{ m.commodityType || 'DRY_CARGO' }}</span>
                        <span class="rcp-inh-hint">Tipo macro · sin desglose por SKU</span>
                      </div>
                    </div>

                    <!-- Campos reservados: visibles en modo fantasma (baja opacidad
                         + borde punteado) hasta que un SUPER_USER los habilite en
                         Configuración. Habilitados se capturan y se guardan con el
                         recibo (seal_number / dock_bay). -->
                    <div class="rcp-ghost-grid">
                      <div v-for="fn in OPTIONAL_RECEIPT_FUNCTIONS" :key="fn.key" class="rcp-ghost"
                        :class="{ 'rcp-ghost--live': isOptionalOn(fn) }">
                        <label class="ds-label">
                          {{ t(fn.labelKey) }}
                          <span class="rcp-ghost-tag" :class="{ 'rcp-ghost-tag--on': isOptionalOn(fn) }">
                            {{ isOptionalOn(fn) ? t('warehouse.optionalFn.tagOn') : t('warehouse.optionalFn.tagOff') }}
                          </span>
                        </label>
                        <input v-model="receiptForms[m.id][fn.key]" type="text" maxlength="64" class="ds-input"
                          :placeholder="isOptionalOn(fn) ? t(fn.placeholderKey) : t('warehouse.optionalFn.offPlaceholder')"
                          :disabled="!isOptionalOn(fn)"
                          :tabindex="isOptionalOn(fn) ? 0 : -1" />
                      </div>
                    </div>
                    <p v-if="!allOptionalOn" class="rcp-ghost-note">
                      {{ canConfigureOptional
                        ? t('warehouse.optionalFn.noteSuper')
                        : t('warehouse.optionalFn.noteRestricted') }}
                    </p>

                    <div class="grid grid-cols-2 gap-2">
                      <div>
                        <label class="ds-label">AWB Reported Pieces</label>
                        <input v-model.number="receiptForms[m.id].awbReportedPieces" type="number" min="0"
                          class="ds-input" />
                      </div>
                      <div>
                        <label class="ds-label">{{ t('warehouse.form.mawbWeight') }}</label>
                        <input v-model.number="receiptForms[m.id].mawbWeightGreatest" type="number" step="0.001"
                          class="ds-input" />
                      </div>
                    </div>
                  </div>

                  <!-- Right column: HAWB info + checkboxes -->
                  <div class="space-y-2">
                    <div class="border-2 border-slate-700 rounded-lg bg-surface overflow-hidden shadow-sm">
                      <div class="flex items-center justify-between bg-slate-700 px-3 py-1.5 border-b border-slate-800">
                        <span class="text-[12px] font-mono font-bold text-white uppercase tracking-wider">
                          HAWBs
                        </span>
                        <div class="flex items-center gap-2 text-[13px] font-mono">
                          <span class="text-slate-200">HAWBs: <strong class="text-white">{{ (receiptForms[m.id].hawbEntries || []).length }}</strong></span>
                          <button @click="addHawbEntry(m)"
                            class="ml-1 w-5 h-5 flex items-center justify-center rounded bg-surface text-slate-700 hover:bg-slate-200 transition text-[14px] font-bold leading-none"
                            title="Agregar HAWB" aria-label="Agregar HAWB">+</button>
                          <span v-if="receiptHawbs[m.id] && receiptHawbs[m.id].length > 0"
                            class="text-tertiary ml-1">({{ receiptHawbs[m.id].length }} en DB)</span>
                        </div>
                      </div>
                      <div class="overflow-x-auto p-2"><table class="data-table data-table--static-head w-full text-[13px] font-mono border-collapse" min-width="0" >
                           <thead>
                             <tr class="text-slate-800 font-bold uppercase tracking-wider">
                               <th class="px-2 py-1 text-left border-b-2 border-strong"># HAWB</th>
                              <th class="px-2 py-1 text-left border-b-2 border-strong">Consignee</th>
                              <th class="px-2 py-1 text-center border-b-2 border-strong">Pcs</th>
                              <th class="px-2 py-1 text-right border-b-2 border-strong">Kg</th>
                              <th class="px-2 py-1 text-center border-b-2 border-strong">Dest</th>
                              <th v-if="(receiptForms[m.id].hawbEntries || []).length > 1"
                                class="px-2 py-1 text-center border-b-2 border-strong"></th>
                            </tr>
                          </thead>
                          <tbody>
                            <tr v-for="(entry, ei) in receiptForms[m.id].hawbEntries" :key="ei"
                              class="hover:bg-surface-hover">
                              <td class="px-1 py-1 border-b border-default">
                                <input v-model="entry.hawbNumber" placeholder="HAWB #"
                                  class="w-[92px] border border-strong rounded px-1.5 py-1 outline-none focus:border-slate-700 bg-surface text-[13px] font-bold text-primary" />
                              </td>
                              <td class="px-1 py-1 border-b border-default">
                                <input v-model="entry.consigneeName" placeholder="Consignee"
                                  class="w-[100px] border border-strong rounded px-1.5 py-1 outline-none focus:border-slate-700 bg-surface text-[13px]" />
                              </td>
                              <td class="px-1 py-1 border-b border-default">
                                <input v-model.number="entry.pieces" type="number" min="0" placeholder="0"
                                  class="w-11 text-center border border-strong rounded px-1 py-1 outline-none focus:border-slate-700 bg-surface text-[13px]" />
                              </td>
                              <td class="px-1 py-1 border-b border-default">
                                <input v-model.number="entry.weightKg" type="number" step="0.1" min="0" placeholder="0"
                                  class="w-[60px] text-right border border-strong rounded px-1.5 py-1 outline-none focus:border-slate-700 bg-surface text-[13px]" />
                              </td>
                              <td class="px-1 py-1 border-b border-default">
                                <input v-model="entry.destination" maxlength="3" placeholder="MIA"
                                  class="w-11 text-center border border-strong rounded px-1 py-1 outline-none focus:border-slate-700 bg-surface text-[13px] uppercase" />
                              </td>
                              <td v-if="(receiptForms[m.id].hawbEntries || []).length > 1"
                                class="px-2 py-1 text-center border-b border-default">
                                <button @click="removeHawbEntry(m.id, ei)" aria-label="Eliminar HAWB"
                                  class="text-tertiary hover:text-secondary transition text-[12px] font-bold">✕</button>
                              </td>
                            </tr>
                          </tbody>
                        </table>
                      </div>
                    </div>

                    <!-- Checkboxes group -->
                    <div class="border-2 border-slate-700 rounded-lg bg-surface p-3 shadow-sm">
                      <div class="text-[12px] font-mono font-bold text-slate-800 uppercase tracking-wider mb-2">Flags / Marcas</div>
                      <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-1.5">
                        <label class="text-[12px] font-mono font-bold text-primary flex items-center gap-1.5 cursor-pointer select-none">
                          <input type="checkbox" v-model="receiptForms[m.id].cashOnly" class="accent-slate-700 rounded w-3 h-3" />
                          <span>Cash Only</span>
                        </label>
                        <label class="text-[12px] font-mono font-bold text-primary flex items-center gap-1.5 cursor-pointer select-none">
                          <input type="checkbox" v-model="receiptForms[m.id].bookedInAcoms" class="accent-slate-700 rounded w-3 h-3" />
                          <span>Booked in ACOMS</span>
                        </label>
                        <label class="text-[12px] font-mono font-bold text-primary flex items-center gap-1.5 cursor-pointer select-none">
                          <input type="checkbox" v-model="receiptForms[m.id].docsProvided" class="accent-slate-700 rounded w-3 h-3" />
                          <span>Documents Provided</span>
                        </label>
                        <label class="text-[12px] font-mono font-bold text-primary flex items-center gap-1.5 cursor-pointer select-none">
                          <input type="checkbox" v-model="receiptForms[m.id].customsCompleted" class="accent-slate-700 rounded w-3 h-3" />
                          <span>Export Customs Completed</span>
                        </label>
                        <label class="text-[12px] font-mono font-bold text-primary flex items-center gap-1.5 cursor-pointer select-none">
                          <input type="checkbox" v-model="receiptForms[m.id].preBuilt" class="accent-slate-700 rounded w-3 h-3" />
                          <span>{{ t('warehouse.checkboxes.preBuilt') }}</span>
                        </label>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- ═══ STEP 2: PIECES ═══ -->
              <div v-if="localStep === 2" class="space-y-1.5">
                <div class="text-[12px] font-mono font-bold text-primary uppercase tracking-wider flex items-center gap-2">
                  <span>Loose Tender — Dimensiones y Pesos</span>
                  <span class="text-[12px] font-mono font-normal text-primary normal-case tracking-normal">
                    ((L x W x H) x #pcs) / 366 = Kg dimensional
                  </span>
                </div>

                <template v-if="(receiptHawbs[m.id] || []).length <= 1 && (receiptForms[m.id]?.hawbEntries || []).length <= 1">
                  <div class="overflow-x-auto border border-strong rounded">
                    <table class="data-table data-table--static-head w-full text-[13px] font-mono border-collapse">
                      <thead>
                        <tr class="bg-slate-700 text-white text-[13px] uppercase tracking-wider">
                          <th class="px-2 py-1 border-r border-slate-600 w-5 text-center">#</th>
                          <th class="px-2 py-1 border-r border-slate-600 w-10 text-center">Pieces</th>
                          <th class="px-2 py-1 border-r border-slate-600 w-14 text-center">Length (in)</th>
                          <th class="px-2 py-1 border-r border-slate-600 w-14 text-center">Width (in)</th>
                          <th class="px-2 py-1 border-r border-slate-600 w-14 text-center">Height (in)</th>
                          <th class="px-2 py-1 border-r border-slate-600 w-14 text-right">Dim Wt</th>
                          <th class="px-2 py-1 border-r border-slate-600 w-16 text-right">Scale LBS</th>
                          <th class="px-2 py-1 border-r border-slate-600 w-16 text-right">Dim LBS</th>
                          <th class="px-2 py-1 border-r border-slate-600 w-16 text-right">S.KGS*</th>
                          <th class="px-2 py-1 border-r border-slate-600 w-16 text-right">Dim KGS</th>
                          <th class="px-2 py-1 border-r border-slate-600 w-16 text-right">CHG KGS</th>
                          <th class="px-2 py-1 w-16 text-right">DOM LBS</th>
                          <th class="px-2 py-1 w-4"></th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr v-for="(p, pi) in receiptForms[m.id].pieces" :key="pi"
                          class="border-b border-strong hover:bg-surface-hover transition-colors">
                          <td class="px-2 py-1 text-center text-primary border-r border-strong">{{ pi + 1 }}</td>
                          <td class="px-2 py-1 border-r border-strong">
                            <input v-model.number="p.pieces" type="number" min="0"
                              class="w-full text-center border border-strong rounded px-1.5 py-0.5 outline-none focus:border-slate-500 bg-surface text-[13px]"
                              @input="calcPiece(m.id, pi)" />
                          </td>
                          <td class="px-2 py-1 border-r border-strong">
                            <input v-model.number="p.lengthIn" type="number" step="0.01" min="0"
                              class="w-full text-center border border-strong rounded px-1.5 py-0.5 outline-none focus:border-slate-500 bg-surface text-[13px]"
                              @input="calcPiece(m.id, pi)" />
                          </td>
                          <td class="px-2 py-1 border-r border-strong">
                            <input v-model.number="p.widthIn" type="number" step="0.01" min="0"
                              class="w-full text-center border border-strong rounded px-1.5 py-0.5 outline-none focus:border-slate-500 bg-surface text-[13px]"
                              @input="calcPiece(m.id, pi)" />
                          </td>
                          <td class="px-2 py-1 border-r border-strong">
                            <input v-model.number="p.heightIn" type="number" step="0.01" min="0"
                              class="w-full text-center border border-strong rounded px-1.5 py-0.5 outline-none focus:border-slate-500 bg-surface text-[13px]"
                              @input="calcPiece(m.id, pi)" />
                          </td>
                          <td class="px-2 py-1 border-r border-strong text-right font-semibold text-primary">{{ p.dimWeight ? p.dimWeight.toFixed(1) : '—' }}</td>
                          <td class="px-2 py-1 border-r border-strong">
                            <input v-model.number="p.scaleWeightLbs" type="number" step="0.001" min="0"
                              class="w-full text-center border border-strong rounded px-1.5 py-0.5 outline-none focus:border-slate-500 bg-surface text-[13px]"
                              @input="calcPiece(m.id, pi)" />
                          </td>
                          <td class="px-2 py-1 border-r border-strong text-right font-semibold text-primary">{{ p.dimWeightLbs ? p.dimWeightLbs.toFixed(1) : '—' }}</td>
                          <td class="px-2 py-1 border-r border-strong text-right font-semibold text-primary">{{ (p.scaleWeightKg || 0).toFixed(2) }}</td>
                          <td class="px-2 py-1 border-r border-strong text-right font-semibold">{{ p.dimWeightKg ? p.dimWeightKg.toFixed(2) : '—' }}</td>
                          <td class="px-2 py-1 border-r border-strong text-right text-primary">{{ p.chargeableKg ? p.chargeableKg.toFixed(2) : '—' }}</td>
                          <td class="px-2 py-1 border-r border-strong text-right text-primary">{{ p.chargeableLbs ? p.chargeableLbs.toFixed(2) : '—' }}</td>
                          <td class="px-2 py-1 text-center">
                            <button @click="removePiece(m.id, pi)" aria-label="Eliminar pieza" class="text-tertiary hover:text-secondary transition text-[12px]">✕</button>
                          </td>
                        </tr>
                      </tbody>
                      <tfoot>
                        <tr class="bg-surface-hover text-primary text-[13px]">
                          <td class="px-2 py-1 border-t border-strong text-primary text-center"></td>
                          <td class="px-2 py-1 border-t border-strong text-center">{{ totalPieces(m.id, null) }}</td>
                          <td class="px-2 py-1 border-t border-strong text-primary text-[13px]" colspan="3">TOTAL</td>
                          <td class="px-2 py-1 border-t border-strong text-right">{{ totalDimWeight(m.id, null).toFixed(1) }}</td>
                          <td class="px-2 py-1 border-t border-strong text-right">{{ totalScaleLbs(m.id, null).toFixed(2) }}</td>
                          <td class="px-2 py-1 border-t border-strong text-right">{{ totalDimLbs(m.id, null).toFixed(2) }}</td>
                          <td class="px-2 py-1 border-t border-strong text-right">{{ totalScaleKg(m.id, null).toFixed(2) }}</td>
                          <td class="px-2 py-1 border-t border-strong text-right">{{ totalDimKg(m.id, null).toFixed(2) }}</td>
                          <td class="px-2 py-1 border-t border-strong text-right text-primary">{{ totalChargeableKg(m.id).toFixed(2) }}</td>
                          <td class="px-2 py-1 border-t border-strong text-right text-primary">{{ totalChargeableLbs(m.id).toFixed(2) }}</td>
                          <td class="px-2 py-1 border-t border-strong"></td>
                        </tr>
                      </tfoot>
                    </table>
                  </div>
                    <div class="flex justify-between items-center">
                      <button @click="addPiece(m.id)" class="text-[12px] text-primary font-mono uppercase tracking-wider hover:text-primary transition">+ Agregar pieza</button>
                      <div class="flex gap-3 text-[12px] font-mono text-primary">
                      <span>Piece Count: <strong class="text-primary">{{ totalPieces(m.id, null) }}</strong></span>
                      <span>Actual: <strong class="text-primary">{{ totalScaleKg(m.id, null).toFixed(0) }} KGS / {{ totalScaleLbs(m.id, null).toFixed(0) }} LBS</strong></span>
                      <span>Chargeable: <strong class="text-primary">{{ totalChargeableKg(m.id).toFixed(0) }} KGS / {{ totalChargeableLbs(m.id).toFixed(0) }} LBS</strong></span>
                    </div>
                  </div>
                  <div class="text-[11px] text-tertiary font-mono mt-1 text-right">* S.KGS = LBS ÷ 2.20462 (auto)</div>
                </template>

                <template v-else>
                  <div v-for="(h, hi) in hawbsForDisplay(m.id)" :key="h._hawbId || h.id" class="border border-strong rounded overflow-hidden bg-surface">
                    <div class="flex items-center justify-between bg-surface-hover px-3 py-1.5 border-b border-strong">
                      <span class="text-[12px] font-mono font-bold text-primary">
                        HAWB {{ hi + 1 }}: {{ h.hawbNumber || h.hawbNumber || '—' }} &mdash; {{ h.consigneeName || h.consigneeName || '—' }}
                        <span class="text-primary font-normal ml-2">({{ piecesByHawb(m.id, h._hawbId || h.id).length }} pieza(s))</span>
                      </span>
                      <span class="text-[12px] font-mono text-primary font-bold">{{ piecesByHawb(m.id, h._hawbId || h.id).reduce((s, p) => s + (p.pieces || 1), 0) }} pcs</span>
                    </div>
                    <div class="overflow-x-auto"><table class="data-table data-table--static-head w-full text-[13px] font-mono border-collapse" min-width="0" >
                         <thead>
                           <tr class="bg-slate-600 text-white text-[13px] uppercase tracking-wider">
                             <th class="px-1 py-0.5 border-r border-slate-500 w-5 text-center">#</th>
                            <th class="px-1 py-0.5 border-r border-slate-500 w-10 text-center">Pcs</th>
                            <th class="px-1 py-0.5 border-r border-slate-500 w-14 text-center">L</th>
                            <th class="px-1 py-0.5 border-r border-slate-500 w-14 text-center">W</th>
                            <th class="px-1 py-0.5 border-r border-slate-500 w-14 text-center">H</th>
                            <th class="px-1 py-0.5 border-r border-slate-500 w-12 text-right">DimWt</th>
                            <th class="px-1 py-0.5 border-r border-slate-500 w-16 text-right">S.LBS</th>
                            <th class="px-1 py-0.5 border-r border-slate-500 w-14 text-right">DLBS</th>
                            <th class="px-1 py-0.5 border-r border-slate-500 w-16 text-right">S.KGS*</th>
                            <th class="px-1 py-0.5 border-r border-slate-500 w-14 text-right">DKGS</th>
                            <th class="px-1 py-0.5 border-r border-slate-500 w-14 text-right">CKGS</th>
                            <th class="px-1 py-0.5 w-16 text-right">CLBS</th>
                            <th class="px-1 py-0.5 w-4"></th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr v-for="(entry, pi) in piecesByHawbIndexed(m.id, (h._hawbId || h.id))" :key="pi"
                            class="border-b border-strong hover:bg-surface-hover">
                            <td class="px-1 py-0.5 text-center text-primary border-r border-strong">{{ pi + 1 }}</td>
                            <td class="px-1 py-0.5 border-r border-strong">
                              <input v-model.number="entry.piece.pieces" type="number" min="0"
                                class="w-full text-center border border-strong rounded px-1 py-0.5 outline-none focus:border-slate-500 bg-surface text-[13px]"
                                @input="calcPiece(m.id, entry.idx)" />
                            </td>
                            <td class="px-1 py-0.5 border-r border-strong">
                              <input v-model.number="entry.piece.lengthIn" type="number" step="0.01"
                                class="w-full text-center border border-strong rounded px-1 py-0.5 outline-none focus:border-slate-500 bg-surface text-[13px]"
                                @input="calcPiece(m.id, entry.idx)" />
                            </td>
                            <td class="px-1 py-0.5 border-r border-strong">
                              <input v-model.number="entry.piece.widthIn" type="number" step="0.01"
                                class="w-full text-center border border-strong rounded px-1 py-0.5 outline-none focus:border-slate-500 bg-surface text-[13px]"
                                @input="calcPiece(m.id, entry.idx)" />
                            </td>
                            <td class="px-1 py-0.5 border-r border-strong">
                              <input v-model.number="entry.piece.heightIn" type="number" step="0.01"
                                class="w-full text-center border border-strong rounded px-1 py-0.5 outline-none focus:border-slate-500 bg-surface text-[13px]"
                                @input="calcPiece(m.id, entry.idx)" />
                            </td>
                            <td class="px-1 py-0.5 border-r border-strong text-right text-primary">{{ entry.piece.dimWeight ? entry.piece.dimWeight.toFixed(1) : '—' }}</td>
                            <td class="px-1 py-0.5 border-r border-strong">
                              <input v-model.number="entry.piece.scaleWeightLbs" type="number" step="0.001"
                                class="w-full text-center border border-strong rounded px-1 py-0.5 outline-none focus:border-slate-500 bg-surface text-[13px]"
                                @input="calcPiece(m.id, entry.idx)" />
                            </td>
                            <td class="px-1 py-0.5 border-r border-strong text-right text-primary">{{ entry.piece.dimWeightLbs ? entry.piece.dimWeightLbs.toFixed(1) : '—' }}</td>
                            <td class="px-1 py-0.5 border-r border-strong text-right text-primary">{{ (entry.piece.scaleWeightKg || 0).toFixed(2) }}</td>
                            <td class="px-1 py-0.5 border-r border-strong text-right">{{ entry.piece.dimWeightKg ? entry.piece.dimWeightKg.toFixed(2) : '—' }}</td>
                            <td class="px-1 py-0.5 border-r border-strong text-right">{{ entry.piece.chargeableKg ? entry.piece.chargeableKg.toFixed(2) : '—' }}</td>
                            <td class="px-1 py-0.5 border-r border-strong text-right">{{ entry.piece.chargeableLbs ? entry.piece.chargeableLbs.toFixed(2) : '—' }}</td>
                            <td class="px-1 py-0.5 text-center">
                              <button @click="removePiece(m.id, entry.idx)" aria-label="Eliminar pieza" class="text-tertiary hover:text-secondary text-[12px]">✕</button>
                            </td>
                          </tr>
                        </tbody>
                        <tfoot>
                          <tr class="bg-surface-hover text-primary text-[13px]">
                            <td class="px-1 py-0.5 border-t border-strong"></td>
                            <td class="px-1 py-0.5 border-t border-strong text-center">{{ hawbTotalPieces(m.id, (h._hawbId || h.id)) }}</td>
                            <td class="px-1 py-0.5 border-t border-strong" colspan="3">TOTAL</td>
                            <td class="px-1 py-0.5 border-t border-strong text-right">{{ hawbDimWeight(m.id, (h._hawbId || h.id)).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t border-strong text-right">{{ hawbScaleLbs(m.id, (h._hawbId || h.id)).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t border-strong text-right">{{ hawbDimLbs(m.id, (h._hawbId || h.id)).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t border-strong text-right">{{ hawbScaleKg(m.id, (h._hawbId || h.id)).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t border-strong text-right">{{ hawbDimKg(m.id, (h._hawbId || h.id)).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t border-strong text-right">{{ hawbChargeableKg(m.id, (h._hawbId || h.id)).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t border-strong text-right">{{ hawbChargeableLbs(m.id, (h._hawbId || h.id)).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t border-strong"></td>
                          </tr>
                        </tfoot>
                      </table>
                    </div>
                    <div class="px-2 py-1 border-t border-strong">
                      <button @click="addPiece(m.id, (h._hawbId || h.id))" class="text-[12px] text-primary font-mono uppercase tracking-wider hover:text-primary transition">+ Agregar pieza a este HAWB</button>
                      <span class="text-[11px] text-tertiary font-mono ml-4">* S.KGS = LBS ÷ 2.20462 (auto)</span>
                    </div>
                  </div>
                  <!-- Resumen General: agrupado por dimensión única LxWxH -->
                  <div class="border-2 border-slate-600 rounded overflow-hidden bg-surface mt-2">
                    <div class="flex items-center justify-between bg-slate-600 px-3 py-1.5 border-b border-slate-700">
                      <span class="text-[12px] font-mono font-bold text-white uppercase tracking-wider">Resumen General — Todas las HAWBs</span>
                      <span class="text-[12px] font-mono text-slate-200">{{ totalPieces(m.id, null) }} piezas · {{ groupedSummary(m.id).length }} dim</span>
                    </div>
                    <div class="overflow-x-auto">
                      <table class="data-table data-table--static-head w-full text-[13px] font-mono border-collapse" >
                        <thead>
                          <tr class="bg-slate-200 text-slate-800 text-[13px] uppercase tracking-wider">
                            <th class="px-1 py-0.5 border-r border-strong w-5 text-center">#</th>
                            <th class="px-1 py-0.5 border-r border-strong w-14 text-center">L</th>
                            <th class="px-1 py-0.5 border-r border-strong w-14 text-center">W</th>
                            <th class="px-1 py-0.5 border-r border-strong w-14 text-center">H</th>
                            <th class="px-1 py-0.5 border-r border-strong w-10 text-center">Pcs</th>
                            <th class="px-1 py-0.5 border-r border-strong w-12 text-right">DimWt</th>
                            <th class="px-1 py-0.5 border-r border-strong w-16 text-right">S.LBS</th>
                            <th class="px-1 py-0.5 border-r border-strong w-14 text-right">DLBS</th>
                            <th class="px-1 py-0.5 border-r border-strong w-16 text-right">S.KGS</th>
                            <th class="px-1 py-0.5 border-r border-strong w-14 text-right">DKGS</th>
                            <th class="px-1 py-0.5 border-r border-strong w-14 text-right">CKGS</th>
                            <th class="px-1 py-0.5 w-14 text-right">CLBS</th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr v-for="(g, gi) in groupedSummary(m.id)" :key="gi"
                            class="border-b border-strong hover:bg-surface-hover">
                            <td class="px-1 py-0.5 text-center text-secondary border-r border-strong">{{ gi + 1 }}</td>
                            <td class="px-1 py-0.5 text-center border-r border-strong">{{ g.lengthIn || '—' }}</td>
                            <td class="px-1 py-0.5 text-center border-r border-strong">{{ g.widthIn || '—' }}</td>
                            <td class="px-1 py-0.5 text-center border-r border-strong">{{ g.heightIn || '—' }}</td>
                            <td class="px-1 py-0.5 text-center border-r border-strong font-bold">{{ g.totalPieces }}</td>
                            <td class="px-1 py-0.5 text-right border-r border-strong">{{ g.totalDimWeight.toFixed(1) }}</td>
                            <td class="px-1 py-0.5 text-right border-r border-strong">{{ g.totalScaleLbs.toFixed(1) }}</td>
                            <td class="px-1 py-0.5 text-right border-r border-strong">{{ g.totalDimLbs.toFixed(1) }}</td>
                            <td class="px-1 py-0.5 text-right border-r border-strong">{{ g.totalScaleKg.toFixed(2) }}</td>
                            <td class="px-1 py-0.5 text-right border-r border-strong">{{ g.totalDimKg.toFixed(2) }}</td>
                            <td class="px-1 py-0.5 text-right border-r border-strong">{{ g.totalChargeableKg.toFixed(2) }}</td>
                            <td class="px-1 py-0.5 text-right">{{ g.totalChargeableLbs.toFixed(2) }}</td>
                          </tr>
                        </tbody>
                        <tfoot>
                          <tr class="bg-surface-hover text-primary text-[13px] font-bold">
                            <td class="px-1 py-0.5 border-t-2 border-strong" colspan="4">TOTAL</td>
                            <td class="px-1 py-0.5 border-t-2 border-strong text-center">{{ totalPieces(m.id, null) }}</td>
                            <td class="px-1 py-0.5 border-t-2 border-strong text-right">{{ totalDimWeight(m.id, null).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t-2 border-strong text-right">{{ totalScaleLbs(m.id, null).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t-2 border-strong text-right">{{ totalDimLbs(m.id, null).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t-2 border-strong text-right">{{ totalScaleKg(m.id, null).toFixed(2) }}</td>
                            <td class="px-1 py-0.5 border-t-2 border-strong text-right">{{ totalDimKg(m.id, null).toFixed(2) }}</td>
                            <td class="px-1 py-0.5 border-t-2 border-strong text-right">{{ totalChargeableKg(m.id, null).toFixed(2) }}</td>
                            <td class="px-1 py-0.5 border-t-2 border-strong text-right">{{ totalChargeableLbs(m.id, null).toFixed(2) }}</td>
                          </tr>
                        </tfoot>
                      </table>
                    </div>
                  </div>
                </template>

              </div>

              <!-- STEP 3: CONTROLES (checklist, cadena de frío, documentación) -->
              <div v-if="localStep === 3" class="space-y-2">
                <div class="rcp-panel">
                  <div class="rcp-panel-hd">
                    <span class="rcp-panel-tt">Checklist operativo — integridad del embalaje</span>
                    <span class="rcp-badge" :class="checksSummary(m).ok ? 'ok' : 'warn'">
                      {{ checksSummary(m).done }}/{{ checksSummary(m).total }} verificados
                    </span>
                  </div>
                  <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-1.5">
                    <label v-for="c in CHECK_ITEMS" :key="c.key"
                      class="flex items-center gap-2 cursor-pointer select-none text-[12.5px] text-primary">
                      <input type="checkbox" v-model="receiptForms[m.id].operationalChecks[c.key]"
                        class="accent-slate-700 rounded w-3.5 h-3.5" />
                      <span>{{ c.label }}</span>
                    </label>
                  </div>
                  <div class="mt-2">
                    <label class="ds-label text-[11px]">Observaciones del checklist</label>
                    <input v-model="receiptForms[m.id].operationalChecks.notes" type="text"
                      placeholder="Daños, faltantes, observaciones del embalaje..." class="ds-input" />
                  </div>
                </div>

                <div class="rcp-panel">
                  <div class="rcp-panel-hd">
                    <span class="rcp-panel-tt">Documentación física y DUA</span>
                  </div>
                  <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-1.5">
                    <label v-for="c in DOC_ITEMS" :key="c.key"
                      class="flex items-center gap-2 cursor-pointer select-none text-[12.5px] text-primary">
                      <input type="checkbox" v-model="receiptForms[m.id].operationalChecks[c.key]"
                        class="accent-slate-700 rounded w-3.5 h-3.5" />
                      <span>{{ c.label }}</span>
                    </label>
                    <div>
                      <label class="ds-label text-[11px]">N.º DUA</label>
                      <input v-model="receiptForms[m.id].duaNumber" type="text" placeholder="Ej. 2026-000123"
                        class="ds-input font-mono uppercase" />
                    </div>
                  </div>
                </div>

                <div class="rcp-panel">
                  <div class="rcp-panel-hd">
                    <span class="rcp-panel-tt">Cadena de frío / perecederos</span>
                    <label class="flex items-center gap-2 cursor-pointer select-none text-[12px] text-primary">
                      <input type="checkbox" v-model="receiptForms[m.id].coldChain.enabled"
                        class="accent-slate-700 rounded w-3.5 h-3.5" />
                      <span>Aplica</span>
                    </label>
                  </div>
                  <!-- Registro de la temperatura de recepción (queda guardado en el recibo) -->
                  <div class="rcp-temp">
                    <div class="min-w-0 flex-1">
                      <label class="ds-label text-[11px]">Temperatura de recepción (°C)</label>
                      <input v-model.number="receiptForms[m.id].coldChain.receivedTempC" type="number" step="0.1"
                        inputmode="decimal" placeholder="Ej. 4.5" class="ds-input rcp-temp-input"
                        @change="markReceptionTemp(m)" />
                    </div>
                    <div class="rcp-temp-actions">
                      <button type="button" @click="registerReceptionTemp(m)"
                        class="rcp-btn-mini">Registrar temperatura</button>
                      <span v-if="receiptForms[m.id].coldChain.receivedAt" class="text-[10.5px] font-mono text-secondary whitespace-nowrap">
                        {{ fmtStamp(receiptForms[m.id].coldChain.receivedAt) }}
                      </span>
                    </div>
                  </div>
                  <div v-if="receiptForms[m.id].coldChain.enabled" class="space-y-2">
                    <div class="grid grid-cols-2 sm:grid-cols-4 gap-2 items-end">
                      <div>
                        <label class="ds-label text-[11px]">Temp. mín (°C)</label>
                        <input v-model.number="receiptForms[m.id].coldChain.minC" type="number" step="0.1"
                          class="ds-input" />
                      </div>
                      <div>
                        <label class="ds-label text-[11px]">Temp. máx (°C)</label>
                        <input v-model.number="receiptForms[m.id].coldChain.maxC" type="number" step="0.1"
                          class="ds-input" />
                      </div>
                      <div class="sm:col-span-2 pb-1">
                        <span class="rcp-badge" :class="coldStatus(m).ok ? 'ok' : 'bad'">{{ coldStatus(m).label }}</span>
                      </div>
                    </div>
                    <div class="space-y-1">
                      <div v-for="(r, ri) in receiptForms[m.id].coldChain.readings" :key="ri"
                        class="flex items-center gap-2">
                        <input v-model.number="r.valueC" type="number" step="0.1" placeholder="°C"
                          class="w-20 border border-strong rounded px-2 py-1 text-[13px] font-mono bg-surface text-primary outline-none focus:border-slate-500" />
                        <input v-model="r.at" type="datetime-local"
                          class="flex-1 min-w-0 border border-strong rounded px-2 py-1 text-[12px] font-mono bg-surface text-primary outline-none focus:border-slate-500" />
                        <button type="button" @click="removeColdReading(m, ri)"
                          class="text-tertiary hover:text-secondary text-[12px] font-bold" aria-label="Eliminar lectura">✕</button>
                      </div>
                      <div v-if="!receiptForms[m.id].coldChain.readings.length"
                        class="text-[11px] font-mono text-tertiary">Sin lecturas de temperatura registradas.</div>
                      <button type="button" @click="addColdReading(m)"
                        class="text-[12px] font-mono uppercase tracking-wider text-primary hover:text-primary transition">+ Registrar temperatura</button>
                    </div>
                  </div>
                </div>
              </div>

              <!-- ═══ STEP 4: REMARKS ═══ -->
              <div v-if="localStep === 4" class="space-y-1.5">
                <label class="ds-label">Remarks / Observaciones</label>
                <textarea v-model="receiptForms[m.id].remarks" rows="3" placeholder="Notas, observaciones, instrucciones especiales..."
                  class="ds-input resize-none"></textarea>
              </div>

              <!-- ═══ STEP 5: EVIDENCE ═══ -->
              <div v-if="localStep === 5" class="space-y-2">
                <div class="flex items-center justify-between gap-2">
                  <p class="text-[12px] font-mono text-primary">{{ t('warehouse.evidence.subtitle') }}</p>
                  <span class="shrink-0 text-[11px] font-mono text-secondary tabular-nums">
                    {{ t('warehouse.evidence.counter', { n: (receiptForms[m.id].evidence || []).length }) }}
                  </span>
                </div>

                <!-- Evidencias del MAWB (gestor, persistidas en el MAWB) -->
                <div v-if="(receiptForms[m.id].mawbEvidence || []).length > 0">
                  <div class="flex items-center justify-between gap-2 mb-1">
                    <span class="text-[11px] font-mono font-bold text-primary uppercase tracking-wider">{{ t('warehouse.evidence.mawbEvidence') }}</span>
                    <button type="button" @click.stop="toggleMawbEvidenceManager(m)" class="text-[11px] font-mono uppercase tracking-wider text-tertiary hover:text-primary transition underline underline-offset-2">
                      {{ t('warehouse.evidence.manage') }}
                    </button>
                  </div>
                  <div class="grid grid-cols-2 sm:grid-cols-4 gap-2 mb-3">
                    <div v-for="(ev, ei) in receiptForms[m.id].mawbEvidence" :key="'mawb-' + ei"
                      class="relative border border-strong rounded bg-surface-hover/30 overflow-hidden group cursor-pointer" @click="previewEvidence(ev)">
                      <img v-if="ev.type === 'image' && ev.url" :src="ev.url" class="w-full h-20 object-cover" alt="" />
                      <div v-else-if="isPdfUrl(ev.url)" class="w-full h-20 flex flex-col items-center justify-center bg-surface-hover text-slate-700 text-[12px] font-mono">
                        <component :is="icons.File" :size="18" class="mb-0.5" />
                        <span class="text-[10px] leading-tight px-1 text-center truncate max-w-full">PDF</span>
                      </div>
                      <div v-else class="w-full h-20 flex items-center justify-center bg-surface-hover text-primary text-[12px] font-mono px-2 text-center leading-tight">{{ ev.name }}</div>
                      <span class="block text-[11px] font-mono text-primary px-1.5 py-0.5 leading-tight truncate">{{ ev.name }}</span>
                    </div>
                  </div>
                </div>

                <!-- Evidencias de este recibo -->
                <div v-if="(receiptForms[m.id].evidence || []).length === 0"
                  class="border border-dashed border-strong rounded px-3 py-4 text-center">
                  <span class="text-[12px] font-mono text-tertiary uppercase tracking-wider">{{ t('warehouse.evidence.empty') }}</span>
                </div>

                <div v-else class="grid grid-cols-2 sm:grid-cols-4 gap-2">
                  <div v-for="(ev, ei) in receiptForms[m.id].evidence" :key="'rec-' + ei"
                    class="relative border border-strong rounded bg-surface overflow-hidden group cursor-pointer" @click="previewEvidence(ev)">
                    <img v-if="ev.type === 'image' && ev.url" :src="ev.url" class="w-full h-20 object-cover" alt="" />
                    <div v-else-if="isPdfUrl(ev.url)" class="w-full h-20 flex flex-col items-center justify-center bg-surface-hover text-slate-700 text-[12px] font-mono">
                      <component :is="icons.File" :size="18" class="mb-0.5" />
                      <span class="text-[10px] leading-tight px-1 text-center truncate max-w-full">PDF</span>
                    </div>
                    <div v-else class="w-full h-20 flex items-center justify-center bg-surface-hover text-primary text-[12px] font-mono">{{ ev.name }}</div>
                    <button @click.stop="removeEvidence(m.id, ei)" :aria-label="t('warehouse.evidence.remove')"
                      class="absolute top-0.5 right-0.5 w-5 h-5 bg-slate-900/80 text-white rounded-full text-[11px] flex items-center justify-center opacity-0 group-hover:opacity-100 focus:opacity-100 transition">
                      <component :is="icons.Close" :size="11" />
                    </button>
                    <span class="block text-[11px] font-mono text-primary px-1.5 py-0.5 truncate">{{ ev.name }}</span>
                  </div>
                </div>

                <!-- Acciones de captura -->
                <div class="grid grid-cols-1 sm:grid-cols-3 gap-2 pt-1">
                  <button type="button" @click="openReceiptCapture(m.id)"
                    class="flex items-center justify-center gap-2 px-3 py-2.5 rounded border-2 border-primary bg-surface hover:bg-surface-hover transition active:scale-[.98]">
                    <component :is="icons.Camera" :size="15" class="text-primary" />
                    <span class="text-[11px] font-mono font-bold uppercase tracking-wider text-primary">{{ t('warehouse.evidence.takePhoto') }}</span>
                  </button>
                  <button type="button" @click="addEvidence(m.id)"
                    class="flex items-center justify-center gap-2 px-3 py-2.5 rounded border border-strong bg-surface hover:bg-surface-hover transition active:scale-[.98]">
                    <component :is="icons.FileUpload" :size="15" class="text-primary" />
                    <span class="text-[11px] font-mono font-bold uppercase tracking-wider text-primary">{{ t('warehouse.evidence.uploadFile') }}</span>
                  </button>
                  <button type="button" @click="toggleMawbEvidenceManager(m)"
                    class="flex items-center justify-center gap-2 px-3 py-2.5 rounded border border-strong bg-surface hover:bg-surface-hover transition active:scale-[.98]">
                    <component :is="icons.Folder" :size="15" class="text-primary" />
                    <span class="text-[11px] font-mono font-bold uppercase tracking-wider text-primary">{{ t('warehouse.evidence.mawbEvidence') }}</span>
                  </button>
                </div>
              </div>

              <!-- ═══ STEP 6: SIGNATURES ═══ -->
              <div v-if="localStep === 6" class="space-y-1.5">
                <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div>
                    <label class="ds-label">Dock Signature</label>
                    <SignaturePad v-model="receiptForms[m.id].dockSignature" :width="280" :height="60" />
                  </div>
                  <div class="flex flex-col justify-end">
                    <label class="ds-label">Print Name</label>
                        <input v-model="receiptForms[m.id].printName" type="text" placeholder="Nombre"
                          class="ds-input" />
                  </div>
                </div>
                <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 border-t border-strong pt-2">
                  <div>
                    <label class="ds-label">Delivered By</label>
                    <div class="grid grid-cols-2 gap-1.5 mb-1">
                      <div>
                        <label class="ds-label text-[12px]">Name</label>
                        <input v-model="receiptForms[m.id].deliveredByName" type="text"
                          class="ds-input" />
                      </div>
                      <div>
                        <label class="ds-label text-[12px]">ID / Cédula</label>
                        <input v-model="receiptForms[m.id].deliveredByIdNum" type="text"
                          class="ds-input" />
                      </div>
                    </div>
                    <SignaturePad v-model="receiptForms[m.id].deliveredBySig" :width="280" :height="50" />
                  </div>
                  <div>
                    <label class="ds-label">Received By</label>
                    <div class="grid grid-cols-2 gap-1.5 mb-1">
                      <div>
                        <label class="ds-label text-[12px]">Name</label>
                        <input v-model="receiptForms[m.id].receivedByName" type="text"
                          class="ds-input" />
                      </div>
                      <div>
                        <label class="ds-label text-[12px]">ID / Cédula</label>
                        <input v-model="receiptForms[m.id].receivedByIdNum" type="text"
                          class="ds-input" />
                      </div>
                    </div>
                    <SignaturePad v-model="receiptForms[m.id].receivedBySig" :width="280" :height="50" />
                  </div>
                  <div>
                    <label class="ds-label">Broker Representative</label>
                    <div class="grid grid-cols-2 gap-1.5 mb-1">
                      <div>
                        <label class="ds-label text-[12px]">Name</label>
                        <input v-model="receiptForms[m.id].brokerName" type="text"
                          class="ds-input" />
                      </div>
                      <div>
                        <label class="ds-label text-[12px]">ID / Cédula</label>
                        <input v-model="receiptForms[m.id].brokerIdNum" type="text"
                          class="ds-input" />
                      </div>
                    </div>
                    <SignaturePad v-model="receiptForms[m.id].brokerSig" :width="280" :height="50" />
                  </div>
                </div>

                <!-- Choferes / camiones: una MAWB puede llegar en varios viajes -->
                <div class="border-t border-strong pt-2 mt-2">
                  <div class="flex items-center justify-between gap-2 mb-1.5">
                    <div>
                      <span class="ds-label !mb-0">Choferes que entregan / camiones</span>
                      <p class="text-[11px] font-mono text-secondary mt-0.5">
                        Registra cada chofer cuando la carga llega en varios camiones o viajes.
                      </p>
                    </div>
                    <button type="button" @click="addDriver(m)"
                      class="shrink-0 text-[12px] font-mono font-bold uppercase tracking-wider text-primary hover:text-primary transition">
                      + Agregar chofer
                    </button>
                  </div>
                  <div v-if="!receiptForms[m.id].driverSignatures.length"
                    class="border border-dashed border-strong rounded px-3 py-2 text-[11px] font-mono text-tertiary">
                    Sin choferes adicionales registrados.
                  </div>
                  <div v-else class="space-y-2">
                    <div v-for="(d, di) in receiptForms[m.id].driverSignatures" :key="di"
                      class="border border-strong rounded p-2 bg-surface">
                      <div class="flex items-center justify-between gap-2 mb-1">
                        <span class="text-[11px] font-mono font-bold uppercase tracking-wider text-primary">
                          Chofer {{ di + 1 }}
                        </span>
                        <button type="button" @click="removeDriver(m, di)"
                          class="text-tertiary hover:text-secondary text-[12px] font-bold" aria-label="Eliminar chofer">✕</button>
                      </div>
                      <div class="grid grid-cols-1 sm:grid-cols-2 gap-1.5 mb-1">
                        <div>
                          <label class="ds-label text-[11px]">Nombre</label>
                          <input v-model="d.name" type="text" placeholder="Nombre del chofer" class="ds-input" />
                        </div>
                        <div>
                          <label class="ds-label text-[11px]">ID / Cédula</label>
                          <input v-model="d.idNum" type="text" placeholder="Cédula" class="ds-input" />
                        </div>
                        <div>
                          <label class="ds-label text-[11px]">Camión / Placa</label>
                          <input v-model="d.truckPlate" type="text" placeholder="Placa del camión"
                            class="ds-input uppercase" />
                        </div>
                        <div>
                          <label class="ds-label text-[11px]">Fecha / hora de llegada</label>
                          <input v-model="d.arrivedAt" type="datetime-local" class="ds-input" />
                        </div>
                      </div>
                      <SignaturePad v-model="d.sig" :width="280" :height="46" />
                    </div>
                  </div>
                </div>
              </div>

              </div> <!-- end scrollable step content -->

              <!-- ═══ Vista: Detalle de Skids (métrico + imperial) ═══ -->
              <div v-if="receiptView === 'skids'" class="ops-body thin-scrollbar overscroll-contain">
                <div class="rcp-vw-hd">
                  <div class="min-w-0">
                    <h3 class="rcp-vw-tt">Detalle de skids y piezas</h3>
                    <p class="rcp-vw-sub">Dimensiones individuales en pulgadas y centímetros, volumen (CBM / CFT) y pesos por línea.</p>
                  </div>
                  <button class="btn-ghost" @click="setReceiptView('ops'); goToStep(2)">&#9998; Editar skids</button>
                </div>

                <div class="overflow-x-auto rounded-lg border border-[var(--border)] bg-[var(--surface)]">
                  <table class="data-table w-full text-[13px] font-mono border-collapse">
                    <thead>
                      <tr class="rcp-sheet-thead">
                        <th class="rcp-num">#</th>
                        <th class="text-left">HAWB</th>
                        <th class="rcp-num">Pzs</th>
                        <th class="rcp-num">L in</th>
                        <th class="rcp-num">W in</th>
                        <th class="rcp-num">H in</th>
                        <th class="rcp-num">L cm</th>
                        <th class="rcp-num">W cm</th>
                        <th class="rcp-num">H cm</th>
                        <th class="rcp-num">CBM</th>
                        <th class="rcp-num">CFT</th>
                        <th class="rcp-num">Báscula kg</th>
                        <th class="rcp-num">Vol. kg</th>
                        <th class="rcp-num">Cobrable</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="r in skidRows" :key="r.key">
                        <td class="rcp-num">{{ r.index }}</td>
                        <td class="text-left font-bold">{{ r.hawb }}</td>
                        <td class="rcp-num">{{ r.qty }}</td>
                        <td class="rcp-num">{{ r.l ? r.l : '—' }}</td>
                        <td class="rcp-num">{{ r.w ? r.w : '—' }}</td>
                        <td class="rcp-num">{{ r.h ? r.h : '—' }}</td>
                        <td class="rcp-num">{{ r.cm.l ? r.cm.l : '—' }}</td>
                        <td class="rcp-num">{{ r.cm.w ? r.cm.w : '—' }}</td>
                        <td class="rcp-num">{{ r.cm.h ? r.cm.h : '—' }}</td>
                        <td class="rcp-num">{{ r.cbm ? r.cbm.toFixed(4) : '—' }}</td>
                        <td class="rcp-num">{{ r.cft ? r.cft.toFixed(3) : '—' }}</td>
                        <td class="rcp-num">{{ r.scaleKg ? r.scaleKg.toFixed(1) : '—' }}</td>
                        <td class="rcp-num">{{ r.dimKg ? r.dimKg.toFixed(1) : '—' }}</td>
                        <td class="rcp-num rcp-cell-strong">{{ r.chargeKg ? r.chargeKg.toFixed(1) : '—' }}</td>
                      </tr>
                      <tr v-if="!skidRows.length">
                        <td colspan="14" class="text-center py-8 text-secondary">Aún no hay piezas capturadas en este recibo.</td>
                      </tr>
                    </tbody>
                    <tfoot v-if="skidRows.length">
                      <tr class="rcp-sheet-tfoot">
                        <td colspan="2">Totales</td>
                        <td class="rcp-num">{{ skidTotals.qty }}</td>
                        <td colspan="6"></td>
                        <td class="rcp-num">{{ skidTotals.cbm.toFixed(4) }}</td>
                        <td class="rcp-num">{{ skidTotals.cft.toFixed(3) }}</td>
                        <td class="rcp-num">{{ skidTotals.scaleKg.toFixed(1) }}</td>
                        <td class="rcp-num">{{ skidTotals.dimKg.toFixed(1) }}</td>
                        <td class="rcp-num rcp-cell-strong">{{ skidTotals.chargeKg.toFixed(1) }}</td>
                      </tr>
                    </tfoot>
                  </table>
                </div>
              </div>

              <!-- ═══ Vista: Previa del recibo oficial ═══ -->
              <div v-if="receiptView === 'preview'" class="ops-body thin-scrollbar overscroll-contain rcp-prev-wrap">
                <div class="rcp-sheet">
                  <header class="rcp-sheet-hd">
                    <div class="min-w-0">
                      <div class="rcp-sheet-brand">AIRCARGO · RECIBO DE BODEGA</div>
                      <div class="rcp-sheet-awb">{{ m.awbNumber || '—' }}</div>
                      <div class="rcp-sheet-sub">Dock Receipt · {{ receiptForms[m.id].gatewayCfs || 'SDQ' }}</div>
                    </div>
                    <div class="rcp-sheet-meta">
                      <div><span>Ruta</span><strong>{{ receiptForms[m.id].origin || '—' }} → {{ receiptForms[m.id].destination || '—' }}</strong></div>
                      <div><span>Estado</span><strong>{{ receiptById[m.id] ? 'Emitido' : 'Borrador' }}</strong></div>
                      <div><span>Commodity</span><strong>{{ m.commodityType || 'DRY_CARGO' }}</strong></div>
                    </div>
                  </header>

                  <section class="rcp-sheet-fields">
                    <div><span>Shipper</span><strong>{{ receiptForms[m.id].shipperName || '—' }}</strong></div>
                    <div><span>Consignee</span><strong>{{ receiptForms[m.id].consigneeName || '—' }}</strong></div>
                    <div><span>Piezas AWB</span><strong>{{ receiptForms[m.id].awbReportedPieces ?? '—' }}</strong></div>
                    <div><span>Peso AWB</span><strong>{{ receiptForms[m.id].mawbWeightGreatest ?? '—' }}</strong></div>
                    <div v-if="receiptForms[m.id].sealNumber">
                      <span>Sello / Precinto</span><strong>{{ receiptForms[m.id].sealNumber }}</strong>
                    </div>
                    <div v-if="receiptForms[m.id].dockBay">
                      <span>Muelle / Bahía</span><strong>{{ receiptForms[m.id].dockBay }}</strong>
                    </div>
                  </section>

                  <section class="rcp-sheet-block">
                    <h4>Resumen de carga</h4>
                    <div class="rcp-sheet-kpis">
                      <div><small>Piezas</small><strong>{{ skidTotals.qty }}</strong></div>
                      <div><small>Báscula</small><strong>{{ skidTotals.scaleKg.toFixed(1) }} kg</strong></div>
                      <div><small>Volumétrico</small><strong>{{ skidTotals.dimKg.toFixed(1) }} kg</strong></div>
                      <div class="accent"><small>Cobrable</small><strong>{{ skidTotals.chargeKg.toFixed(1) }} kg</strong></div>
                    </div>
                  </section>

                  <section class="rcp-sheet-block">
                    <h4>Skids</h4>
                    <table class="rcp-sheet-table">
                      <thead>
                        <tr><th>#</th><th>HAWB</th><th>Pzs</th><th>Dimensiones</th><th>CBM</th><th>Cobrable kg</th></tr>
                      </thead>
                      <tbody>
                        <tr v-for="r in skidRows" :key="'p-' + r.key">
                          <td>{{ r.index }}</td>
                          <td>{{ r.hawb }}</td>
                          <td>{{ r.qty }}</td>
                          <td class="rcp-num">{{ r.dimsLabel }}</td>
                          <td class="rcp-num">{{ r.cbm ? r.cbm.toFixed(3) : '—' }}</td>
                          <td class="rcp-num">{{ r.chargeKg ? r.chargeKg.toFixed(1) : '—' }}</td>
                        </tr>
                        <tr v-if="!skidRows.length"><td colspan="6" class="text-center">Sin piezas capturadas</td></tr>
                      </tbody>
                    </table>
                  </section>

                  <section class="rcp-sheet-block">
                    <h4>HAWBs</h4>
                    <div class="rcp-sheet-tags">
                      <span v-for="(h, hi) in (receiptForms[m.id].hawbEntries || [])" :key="'h-' + hi" class="rcp-sheet-tag">
                        {{ h.hawbNumber || 'S/N' }} · {{ h.pieces || 0 }} pzs · {{ h.destination || '—' }}
                      </span>
                      <span v-if="!(receiptForms[m.id].hawbEntries || []).length" class="rcp-sheet-tag">Sin HAWBs</span>
                    </div>
                  </section>

                  <section class="rcp-sheet-block">
                    <h4>Inspección y evidencias</h4>
                    <div class="rcp-sheet-tags">
                      <span class="rcp-sheet-tag" :class="{ ok: receiptForms[m.id].operationalChecks?.packagingIntact }">Embalaje intacto</span>
                      <span class="rcp-sheet-tag" :class="{ ok: receiptForms[m.id].operationalChecks?.noDamage }">Sin daños</span>
                      <span class="rcp-sheet-tag" :class="{ ok: receiptForms[m.id].operationalChecks?.sealsIntact }">Precintos</span>
                      <span class="rcp-sheet-tag" :class="{ ok: receiptForms[m.id].operationalChecks?.countVerified }">Conteo verificado</span>
                      <span class="rcp-sheet-tag">Evidencias: {{ (receiptForms[m.id].evidence || []).length }}</span>
                      <span class="rcp-sheet-tag">Evidencias MAWB: {{ (receiptForms[m.id].mawbEvidence || []).length }}</span>
                    </div>
                  </section>

                  <footer class="rcp-sheet-sigs">
                    <div class="rcp-sig">
                      <img v-if="receiptForms[m.id].dockSignature" :src="receiptForms[m.id].dockSignature" alt="Firma de entrega" />
                      <span>Entrega · {{ receiptForms[m.id].printName || '—' }}</span>
                    </div>
                    <div class="rcp-sig">
                      <img v-if="receiptForms[m.id].receivedBySig" :src="receiptForms[m.id].receivedBySig" alt="Firma de recepción" />
                      <span>Recibe · {{ receiptForms[m.id].receivedByName || '—' }}</span>
                    </div>
                    <div class="rcp-sig">
                      <img v-if="receiptForms[m.id].brokerSig" :src="receiptForms[m.id].brokerSig" alt="Firma del corredor" />
                      <span>Broker · {{ receiptForms[m.id].brokerName || '—' }}</span>
                    </div>
                  </footer>
                </div>
              </div>

              <div class="ops-foot">
                <div class="ops-fl">
                  <button class="btn-ghost" @click="prevStep" :disabled="localStep === 1">&#9664; Atras</button>
                  <button class="btn-ghost danger" @click="cancelForm">&#10005; Cancelar</button>
                  <button class="btn-ghost draft" @click="submitDraft(m)" :disabled="draftSaving || submitting">
                    <span>{{ draftSaving ? t('warehouse.draft.saving') : t('warehouse.draft.save') }}</span>
                  </button>
                  <span v-if="stepRecap" class="ops-recap">{{ stepRecap.pcs }} pzs · {{ stepRecap.kg }} kg</span>
                  <span v-if="successMsg" class="ops-ok">{{ successMsg }}</span>
                </div>
                <div v-if="receiptView !== 'ops'" class="ops-fr">
                  <button class="btn-ghost" @click="setReceiptView('ops')">&#9664; Volver al formulario</button>
                  <button v-if="receiptView === 'preview'" class="btn-ok" @click="printPreview(m)">PDF / Imprimir</button>
                </div>
                <div v-else-if="localStep < 6" class="ops-fr">
                  <button class="btn-ok" @click="nextStep">Siguiente &#9654;</button>
                </div>
                <div v-else class="ops-fr">
                  <button class="btn-ok" @click="openConfirmModal(m)" :disabled="submitting">
                    <span>{{ submitting ? 'Guardando...' : '&#10003; Confirmar y Guardar' }}</span>
                  </button>
                </div>
              </div>
              </div> <!-- end ops-modal -->
            </div> <!-- end ops-overlay -->
</Teleport>
          </div>
          </div>
        </template>
      </div>
    </section>

    <!-- MAWB Evidence Manager Modal -->
    <Teleport to="body">
      <div v-if="mawbEvidenceMgr.show" class="ds-modal-backdrop" @click.self="closeMawbEvidenceMgr">
        <div class="ds-modal-panel max-w-xl p-0" style="max-height: 80vh;">
          <div class="ds-modal-header px-4 py-2.5">
            <span class="ds-modal-title">
              {{ t('warehouse.evidence.title') }} — {{ mawbEvidenceMgr.mawb?.awbNumber || 'MAWB' }}
            </span>
            <button @click="closeMawbEvidenceMgr" :aria-label="t('common.close')" class="text-primary hover:text-primary transition text-base">✕</button>
          </div>
          <div class="p-4 overflow-y-auto" style="max-height: calc(80vh - 120px);">
            <div v-if="mawbEvidenceMgr.docs.length === 0" class="text-[14px] font-mono text-primary text-center py-6 uppercase tracking-widest">
              {{ t('warehouse.evidence.empty') }}
            </div>
            <div v-else class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-2 mb-4">
              <div v-for="(doc, di) in mawbEvidenceMgr.docs" :key="di"
                class="relative border border-strong rounded overflow-hidden bg-surface group cursor-pointer" @click="previewEvidence(doc)">
                <img v-if="doc.type === 'image' && doc.url" :src="doc.url" class="w-full h-20 object-cover" />
                <div v-else-if="isPdfUrl(doc.url)" class="w-full h-20 flex flex-col items-center justify-center bg-surface-hover text-slate-700 font-mono">
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="w-7 h-7 mb-0.5"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" /><polyline points="14 2 14 8 20 8" /><line x1="16" y1="13" x2="8" y2="13" /><line x1="16" y1="17" x2="8" y2="17" /><polyline points="10 9 9 9 8 9" /></svg>
                  <span class="text-[12px] leading-tight px-1 text-center truncate max-w-full">PDF</span>
                </div>
                <div v-else class="w-full h-20 flex items-center justify-center bg-surface-hover text-primary text-[13px] font-mono">{{ doc.name }}</div>
                <button @click.stop="removeMawbEvidence(di)" :aria-label="t('warehouse.evidence.remove')" class="absolute top-0.5 right-0.5 w-3.5 h-3.5 bg-surface-hover0 text-white rounded-full text-[12px] flex items-center justify-center opacity-0 group-hover:opacity-100 transition">✕</button>
                <span class="block text-[13px] font-mono text-primary px-2 py-1 truncate">{{ doc.name }}</span>
              </div>
            </div>
            <div class="flex items-center gap-2 border-t border-strong pt-3">
              <button @click="openMawbCapture()"
                class="ds-btn-secondary text-[11px] px-2.5 py-1 flex items-center gap-1">
                <component :is="icons.Camera" :size="12" /> {{ t('warehouse.evidence.takePhoto') }}
              </button>
              <button @click="addMawbEvidenceFile()"
                class="ds-btn-secondary text-[11px] px-2.5 py-1 flex items-center gap-1">
                <component :is="icons.FileUpload" :size="12" /> {{ t('warehouse.evidence.uploadFile') }}
              </button>
              <button v-if="mawbEvidenceMgr.docs.length > 0" @click="downloadMawbEvidencePdf()"
                class="ml-auto ds-btn-primary text-[11px] px-2.5 py-1">
                &#128196; PDF
              </button>
            </div>
          </div>
          <div class="flex justify-end px-4 py-2.5 border-t border-strong bg-surface-hover">
            <button @click="saveMawbEvidence()"
              class="ds-btn-primary text-[11px] px-3 py-1.5">
              {{ t('common.save') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Confirm Submit Modal -->
    <Teleport to="body">
      <div v-if="showConfirmModal" class="ds-modal-backdrop" @click.self="showConfirmModal = false" style="z-index: 70;">
        <div class="ds-modal-panel max-w-lg p-0" style="max-height: 80vh;">
          <div class="ds-modal-header px-4 py-2.5">
            <span class="ds-modal-title">Confirmar Recibo</span>
            <button @click="showConfirmModal = false" aria-label="Cerrar" class="text-primary hover:text-primary transition text-base">✕</button>
          </div>
          <div class="overflow-y-auto" style="max-height: calc(80vh - 110px);">
            <template v-if="pendingSubmitMawb">
              <div class="px-4 py-3 space-y-2 text-[13px] font-mono text-primary">
                <div class="grid grid-cols-2 gap-x-4 gap-y-1.5">
                  <span class="text-secondary uppercase tracking-wider">MAWB</span>
                  <span class="font-bold text-right">{{ pendingSubmitMawb.awbNumber }}</span>
                  <span class="text-secondary uppercase tracking-wider">{{ t('warehouse.form.destination') }}</span>
                  <span class="font-bold text-right">{{ receiptForms[pendingSubmitMawb.id]?.destination || pendingSubmitMawb.destination || '—' }}</span>
                  <span class="text-secondary uppercase tracking-wider">{{ t('common.pieces') }}</span>
                  <span class="font-bold text-right">{{ (receiptForms[pendingSubmitMawb.id]?.pieces || []).reduce((s, p) => s + (p.pieces || 1), 0) }}</span>
                  <span class="text-secondary uppercase tracking-wider">Peso (Kg)</span>
                  <span class="font-bold text-right">{{ (receiptForms[pendingSubmitMawb.id]?.pieces || []).reduce((s, p) => s + (p.scaleWeightKg || 0), 0).toFixed(1) }}</span>
                  <span class="text-secondary uppercase tracking-wider">{{ t('warehouse.form.shipperName') }}</span>
                  <span class="font-bold text-right truncate">{{ receiptForms[pendingSubmitMawb.id]?.shipperName || pendingSubmitMawb.shipperName || '—' }}</span>
                  <span class="text-secondary uppercase tracking-wider">Consignee</span>
                  <span class="font-bold text-right truncate">{{ receiptForms[pendingSubmitMawb.id]?.consigneeName || pendingSubmitMawb.consigneeName || '—' }}</span>
                </div>
                <div class="border-t border-strong pt-2 mt-2">
                  <div class="flex flex-wrap gap-2">
                    <span v-if="receiptForms[pendingSubmitMawb.id]?.cashOnly" class="bg-surface-hover text-slate-700 px-1.5 py-0.5 rounded text-[12px] uppercase tracking-wider">Cash Only</span>
                    <span v-if="receiptForms[pendingSubmitMawb.id]?.bookedInAcoms" class="bg-surface-hover text-slate-700 px-1.5 py-0.5 rounded text-[12px] uppercase tracking-wider">Booked in ACOMS</span>
                    <span v-if="receiptForms[pendingSubmitMawb.id]?.docsProvided" class="bg-surface-hover text-slate-700 px-1.5 py-0.5 rounded text-[12px] uppercase tracking-wider">Docs Provided</span>
                    <span v-if="receiptForms[pendingSubmitMawb.id]?.customsCompleted" class="bg-surface-hover text-slate-700 px-1.5 py-0.5 rounded text-[12px] uppercase tracking-wider">Customs Done</span>
                    <span v-if="receiptForms[pendingSubmitMawb.id]?.preBuilt" class="bg-surface-hover text-slate-700 px-1.5 py-0.5 rounded text-[12px] uppercase tracking-wider">Pre-built</span>
                  </div>
                </div>
                <div class="border-t border-strong pt-2 mt-2 flex justify-between text-[12px] text-secondary">
                  <span>{{ (receiptForms[pendingSubmitMawb.id]?.evidence || []).length }} evidencias</span>
                  <span>{{ (receiptForms[pendingSubmitMawb.id]?.pieces || []).filter(p => p.lengthIn).length }} piezas con dimensiones</span>
                </div>
              </div>
            </template>
          </div>
          <div class="flex items-center justify-end gap-2 px-4 py-2 border-t border-strong bg-surface-hover">
            <button @click="showConfirmModal = false"
              class="ds-btn-secondary text-[11px] px-3 py-1.5">
              Cancelar
            </button>
            <button @click="confirmSubmit"
              class="ds-btn-primary text-[11px] px-3 py-1.5">
              &#10003; Confirmar
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Booking Correction Modal -->
    <Teleport to="body">
      <div v-if="showBookingCorrectionModal" class="ds-modal-backdrop" @click.self="cancelBookingCorrection" style="z-index: 80; --tw-bg-opacity: 0.7;">
        <div class="ds-modal-panel max-w-md p-0">
          <div class="px-5 py-4 border-b border-amber-300 bg-amber-50">
            <div class="flex items-center gap-2">
              <span class="text-amber-600 text-lg">&#9888;</span>
              <span class="ds-modal-title text-amber-900">Booking Auto-Correccion</span>
            </div>
          </div>
          <div class="px-5 py-4 space-y-3">
            <p class="text-[14px] font-mono text-secondary">
              Las piezas recibidas superan las reservadas en el Booking. El sistema corregira automaticamente:
            </p>
            <div v-for="(c, idx) in pendingBookingCorrections" :key="idx"
              class="bg-amber-50 border border-amber-200 rounded-md px-3 py-2.5">
              <div class="text-[13px] font-mono text-amber-900" v-html="formatCorrection(c)"></div>
            </div>
            <p class="text-[13px] font-mono text-secondary italic">
              Al aceptar, el Booking se actualizara y el recibo se guardara.
            </p>
          </div>
          <div class="flex items-center justify-end gap-2 px-5 py-3 border-t border-amber-200 bg-amber-50/50">
            <button @click="cancelBookingCorrection"
              class="ds-btn-secondary text-[12px] px-3 py-1.5 text-slate-700">
              &#10007; Ajustar
            </button>
            <button @click="confirmBookingCorrection"
              class="flex items-center gap-1.5 text-[12px] px-4 py-1.5 rounded font-mono uppercase tracking-wider font-bold text-white bg-amber-700 hover:bg-amber-500 transition">
              &#10003; Aceptar y Emitir
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Evidence Preview Modal -->
    <Teleport to="body">
      <div v-if="evidencePreview.show" class="ds-modal-backdrop" @click.self="closeEvidencePreview" style="z-index: 60; --tw-bg-opacity: 0.7;">
        <div class="ds-modal-panel max-w-4xl p-0" style="max-height: 90vh;">
          <div class="ds-modal-header px-4 py-2.5">
            <span class="ds-modal-title truncate max-w-[70%]">
              {{ evidencePreview.item?.name || 'Vista previa' }}
            </span>
            <button @click="closeEvidencePreview" aria-label="Cerrar" class="text-primary hover:text-primary transition text-base">✕</button>
          </div>
          <div class="overflow-auto bg-slate-900 flex items-center justify-center" style="max-height: calc(90vh - 110px); min-height: 200px;">
            <img v-if="evidencePreview.item?.type === 'image' && evidencePreview.item?.url" :src="evidencePreview.item.url"
              class="max-w-full max-h-full object-contain" style="max-height: calc(90vh - 120px);" />
            <embed v-else-if="isPdfUrl(evidencePreview.item?.url)" :src="evidencePreview.item.url"
              type="application/pdf" class="w-full" style="height: calc(90vh - 110px);" />
            <div v-else class="text-white/60 font-mono text-[14px] p-8 text-center">
              {{ evidencePreview.item?.name || 'Sin vista previa disponible' }}
            </div>
          </div>
          <div class="flex items-center justify-end gap-2 px-4 py-2 border-t border-strong bg-surface-hover">
            <button @click="closeEvidencePreview"
              class="ds-btn-secondary text-[11px] px-3 py-1.5">
              Cerrar
            </button>
            <button @click="downloadEvidenceItem(evidencePreview.item)"
              class="ds-btn-primary text-[11px] px-3 py-1.5">
              &#128229; Descargar
            </button>
          </div>
        </div>
      </div>
    </Teleport>
    <EditReceiptModal ref="editModalRef" @saved="onEditReceiptSaved" />

    <!-- ═══ CAPTURA DE EVIDENCIAS (instancia única) ═══
         Vive FUERA de los v-if de paso/modal a propósito: así el overlay
         queda por encima de .ops-overlay (z 60) y del gestor MAWB (z 50),
         y cambiar de paso mientras está abierto no deja estado zombi. -->
    <EvidenceCaptureModal
      :show="capture.open"
      :z-index="95"
      @close="capture.open = false"
      @captured="onEvidenceCaptured"
    />

    <!-- ═══ ESCANEO DE CÓDIGOS (MAWB/HAWB) ═══
         Escanea con la cámara y abre el recibo de la MAWB resuelta. La
         resolución HAWB→MAWB padre la hace el backend (/api/scan/lookup). -->
    <BarcodeCameraModal
      :show="showScanModal"
      :z-index="96"
      :title="t('warehouse.scan.button')"
      :hint="t('scan.cameraHint')"
      @detected="onScanDetected"
      @close="closeScanModal"
    />

    <!-- ═══ SELECTOR DE MAWBs PENDIENTES (cola de recepción) ═══
         Permite cargar un recibo directamente desde la cola de MAWBs que
         aún no tienen recepción, sin buscarlo en la tabla. -->
    <Teleport to="body">
      <div v-if="showQueueModal" class="ops-overlay" @click.self="closeQueueModal">
        <div class="ops-modal" role="dialog" aria-modal="true" style="max-width: 720px;">
          <div class="ops-hdr">
            <div class="ops-fl">
              <span class="ops-tt">{{ t('warehouse.queue.title') }}</span>
              <span class="ops-sub">{{ pendingMawbQueue.length }} · {{ t('warehouse.queue.subtitle') }}</span>
            </div>
            <button class="xbtn" @click="closeQueueModal" title="Cerrar">&#10005;</button>
          </div>
          <div class="px-4 py-2 border-b" style="border-color: var(--border)">
            <div class="ds-search max-w-none">
              <component :is="icons.Search" :size="14" class="ds-search-icon" :stroke-width="2" />
              <input v-model="queueSearch" type="text" :placeholder="t('warehouse.queue.search')"
                class="ds-input w-full pl-9" />
            </div>
          </div>
          <div class="ops-body thin-scrollbar">
            <div v-if="!pendingMawbQueue.length" class="text-[12px] font-mono text-tertiary py-6 text-center">
              {{ t('warehouse.queue.empty') }}
            </div>
            <div v-else class="divide-y" style="border-color: var(--border)">
              <button v-for="qm in pendingMawbQueue" :key="qm.id" type="button"
                class="w-full flex items-center gap-3 px-4 py-2 text-left hover:bg-[var(--surface-2)] transition"
                @click="openQueueMawb(qm)">
                <span class="font-mono font-bold text-[12.5px] text-[var(--text)]">{{ qm.awbNumber || qm.id?.slice(0, 8) }}</span>
                <span class="text-[11.5px] text-[var(--text-2)] truncate flex-1">{{ qm.shipperName || '—' }}</span>
                <span class="text-[11px] font-mono" style="color: var(--muted)">{{ fmtFlight(qm) }}</span>
                <span class="text-[11px] font-bold" style="color: var(--accent)">{{ t('warehouse.queue.open') }} &#8594;</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- SDD §7: barra sticky Scan MAWB/HAWB siempre visible en móvil -->
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, reactive } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '../stores/app'
import { useAuthStore } from '../stores/auth'

const { t } = useI18n()
import SignaturePad from '../components/SignaturePad.vue'
import EvidenceCaptureModal from '../components/EvidenceCaptureModal.vue'
import BarcodeCameraModal from '../components/BarcodeCameraModal.vue'
import EditReceiptModal from '../components/EditReceiptModal.vue'
import LocaleDatePicker from '../components/LocaleDatePicker.vue'
import EmptyState from '../components/EmptyState.vue'
import { useIcons } from '../composables/useIcons'
import { hawbsApi } from '../api/hawbs'

const icons = useIcons()
import { mawbsApi } from '../api/mawbs'
import { receiptsApi } from '../api/receipts'
import { scanApi } from '../api/scan'
import { calcConfigApi } from '../api/receiptCalcConfig'
import { receiptFormConfigApi } from '../api/receiptFormConfig'
import { useToastStore } from '../stores/toast'
import { extractError } from '../utils/error'
import { normalizeAwb } from '../utils/awb'
import { activeReceiptForMawb, draftReceiptForMawb, isDraftReceipt } from '../utils/mawbDispatch'
import { useConfirm } from '../composables/useConfirm'
import { useLiveRefresh } from '../composables/useLiveRefresh'

const store = useAppStore()
const auth = useAuthStore()
const toast = useToastStore()
const { confirm } = useConfirm()
const route = useRoute()

function airlineCodeById(airlineId) {
  const a = store.airlines.find(x => x.id === airlineId)
  return a?.code || 'AIR'
}

function fmtFlightDate(iso) {
  if (!iso) return ''
  try {
    const localeCode = t('common.monthsShort[0]') === 'Jan' ? 'en-US' : 'es-DO'
    return new Intl.DateTimeFormat(localeCode, { day: '2-digit', month: 'short', year: 'numeric' })
      .format(new Date(iso + 'T00:00:00'))
  } catch {
    return iso
  }
}

// Deriva el estado operativo de la MAWB según lógica de negocio (pipeline:
// PENDIENTE → RECIBIDA → EN_PROCESO → CARGADA → MANIFESTADA → DESPACHADA):
// 1. PENDIENTE: recibo no completado y piezas NO en ULDs
// 2. RECIBIDA: recibo completado (5 pasos OK) pero piezas NO en ULDs
// 3. CARGADA: piezas YA montadas en ULDs pero recibo sin completar (alert rojo)
// 4. DESPACHADA: recibo completo y ≥1 ULD en vuelo DEPARTED (parcial o completa)
// 5. EN_PROCESO: recibo completo, piezas en ULDs pero suma < piezas recibidas
// 6. MANIFESTADA: recibo completo y todas las piezas en ULDs, vuelos NO despachados
function deriveMawbOperationalStatusPure(m, uldAwbs, flights, receipts) {
  const mawbId = m.id
  const targetCode = normalizeAwb(m.awbNumber)

  // Obtener links de ULD-AWB para esta MAWB
  const links = (uldAwbs || []).filter(ua => {
    if (ua.mawbId && ua.mawbId === mawbId) return true
    return ua.mawbLabel && normalizeAwb(ua.mawbLabel) === targetCode
  })

  // 1-2. Sin links: recibo completo = RECIBIDA; sin recibo = PENDIENTE
  if (!links.length) {
    return isReceiptCompletePure(m, receipts) ? 'RECIBIDA' : 'PENDIENTE'
  }

  // 3. Montada en ULDs pero sin recibo completado → CARGADA (alert operativo)
  if (!isReceiptCompletePure(m, receipts)) {
    return 'CARGADA'
  }

  // Obtener total de piezas recibidas del recibo
  const receivedPieces = (() => {
    const totals = {}
    for (const r of receipts || []) {
      if (r.superseded || isDraftReceipt(r)) continue
      const mid = r.mawb?.id || r.mawbId
      if (!mid) continue
      if (!totals[mid]) totals[mid] = { pieces: 0 }
      totals[mid].pieces += (r.pieceCount || 0)
    }
    return totals[mawbId]?.pieces || 0
  })()

  // Sumar piezas en ULDs
  let totalUldPieces = 0
  let anyOnDepartedFlights = false

  const uldToFlightId = {}
  for (const u of store.ulds || []) {
    if (u.flightId) uldToFlightId[u.id] = u.flightId
  }
  const flightStatusMap = {}
  for (const f of flights || []) {
    flightStatusMap[f.id] = f.status
  }

  for (const link of links) {
    const pieces = link.pieces || 0
    totalUldPieces += pieces

    const uldId = link.uldId
    if (!uldId) continue
    const flightId = uldToFlightId[uldId]
    if (!flightId) continue
    if (flightStatusMap[flightId] === 'DEPARTED') {
      anyOnDepartedFlights = true
    }
  }

  // 4. Recibo completo y ≥1 ULD en vuelo DEPARTED → DESPACHADA (parcial o completa)
  if (anyOnDepartedFlights) {
    return 'DESPACHADA'
  }

  // 5. Carga en progreso: piezas montadas < piezas recibidas
  if (totalUldPieces < receivedPieces) {
    return 'EN_PROCESO'
  }

  // 6. Recibo completo y todas las recibidas montadas, vuelos NO despachados
  return 'MANIFESTADA'
}

// Versión optimizada: recibe los mapas pre-computados para evitar O(n*m) por MAWB
function deriveMawbOperationalStatusPureOptimized(m, uldAwbs, uldToFlightId, flightStatusMap, receiptPiecesByMawb, activeReceiptByMawb) {
  const mawbId = m.id
  const targetCode = normalizeAwb(m.awbNumber)

  // Obtener links de ULD-AWB para esta MAWB
  const links = (uldAwbs || []).filter(ua => {
    if (ua.mawbId && ua.mawbId === mawbId) return true
    return ua.mawbLabel && normalizeAwb(ua.mawbLabel) === targetCode
  })

  // 1-2. Sin links: recibo completo = RECIBIDA; sin recibo = PENDIENTE
  if (!links.length) {
    return (activeReceiptByMawb[mawbId]?.pieceCount || 0) > 0 ? 'RECIBIDA' : 'PENDIENTE'
  }

  // 3. Montada en ULDs pero sin recibo completado → CARGADA (alert operativo)
  if ((activeReceiptByMawb[mawbId]?.pieceCount || 0) === 0) {
    return 'CARGADA'
  }

  const receivedPieces = receiptPiecesByMawb[mawbId] || 0

  // Sumar piezas en ULDs
  let totalUldPieces = 0
  let anyOnDepartedFlights = false

  for (const link of links) {
    const pieces = link.pieces || 0
    totalUldPieces += pieces

    const uldId = link.uldId
    if (!uldId) continue
    const flightId = uldToFlightId[uldId]
    if (!flightId) continue
    if (flightStatusMap[flightId] === 'DEPARTED') {
      anyOnDepartedFlights = true
    }
  }

  // 4. Recibo completo y ≥1 ULD en vuelo DEPARTED → DESPACHADA (parcial o completa)
  if (anyOnDepartedFlights) {
    return 'DESPACHADA'
  }

  // 5. Carga en progreso: piezas montadas < piezas recibidas
  if (totalUldPieces < receivedPieces) {
    return 'EN_PROCESO'
  }

  // 6. Recibo completo y todas las recibidas montadas, vuelos NO despachados
  return 'MANIFESTADA'
}

// El recibo vigente se resuelve con el helper compartido en vez de reimplementar
// aquí el "primer recibo no superseded de esta MAWB". El helper además acepta
// el AWB normalizado como segundo criterio de búsqueda, así que un recibo cuyo
// `mawbId` venga nulo pero traiga `mawbNumber` (formato histórico, p.ej.
// 40605857585) ya no se cuenta como MAWB sin recibir.
function isReceiptCompletePure(m, receipts) {
  return (activeReceiptForMawb(m, receipts)?.pieceCount || 0) > 0
}

function deriveMawbOperationalStatus(m) {
  return deriveMawbOperationalStatusPure(m, store.uldAwbs, store.flights, store.receipts)
}

function isReceiptComplete(m) {
  const receipt = activeReceiptForMawb(m, store.receipts)
  return receipt && !receipt.superseded && receipt.pieceCount > 0
}

// True cuando el MAWB está en ULDs pero el total montado < piezas recibidas
// (carga parcial en curso — nota junto a DESPACHADA parcial y EN_PROCESO).
function isPartialLoadingPure(m, uldAwbs, receipts) {
  const mawbId = m.id
  const targetCode = normalizeAwb(m.awbNumber)
  const links = (uldAwbs || []).filter(ua => {
    if (ua.mawbId && ua.mawbId === mawbId) return true
    return ua.mawbLabel && normalizeAwb(ua.mawbLabel) === targetCode
  })
  if (!links.length) return false
  let onUld = 0
  for (const link of links) onUld += link.pieces || 0
  const receivedPieces = (() => {
    const totals = {}
    for (const r of receipts || []) {
      if (r.superseded || isDraftReceipt(r)) continue
      const mid = r.mawb?.id || r.mawbId
      if (!mid) continue
      if (!totals[mid]) totals[mid] = { pieces: 0 }
      totals[mid].pieces += (r.pieceCount || 0)
    }
    return totals[mawbId]?.pieces || 0
  })()
  return onUld < receivedPieces
}

function isPartialLoading(m) {
  return isPartialLoadingPure(m, store.uldAwbs, store.receipts)
}

const mawbOperationalStatusMap = computed(() => {
  // Pre-compute lookup maps ONCE to avoid O(n*m) rebuild per MAWB
  const uldToFlightId = {}
  for (const u of store.ulds || []) {
    if (u.flightId) uldToFlightId[u.id] = u.flightId
  }
  const flightStatusMap = {}
  for (const f of store.flights || []) {
    flightStatusMap[f.id] = f.status
  }
  const receiptPiecesByMawb = {}
  for (const r of store.receipts || []) {
    if (r.superseded || isDraftReceipt(r)) continue
    const mid = r.mawb?.id || r.mawbId
    if (!mid) continue
    receiptPiecesByMawb[mid] = (receiptPiecesByMawb[mid] || 0) + (r.pieceCount || 0)
  }
  const activeReceiptByMawb = {}
  for (const r of store.receipts || []) {
    if (r.superseded || isDraftReceipt(r)) continue
    const mid = r.mawb?.id || r.mawbId
    if (!mid || activeReceiptByMawb[mid]) continue
    activeReceiptByMawb[mid] = r
  }

  const map = {}
  for (const m of store.mawbs) {
    map[m.id] = deriveMawbOperationalStatusPureOptimized(
      m,
      store.uldAwbs,
      uldToFlightId,
      flightStatusMap,
      receiptPiecesByMawb,
      activeReceiptByMawb
    )
  }
  return map
})

// ── Cola de MAWBs pendientes de recepción (selector "Cargar de la cola") ──
const pendingMawbQueue = computed(() => {
  const q = queueSearch.value.trim().toLowerCase()
  return (store.mawbs || [])
    .filter(m => mawbOperationalStatusMap.value[m.id] === 'PENDIENTE')
    .filter(m => !q
      || String(m.awbNumber || '').toLowerCase().includes(q)
      || String(m.shipperName || '').toLowerCase().includes(q))
    .slice(0, 150)
})

function openQueueModal() {
  queueSearch.value = ''
  showQueueModal.value = true
}

function closeQueueModal() {
  showQueueModal.value = false
}

function openQueueMawb(m) {
  showQueueModal.value = false
  queueSearch.value = ''
  if (!m) return
  if (expandedId.value === m.id) return
  toggleExpand(m)
}

// ── Cockpit de recepción (pesos, cubicaje y discrepancias en vivo) ────────
const activeMawb = computed(() => (store.mawbs || []).find(x => x.id === expandedId.value) || null)

function reservedKgFor(m) {
  if (!m) return 0
  const target = normalizeAwb(m.awbNumber)
  const booking = (store.bookings || []).find(b =>
    (b.mawbId && b.mawbId === m.id)
    || (target && normalizeAwb(b.awbNumber) === target))
  const fromBooking = Number(booking?.reservedKg) || 0
  if (fromBooking > 0) return fromBooking
  return Number(m.reportedWeightKg) || 0
}

function cockpitFor(m) {
  if (!m || !receiptForms[m.id]) return null
  const f = receiptForms[m.id]
  const actualKg = totalScaleKg(m.id, null)
  const actualLbs = totalScaleLbs(m.id, null)
  const dimKg = totalDimKg(m.id, null)
  const dimLbs = totalDimLbs(m.id, null)
  const chargeKg = totalChargeableKg(m.id, null)
  const chargeLbs = totalChargeableLbs(m.id, null)
  const cubicIn3 = (f.pieces || []).reduce((s, p) => {
    const vol = (Number(p.lengthIn) || 0) * (Number(p.widthIn) || 0) * (Number(p.heightIn) || 0)
    return s + vol * (Number(p.pieces) || 1)
  }, 0)
  const reservedKg = reservedKgFor(m)
  const deltaKg = chargeKg - reservedKg
  const deltaPct = reservedKg > 0 ? (deltaKg / reservedKg) * 100 : 0
  const deltaOk = reservedKg <= 0 || Math.abs(deltaPct) <= 5
  return {
    actualKg, actualLbs, dimKg, dimLbs, chargeKg, chargeLbs,
    cubicIn3, cubicFt3: cubicIn3 / 1728, cubicM3: cubicIn3 / 61023.7,
    reservedKg, reservedLbs: reservedKg * 2.20462,
    deltaKg, deltaPct, deltaOk,
    excessWarning: reservedKg > 0 && deltaPct > 5,
    volumeWarning: dimKg > actualKg && dimKg > 0,
    pcs: totalPieces(m.id, null),
  }
}

const activeCockpit = computed(() => cockpitFor(activeMawb.value))

// ── Checklist operativo / documentación / cadena de frío ──────────────────
const CHECK_ITEMS = [
  { key: 'packagingIntact', label: 'Embalaje íntegro' },
  { key: 'noDamage', label: 'Sin daños visibles ni humedad' },
  { key: 'sealsIntact', label: 'Precintos / sellos intactos' },
  { key: 'labelsLegible', label: 'Etiquetas y marcas legibles' },
  { key: 'palletized', label: 'Carga paletizada / asegurada' },
  { key: 'noLeaks', label: 'Sin derrames ni olores' },
  { key: 'countVerified', label: 'Conteo de piezas verificado' },
]
const DOC_ITEMS = [
  { key: 'docsReceived', label: 'Documentos físicos recibidos' },
  { key: 'customsDocs', label: 'Documentación de aduana completa' },
  { key: 'duaAttached', label: 'DUA adjunto' },
]

function checksSummary(m) {
  const f = m ? receiptForms[m.id] : null
  const all = [...CHECK_ITEMS, ...DOC_ITEMS]
  if (!f) return { done: 0, total: all.length, ok: false }
  const done = all.filter(c => !!f.operationalChecks?.[c.key]).length
  return { done, total: all.length, ok: done === all.length }
}

function nowLocalInput() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

// Marca que la temperatura capturada es la lectura tomada en la recepción
// (se guarda como receivedTempC + receivedAt y alimenta el detalle de lecturas).
function markReceptionTemp(m) {
  const cc = m ? receiptForms[m.id]?.coldChain : null
  if (!cc) return
  const v = Number(cc.receivedTempC)
  if (cc.receivedTempC === null || cc.receivedTempC === '' || !Number.isFinite(v)) return
  cc.enabled = true
  if (!cc.receivedAt) cc.receivedAt = nowLocalInput()
}

function registerReceptionTemp(m) {
  const cc = m ? receiptForms[m.id]?.coldChain : null
  if (!cc) return
  const v = Number(cc.receivedTempC)
  if (cc.receivedTempC === null || cc.receivedTempC === '' || !Number.isFinite(v)) {
    toast.warning('Ingresa la temperatura de recepción en °C')
    return
  }
  cc.enabled = true
  cc.receivedAt = nowLocalInput()
  if (!Array.isArray(cc.readings)) cc.readings = []
  cc.readings.push({ valueC: v, at: cc.receivedAt, reception: true })
  toast.success(`Temperatura de recepción registrada: ${v} °C`)
}

function fmtStamp(value) {
  if (!value) return ''
  return String(value).replace('T', ' ')
}

function addColdReading(m) {
  const cc = m ? receiptForms[m.id]?.coldChain : null
  if (!cc) return
  cc.enabled = true
  if (!Array.isArray(cc.readings)) cc.readings = []
  cc.readings.push({ valueC: null, at: nowLocalInput() })
}

function removeColdReading(m, idx) {
  const cc = m ? receiptForms[m.id]?.coldChain : null
  if (cc?.readings) cc.readings.splice(idx, 1)
}

function tempValueOrNull(raw) {
  if (raw === null || raw === undefined || raw === '') return null
  const v = Number(raw)
  return Number.isFinite(v) ? v : null
}

// true = sin excursión, false = excursión, null = no aplica cadena de frío.
function coldChainOkFlag(m) {
  const cc = m ? receiptForms[m.id]?.coldChain : null
  if (!cc?.enabled) return null
  return coldStatus(m).ok
}

function coldStatus(m) {
  const cc = m ? receiptForms[m.id]?.coldChain : null
  if (!cc?.enabled) return { ok: true, label: 'No aplica' }
  const readings = (cc.readings || []).filter(r => r.valueC !== null && r.valueC !== '' && Number.isFinite(Number(r.valueC)))
  const hasRange = cc.minC !== null && cc.minC !== '' && cc.maxC !== null && cc.maxC !== ''
  const excursions = hasRange
    ? readings.filter(r => Number(r.valueC) < Number(cc.minC) || Number(r.valueC) > Number(cc.maxC))
    : []
  if (!readings.length) return { ok: true, label: 'Sin lecturas' }
  if (excursions.length) {
    return { ok: false, label: `⚠ Excursión en ${excursions.length} lectura(s)` }
  }
  return { ok: true, label: `OK · ${readings.length} lectura(s)` }
}

function addDriver(m) {
  const f = m ? receiptForms[m.id] : null
  if (!f) return
  if (!Array.isArray(f.driverSignatures)) f.driverSignatures = []
  f.driverSignatures.push({ name: '', idNum: '', truckPlate: '', arrivedAt: nowLocalInput(), sig: '' })
}

function removeDriver(m, idx) {
  const f = m ? receiptForms[m.id] : null
  if (Array.isArray(f?.driverSignatures)) f.driverSignatures.splice(idx, 1)
}

function parseJsonValue(v, fallback) {
  if (v === null || v === undefined || v === '') return fallback
  if (typeof v === 'object') return v
  try { return JSON.parse(v) } catch { return fallback }
}

// (Pedido del usuario, oct 2026) Ya no se oculta ninguna MAWB por antigüedad:
// se muestran siempre las trabajadas (recibo emitido), las procesadas o en
// proceso con borrador y TODAS las reservadas por los vuelos.

const expandedId = ref(localStorage.getItem('WAREHOUSE_EXPANDED_MAWB') || null)
const showScanModal = ref(false)
const showQueueModal = ref(false)
const queueSearch = ref('')
const scanBusy = ref(false)
const queueExpanded = ref(true)
// El cockpit arranca abierto en escritorio/tablet y plegado en móvil (espacio).
const cockpitOpen = ref(typeof window === 'undefined' ? true : window.innerWidth >= 768)
const localStep = ref(1)
// Validación por paso: `touchedSteps[si]` se marca al intentar avanzar (guía al operador sin
// marcar en rojo un formulario recién abierto). `stepErrorMsg` es el primer error visible.
const touchedSteps = reactive([false, false, false, false, false, false])
const stepErrorMsg = ref('')
const submitting = ref(false)
const draftSaving = ref(false)
const successMsg = ref('')
const showConfirmModal = ref(false)
const editModalRef = ref(null)
const pendingSubmitMawb = ref(null)
const showBookingCorrectionModal = ref(false)
const pendingBookingCorrections = ref([])
const pendingEmitPayload = ref(null)
const pendingEmitMawb = ref(null)
const pendingEmitHawbs = ref(null)
const formVersion = ref(0)
function bumpFormVersion() { formVersion.value++ }
let formVersionDebounceTimer = null
// Version debounced: coalesce varias pulsaciones de teclado en un solo refresco del bloque
// memoizado (v-memo) del MAWB, para no forzar un re-render pesado por cada digito tecleado
// en dimensiones/cantidad, manteniendo la sensacion de actualizacion "en vivo".
function bumpFormVersionDebounced(delay = 150) {
  if (formVersionDebounceTimer) clearTimeout(formVersionDebounceTimer)
  formVersionDebounceTimer = setTimeout(() => {
    bumpFormVersion()
    formVersionDebounceTimer = null
  }, delay)
}
let successTimer = null

const receiptForms = reactive({})
const receiptHawbs = reactive({})
const generatedReceiptId = ref(null)

// ── Funciones opcionales del recibo (se activan una por una desde esta vista) ──
// Cada entrada es una función independiente: su propio interruptor, su propia
// bandera persistida y su propio campo en el recibo. Añadir una función nueva
// es añadir una entrada aquí (más su columna/bandera en el backend).
const OPTIONAL_RECEIPT_FUNCTIONS = [
  {
    key: 'sealNumber',
    cfgFlag: 'sealNumberEnabled',
    labelKey: 'warehouse.optionalFn.sealLabel',
    helpKey: 'warehouse.optionalFn.sealHelp',
    placeholderKey: 'warehouse.optionalFn.sealPlaceholder',
  },
  {
    key: 'dockBay',
    cfgFlag: 'dockBayEnabled',
    labelKey: 'warehouse.optionalFn.dockLabel',
    helpKey: 'warehouse.optionalFn.dockHelp',
    placeholderKey: 'warehouse.optionalFn.dockPlaceholder',
  },
]

const receiptFormCfg = reactive({ sealNumberEnabled: false, dockBayEnabled: false })
const optionalFnOpen = ref(false)
const optionalFnSaving = ref('')
const canConfigureOptional = computed(() => auth.role === 'SUPER_USER')
const allOptionalOn = computed(() => OPTIONAL_RECEIPT_FUNCTIONS.every(fn => isOptionalOn(fn)))

function isOptionalOn(fn) {
  return !!receiptFormCfg[fn.cfgFlag]
}

function applyReceiptFormConfig(data) {
  receiptFormCfg.sealNumberEnabled = !!data?.sealNumberEnabled
  receiptFormCfg.dockBayEnabled = !!data?.dockBayEnabled
}

async function loadReceiptFormConfig() {
  try {
    const res = await receiptFormConfigApi.get()
    applyReceiptFormConfig(res.data)
  } catch {
    // Keep both disabled: the fields stay in ghost mode rather than silently
    // accepting data the backend settings say are off.
    applyReceiptFormConfig(null)
  }
}

// Toggle a single optional function and persist only that function's flag.
async function toggleOptional(fn) {
  if (!canConfigureOptional.value || optionalFnSaving.value) return
  const next = !isOptionalOn(fn)
  optionalFnSaving.value = fn.key
  try {
    const res = await receiptFormConfigApi.update({ [fn.cfgFlag]: next })
    applyReceiptFormConfig(res.data)
    const name = t(fn.labelKey)
    toast.success(t(next ? 'warehouse.optionalFn.fnEnabled' : 'warehouse.optionalFn.fnDisabled', { name }))
  } catch (e) {
    toast.error(extractError(e))
  } finally {
    optionalFnSaving.value = ''
  }
}

// Draft persistence (auto-save)
const DRAFT_PREFIX = 'warehouse_draft_'
const lastDraftSave = ref('')

function saveDraft(mawbId) {
  const f = receiptForms[mawbId]
  if (!f) return
  try {
    const data = {
      gatewayCfs: f.gatewayCfs,
      shipperName: f.shipperName,
      consigneeName: f.consigneeName,
      origin: f.origin,
      destination: f.destination,
      awbReportedPieces: f.awbReportedPieces,
      mawbWeightGreatest: f.mawbWeightGreatest,
      dimFactorKg: f.dimFactorKg,
      dimFactorLbs: f.dimFactorLbs,
      chargeableMethod: f.chargeableMethod,
      roundUpKg: f.roundUpKg,
      roundUpLbs: f.roundUpLbs,
      minChargeableKg: f.minChargeableKg,
      minChargeableLbs: f.minChargeableLbs,
      cashOnly: f.cashOnly,
      bookedInAcoms: f.bookedInAcoms,
      docsProvided: f.docsProvided,
      customsCompleted: f.customsCompleted,
      preBuilt: f.preBuilt,
      hawbCount: f.hawbCount,
      hawbEntries: f.hawbEntries.map(e => ({
        hawbNumber: e.hawbNumber, consigneeName: e.consigneeName,
        pieces: e.pieces, weightKg: e.weightKg, destination: e.destination,
        _dbId: e._dbId || null, _hawbId: e._hawbId || null,
      })),
      pieces: f.pieces.map(p => ({
        pieces: p.pieces, hawbId: p.hawbId,
        lengthIn: p.lengthIn, widthIn: p.widthIn, heightIn: p.heightIn,
        scaleWeightLbs: p.scaleWeightLbs,
      })),
      remarks: f.remarks,
      dockSignature: f.dockSignature || '',
      printName: f.printName,
      deliveredByName: f.deliveredByName,
      deliveredByIdNum: f.deliveredByIdNum,
      deliveredBySig: f.deliveredBySig || '',
      receivedByName: f.receivedByName,
      receivedByIdNum: f.receivedByIdNum,
      receivedBySig: f.receivedBySig || '',
      brokerName: f.brokerName,
      brokerIdNum: f.brokerIdNum,
      brokerSig: f.brokerSig || '',
      driverSignatures: JSON.parse(JSON.stringify(f.driverSignatures || [])),
      operationalChecks: { ...(f.operationalChecks || {}) },
      coldChain: JSON.parse(JSON.stringify(f.coldChain || {})),
      duaNumber: f.duaNumber || '',
      sealNumber: f.sealNumber || '',
      dockBay: f.dockBay || '',
    }
    localStorage.setItem(DRAFT_PREFIX + mawbId, JSON.stringify(data))
    lastDraftSave.value = new Date().toLocaleTimeString()
  } catch {}
}

function loadDraft(mawbId) {
  try {
    const raw = localStorage.getItem(DRAFT_PREFIX + mawbId)
    if (!raw) return null
    return JSON.parse(raw)
  } catch { return null }
}

function clearDraft(mawbId) {
  try { localStorage.removeItem(DRAFT_PREFIX + mawbId) } catch {}
}

function applyDraftToForm(mawbId) {
  const draft = loadDraft(mawbId)
  const f = receiptForms[mawbId]
  if (!draft || !f) return
  Object.assign(f, {
    gatewayCfs: draft.gatewayCfs ?? f.gatewayCfs,
    shipperName: draft.shipperName ?? f.shipperName,
    consigneeName: draft.consigneeName ?? f.consigneeName,
    origin: draft.origin ?? f.origin,
    destination: draft.destination ?? f.destination,
    awbReportedPieces: draft.awbReportedPieces ?? f.awbReportedPieces,
    mawbWeightGreatest: draft.mawbWeightGreatest ?? f.mawbWeightGreatest,
    dimFactorKg: draft.dimFactorKg ?? f.dimFactorKg,
    dimFactorLbs: draft.dimFactorLbs ?? f.dimFactorLbs,
    chargeableMethod: draft.chargeableMethod ?? f.chargeableMethod,
    roundUpKg: draft.roundUpKg ?? f.roundUpKg,
    roundUpLbs: draft.roundUpLbs ?? f.roundUpLbs,
    minChargeableKg: draft.minChargeableKg ?? f.minChargeableKg,
    minChargeableLbs: draft.minChargeableLbs ?? f.minChargeableLbs,
    cashOnly: draft.cashOnly ?? f.cashOnly,
    bookedInAcoms: draft.bookedInAcoms ?? f.bookedInAcoms,
    docsProvided: draft.docsProvided ?? f.docsProvided,
    customsCompleted: draft.customsCompleted ?? f.customsCompleted,
    preBuilt: draft.preBuilt ?? f.preBuilt,
    hawbCount: draft.hawbCount ?? f.hawbCount,
    hawbEntries: draft.hawbEntries?.length ? draft.hawbEntries.map(e => ({
      hawbNumber: e.hawbNumber || '', consigneeName: e.consigneeName || '',
      pieces: e.pieces || 0, weightKg: e.weightKg || 0, destination: e.destination || 'MIA',
      _dbId: e._dbId || null, _hawbId: e._hawbId || null,
    })) : f.hawbEntries,
    pieces: draft.pieces?.length ? draft.pieces.map(p => ({
      pieces: p.pieces ?? 1, hawbId: p.hawbId ?? null,
      lengthIn: p.lengthIn ?? null, widthIn: p.widthIn ?? null, heightIn: p.heightIn ?? null,
      scaleWeightLbs: p.scaleWeightLbs ?? null,
      dimWeight: 0, dimWeightLbs: 0, scaleWeightKg: 0, dimWeightKg: 0, chargeableKg: 0, chargeableLbs: 0,
    })) : f.pieces,
    remarks: draft.remarks ?? f.remarks,
    dockSignature: draft.dockSignature ?? f.dockSignature,
    printName: draft.printName ?? f.printName,
    deliveredByName: draft.deliveredByName ?? f.deliveredByName,
    deliveredByIdNum: draft.deliveredByIdNum ?? f.deliveredByIdNum,
    deliveredBySig: draft.deliveredBySig ?? f.deliveredBySig,
      receivedByName: draft.receivedByName ?? f.receivedByName,
      receivedByIdNum: draft.receivedByIdNum ?? f.receivedByIdNum,
      receivedBySig: draft.receivedBySig ?? f.receivedBySig,
      brokerName: draft.brokerName ?? f.brokerName,
    brokerIdNum: draft.brokerIdNum ?? f.brokerIdNum,
    brokerSig: draft.brokerSig ?? f.brokerSig,
    driverSignatures: draft.driverSignatures ?? f.driverSignatures,
    operationalChecks: { ...f.operationalChecks, ...(draft.operationalChecks || {}) },
    coldChain: { ...f.coldChain, ...(draft.coldChain || {}) },
    duaNumber: draft.duaNumber ?? f.duaNumber,
    sealNumber: draft.sealNumber ?? f.sealNumber,
    dockBay: draft.dockBay ?? f.dockBay,
  })
  lastDraftSave.value = 'borrador'
  f.pieces.forEach((_, pi) => calcPiece(mawbId, pi))
}

// Auto-save watch (debounced on active form) - SOLO campos de piezas (dimensiones/pesos)
const activeFormJson = computed(() => {
  const mId = expandedId.value
  if (!mId || !receiptForms[mId]) return null
  const f = receiptForms[mId]
  // Solo serializa dimensiones/pesos de piezas (campos que cambian visualmente al tipear)
  return JSON.stringify({
    step: localStep.value,
    pieces: f.pieces.map(p => ({
      l: p.lengthIn, w: p.widthIn, h: p.heightIn,
      sl: p.scaleWeightLbs, sk: p.scaleWeightKg,
      dl: p.dimWeightLbs, dk: p.dimWeightKg
    }))
  })
})

let draftTimer = null
watch(activeFormJson, (json) => {
  if (!json) return
  if (draftTimer) clearTimeout(draftTimer)
  draftTimer = setTimeout(() => {
    const mId = expandedId.value
    if (mId) saveDraft(mId)
  }, 2000)
})

const filterTextRaw = ref('')
const filterText = ref('')
const filterDate = ref('')

// Sello de frescura — se actualiza cada vez que useLiveRefresh completa (< 5 s)
const lastRefreshMs = ref(Date.now())
const refreshNow = ref(Date.now())
const pipelineAgo = computed(() => Math.max(0, Math.floor((refreshNow.value - lastRefreshMs.value) / 1000)))
let pipelineClock = null
onMounted(() => {
  pipelineClock = setInterval(() => { refreshNow.value = Date.now() }, 10000)
})
onUnmounted(() => {
  if (pipelineClock) clearInterval(pipelineClock)
})

let filterDebounce = null
watch(filterTextRaw, (val) => {
  if (filterDebounce) clearTimeout(filterDebounce)
  filterDebounce = setTimeout(() => { filterText.value = val }, 200)
})

const statusPriority = { PENDIENTE: 0, RECIBIDA: 1, EN_PROCESO: 2, CARGADA: 3, MANIFESTADA: 4, DESPACHADA: 5 }

const tableMode = ref('master')
const collapsedFlights = ref(new Set())

function openMaster() {
  tableMode.value = 'master'
}
function toggleMode(mode) {
  tableMode.value = tableMode.value === mode ? 'master' : mode
}
function toggleGroup(key) {
  const set = new Set(collapsedFlights.value)
  if (set.has(key)) set.delete(key)
  else set.add(key)
  collapsedFlights.value = set
}
function isCollapsedGroup(key) {
  return collapsedFlights.value.has(key)
}

function flightOf(m) {
  if (!m) return null
  if (m.flightId && store.flights?.length) {
    const f = store.flights.find(x => x.id === m.flightId)
    if (f) return f
  }
  const link = (store.uldAwbs || []).find(x => (x.mawbId && x.mawbId === m.id) || normalizeAwb(x.mawbLabel || '') === normalizeAwb(m.awbNumber || ''))
  if (link && link.flightId && store.flights?.length) {
    let f = store.flights.find(x => x.id === link.flightId)
    if (f) return f
    if (link.flightId && store.ulds?.length) {
      const u = store.ulds.find(u => u.id === link.uldId || u.uid === link.uldId)
      if (u && u.flightId) f = store.flights.find(x => x.id === u.flightId)
    }
  }
  return null
}
function flightKeyOf(m) {
  const f = flightOf(m)
  return f?.id || m.flightId || 'none'
}
function fmtFlight(m) {
  const f = flightOf(m)
  if (!f) return t('warehouse.noFlight')
  const parts = f.flightNumber ? String(f.flightNumber).split(' ') : []
  const code = airlineCodeById(f.airlineId) || (parts.length > 1 ? parts[0] : '')
  const num = parts.length > 1 ? parts.slice(1).join(' ') : f.flightNumber
  const date = fmtFlightDate(f.flightDate)
  return [code, num, date].filter(Boolean).join(' - ') || f.id?.slice(0, 8)
}
function flightSub(key) {
  const g = groupByKey(key)
  const f = g?.flight
  if (!f) return t('warehouse.noFlight')
  const date = fmtFlightDate(f.flightDate)
  // NO llamarla `route`: ese nombre es el `useRoute()` del setup y el
  // sombreado hacía muy fácil usar uno por otro dentro de esta función.
  const legs = [f.origin, f.destination].filter(Boolean).join(' → ')
  return [date, legs].filter(Boolean).join(' · ')
}
function byFlightGroups() {
  const map = new Map()
  for (const m of filteredMawbs.value) {
    const key = flightKeyOf(m)
    if (!map.has(key)) map.set(key, { key, flight: flightOf(m) || null, mawbs: [] })
    map.get(key).mawbs.push(m)
  }
  const arr = [...map.values()]
  arr.sort((a, b) => {
    const d = (b.flight?.flightDate || '') < (a.flight?.flightDate || '') ? 1 : ((b.flight?.flightDate || '') > (a.flight?.flightDate || '') ? -1 : 0)
    if (d) return d
    if (a.key === 'none') return 1
    if (b.key === 'none') return -1
    return String(a.flight?.flightNumber || '').localeCompare(String(b.flight?.flightNumber || ''))
  })
  return arr
}
function groupByKey(key) {
  return byFlightGroups().find(g => g.key === key)
}
function flightGroupStarts() {
  const starts = new Set()
  for (const g of byFlightGroups()) if (g.mawbs.length) starts.add(g.mawbs[0].id)
  return starts
}
const STATUS_CHIP_CLS = {
  PENDIENTE: 'st-pendiente', RECIBIDA: 'st-recibida', EN_PROCESO: 'st-en-proceso',
  CARGADA: 'st-cargada', MANIFESTADA: 'st-manifestada', DESPACHADA: 'st-despachada',
}
const STATUS_DOT_CLS = {
  PENDIENTE: 'bg-slate-400', RECIBIDA: 'bg-amber-500', EN_PROCESO: 'bg-amber-500',
  CARGADA: 'bg-red-500', MANIFESTADA: 'bg-emerald-500', DESPACHADA: 'bg-blue-500',
}
function statusChipClass(m) {
  return STATUS_CHIP_CLS[deriveMawbOperationalStatus(m)] || 'st-pendiente'
}
function statusDotClass(m) {
  return STATUS_DOT_CLS[deriveMawbOperationalStatus(m)] || 'bg-slate-400'
}

// Pipeline de recepción (s1 zona A) — segmentos por estado operativo
const PIPELINE_COLORS = {
  PENDIENTE: '#475569', RECIBIDA: '#0d9488', EN_PROCESO: '#0284c7',
  CARGADA: '#7c3aed', MANIFESTADA: '#b45309', DESPACHADA: '#15803d',
}
const statusFilter = ref('')
const flightFilter = ref('')
const statusCounts = computed(() => {
  const c = { PENDIENTE: 0, RECIBIDA: 0, EN_PROCESO: 0, CARGADA: 0, MANIFESTADA: 0, DESPACHADA: 0 }
  for (const m of store.mawbs) {
    const s = mawbOperationalStatusMap.value[m.id]
    if (s && s in c) c[s]++
  }
  return c
})
const pipeTotal = computed(() => store.mawbs.length)
const pipeSegments = computed(() =>
  ['PENDIENTE', 'RECIBIDA', 'EN_PROCESO', 'CARGADA', 'MANIFESTADA', 'DESPACHADA'].map(key => ({
    key,
    n: statusCounts.value[key],
    color: PIPELINE_COLORS[key],
  }))
)
function setStatusFilter(key) {
  statusFilter.value = statusFilter.value === key ? '' : key
}
const flightOptions = computed(() => {
  const seen = new Map()
  for (const m of store.mawbs) {
    const k = flightKeyOf(m)
    if (!k || k === 'none') continue
    if (!seen.has(k)) seen.set(k, { key: k, label: fmtFlight(m) })
  }
  return [...seen.values()].sort((a, b) => a.label.localeCompare(b.label))
})

const filteredMawbs = computed(() => {
  let list = [...store.mawbs]
  if (filterDate.value) {
    const target = filterDate.value
    const mawbsWithReceipt = (store.receipts || [])
      .filter(r => {
        const d = r.receiptDate || r.createdAt
        return d && d.startsWith(target)
      })
      .map(r => r.mawb?.id || r.mawbId)
      .filter(Boolean)
    list = list.filter(m => mawbsWithReceipt.includes(m.id))
  }
  const ft = filterText.value.trim()
  if (ft) list = list.filter(m => applyFilter(m, ft))
  if (statusFilter.value) list = list.filter(m => mawbOperationalStatusMap.value[m.id] === statusFilter.value)
  if (flightFilter.value) list = list.filter(m => flightKeyOf(m) === flightFilter.value)

  return [...list].sort((a, b) => {
    const pa = statusPriority[mawbOperationalStatusMap.value[a.id]] ?? 0
    const pb = statusPriority[mawbOperationalStatusMap.value[b.id]] ?? 0
    if (pa !== pb) return pa - pb
    return (b.awbNumber || '').localeCompare(a.awbNumber || '')
  })
})

// Keep the MAWB whose receipt form is open in the rendered list even when a
// filter/refresh would otherwise drop it. The Teleport holding the form lives
// inside this v-for, so if the row leaves the list the form unmounts and
// disappears mid-edit.
const renderMawbs = computed(() => {
  const list = filteredMawbs.value
  const id = expandedId.value
  if (id && !list.some(x => x.id === id)) {
    const extra = (store.mawbs || []).find(x => x.id === id)
    if (extra) return [extra, ...list]
  }
  return list
})

function applyFilter(m, ft) {
  const numMatch = ft.match(/^(>=?|<=?|=)?(\d+(?:\.\d+)?)$/)
  if (numMatch) {
    const op = numMatch[1] || '='
    const val = parseFloat(numMatch[2])
    // Use received pieces from receiptTotals (what's actually shown in the column)
    const num = receiptTotals.value[m.id]?.pieces || m.pieces || 0
    switch (op) {
      case '>=': return num >= val
      case '>':  return num > val
      case '<=': return num <= val
      case '<':  return num < val
      case '=':  return num === val
      default:   return false
    }
  }
  if (ft.startsWith('*') && ft.endsWith('*')) {
    const mid = ft.slice(1, -1).toLowerCase()
    return matchAnyField(m, v => v.toLowerCase().includes(mid))
  }
  if (ft.startsWith('*')) {
    const suffix = ft.slice(1).toLowerCase()
    return matchAnyField(m, v => v.toLowerCase().endsWith(suffix))
  }
  if (ft.endsWith('*')) {
    const prefix = ft.slice(0, -1).toLowerCase()
    return matchAnyField(m, v => v.toLowerCase().startsWith(prefix))
  }
  const lower = ft.toLowerCase()
  return matchAnyField(m, v => v.toLowerCase().includes(lower))
}

function matchAnyField(m, fn) {
  const fields = [
    m.awbNumber, m.shipperName, m.consigneeName,
    m.destination, m.origin,
  ].filter(Boolean)
  return fields.some(fn)
}

const steps = ['HEADER', 'PIECES', 'CONTROLES', 'REMARKS', 'EVIDENCE', 'SIGNATURES']

function editOrExpandReceipt(m) {
  const rid = receiptById.value[m.id]
  if (rid && editModalRef.value) {
    editModalRef.value.open(rid)
  } else {
    toggleExpand(m)
  }
}

// Reanudar un borrador guardado: expande la fila (toggleExpand precarga el
// formulario con loadExistingReceiptData, que sí considera los borradores).
async function continueDraft(m) {
  if (expandedId.value !== m.id) await toggleExpand(m)
  toast.info(t('warehouse.draft.continueToast', { awb: m.awbNumber || String(m.id).slice(0, 8) }))
}

async function newReceipt() {
  const target = filteredMawbs.value.find(m => mawbOperationalStatusMap.value[m.id] !== 'RECIBIDA') || filteredMawbs.value[0]
  if (!target) {
    toast.info(t('warehouse.empty'))
    return
  }
  await toggleExpand(target)
  toast.info(`${t('warehouse.newReceipt')} · ${target.awbNumber || String(target.id).slice(0, 8)}`)
}

async function onEditReceiptSaved() {
  await store.loadReceipts()
  // El universo de esta vista es TODAS las MAWBs (el vuelo se filtra aparte con flightFilter).
  // Nunca cargar por store.selectedFlightId: es estado global de otras vistas y recortaría la lista.
  await store.loadAllMawbs()
  successMsg.value = 'Recibo actualizado correctamente'
  if (successTimer) clearTimeout(successTimer)
  successTimer = setTimeout(() => { successMsg.value = '' }, 4000)
}

function initForm(m) {
  if (!receiptForms[m.id]) {
  const hawbs = hawbsForDisplay(m.id)
    const h0 = hawbs[0]
    const fallbackShipper = h0?.shipperName || m.shipperName || ''
    const fallbackConsignee = h0?.consigneeName || m.consigneeName || (hawbs.length === 1 ? hawbs[0]?.consigneeName : '') || ''
    const hawbEntries = hawbs.length > 0
      ? hawbs.map(h => ({
          hawbNumber: h.hawbNumber || '',
          consigneeName: h.consigneeName || '',
          pieces: h.pieces || 0,
          weightKg: h.weightKg ? Number(h.weightKg) : 0,
          destination: h.destination || m.destination || 'MIA',
          _dbId: h.id,
          _hawbId: h.id,
        }))
      : [{ hawbNumber: '', consigneeName: '', pieces: 0, weightKg: 0, destination: m.destination || 'MIA', _dbId: null, _hawbId: '_hawb_' + Date.now() }]

    receiptForms[m.id] = {
      gatewayCfs: 'SDQ',
      shipperName: m.shipperName || fallbackShipper,
      consigneeName: m.consigneeName || fallbackConsignee,
      origin: m.origin || store.selectedFlight?.origin || 'SDQ',
      destination: m.destination || store.selectedFlight?.destination || 'MIA',
      awbReportedPieces: m.pieces || (hawbs.length > 0 ? hawbs.reduce((s, h) => s + (h.pieces || 0), 0) : 0) || 0,
      mawbWeightGreatest: 0, // auto-calculado desde scaleWeightLbs de las piezas al cargar
      dimFactorKg: 366, // factor dimensional internacional (KG); dimFactorIntl del backend
      dimFactorLbs: 194, // factor dimensional doméstico (LBS); dimFactorDom del backend
      chargeableMethod: 'MAX', // MAX | SUM | SCALE | DIM — calculado/resuelto por el backend
      roundUpKg: 0,
      roundUpLbs: 0,
      minChargeableKg: 0,
      minChargeableLbs: 0,
      cashOnly: false,
      bookedInAcoms: false,
      docsProvided: false,
      customsCompleted: false,
      preBuilt: false,
      hawbCount: Math.max(hawbs.length, 1),
      hawbEntries,
      pieces: hawbEntries.length > 0
        ? hawbEntries.map(e => ({ pieces: 1, hawbId: e._hawbId || null, lengthIn: null, widthIn: null, heightIn: null, scaleWeightLbs: null, dimWeight: 0, dimWeightLbs: 0, scaleWeightKg: 0, dimWeightKg: 0, chargeableKg: 0, chargeableLbs: 0 }))
        : [{ pieces: 1, hawbId: null, lengthIn: null, widthIn: null, heightIn: null, scaleWeightLbs: null, dimWeight: 0, dimWeightLbs: 0, scaleWeightKg: 0, dimWeightKg: 0, chargeableKg: 0, chargeableLbs: 0 }],
      remarks: '',
      evidence: [],
      mawbEvidence: [],
      dockSignature: '',
      printName: '',
      deliveredByName: '',
      deliveredByIdNum: '',
      deliveredBySig: '',
      brokerName: '',
      brokerIdNum: '',
      brokerSig: '',
      receivedByName: '',
      receivedByIdNum: '',
      receivedBySig: '',
      driverSignatures: [],
      operationalChecks: {
        packagingIntact: false, noDamage: false, sealsIntact: false, labelsLegible: false,
        palletized: false, noLeaks: false, countVerified: false, notes: '',
        docsReceived: false, customsDocs: false, duaAttached: false,
      },
      coldChain: { enabled: false, minC: null, maxC: null, receivedTempC: null, receivedAt: '', readings: [] },
      duaNumber: '',
      sealNumber: '',
      dockBay: '',
      receiptDate: null,
      startDatetime: null,
      _piecesLoadedFromDb: false,
      _loadedFromConfig: false,
      pieceCount: 0,
      totalWeightKg: 0,
    }
  }
}

async function loadExistingReceiptData(m) {
  const f = receiptForms[m.id]
  if (!f) return
  const existingReceipts = (store.receipts || []).filter(r => (r.mawb?.id || r.mawbId) === m.id)
  if (existingReceipts.length === 0) return
  existingReceipts.sort((a, b) => {
    const ca = a.createdAt ? new Date(a.createdAt).getTime() : 0
    const cb = b.createdAt ? new Date(b.createdAt).getTime() : 0
    return ca - cb
  })
  // Prefer non-superseded receipts as the active source
  const activeReceipts = existingReceipts.filter(r => !r.superseded)
  const sourceReceipts = activeReceipts.length > 0 ? activeReceipts : existingReceipts
  // Use the last receipt for metadata (field values), but load pieces from ALL
  const sourceReceipt = sourceReceipts[sourceReceipts.length - 1]
  f.gatewayCfs = sourceReceipt.gatewayCfs ?? 'SDQ'
  f.shipperName = sourceReceipt.shipperName ?? f.shipperName
  f.consigneeName = sourceReceipt.consigneeName ?? f.consigneeName
  f.origin = sourceReceipt.origin ?? f.origin
  f.destination = sourceReceipt.destination ?? f.destination
  f.awbReportedPieces = sourceReceipt.awbReportedPieces ?? f.awbReportedPieces
  f.mawbWeightGreatest = sourceReceipt.mawbWeightGreatest ?? f.mawbWeightGreatest
  f.dimFactorKg = sourceReceipt.dimFactorIntl ? Number(sourceReceipt.dimFactorIntl) : f.dimFactorKg
  f.dimFactorLbs = sourceReceipt.dimFactorDom ? Number(sourceReceipt.dimFactorDom) : f.dimFactorLbs
  f.chargeableMethod = sourceReceipt.chargeableMethod ?? f.chargeableMethod
  f.roundUpKg = Number(sourceReceipt.roundUpKg ?? f.roundUpKg ?? 0)
  f.roundUpLbs = Number(sourceReceipt.roundUpLbs ?? f.roundUpLbs ?? 0)
  f.minChargeableKg = Number(sourceReceipt.minChargeableKg ?? f.minChargeableKg ?? 0)
  f.minChargeableLbs = Number(sourceReceipt.minChargeableLbs ?? f.minChargeableLbs ?? 0)
  f._loadedFromConfig = true // recibos existentes son la fuente de verdad
  f.cashOnly = sourceReceipt.cashOnly ?? f.cashOnly
  f.bookedInAcoms = sourceReceipt.bookedInAcoms ?? f.bookedInAcoms
  f.docsProvided = sourceReceipt.docsProvided ?? f.docsProvided
  f.customsCompleted = sourceReceipt.customsCompleted ?? f.customsCompleted
  f.preBuilt = sourceReceipt.preBuilt ?? f.preBuilt
  f.remarks = sourceReceipt.remarks ?? f.remarks
  f.dockSignature = sourceReceipt.dockSignature ?? f.dockSignature
  f.printName = sourceReceipt.printName ?? f.printName
  f.deliveredByName = sourceReceipt.deliveredByName ?? f.deliveredByName
  f.deliveredByIdNum = sourceReceipt.deliveredByIdNum ?? f.deliveredByIdNum
  f.deliveredBySig = sourceReceipt.deliveredBySigUrl ?? f.deliveredBySig
  f.brokerName = sourceReceipt.brokerName ?? f.brokerName
  f.brokerIdNum = sourceReceipt.brokerIdNum ?? f.brokerIdNum
  f.brokerSig = sourceReceipt.brokerSigUrl ?? f.brokerSig
  f.driverSignatures = parseJsonValue(sourceReceipt.driverSignatures, f.driverSignatures || [])
  f.operationalChecks = { ...f.operationalChecks, ...parseJsonValue(sourceReceipt.operationalChecks, {}) }
  f.coldChain = { ...f.coldChain, ...parseJsonValue(sourceReceipt.coldChain, {}) }
  // La temperatura de recepción también vive en su propia columna: priorizarla
  // cuando el JSON histórico no la traiga.
  if (sourceReceipt.receivedTempC !== null && sourceReceipt.receivedTempC !== undefined) {
    f.coldChain.receivedTempC = Number(sourceReceipt.receivedTempC)
  }
  f.duaNumber = sourceReceipt.duaNumber ?? f.duaNumber
  f.sealNumber = sourceReceipt.sealNumber ?? f.sealNumber ?? ''
  f.dockBay = sourceReceipt.dockBay ?? f.dockBay ?? ''
  f.receivedByName = sourceReceipt.receivedByName ?? f.receivedByName
  f.receivedByIdNum = sourceReceipt.receivedByIdNum ?? f.receivedByIdNum
  f.receivedBySig = sourceReceipt.receivedBySigUrl ?? f.receivedBySig
  f.receiptDate = sourceReceipt.receiptDate ?? f.receiptDate
  f.startDatetime = sourceReceipt.startDatetime ?? f.startDatetime
  f.pieceCount = sourceReceipt.pieceCount ?? 0
  f.totalWeightKg = sourceReceipt.actualWeightKg ?? sourceReceipt.chargeableWeightKg ?? 0
  // Load pieces from ALL non-superseded receipts (not just one)
  try {
    const pieceResults = await Promise.all(
      sourceReceipts.map(r => receiptsApi.getPieces(r.id).catch(() => ({ data: [] })))
    )
    const allLoadedPieces = pieceResults.flatMap(res => res.data || [])
    if (allLoadedPieces.length > 0) {
      f.pieces = allLoadedPieces.map((p) => {
        return {
          pieces: p.pieces ?? 1,
          hawbId: p.hawbId ?? null,
          lengthIn: p.lengthIn ?? null,
          widthIn: p.widthIn ?? null,
          heightIn: p.heightIn ?? null,
          scaleWeightLbs: p.scaleWeightLbs ?? null,
          dimWeight: Number(p.dimWeightLbs) || 0,
          dimWeightLbs: Number(p.dimWeightLbs) || 0,
          scaleWeightKg: Number(p.scaleWeightKg) || 0,
          dimWeightKg: Number(p.dimWeightKg) || 0,
          chargeableKg: Number(p.chargeableKg) || 0,
          chargeableLbs: Number(p.chargeableLbs) || 0,
        }
      })
      f._piecesLoadedFromDb = true
      f.mawbWeightGreatest = totalScaleLbs(m.id, null)
    }
  } catch (e) { toast.error(extractError(e)) }
  try {
    const docsRes = await receiptsApi.getSupportingDocsJson(sourceReceipt.id)
    if (docsRes.data && Array.isArray(docsRes.data)) {
      f.evidence = docsRes.data.map(d => ({
        name: d.name || 'documento',
        type: d.type || 'document',
        url: d.url || '',
        file: null,
      }))
    }
  } catch { /* no supporting docs yet */ }
}

function calcPiece(mawbId, pi) {
  const p = receiptForms[mawbId].pieces[pi]
  const f = receiptForms[mawbId]
  const l = Number(p.lengthIn) || 0
  const w = Number(p.widthIn) || 0
  const h = Number(p.heightIn) || 0
  const qty = p.pieces || 1
  const vol = l * w * h * qty
  const dimFactorKg = f?.dimFactorKg || 366
  const dimFactorLbs = f?.dimFactorLbs || 194
  p.dimWeightKg = vol > 0 ? vol / dimFactorKg : 0
  p.dimWeightLbs = vol > 0 ? vol / dimFactorLbs : 0
  p.dimWeight = p.dimWeightLbs
  const scaleLbs = Number(p.scaleWeightLbs) || 0
  p.scaleWeightKg = scaleLbs ? scaleLbs / 2.20462 : 0
  // Método: MAX | SUM | SCALE | DIM (perfil por aerolínea resuelto por el backend)
  const method = (f?.chargeableMethod || 'MAX').toUpperCase()
  let baseLbs = Math.max(scaleLbs, p.dimWeightLbs || 0)
  let baseKg = Math.max(p.scaleWeightKg, p.dimWeightKg || 0)
  if (method === 'SUM') {
    baseLbs = scaleLbs + (p.dimWeightLbs || 0)
    baseKg = p.scaleWeightKg + (p.dimWeightKg || 0)
  } else if (method === 'SCALE') {
    baseLbs = scaleLbs
    baseKg = p.scaleWeightKg
  } else if (method === 'DIM') {
    baseLbs = p.dimWeightLbs || 0
    baseKg = p.dimWeightKg || 0
  }
  const roundUp = (v, step) => step > 0 ? Math.ceil(v / step) * step : v
  const applyMin = (v, min) => min > 0 ? Math.max(v, min) : v
  p.chargeableLbs = roundUp(applyMin(baseLbs, Number(f?.minChargeableLbs) || 0), Number(f?.roundUpLbs) || 0)
  p.chargeableKg = roundUp(applyMin(baseKg, Number(f?.minChargeableKg) || 0), Number(f?.roundUpKg) || 0)
  if (method === 'SCALE') {
    // báscula en ambos ejes: derivar kg del valor redondeado en lbs
    p.chargeableKg = p.chargeableLbs / 2.20462
  }
  // MAWB weight = suma de pesos de bascula de todas las piezas
  if (f) f.mawbWeightGreatest = totalScaleLbs(mawbId, null)
  bumpFormVersionDebounced() // refresca (con debounce) el bloque memoizado del MAWB para reflejar totales al tipear
}

function numOr(v, dflt) {
  if (v === null || v === undefined || v === '') return dflt
  const n = Number(v)
  return Number.isFinite(n) ? n : dflt
}

function normMethod(v) {
  const m = (v || 'MAX').toUpperCase()
  return ['MAX', 'SUM', 'SCALE', 'DIM'].includes(m) ? m : 'MAX'
}

// Pre-carga el perfil de cálculo resuelto para la aerolínea del MAWB
// (solo rellena el formulario nuevo; recibos existentes y drafts prevalecen).
async function preloadCalcConfig(m) {
  const f = receiptForms[m.id]
  if (!f || f._loadedFromConfig) return
  const airlineId = m.airline?.id || m.airlineId || store.selectedFlight?.airlineId || null
  try {
    const res = await calcConfigApi.resolve(airlineId)
    const p = res.data || {}
    const draft = loadDraft(m.id)
    if (draft && draft.chargeableMethod !== undefined) {
      // el borrador ya arrastra los parámetros de cálculo completos
      f._loadedFromConfig = true
      return
    }
    f.chargeableMethod = normMethod(p.method)
    f.roundUpKg = numOr(p.roundUpKg, 0)
    f.roundUpLbs = numOr(p.roundUpLbs, 0)
    f.minChargeableKg = numOr(p.minChargeableKg, 0)
    f.minChargeableLbs = numOr(p.minChargeableLbs, 0)
    if (!draft) {
      f.dimFactorKg = numOr(p.dimFactorIntl, f.dimFactorKg)
      f.dimFactorLbs = numOr(p.dimFactorDom, f.dimFactorLbs)
    }
    f._loadedFromConfig = true
  } catch { /* fallback a los defaults del formulario */ }
}

function allPieces(mawbId, hawbId) {
  const pieces = receiptForms[mawbId]?.pieces || []
  return hawbId ? pieces.filter(p => p.hawbId === hawbId) : pieces
}

function totalPieces(mawbId, hawbId) {
  return allPieces(mawbId, hawbId).reduce((s, p) => s + (p.pieces || 1), 0)
}

function totalDimWeight(mawbId, hawbId) {
  return allPieces(mawbId, hawbId).reduce((s, p) => s + (p.dimWeight || 0), 0)
}

function totalScaleLbs(mawbId, hawbId) {
  return allPieces(mawbId, hawbId).reduce((s, p) => s + (p.scaleWeightLbs || 0), 0)
}

function totalDimLbs(mawbId, hawbId) {
  return allPieces(mawbId, hawbId).reduce((s, p) => s + (p.dimWeightLbs || 0), 0)
}

function totalScaleKg(mawbId, hawbId) {
  return allPieces(mawbId, hawbId).reduce((s, p) => s + (p.scaleWeightKg || 0), 0)
}

function totalDimKg(mawbId, hawbId) {
  return allPieces(mawbId, hawbId).reduce((s, p) => s + (p.dimWeightKg || 0), 0)
}

function totalChargeableKg(mawbId, hawbId) {
  const pieces = allPieces(mawbId, hawbId)
  return pieces.reduce((s, p) => s + (p.chargeableKg || 0), 0)
}

function totalChargeableLbs(mawbId, hawbId) {
  const pieces = allPieces(mawbId, hawbId)
  return pieces.reduce((s, p) => s + (p.chargeableLbs || 0), 0)
}

function hawbsForDisplay(mawbId) {
  return receiptForms[mawbId]?.hawbEntries || []
}

function piecesByHawb(mawbId, hawbId) {
  return allPieces(mawbId, hawbId)
}

// Igual que piecesByHawb, pero conserva el indice real dentro de receiptForms[mawbId].pieces
// para cada elemento. Evita tener que hacer pieces.indexOf(p) repetidamente en el template
// (O(n) por cada input, y fragil si dos piezas fueran el mismo objeto por referencia).
function piecesByHawbIndexed(mawbId, hawbId) {
  if (hawbId == null) return []
  const pieces = receiptForms[mawbId]?.pieces || []
  const result = []
  for (let i = 0; i < pieces.length; i++) {
    if (pieces[i].hawbId === hawbId) {
      result.push({ piece: pieces[i], idx: i })
    }
  }
  return result
}

function groupedSummary(mawbId) {
  const pieces = receiptForms[mawbId]?.pieces || []
  const groups = {}
  pieces.forEach(p => {
    const l = Number(p.lengthIn) || 0
    const w = Number(p.widthIn) || 0
    const h = Number(p.heightIn) || 0
    const key = `${l}x${w}x${h}`
    if (!groups[key]) {
      groups[key] = { lengthIn: l, widthIn: w, heightIn: h, totalPieces: 0, totalDimWeight: 0, totalScaleLbs: 0, totalDimLbs: 0, totalScaleKg: 0, totalDimKg: 0, totalChargeableKg: 0, totalChargeableLbs: 0 }
    }
    const g = groups[key]
    g.totalPieces += Number(p.pieces) || 1
    g.totalDimWeight += Number(p.dimWeight) || 0
    g.totalScaleLbs += Number(p.scaleWeightLbs) || 0
    g.totalDimLbs += Number(p.dimWeightLbs) || 0
    g.totalScaleKg += Number(p.scaleWeightKg) || 0
    g.totalDimKg += Number(p.dimWeightKg) || 0
    g.totalChargeableKg += Number(p.chargeableKg) || 0
    g.totalChargeableLbs += Number(p.chargeableLbs) || 0
  })
  return Object.values(groups)
}

function hawbTotalPieces(mawbId, hawbId) { return totalPieces(mawbId, hawbId) }
function hawbDimWeight(mawbId, hawbId) { return totalDimWeight(mawbId, hawbId) }
function hawbScaleLbs(mawbId, hawbId) { return totalScaleLbs(mawbId, hawbId) }
function hawbDimLbs(mawbId, hawbId) { return totalDimLbs(mawbId, hawbId) }
function hawbScaleKg(mawbId, hawbId) { return totalScaleKg(mawbId, hawbId) }
function hawbDimKg(mawbId, hawbId) { return totalDimKg(mawbId, hawbId) }
function hawbChargeableKg(mawbId, hawbId) { return totalChargeableKg(mawbId, hawbId) }
function hawbChargeableLbs(mawbId, hawbId) { return totalChargeableLbs(mawbId, hawbId) }

function removeHawbEntry(mawbId, idx) {
  const f = receiptForms[mawbId]
  if (!f || f.hawbEntries.length <= 1) return
  const removedHawbId = f.hawbEntries[idx]._hawbId
  f.hawbEntries.splice(idx, 1)
  f.hawbCount = f.hawbEntries.length
  f.pieces = f.pieces.filter(p => p.hawbId !== removedHawbId)
  if (f.pieces.length === 0) {
    f.pieces.push({ pieces: 1, hawbId: f.hawbEntries[0]?._hawbId || null, lengthIn: null, widthIn: null, heightIn: null, scaleWeightLbs: null, dimWeight: 0, dimWeightLbs: 0, scaleWeightKg: 0, dimWeightKg: 0, chargeableKg: 0, chargeableLbs: 0 })
  }
  bumpFormVersion()
}

function addHawbEntry(m) {
  const f = receiptForms[m.id]
  if (!f) return
  const newHawbId = '_hawb_' + Date.now() + '_' + f.hawbEntries.length
  f.hawbEntries.push({
    hawbNumber: '', consigneeName: '', pieces: 0, weightKg: 0,
    destination: f.destination || m.destination || 'MIA', _dbId: null,
    _hawbId: newHawbId
  })
  f.hawbCount = f.hawbEntries.length
  f.pieces.push({ pieces: 1, hawbId: newHawbId, lengthIn: null, widthIn: null, heightIn: null, scaleWeightLbs: null, dimWeight: 0, dimWeightLbs: 0, scaleWeightKg: 0, dimWeightKg: 0, chargeableKg: 0, chargeableLbs: 0 })
  bumpFormVersion()
}

async function syncMawbName(m, field) {
  const val = receiptForms[m.id]?.[field]
  if (val !== undefined && val !== m[field]) {
    try {
      await mawbsApi.update(m.id, { [field]: val })
      m[field] = val
    } catch (e) {
      toast.error(extractError(e))
      console.warn('Error syncing MAWB ' + field + ':', e)
    }
  }
}

function addPiece(mawbId, hawbId) {
  const f = receiptForms[mawbId]
  const newPiece = { pieces: 1, hawbId: hawbId || null, lengthIn: null, widthIn: null, heightIn: null, scaleWeightLbs: null, dimWeight: 0, dimWeightLbs: 0, scaleWeightKg: 0, dimWeightKg: 0, chargeableKg: 0, chargeableLbs: 0 }
  f.pieces = [...f.pieces, newPiece]
  if (f) f.mawbWeightGreatest = totalScaleLbs(mawbId, null)
  bumpFormVersion()
}

function removePiece(mawbId, idx) {
  const f = receiptForms[mawbId]
  const pieces = f.pieces
  if (pieces.length > 1) f.pieces = pieces.filter((_, i) => i !== idx)
  if (f) f.mawbWeightGreatest = totalScaleLbs(mawbId, null)
  bumpFormVersion()
}

function cancelForm() {
  const mId = expandedId.value
  if (!mId) return
  saveDraft(mId)
  expandedId.value = null
  localStep.value = 1
  generatedReceiptId.value = null
  lastDraftSave.value = ''
  resetStepValidation()
}

// Step validation helpers for progress bar
function stepData(mawbId) {
  return receiptForms[mawbId] || null
}

function stepDone(si) {
  const mId = expandedId.value
  const d = stepData(mId)
  if (!d) return false
  if (si === 0) return !!(d.shipperName && d.consigneeName && d.origin && d.destination)
  if (si === 1) return d.pieces.length > 0 && d.pieces.some(p => p.lengthIn || p.widthIn || p.heightIn)
  if (si === 2) return checksSummary(activeMawb.value).ok // checklist operativo
  if (si === 3) return true // remarks optional
  if (si === 4) return true // evidence optional
  if (si === 5) return !!(d.printName || d.dockSignature)
  return false
}

function stepError(si) {
  return touchedSteps[si] && validateStep(si).length > 0
}

// ── Validación e integridad del recibo (guía al operador) ──────────────────
function hawbKey(s) {
  return String(s || '').replace(/[\s\-_/]/g, '').toUpperCase()
}
function hasAnyDim(p) {
  return !!(Number(p.lengthIn) || Number(p.widthIn) || Number(p.heightIn))
}
function hasAllDims(p) {
  return !!(Number(p.lengthIn) && Number(p.widthIn) && Number(p.heightIn))
}
function hasPartialDims(p) {
  return hasAnyDim(p) && !hasAllDims(p)
}
function activePieces(d) {
  return (d.pieces || []).filter(p => (Number(p.pieces) || 0) > 0 || hasAnyDim(p) || Number(p.scaleWeightLbs) > 0)
}

// Errores bloqueantes por paso (0-indexed)
function validateStep(si) {
  const d = stepData(expandedId.value)
  const errs = []
  if (!d) return errs
  if (si === 0) {
    if (!String(d.shipperName || '').trim()) errs.push('Shipper Name es obligatorio')
    if (!String(d.consigneeName || '').trim()) errs.push('Consignee Name es obligatorio')
    if (String(d.origin || '').trim().length !== 3) errs.push('Origin debe tener 3 letras (ej. SDQ)')
    if (String(d.destination || '').trim().length !== 3) errs.push('Destination debe tener 3 letras (ej. MIA)')
  } else if (si === 1) {
    const pieces = activePieces(d)
    if (pieces.length === 0) errs.push('Ingresa al menos una pieza con dimensiones o peso')
    const partial = pieces.filter(hasPartialDims).length
    if (partial > 0) errs.push(partial + ' pieza(s) con dimensiones incompletas (L × W × H)')
    if (pieces.some(p => Number(p.pieces) < 0 || Number(p.scaleWeightLbs) < 0)) errs.push('No se permiten valores negativos en piezas o pesos')
  } else if (si === 5) {
    if (!String(d.printName || '').trim() && !d.dockSignature) errs.push('Firma de quien entrega o nombre impreso es obligatorio')
  }
  return errs
}

// Advertencias de integridad (no bloquean el guardado)
function warnStep(si) {
  const d = stepData(expandedId.value)
  const warns = []
  if (!d) return warns
  if (si === 0) {
    const seen = new Set(); const dups = new Set()
    for (const e of (d.hawbEntries || [])) {
      const k = hawbKey(e.hawbNumber)
      if (!k) continue
      if (seen.has(k)) dups.add(e.hawbNumber); seen.add(k)
    }
    if (dups.size) warns.push('HAWB duplicado: ' + [...dups].join(', '))
    const blankWithPieces = (d.hawbEntries || []).filter(e => !hawbKey(e.hawbNumber) && Number(e.pieces) > 0).length
    if (blankWithPieces) warns.push(blankWithPieces + ' HAWB sin número pero con piezas')
  } else if (si === 1) {
    const sum = (d.hawbEntries || []).reduce((s, e) => s + (Number(e.pieces) || 0), 0)
    const rep = Number(d.awbReportedPieces) || 0
    if (rep > 0 && sum > 0 && sum !== rep) warns.push('Piezas HAWB (' + sum + ') no coinciden con AWB reportado (' + rep + ')')
    const pcs = (d.pieces || []).reduce((s, p) => s + (Number(p.pieces) || 1), 0)
    if (sum > 0 && pcs !== sum) warns.push('Piezas en detalle (' + pcs + ') no coinciden con HAWBs (' + sum + ')')
  }
  return warns
}

const stepWarnings = computed(() => warnStep(localStep.value - 1))

function firstInvalidStep() {
  for (let s = 0; s < 6; s++) { if (validateStep(s).length) return s }
  return -1
}

function resetStepValidation() {
  for (let i = 0; i < 6; i++) touchedSteps[i] = false
  stepErrorMsg.value = ''
}

// Navegación guiada: avanzar valida; saltar hacia adelante valida los pasos previos.
function goToStep(target) {
  const mId = expandedId.value
  if (!receiptForms[mId]) return
  receiptView.value = 'ops'
  if (target <= localStep.value) { stepErrorMsg.value = ''; localStep.value = target; return }
  for (let s = localStep.value - 1; s < target - 1; s++) {
    touchedSteps[s] = true
    const errs = validateStep(s)
    if (errs.length) {
      stepErrorMsg.value = 'Paso ' + (s + 1) + ': ' + errs[0]
      localStep.value = s + 1
      return
    }
  }
  stepErrorMsg.value = ''
  localStep.value = target
}

const stepRecap = computed(() => {
  const d = stepData(expandedId.value)
  if (!d) return null
  const pcs = (d.pieces || []).reduce((s, p) => s + (Number(p.pieces) || 1), 0)
  const kg = (d.pieces || []).reduce((s, p) => s + (Number(p.chargeableKg) || 0), 0)
  return { pcs, kg: Math.round(kg * 10) / 10 }
})

// ── Vistas del popup (tablet): formulario guiado · detalle de skids · previa
const STEP_LABELS = ['Datos', 'Skids', 'Controles', 'Notas', 'Evidencias', 'Firmas']
const RECEIPT_VIEWS = ['ops', 'skids', 'preview']
const receiptView = ref('ops')
function setReceiptView(v) {
  receiptView.value = RECEIPT_VIEWS.includes(v) ? v : 'ops'
}
// Al abrir/cerrar otro recibo el popup vuelve siempre a la vista operativa.
watch(expandedId, () => { receiptView.value = 'ops' })

const IN_TO_CM = 2.54
const skidRows = computed(() => {
  const m = activeMawb.value
  const f = m ? receiptForms[m.id] : null
  if (!f) return []
  return (f.pieces || []).map((p, i) => {
    const hawb = (f.hawbEntries || []).find(h => h._hawbId === p.hawbId)
    const qty = Number(p.pieces) || 1
    const l = Number(p.lengthIn) || 0
    const w = Number(p.widthIn) || 0
    const h = Number(p.heightIn) || 0
    const cm = { l: l ? Math.round(l * IN_TO_CM * 10) / 10 : 0, w: w ? Math.round(w * IN_TO_CM * 10) / 10 : 0, h: h ? Math.round(h * IN_TO_CM * 10) / 10 : 0 }
    const cbm = l && w && h ? l * w * h * qty * 0.0000163871 : 0
    return {
      key: `${p.hawbId || 'x'}-${i}`,
      index: i + 1,
      hawb: hawb?.hawbNumber || '—',
      qty,
      l, w, h,
      cm,
      dimsLabel: l && w && h ? `${l}x${w}x${h} in (${cm.l}x${cm.w}x${cm.h} cm)` : '—',
      cbm,
      cft: cbm ? cbm * 35.3147 : 0,
      scaleKg: Number(p.scaleWeightKg) || 0,
      dimKg: Number(p.dimWeightKg) || 0,
      chargeKg: Number(p.chargeableKg) || 0,
    }
  })
})

const skidTotals = computed(() => skidRows.value.reduce((t, r) => ({
  qty: t.qty + r.qty,
  cbm: t.cbm + r.cbm,
  cft: t.cft + r.cft,
  scaleKg: t.scaleKg + r.scaleKg,
  dimKg: t.dimKg + r.dimKg,
  chargeKg: t.chargeKg + r.chargeKg,
}), { qty: 0, cbm: 0, cft: 0, scaleKg: 0, dimKg: 0, chargeKg: 0 }))

function openScanModal() {
  showScanModal.value = true
}

function closeScanModal() {
  showScanModal.value = false
}

// Resuelve el código escaneado (MAWB o HAWB → su MAWB padre) vía el backend y
// abre el formulario de esa MAWB. La cámara permanece abierta para escanear en
// cadena; solo se cierra si el operador lo pide.
async function onScanDetected(code) {
  const raw = String(code || '').trim()
  if (!raw || scanBusy.value) return
  scanBusy.value = true
  try {
    const res = await scanApi.lookup(raw)
    const dto = res.data || {}
    const target = normalizeAwb(dto.awbNumber || raw)
    const m = (store.mawbs || []).find(x => normalizeAwb(x.awbNumber) === target)
    if (!m) {
      toast.error(t('warehouse.scan.notFound', { code: raw }))
      return
    }
    if (expandedId.value !== m.id) await toggleExpand(m)
    toast.success(t('warehouse.scan.opened', { awb: dto.awbNumber || raw }))
    showScanModal.value = false
  } catch (e) {
    toast.error(extractError(e) || t('warehouse.scan.notFound', { code: raw }))
  } finally {
    scanBusy.value = false
  }
}

async function toggleExpand(m) {
  if (expandedId.value === m.id) {
    cancelForm()
  } else {
    expandedId.value = m.id
    localStep.value = 1
    generatedReceiptId.value = null
    resetStepValidation()
    initForm(m)
    applyDraftToForm(m.id)
    try {
      const [hawbRes, docsRes] = await Promise.all([
        hawbsApi.getByMawb(m.id),
        mawbsApi.getSupportingDocs(m.id).catch(() => ({ data: [] })),
      ])
      const hawbData = hawbRes.data
      receiptHawbs[m.id] = hawbData
      const f = receiptForms[m.id]
      if (f) {
        // Cargar evidencias del MAWB (solo lectura) en el formulario
        f.mawbEvidence = (docsRes.data || []).filter(d => d.type === 'image' || d.type === 'document')

        await loadExistingReceiptData(m)
        await preloadCalcConfig(m)
        bumpFormVersion()

        if (hawbData.length > 0) {
          const h0 = hawbData[0]
          if (!f.shipperName) f.shipperName = m.shipperName || h0?.shipperName || ''
          if (!f.consigneeName) f.consigneeName = m.consigneeName || (hawbData.length === 1 ? h0?.consigneeName : '') || ''
          // mawbWeightGreatest se auto-calculó desde scaleWeightLbs en loadExistingReceiptData o calcPiece
          if (f.awbReportedPieces == null) f.awbReportedPieces = m.pieces || hawbData.reduce((s, h) => s + (h.pieces || 0), 0) || 0
          const existingIds = new Set(f.hawbEntries.filter(e => e._dbId).map(e => e._dbId))
          for (const h of hawbData) {
            if (!existingIds.has(h.id)) {
              f.hawbEntries.push({
                hawbNumber: h.hawbNumber || '',
                consigneeName: h.consigneeName || '',
                pieces: h.pieces || 0,
                weightKg: h.weightKg ? Number(h.weightKg) : 0,
                destination: h.destination || f.destination || 'MIA',
                _dbId: h.id,
                _hawbId: h.id,
              })
            }
          }
          // Remove phantom default entries (no _dbId) when real DB HAWBs exist
          f.hawbEntries = f.hawbEntries.filter(e => e._dbId != null || f.hawbEntries.filter(e2 => e2._dbId != null).length === 0)
          f.hawbCount = f.hawbEntries.length
          // Build set of valid hawbIds from real entries
          const validHawbIds = new Set(f.hawbEntries.map(e => e._hawbId).filter(Boolean))
          // Remove orphaned pieces (hawbId doesn't match any real entry) that have no dimensions
          f.pieces = f.pieces.filter(p => validHawbIds.has(p.hawbId) || p.lengthIn || p.widthIn || p.heightIn || p.scaleWeightLbs)
          // Re-link any remaining orphaned pieces (with dimensions but stale hawbId) to first real entry
          for (const p of f.pieces) {
            if (!validHawbIds.has(p.hawbId)) {
              p.hawbId = f.hawbEntries[0]?._hawbId || null
            }
          }
        }
      }
    } catch (e) {
      toast.error(extractError(e))
      receiptHawbs[m.id] = []
    }
  }
}

function nextStep() {
  if (localStep.value >= 6) return
  goToStep(localStep.value + 1)
}
function prevStep() {
  if (localStep.value <= 1) return
  stepErrorMsg.value = ''
  localStep.value--
}

// Esc cierra primero el modal de confirmación / gestor de evidencias y si no, el wizard.
function onGlobalEsc() {
  if (showConfirmModal.value) { showConfirmModal.value = false; return }
  if (mawbEvidenceMgr.show) { closeMawbEvidenceMgr(); return }
  if (expandedId.value) cancelForm()
}

/* ══════════════════════════════════════════════════════════════════
   EVIDENCIAS — captura de foto/cámara + subida de archivos
   Objetivo: que la captura NUNCA falle y nunca deje estado zombi.
   - Una sola instancia de EvidenceCaptureModal vive en el template raíz
     (fuera de los v-if) → siempre por encima de cualquier overlay.
   - Toda mutación pasa por pushEvidence() con guardas + límites.
   ══════════════════════════════════════════════════════════════════ */

const MAX_EVIDENCE_DOCS = 24          // por formulario
const MAX_MAWB_EVIDENCE_DOCS = 24     // por MAWB
const MAX_DOC_CHARS = 1_400_000       // ~1.4 MB en base64

/** Contexto de la captura abierta: 'receipt' | 'mawb'. */
const capture = reactive({ open: false, target: 'receipt', mawbId: null })

function openReceiptCapture(mawbId) {
  if (!mawbId || !receiptForms[mawbId]) return
  capture.target = 'receipt'
  capture.mawbId = mawbId
  capture.open = true
}

function openMawbCapture() {
  const id = mawbEvidenceMgr.mawbId
  if (!id) {
    toast.error(t('warehouse.evidence.mawbRequired'))
    return
  }
  capture.target = 'mawb'
  capture.mawbId = id
  capture.open = true
}

/** Punto único de entrada de todo lo que llega desde EvidenceCaptureModal. */
function onEvidenceCaptured(payload) {
  if (!payload?.url) return
  if (capture.target === 'mawb') pushMawbEvidence(payload)
  else pushReceiptEvidence(capture.mawbId, payload)
}

function pushReceiptEvidence(mawbId, doc) {
  const form = receiptForms[mawbId]
  if (!form) return
  if (!doc || !doc.url) {
    console.warn('[pushReceiptEvidence] invalid doc:', doc)
    return
  }
  if (!Array.isArray(form.evidence)) form.evidence = []
  if (form.evidence.length >= MAX_EVIDENCE_DOCS) {
    toast.error(t('warehouse.evidence.limitReached', { max: MAX_EVIDENCE_DOCS }))
    return
  }
  if (typeof doc.url !== 'string' || doc.url.length > MAX_DOC_CHARS) {
    toast.error(t('warehouse.evidence.fileTooLarge'))
    return
  }
  form.evidence.push({
    name: doc.name || 'documento',
    type: doc.type || 'image',
    url: doc.url,
    file: null,
  })
}

function pushMawbEvidence(doc) {
  if (!doc || !doc.url) {
    console.warn('[pushMawbEvidence] invalid doc:', doc)
    return
  }
  if (!Array.isArray(mawbEvidenceMgr.docs)) mawbEvidenceMgr.docs = []
  if (mawbEvidenceMgr.docs.length >= MAX_MAWB_EVIDENCE_DOCS) {
    toast.error(t('warehouse.evidence.limitReached', { max: MAX_MAWB_EVIDENCE_DOCS }))
    return
  }
  if (typeof doc.url !== 'string' || doc.url.length > MAX_DOC_CHARS) {
    toast.error(t('warehouse.evidence.fileTooLarge'))
    return
  }
  mawbEvidenceMgr.docs.push({ name: doc.name || 'documento', type: doc.type || 'image', url: doc.url })
  mawbEvidenceMgr.dirty = true
}

/** Lectura + compresión compartida por ambas vías de archivo. */
function readAsDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = (ev) => resolve(ev.target.result)
    reader.onerror = () => reject(new Error('read-failed'))
    reader.readAsDataURL(file)
  })
}

function isPdfUrl(url) {
  if (!url) return false
  if (url.startsWith('data:application/pdf')) return true
  if (url.toLowerCase().endsWith('.pdf')) return true
  return false
}

async function prepareEvidenceFile(file) {
  const isImage = (file.type || '').startsWith('image/')
  const raw = await readAsDataUrl(file)
  if (!isImage) return { name: file.name, type: 'document', url: raw }
  let url = await compressImage(raw)
  if (url && url.length > MAX_DOC_CHARS) {
    url = await compressImage(raw, 900, 0.5)
  }
  if (!url || url === 'data:,') return null
  return { name: file.name, type: 'image', url }
}

/** Selector genérico de archivos (usado por "Subir archivo" en ambas zonas). */
function pickFiles({ accept, multiple = true, onFiles }) {
  const input = document.createElement('input')
  input.type = 'file'
  if (accept) input.accept = accept
  input.multiple = multiple
  input.style.cssText = 'position:fixed;left:-9999px;top:-9999px;width:0;height:0;opacity:0;pointer-events:none'
  const handler = async (e) => {
    input.removeEventListener('change', handler)
    input.remove()
    const files = e.target.files
    if (!files || !files.length) return
    try {
      for (const file of Array.from(files)) {
        const prepared = await prepareEvidenceFile(file)
        if (prepared) onFiles(prepared)
      }
    } catch {
      toast.error(t('warehouse.evidence.readError'))
    }
  }
  input.addEventListener('change', handler)
  document.body.appendChild(input)
  input.click()
}

function addEvidence(mawbId) {
  pickFiles({
    accept: 'image/*,.pdf',
    onFiles: (doc) => pushReceiptEvidence(mawbId, doc),
  })
}

function addMawbEvidenceFile() {
  pickFiles({
    accept: 'image/*,.pdf',
    onFiles: (doc) => pushMawbEvidence(doc),
  })
}

function compressImage(dataUrl, maxDim = 1280, quality = 0.72) {
  return new Promise(resolve => {
    const img = new Image()
    img.onload = () => {
      let w = img.width, h = img.height
      if (!w || !h) return resolve(dataUrl)
      if (w > maxDim || h > maxDim) {
        const s = Math.min(maxDim / w, maxDim / h, 1)
        w = Math.round(w * s)
        h = Math.round(h * s)
      }
      const c = document.createElement('canvas')
      c.width = w; c.height = h
      const ctx = c.getContext('2d')
      ctx.imageSmoothingQuality = 'high'
      ctx.drawImage(img, 0, 0, w, h)
      resolve(c.toDataURL('image/jpeg', quality))
    }
    img.onerror = () => resolve(dataUrl)
    img.src = dataUrl
  })
}

function removeEvidence(mawbId, idx) {
  const form = receiptForms[mawbId]
  if (!form || !Array.isArray(form.evidence)) return
  form.evidence.splice(idx, 1)
}

async function downloadReceiptById(m) {
  const id = receiptById.value[m.id]
  if (!id) return
  try {
    const res = await receiptsApi.export(id)
    const disposition = res.headers?.['content-disposition'] || ''
    const match = disposition.match(/filename\*?=(?:UTF-8'')?"?([^";\n]+)"?/)
    const filename = match ? match[1].trim() : `RECIBO_BODEGA_${id.slice(0, 8)}.xlsx`
    const url = URL.createObjectURL(new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }))
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    toast.error(extractError(e))
    console.error('Download error:', e)
  }
}

async function downloadReceiptByIdAuto(receiptId, awbNumber) {
  try {
    const res = await receiptsApi.export(receiptId)
    const filename = `RECIBO_DE_BODEGA_AWB ${awbNumber || receiptId.slice(0, 8)}${receiptVersionTag(receiptId)}.xlsx`
    const url = URL.createObjectURL(new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }))
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    console.warn('Auto-download Excel failed:', e)
  }
}

async function downloadReceiptPdfAuto(receiptId, awbNumber) {
  try {
    const res = await receiptsApi.getFullPdf(receiptId)
    const filename = `RECIBO_DE_BODEGA_AWB ${awbNumber || receiptId.slice(0, 8)}${receiptVersionTag(receiptId)}.pdf`
    const url = URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }))
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    console.warn('Auto-download PDF failed:', e)
  }
}

async function downloadHtmlById(m) {
  const id = receiptById.value[m.id]
  if (!id) return
  try {
    const res = await receiptsApi.getSupportingDocsHtml(id)
    const blob = new Blob([res.data], { type: 'text/html; charset=UTF-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `EVIDENCIAS_${id.slice(0, 8)}.html`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    toast.error(extractError(e))
    console.error('Supporting docs HTML error:', e)
  }
}

async function downloadPdfById(m) {
  const id = receiptById.value[m.id]
  if (!id) return
  try {
    const res = await receiptsApi.getSupportingDocsPdf(id)
    const blob = new Blob([res.data], { type: 'application/pdf' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `EVIDENCIAS_${id.slice(0, 8)}.pdf`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    toast.error(extractError(e))
    console.error('Supporting docs PDF error:', e)
  }
}

function openConfirmModal(m) {
  const f = receiptForms[m.id]
  if (!f) return
  f.pieces = f.pieces.filter(p => p.hawbId != null || p.lengthIn || p.widthIn || p.heightIn || p.scaleWeightLbs)
  // Validación completa de todos los pasos antes de confirmar
  for (let s = 0; s < 6; s++) touchedSteps[s] = true
  const bad = firstInvalidStep()
  if (bad !== -1) {
    const errs = validateStep(bad)
    stepErrorMsg.value = 'Paso ' + (bad + 1) + ': ' + errs[0]
    localStep.value = bad + 1
    toast.warning(stepErrorMsg.value)
    return
  }
  stepErrorMsg.value = ''
  pendingSubmitMawb.value = m
  showConfirmModal.value = true
}

async function confirmSubmit() {
  if (!pendingSubmitMawb.value) return
  showConfirmModal.value = false
  const m = pendingSubmitMawb.value
  pendingSubmitMawb.value = null
  await submitReceipt(m)
}

function formatCorrection(c) {
  const m = c.match(/(\d+)\s*→\s*(\d+)/)
  if (m) {
    return c.replace(m[0],
      `<span class="line-through opacity-50">${m[1]} skids</span> <span class="opacity-40 mx-1">&#8594;</span> <span class="font-bold text-amber-700">${m[2]} skids</span>`
    )
  }
  return c
}

function cancelBookingCorrection() {
  showBookingCorrectionModal.value = false
  pendingBookingCorrections.value = []
  pendingEmitPayload.value = null
  pendingEmitMawb.value = null
  pendingEmitHawbs.value = null
  submitting.value = false
}

async function confirmBookingCorrection() {
  showBookingCorrectionModal.value = false
  const payload = pendingEmitPayload.value
  const m = pendingEmitMawb.value
  const hawbs = pendingEmitHawbs.value
  pendingBookingCorrections.value = []
  pendingEmitPayload.value = null
  pendingEmitMawb.value = null
  pendingEmitHawbs.value = null
  try {
    await executeEmit(m, hawbs, payload)
  } catch (e) {
    submitting.value = false
    toast.error(extractError(e))
    const data = e.response?.data
    const msg = data?.error || data?.message || (typeof data === 'string' ? data : null) || e.message
    console.error('Booking correction emit error:', { error: e, responseData: data })
    toast.error('Error (' + (e.response?.status || '?') + '): ' + msg)
  }
}

// Contexto compartido por el guardado de borrador y la emisión: sincroniza los
// nombres MAWB/HAWB, asegura que cada HAWB del formulario exista en el backend
// (crea los faltantes y reasigna sus piezas) y devuelve `{ f, hawbs }`.
async function prepareReceiptContext(m) {
  const f = receiptForms[m.id]
  const hawbs = receiptHawbs[m.id] || []
  if (!f) return null
  console.warn('[Context] START', { mawbId: m.id, awbNumber: m.awbNumber, pieceCount: f.pieces.length, hawbCount: hawbs.length })
  try {
    if (f.shipperName && f.shipperName !== m.shipperName) {
      await mawbsApi.update(m.id, { shipperName: f.shipperName })
      m.shipperName = f.shipperName
    }
    if (f.consigneeName && f.consigneeName !== m.consigneeName) {
      await mawbsApi.update(m.id, { consigneeName: f.consigneeName })
      m.consigneeName = f.consigneeName
    }
    for (const h of hawbs) {
      if (h._dirty && h.id) {
        await hawbsApi.update(h.id, { consigneeName: h.consigneeName })
      }
    }
  } catch (nameErr) {
    console.warn('Non-critical: failed to sync MAWB/HAWB names', nameErr)
  }

  for (const entry of f.hawbEntries) {
    if (!entry._dbId && entry.hawbNumber && !hawbs.includes(entry)) {
      hawbs.push(entry)
    }
  }

  for (const h of hawbs) {
    if (!h._dbId && h.hawbNumber) {
      const oldHawbId = h._hawbId
      try {
        const res = await hawbsApi.create({
          mawbId: m.id,
          hawbNumber: h.hawbNumber,
          consigneeName: h.consigneeName || '',
          pieces: h.pieces || 1,
          weightKg: h.weightKg || 0,
          destination: h.destination || f.destination || 'MIA',
        })
        h._dbId = res.data.id
        h._hawbId = res.data.id
        for (const p of f.pieces) {
          if (p.hawbId === oldHawbId) {
            p.hawbId = res.data.id
          }
        }
      } catch (hawbErr) {
        console.warn('Failed to persist HAWB:', h.hawbNumber, hawbErr)
      }
    }
  }
  return { f, hawbs }
}

function buildReceiptPayload(m, f, hawbs, pieceList, remarkSuffix) {
  const hawbId = hawbs.length <= 1 ? (hawbs[0]?.id || hawbs[0]?._hawbId || null) : null
  const validHawbId = hawbId && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(hawbId) ? hawbId : null
  return {
    mawbId: m.id,
    airlineId: m.airline?.id || m.airlineId || null,
    hawbId: validHawbId,
    gatewayCfs: f.gatewayCfs || 'SDQ',
    shipperName: f.shipperName ?? m.shipperName ?? '',
    consigneeName: f.consigneeName ?? m.consigneeName ?? '',
    origin: f.origin || 'SDQ',
    destination: f.destination || 'MIA',
    awbReportedPieces: f.awbReportedPieces ?? pieceList.reduce((s, p) => s + (p.pieces || 1), 0),
    mawbWeightGreatest: f.mawbWeightGreatest || 0,
    dimFactorIntl: f.dimFactorKg || 366,
    dimFactorDom: f.dimFactorLbs || 194,
    chargeableMethod: f.chargeableMethod || 'MAX',
    roundUpKg: f.roundUpKg || 0,
    roundUpLbs: f.roundUpLbs || 0,
    minChargeableKg: f.minChargeableKg || 0,
    minChargeableLbs: f.minChargeableLbs || 0,
    pieceCount: pieceList.reduce((s, p) => s + (p.pieces || 1), 0),
    cashOnly: f.cashOnly || false,
    bookedInAcoms: f.bookedInAcoms || false,
    docsProvided: f.docsProvided || false,
    customsCompleted: f.customsCompleted || false,
    preBuilt: f.preBuilt || false,
    remarks: (f.remarks || '').replace(/ — RECIBO GENERAL(?:\s*— RECIBO GENERAL)*$/, '') + (remarkSuffix ? ' — ' + remarkSuffix : ''),
    dockSignature: f.dockSignature || '',
    printName: f.printName || '',
    deliveredByName: f.deliveredByName || '',
    deliveredByIdNum: f.deliveredByIdNum || '',
    deliveredBySigUrl: f.deliveredBySig || '',
    receivedByName: f.receivedByName || f.printName || '',
    receivedBySigUrl: f.receivedBySig || '',
    brokerName: f.brokerName || '',
    brokerIdNum: f.brokerIdNum || '',
    brokerSigUrl: f.brokerSig || '',
    driverSignatures: JSON.stringify((f.driverSignatures || []).filter(d =>
      (d.name && String(d.name).trim()) || (d.idNum && String(d.idNum).trim())
      || (d.truckPlate && String(d.truckPlate).trim()) || d.sig)),
    operationalChecks: JSON.stringify(f.operationalChecks || {}),
    coldChain: JSON.stringify(f.coldChain || {}),
    duaNumber: f.duaNumber || '',
    // Cada función opcional solo viaja al backend cuando SU ajuste está activo,
    // así un recibo nunca guarda datos que el operador no puede ver/editar.
    ...Object.fromEntries(OPTIONAL_RECEIPT_FUNCTIONS.map(fn => [
      fn.key, isOptionalOn(fn) ? (f[fn.key] || '') : '',
    ])),
    receivedTempC: tempValueOrNull(f.coldChain?.receivedTempC),
    coldChainOk: coldChainOkFlag(m),
    startDatetime: f.startDatetime || new Date().toISOString(),
    receiptDate: f.receiptDate || new Date().toISOString(),
    pieces: pieceList.map((p, i) => ({
      pieceNumber: i + 1,
      pieces: p.pieces || 1,
      hawbId: (p.hawbId && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(p.hawbId)) ? p.hawbId : null,
      lengthIn: p.lengthIn || 0,
      widthIn: p.widthIn || 0,
      heightIn: p.heightIn || 0,
      scaleWeightLbs: p.scaleWeightLbs || 0,
      scaleWeightKg: p.scaleWeightKg || 0,
      dimWeightLbs: p.dimWeightLbs || 0,
      dimWeightKg: p.dimWeightKg || 0,
      chargeableLbs: p.chargeableLbs || 0,
      chargeableKg: p.chargeableKg || 0,
    })),
    supportingDocs: JSON.stringify(f.evidence.map(ev => ({
      name: ev.name,
      type: ev.type,
      url: ev.url,
    }))),
  }
}

async function submitReceipt(m) {
  if (submitting.value) return
  const f = receiptForms[m.id]
  if (!f) return
  f.pieces = f.pieces.filter(p => p.hawbId != null || p.lengthIn || p.widthIn || p.heightIn || p.scaleWeightLbs)
  if (f.pieces.length === 0) {
    toast.warning('Ingresa al menos una pieza')
    return
  }
  if (receiptById.value[m.id]) {
    if (!(await confirm({ message: 'Este MAWB ya tiene un recibo activo. Emitir uno nuevo reemplazara el recibo existente. ¿Continuar?', danger: true }))) {
      return
    }
  }
  submitting.value = true
  try {
    const ctx = await prepareReceiptContext(m)
    if (!ctx) return
    const { f: cf, hawbs } = ctx
    const payload = hawbs.length <= 1
      ? buildReceiptPayload(m, cf, hawbs, cf.pieces, '')
      : buildReceiptPayload(m, cf, hawbs, cf.pieces, 'RECIBO GENERAL')

    // ─── VALIDATE FIRST (dry-run) ────────────────────────
    const newPieceCount = cf.pieces.reduce((s, p) => s + (p.pieces || 1), 0)
    try {
      const valRes = await receiptsApi.validate({
        mawbId: m.id,
        pieces: [{ pieces: newPieceCount }],
      })
      const corrections = valRes.data?.corrections || []
      if (corrections.length > 0) {
        // Show booking correction modal — do NOT emit yet
        pendingBookingCorrections.value = corrections
        pendingEmitPayload.value = payload
        pendingEmitMawb.value = m
        pendingEmitHawbs.value = hawbs
        showBookingCorrectionModal.value = true
        return
      }
    } catch (valErr) {
      console.warn('Validation failed, proceeding with emit:', valErr)
    }

    // ─── NO CORRECTIONS → EMIT DIRECTLY ──────────────────
    await executeEmit(m, hawbs, payload)
  } catch (e) {
    toast.error(extractError(e))
    const data = e.response?.data
    const msg = data?.error || data?.message || (typeof data === 'string' ? data : null) || e.message
    console.error('Receipt submit error:', { error: e, responseData: data })
    toast.error('Error (' + e.response?.status + '): ' + msg)
  } finally {
    submitting.value = false
  }
}

// Guarda el estado actual como borrador en el servidor (NO emite recibo ni
// genera artefactos). Mantiene el formulario abierto y el autoguardado local;
// permite terminar la recepción después, incluso desde otro usuario/dispositivo.
async function submitDraft(m) {
  if (draftSaving.value || submitting.value) return
  const f = receiptForms[m.id]
  if (!f) return
  f.pieces = f.pieces.filter(p => p.hawbId != null || p.lengthIn || p.widthIn || p.heightIn || p.scaleWeightLbs)
  if (f.pieces.length === 0) {
    toast.warning('Ingresa al menos una pieza')
    return
  }
  draftSaving.value = true
  try {
    const ctx = await prepareReceiptContext(m)
    if (!ctx) return
    const { f: cf, hawbs } = ctx
    const payload = {
      ...buildReceiptPayload(m, cf, hawbs, cf.pieces, hawbs.length > 1 ? 'RECIBO GENERAL' : ''),
      status: 'DRAFT',
    }
    await store.saveReceiptDraft(payload)
    await store.loadReceipts()
    toast.success(t('warehouse.draft.saved'))
  } catch (e) {
    toast.error(extractError(e))
  } finally {
    draftSaving.value = false
  }
}

async function executeEmit(m, hawbs, payload) {
  const f = receiptForms[m.id]
  if (!f) return

  const res = await store.emitReceipt(payload)
  const receiptId = res?.id || null
  if (receiptId) generatedReceiptId.value = receiptId

  await store.loadReceipts()
  await loadExistingReceiptData(m)
  bumpFormVersion()
  localStep.value = 6
  clearDraft(m.id)

  // Universo completo — ver nota en saveChanges (loadAllMawbs siempre).
  await store.loadAllMawbs()
  const totalKg = (receiptForms[m.id]?.pieces || []).reduce((s, p) => s + (p.scaleWeightKg || 0), 0)
  const totalLbs = (receiptForms[m.id]?.pieces || []).reduce((s, p) => s + (p.scaleWeightLbs || 0), 0)
  const chargeKg = (receiptForms[m.id]?.pieces || []).reduce((s, p) => s + Math.max(p.dimWeightKg || 0, p.scaleWeightKg || 0), 0)
  successMsg.value = 'Recibo generado' +
    ` — ${totalPieces(m.id, null)} pzas, ${totalKg.toFixed(1)} KGS / ${totalLbs.toFixed(1)} LBS (facturable: ${chargeKg.toFixed(1)} KGS)`
  if (successTimer) clearTimeout(successTimer)
  successTimer = setTimeout(() => { successMsg.value = '' }, 6000)

  let downloadId = receiptById.value[m.id] || generatedReceiptId.value
  if (downloadId) {
    setTimeout(() => downloadReceiptByIdAuto(downloadId, m.awbNumber), 1500)
    setTimeout(() => downloadReceiptPdfAuto(downloadId, m.awbNumber), 3000)
  }
}

const receiptTotals = computed(() => {
  const totals = {}
  for (const r of store.receipts || []) {
    if (r.superseded || isDraftReceipt(r)) continue
    const mawbId = r.mawb?.id || r.mawbId
    if (!mawbId) continue
    if (!totals[mawbId]) totals[mawbId] = { pieces: 0, weightKg: 0, weightLbs: 0 }
    const pc = r.pieceCount || 0
    totals[mawbId].pieces += pc
    const wk = Number(r.actualWeightKg ?? r.chargeableWeightKg ?? 0)
    totals[mawbId].weightKg += wk
    const wl = Number(r.actualWeightLbs ?? r.chargeableWeightLbs ?? 0)
    totals[mawbId].weightLbs += wl
  }
  return totals
})

const attentionQueue = computed(() => {
  const flightMap = {}
  for (const f of store.flights) flightMap[f.id] = f
  const seen = new Set()
  const items = []
  for (const m of store.mawbs) {
    if (seen.has(m.id)) continue
    const st = mawbOperationalStatusMap.value[m.id]
    // Incluye: CARGAda/EN_PROCESO con recibo incompleto, PENDIENTE sin recibo
    const hasReceipt = activeReceiptForMawb(m, store.receipts) && !isDraftReceipt(activeReceiptForMawb(m, store.receipts)) && !isReceiptComplete(m)
    if ((st === 'CARGADA' || st === 'EN_PROCESO') && !hasReceipt) {
      seen.add(m.id)
      items.push({ mawb: m, flight: flightMap[m.flightId] || null, whyKey: st === 'EN_PROCESO' ? 'warehouse.attention.whyPartial' : 'warehouse.attention.whyLoaded' })
    }
    if (st === 'PENDIENTE' && !hasReceipt) {
      seen.add(m.id)
      items.push({ mawb: m, flight: flightMap[m.flightId] || null, whyKey: 'warehouse.attention.whyNoReceipt' })
    }
  }
  return items
})

const receiptById = computed(() => {
  const map = {}
  for (const r of store.receipts || []) {
    if (r.superseded || isDraftReceipt(r)) continue
    const mawbId = r.mawb?.id || r.mawbId
    if (mawbId) map[mawbId] = r.id
  }
  return map
})

// Borrador vigente (no superseded, status DRAFT) de una MAWB (máx. 1 por MAWB)
function draftForMawb(m) {
  return draftReceiptForMawb(m, store.receipts)
}

function receiptVersionTag(receiptId) {
  const r = (store.receipts || []).find(x => x.id === receiptId)
  const v = r?.correctionNumber ?? 1
  return '-V' + v
}

// ── Evidence Preview ──
const evidencePreview = reactive({ show: false, item: null })

function previewEvidence(item) {
  evidencePreview.item = item
  evidencePreview.show = true
}

function closeEvidencePreview() {
  evidencePreview.show = false
  evidencePreview.item = null
}

function downloadEvidenceItem(item) {
  if (!item || !item.url) return
  const isPdf = isPdfUrl(item.url)
  const mime = isPdf ? 'application/pdf' : (item.type === 'image' ? 'image/png' : 'application/octet-stream')
  const blob = dataUriToBlob(item.url, mime)
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = item.name || (isPdf ? 'documento.pdf' : 'imagen.png')
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

function dataUriToBlob(dataUri, mimeType) {
  const byteString = atob(dataUri.split(',')[1])
  const ab = new ArrayBuffer(byteString.length)
  const ia = new Uint8Array(ab)
  for (let i = 0; i < byteString.length; i++) ia[i] = byteString.charCodeAt(i)
  return new Blob([ab], { type: mimeType })
}

// ── MAWB Evidence Manager ──
const mawbEvidenceMgr = reactive({ show: false, mawbId: null, mawb: null, docs: [], dirty: false })

async function toggleMawbEvidenceManager(m) {
  if (mawbEvidenceMgr.show && mawbEvidenceMgr.mawbId === m.id) {
    await closeMawbEvidenceMgr()
    return
  }
  mawbEvidenceMgr.show = true
  mawbEvidenceMgr.mawbId = m.id
  mawbEvidenceMgr.mawb = m
  mawbEvidenceMgr.dirty = false
  try {
    const res = await mawbsApi.getSupportingDocs(m.id)
    mawbEvidenceMgr.docs = res.data || []
  } catch (e) {
    toast.error(extractError(e))
    mawbEvidenceMgr.docs = []
  }
}

async function closeMawbEvidenceMgr() {
  if (mawbEvidenceMgr.dirty) {
    if (!(await confirm({ message: 'Hay cambios sin guardar. ¿Descartar cambios?' }))) return
  }
  mawbEvidenceMgr.show = false
  mawbEvidenceMgr.mawbId = null
  mawbEvidenceMgr.mawb = null
  mawbEvidenceMgr.docs = []
  mawbEvidenceMgr.dirty = false
}

function removeMawbEvidence(idx) {
  mawbEvidenceMgr.docs.splice(idx, 1)
  mawbEvidenceMgr.dirty = true
}

async function saveMawbEvidence() {
  const id = mawbEvidenceMgr.mawbId
  if (!id) return
  try {
    const docsToSave = mawbEvidenceMgr.docs.map(d => ({ name: d.name, type: d.type, url: d.url }))
    await mawbsApi.updateSupportingDocs(id, docsToSave)
    mawbEvidenceMgr.dirty = false
    toast.success('Evidencias guardadas correctamente')
  } catch (e) {
    toast.error(extractError(e))
  }
}

async function downloadMawbEvidencePdf() {
  const id = mawbEvidenceMgr.mawbId
  if (!id || mawbEvidenceMgr.docs.length === 0) return
  try {
    const res = await mawbsApi.getSupportingDocsPdf(id)
    const blob = new Blob([res.data], { type: 'application/pdf' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `EVIDENCIAS_MAWB_${mawbEvidenceMgr.mawb?.awbNumber || id.slice(0, 8)}.pdf`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    toast.error(extractError(e))
    console.error('MAWB evidence PDF error:', e)
    toast.error('Error descargando PDF de evidencias')
  }
}

// ── Print Preview ──
async function printPreview(m) {
  const f = receiptForms[m.id]
  if (!f) return
  const receiptId = receiptById.value[m.id] || generatedReceiptId.value
  if (!receiptId) {
    toast.error('Primero confirma el recibo antes de imprimir')
    return
  }
  try {
    const res = await receiptsApi.getFullPdf(receiptId)
    const blob = new Blob([res.data], { type: 'application/pdf' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `RECIBO_DE_BODEGA_AWB ${m.awbNumber || receiptId.slice(0, 8)}${receiptVersionTag(receiptId)}.pdf`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    toast.error('Error generando PDF: ' + (e.response?.data?.error || e.message))
    console.error('Print preview PDF error:', e)
  }
}

onMounted(async () => {
  // Enabled reserved fields come from Settings; load them before the first form opens.
  await loadReceiptFormConfig()
  if (!store.airlines.length) await store.loadAirlines()
  if (!store.flights.length) await store.loadFlights()
  await store.loadReceipts()
  await Promise.all([store.loadUldAwbs(), store.loadAllMawbs(), store.loadUlds(), store.loadBookings()])
  if (route.query.mawbId && store.mawbs.length) {
    const m = store.mawbs.find(x => x.id === route.query.mawbId)
    if (m) {
      expandedId.value = m.id
      initForm(m)
      try {
        const [hawbRes, docsRes] = await Promise.all([
          hawbsApi.getByMawb(m.id),
          mawbsApi.getSupportingDocs(m.id).catch(() => ({ data: [] })),
        ])
        receiptHawbs[m.id] = hawbRes.data
        const f = receiptForms[m.id]
        if (f) {
          f.mawbEvidence = (docsRes.data || []).filter(d => d.type === 'image' || d.type === 'document')
          await loadExistingReceiptData(m)
          bumpFormVersion()
          const hawbData = hawbRes.data
          if (hawbData.length > 0) {
            const h0 = hawbData[0]
            if (!f.shipperName) f.shipperName = m.shipperName || h0?.shipperName || ''
            if (!f.consigneeName) f.consigneeName = m.consigneeName || (hawbData.length === 1 ? h0?.consigneeName : '') || ''
            if (f.awbReportedPieces == null) f.awbReportedPieces = m.pieces || hawbData.reduce((s, h) => s + (h.pieces || 0), 0) || 0
            const existingIds = new Set(f.hawbEntries.filter(e => e._dbId).map(e => e._dbId))
            for (const h of hawbData) {
              if (!existingIds.has(h.id)) {
                f.hawbEntries.push({
                  hawbNumber: h.hawbNumber || '',
                  consigneeName: h.consigneeName || '',
                  pieces: h.pieces || 0,
                  weightKg: h.weightKg ? Number(h.weightKg) : 0,
                  destination: h.destination || f.destination || 'MIA',
                  _dbId: h.id,
                  _hawbId: h.id,
                })
              }
            }
            f.hawbEntries = f.hawbEntries.filter(e => e._dbId != null || f.hawbEntries.filter(e2 => e2._dbId != null).length === 0)
            f.hawbCount = f.hawbEntries.length
            if (!f._piecesLoadedFromDb) {
              const validHawbIds = new Set(f.hawbEntries.map(e => e._hawbId).filter(Boolean))
              f.pieces = f.pieces.filter(p => validHawbIds.has(p.hawbId) || p.lengthIn || p.widthIn || p.heightIn || p.scaleWeightLbs)
              for (const p of f.pieces) {
                if (!validHawbIds.has(p.hawbId)) {
                  p.hawbId = f.hawbEntries[0]?._hawbId || null
                }
              }
            }
          }
        }
      } catch (e) {
        toast.error(extractError(e))
        receiptHawbs[m.id] = []
      }
    }
  } else if (expandedId.value && store.mawbs.length) {
    const m = store.mawbs.find(x => x.id === expandedId.value)
    if (m) {
      initForm(m)
      try {
        const [hawbRes, docsRes] = await Promise.all([
          hawbsApi.getByMawb(m.id),
          mawbsApi.getSupportingDocs(m.id).catch(() => ({ data: [] })),
        ])
        receiptHawbs[m.id] = hawbRes.data
        const f = receiptForms[m.id]
        if (f) {
          f.mawbEvidence = (docsRes.data || []).filter(d => d.type === 'image' || d.type === 'document')
          await loadExistingReceiptData(m)
          bumpFormVersion()
          const hawbData = hawbRes.data
          if (hawbData.length > 0) {
            const h0 = hawbData[0]
            if (!f.shipperName) f.shipperName = m.shipperName || h0?.shipperName || ''
            if (!f.consigneeName) f.consigneeName = m.consigneeName || (hawbData.length === 1 ? h0?.consigneeName : '') || ''
            if (f.awbReportedPieces == null) f.awbReportedPieces = m.pieces || hawbData.reduce((s, h) => s + (h.pieces || 0), 0) || 0
            const existingIds = new Set(f.hawbEntries.filter(e => e._dbId).map(e => e._dbId))
            for (const h of hawbData) {
              if (!existingIds.has(h.id)) {
                f.hawbEntries.push({
                  hawbNumber: h.hawbNumber || '',
                  consigneeName: h.consigneeName || '',
                  pieces: h.pieces || 0,
                  weightKg: h.weightKg ? Number(h.weightKg) : 0,
                  destination: h.destination || f.destination || 'MIA',
                  _dbId: h.id,
                  _hawbId: h.id,
                })
              }
            }
            f.hawbEntries = f.hawbEntries.filter(e => e._dbId != null || f.hawbEntries.filter(e2 => e2._dbId != null).length === 0)
            f.hawbCount = f.hawbEntries.length
            if (!f._piecesLoadedFromDb) {
              const validHawbIds = new Set(f.hawbEntries.map(e => e._hawbId).filter(Boolean))
              f.pieces = f.pieces.filter(p => validHawbIds.has(p.hawbId) || p.lengthIn || p.widthIn || p.heightIn || p.scaleWeightLbs)
              for (const p of f.pieces) {
                if (!validHawbIds.has(p.hawbId)) {
                  p.hawbId = f.hawbEntries[0]?._hawbId || null
                }
              }
            }
          }
        }
      } catch (e) {
        toast.error(extractError(e))
        receiptHawbs[m.id] = []
      }
    }
  }

  if (route.query.addHawb === '1' && store.mawbs.length) {
    const persisted = localStorage.getItem('WAREHOUSE_EXPANDED_MAWB')
    const target =
      (expandedId.value && store.mawbs.find(x => x.id === expandedId.value)) ||
      (persisted && store.mawbs.find(x => x.id === persisted)) ||
      store.mawbs[0]
    if (target) {
      expandedId.value = target.id
      if (!receiptForms[target.id]) initForm(target)
      addHawbEntry(target)
      bumpFormVersion()
    }
  }
})

// Esc global handler for wizard / confirm modal / evidence manager
function _onGlobalKeydown(e) { if (e.key === 'Escape') onGlobalEsc() }
onMounted(() => window.addEventListener('keydown', _onGlobalKeydown))
onUnmounted(() => window.removeEventListener('keydown', _onGlobalKeydown))

watch(expandedId, async (id) => {
  if (id) {
    localStorage.setItem('WAREHOUSE_EXPANDED_MAWB', id)
    const m = store.mawbs.find(x => x.id === id)
    if (m) {
      initForm(m)
      try {
        const [hawbRes, docsRes] = await Promise.all([
          hawbsApi.getByMawb(id),
          mawbsApi.getSupportingDocs(id).catch(() => ({ data: [] })),
        ])
        receiptHawbs[id] = hawbRes.data
        const f = receiptForms[id]
        if (f) {
          f.mawbEvidence = (docsRes.data || []).filter(d => d.type === 'image' || d.type === 'document')
          await loadExistingReceiptData(m)
          await preloadCalcConfig(m)
          bumpFormVersion()
        }
      } catch (e) {
        toast.error(extractError(e))
        receiptHawbs[id] = []
      }
    }
  } else {
    localStorage.removeItem('WAREHOUSE_EXPANDED_MAWB')
  }
})

const statusSnapshot = ref({})
const rowFlash = reactive(new Set())
const flashing = new Set()

function takeStatusSnapshot() {
  const snap = {}
  for (const m of store.mawbs) {
    const a = m.awbNumber || m.id
    if (!a) continue
    snap[a] = {
      st: deriveMawbOperationalStatus(m),
      pcs: receiptTotals.value[m.id]?.pieces || 0,
      kg: Number(receiptTotals.value[m.id]?.weightKg || 0),
    }
  }
  return snap
}
function flashRow(id) {
  if (!id || flashing.has(id)) return
  flashing.add(id)
  rowFlash.add(id)
  setTimeout(() => {
    flashing.delete(id)
    rowFlash.delete(id)
  }, 900)
}
function flashChangedRows(prev) {
  for (const m of store.mawbs) {
    const a = m.awbNumber || m.id
    if (!a) continue
    const old = prev[a]
    if (!old) { flashRow(m.id); continue }
    const cur = {
      st: deriveMawbOperationalStatus(m),
      pcs: receiptTotals.value[m.id]?.pieces || 0,
      kg: Number(receiptTotals.value[m.id]?.weightKg || 0),
    }
    if (old.st !== cur.st || old.pcs !== cur.pcs || old.kg !== cur.kg) flashRow(m.id)
  }
}
async function refreshData() {
  const prev = statusSnapshot.value
  lastRefreshMs.value = Date.now()
  await Promise.all([
    store.loadReceipts({ silent: true }),
    store.loadUldAwbs({ silent: true }),
    store.loadAllMawbs({ silent: true }),
    store.loadBookings(undefined, { silent: true }),
  ])
  statusSnapshot.value = takeStatusSnapshot()
  flashChangedRows(prev)
}

useLiveRefresh(refreshData,
{ interval: 45000, pauses: [submitting, showConfirmModal, showBookingCorrectionModal, () => capture.open, () => !!expandedId.value] })
</script>

<style scoped>
.thin-scrollbar::-webkit-scrollbar { width: 4px; }
.thin-scrollbar::-webkit-scrollbar-track { background: transparent; }
.thin-scrollbar::-webkit-scrollbar-thumb { background: #94a3b8; border-radius: 2px; }
.thin-scrollbar::-webkit-scrollbar-thumb:hover { background: #64748b; }
.thin-scrollbar { scrollbar-width: thin; scrollbar-color: #94a3b8 transparent; }
.overscroll-contain { overscroll-behavior: contain; }

/* Status badge colors — soft tokens (theme + accent aware) */
.st-pendiente { background: var(--surface-3, #f1f5f9); border-color: var(--border-2, #cbd5e1); color: var(--text-2, #475569); }
.st-pendiente .cir { background: var(--text-3, #94a3b8); }
.st-recibida { background: var(--warn-soft, #fef3c7); border-color: var(--warn, #f59e0b); color: var(--warn, #92400e); }
.st-recibida .cir { background: var(--warn, #f59e0b); }
.st-manifestada { background: var(--ok-soft, #d1fae5); border-color: var(--ok, #10b981); color: var(--ok, #065f46); }
.st-manifestada .cir { background: var(--ok, #10b981); }
.st-en-proceso { background: var(--warn-soft, #fef3c7); border-color: var(--warn, #f59e0b); color: var(--warn, #92400e); }
.st-en-proceso .cir { background: var(--warn, #f59e0b); }
.st-despachada { background: var(--info-soft, #dbeafe); border-color: var(--info, #3b82f6); color: var(--info, #1e40af); }
.st-despachada .cir { background: var(--info, #3b82f6); }
.st-cargada { background: var(--danger-soft, #fee2e2); border-color: var(--danger, #ef4444); color: var(--danger, #991b1b); }
.st-cargada .cir { background: var(--danger, #ef4444); }

/* ── Selector de vista (maestro / por vuelo) ── */
.vm-pill { display: inline-flex; align-items: center; gap: 6px; padding: 4px 12px; border: 1px solid transparent;
  border-radius: 999px; background: transparent; color: var(--muted, #64748b); font-size: 11px; font-weight: 700;
  letter-spacing: .02em; cursor: pointer; transition: all .12s; }
.vm-pill:hover { background: color-mix(in srgb, var(--accent) 8%, transparent); color: var(--accent); }
.vm-pill.on { background: var(--accent); border-color: transparent; color: #fff;
  box-shadow: 0 4px 10px color-mix(in srgb, var(--accent) 35%, transparent); }

/* ── Chip de frescura + botón nuevo recibo (page-head) ── */
.vm-chip { display: inline-flex; align-items: center; gap: 6px; padding: 3px 12px; border: 1px solid var(--border-2, #e2e8f0);
  border-radius: 999px; background: var(--surface-3, #f1f5f9); color: var(--text-2, #475569); font-size: 11px; font-weight: 700;
  letter-spacing: .02em; white-space: nowrap; }
.wrap-actions { flex-wrap: wrap; }

/* ── Cabecera de grupo por vuelo (fila 0 del bloque) ── */
.fl-group-hd { display: flex; align-items: center; gap: 10px; padding: 6px 12px; cursor: pointer;
  background: color-mix(in srgb, var(--accent) 6%, transparent);
  border-top: 1px solid var(--border-2, #e2e8f0); border-bottom: 1px solid var(--border-2, #e2e8f0);
  }
    /* Tablet: header adaptativo, sin min-width fijo */
.fl-group-hd:hover { background: color-mix(in srgb, var(--accent) 11%, transparent); }
.fl-group-hd.closed { background: color-mix(in srgb, var(--accent) 3%, transparent); }
.fl-group-hd.closed .fl-caret { color: #64748b; }
.fl-chip { display: inline-flex; align-items: center; gap: 6px; width: 148px; font-family: var(--font-family-mono, monospace);
  font-size: 11px; font-weight: 700; color: var(--text, #0f172a); letter-spacing: .01em; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.fl-sub { flex: 0 0 auto; font-size: 10px; font-weight: 600; color: var(--muted, #64748b); white-space: nowrap; }
.fl-count { display: inline-flex; align-items: center; justify-content: center; min-width: 34px; padding: 0 8px;
  border-radius: 999px; background: var(--accent-soft, rgba(37, 99, 235, .14)); color: var(--accent, #2563eb);
  font-size: 11px; font-weight: 800; font-family: var(--font-family-mono, monospace); }
.fl-caret { display: inline-flex; align-items: center; justify-content: center; width: 18px; font-size: 10px; color: var(--accent); }

/* ── Acciones en fila ── */
.mini-act { display: inline-flex; align-items: center; justify-content: center; width: 24px; height: 24px;
  border: 1px solid var(--border-2, #e2e8f0); border-radius: 7px; background: var(--surface, #fff); color: var(--muted, #64748b);
  font-size: 12px; cursor: pointer; transition: all .12s; }
.mini-act:hover { border-color: var(--accent); color: var(--accent); background: var(--accent-soft, rgba(37, 99, 235, .06)); }
.mini-act.act-edit-has { border-color: color-mix(in srgb, var(--warn) 55%, transparent); color: var(--warn); background: var(--warn-soft, #fffbeb); }
.mini-act.act-edit-has:hover { border-color: var(--warn); color: #fff; background: var(--warn); }
.mini-act.act-draft { width: auto; padding: 0 8px; gap: 4px; font-size: 10px; font-weight: 800; text-transform: uppercase;
  letter-spacing: .03em; border-color: color-mix(in srgb, var(--warn) 55%, transparent); color: var(--warn); background: var(--warn-soft, #fffbeb); }
.mini-act.act-draft:hover { border-color: var(--warn); color: #fff; background: var(--warn); }
.badge-draft { display: inline-flex; align-items: center; gap: 4px; padding: 1px 6px; border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--warn) 55%, transparent); background: var(--warn-soft, #fffbeb);
  color: var(--warn); font-size: 10px; font-weight: 800; text-transform: uppercase; letter-spacing: .03em; line-height: 1.5; }
.btn-rec { display: inline-flex; align-items: center; gap: 5px; padding: 3px 12px; border: 1px solid var(--accent);
  border-radius: 999px; background: var(--accent); color: #fff; font-size: 10px; font-weight: 800;
  text-transform: uppercase; letter-spacing: .04em; cursor: pointer; transition: all .12s; }
.btn-rec:hover { filter: brightness(1.08); box-shadow: 0 3px 8px color-mix(in srgb, var(--accent) 35%, transparent); }

/* ── Etiquetas de estado de acción ── */
.tag-final { display: inline-flex; align-items: center; gap: 4px; padding: 2px 8px; border-radius: 999px;
  background: var(--ok-soft, #d1fae5); border: 1px solid var(--ok, #34d399); color: var(--ok, #065f46); font-size: 9px; font-weight: 800;
  text-transform: uppercase; letter-spacing: .04em; }
.tag-neutral { display: inline-flex; align-items: center; gap: 4px; padding: 2px 8px; border-radius: 999px;
  background: var(--surface-3, #f1f5f9); border: 1px solid var(--border-2, #cbd5e1); color: var(--text-2, #475569); font-size: 9px; font-weight: 800;
  text-transform: uppercase; letter-spacing: .04em; }
.tag-warn { display: inline-flex; align-items: center; gap: 4px; padding: 2px 8px; border-radius: 999px;
  background: var(--warn-soft, #fffbeb); border: 1px solid color-mix(in srgb, var(--warn) 55%, transparent); color: var(--warn, #92400e); font-size: 9px; font-weight: 800;
  text-transform: uppercase; letter-spacing: .04em; white-space: nowrap; }

/* ── Destello de fila al refrescar ── */
.row-flash { animation: row-flash 900ms ease-out; }
@keyframes row-flash {
  0% { background-color: var(--warn-soft, #fef3c7); }
  60% { background-color: var(--warn-soft, #fef3c7); }
  100% { background-color: transparent; }
}

/* ── Cola "Por atender" (s1) ── */
.exq { border-left: 3px solid var(--warn); border-right: 1px solid var(--border-2, #e2e8f0);
  border-top: 1px solid var(--border-2, #e2e8f0); border-bottom: 1px solid var(--border-2, #e2e8f0);
  background: var(--surface, #fff); box-shadow: var(--shadow-xs, 0 1px 2px rgba(15, 23, 42, .05)); }
.exq-hd { background: var(--warn-soft, #fffbeb); }
.exq-hd:hover { background: color-mix(in srgb, var(--warn) 8%, var(--surface, #fff)); }
.exq-n { margin: 0 2px; padding: 0 8px; border-radius: 999px; background: var(--warn); color: #fff;
  font-size: 11px; font-weight: 800; line-height: 1.55; }
.exq-go { border: 1px solid color-mix(in srgb, var(--warn) 55%, transparent); background: var(--warn-soft, #fffbeb); color: var(--warn); transition: all .12s; }
.exq-go:hover { background: var(--warn); border-color: var(--warn); color: #fff; }
.exq-row { border-top: 1px solid var(--border-2, #e2e8f0); background: var(--surface-2, #fffdf8); }
.exq-row:hover { background: color-mix(in srgb, var(--warn) 6%, var(--surface, #fff)); }

/* ── Formulario de recibo: distribución vertical compacta y responsiva.
   La altura se adapta al viewport (dvh, funciona en móvil con barras de
   navegador) pero nunca se vuelve minúscula ni se estira a toda la pantalla. */
.receipt-form { --receipt-form-h: 50dvh; }
@media (min-width: 768px) { .receipt-form { --receipt-form-h: 45dvh; } }
@media (min-height: 800px) { .receipt-form { --receipt-form-h: 56dvh; } }

/* ── Responsive móvil: la lista MAWB principal fuerza min-width:700px con
   scroll horizontal. En <=640px dejamos que el grid colapse (wrap) para
   mostrar las columnas esenciales apiladas, y ocultamos las secundarias. */
@media (max-width: 640px) {
  .receipt-list-header { min-width: 0; }
  .receipt-list-cell[data-col="shipper"],
  .receipt-list-cell[data-col="dest"],
  .receipt-list-cell[data-col="docs"],
  .receipt-list-cell[data-col="flight"] { display: none !important; }
  .receipt-list-cell { font-size: 11px; }
}

/* ── OPS modal wizard (OPS-RCA-v6) ─────────────────────────────── */
.ops-overlay { position: fixed; inset: 0; z-index: 60;
  background: color-mix(in srgb, var(--shadow-tint, #0f172a) 55%, transparent);
  -webkit-backdrop-filter: blur(3px); backdrop-filter: blur(3px);
  display: flex; align-items: center; justify-content: center; padding: 18px; }
.ops-modal { width: min(940px,100%); height: auto; max-width: calc(100vw - 36px); max-height: calc(100dvh - 36px);
  min-height: 380px; display: flex; flex-direction: column;
  background: var(--surface-2, #f6f8fd); border: 1px solid var(--border, #e2e8f0);
  border-top: 3px solid var(--accent, #3e7bfa);
  border-radius: 12px; box-shadow: var(--shadow-lg, 0 24px 60px -24px rgba(15,23,42,.35));
  position: relative; overflow: hidden; resize: both; }
.ops-modal::after { content: ''; position: absolute; right: 3px; bottom: 3px; width: 14px; height: 14px;
  background: linear-gradient(135deg, transparent 0, transparent 5px, var(--border-strong, #cbd5e1) 5px, var(--border-strong, #cbd5e1) 7px, transparent 7px, transparent 12px, var(--border-strong, #cbd5e1) 12px, var(--border-strong, #cbd5e1) 14px);
  opacity: .6; pointer-events: none; }

.ops-hdr { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px 18px;
  background: var(--surface, #fff); border-bottom: 1px solid var(--border, #e2e8f0); }
.ops-tt { font-size: 15px; font-weight: 800; color: var(--text, #0f172a); line-height: 1.2; }
.ops-sub { font-size: 11px; font-family: var(--font-family-mono, monospace); color: var(--muted, #5b6b85); }
.xbtn { display: inline-flex; align-items: center; justify-content: center; width: 32px; height: 32px;
  border-radius: 8px; border: 1px solid var(--border, #e2e8f0); background: var(--surface-2, #f6f8fd);
  color: var(--text-2, #475569); font-size: 15px; cursor: pointer; transition: background .12s, color .12s, border-color .12s; }
.xbtn:hover { background: var(--danger-soft, #fef2f2); color: var(--danger, #dc2626); border-color: color-mix(in srgb, var(--danger, #dc2626) 35%, transparent); }

.ops-steps { display: flex; align-items: center; gap: 0; flex-wrap: wrap; padding: 11px 18px;
  background: var(--surface, #fff); border-bottom: 1px solid var(--border, #e2e8f0); }
.ops-step { display: flex; align-items: center; gap: 8px; }
.ops-sdot { display: inline-flex; align-items: center; justify-content: center; width: 28px; height: 28px;
  border-radius: 50%; background: var(--surface-3, #f1f5f9); border: 1.5px solid var(--border, #e2e8f0);
  color: var(--muted, #5b6b85); font-size: 12px; font-weight: 700; cursor: pointer; user-select: none; transition: all .12s; }
.ops-sdot.on { background: var(--accent, #3e7bfa); border-color: var(--accent, #3e7bfa); color: #fff;
  box-shadow: 0 0 0 3px var(--accent-soft, rgba(62,123,250,.12)); }
.ops-sdot.dn { background: var(--ok, #059669); border-color: var(--ok, #059669); color: #fff; }
.ops-sdot.er { background: var(--danger, #dc2626); border-color: var(--danger, #dc2626); color: #fff;
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--danger, #dc2626) 18%, transparent); }
.ops-slbl { font-size: 8px; font-weight: 800; text-transform: uppercase; letter-spacing: .06em;
  color: var(--muted, #5b6b85); cursor: pointer; transition: color .12s; }
.ops-sdot.on + .ops-slbl { color: var(--accent, #3e7bfa); }
.ops-sln { width: 26px; height: 2px; background: var(--border, #e2e8f0); margin: 0 4px; border-radius: 2px; }
.ops-sln.dn { background: var(--ok, #059669); }

.ops-err { display: flex; align-items: center; gap: 6px; margin: 10px 18px 0; padding: 8px 11px;
  border-radius: 8px; font-size: 12px; font-weight: 700; color: var(--danger, #dc2626);
  background: var(--danger-soft, #fef2f2); border: 1px solid color-mix(in srgb, var(--danger, #dc2626) 32%, transparent); }
.ops-warn { display: flex; flex-direction: column; gap: 2px; margin: 10px 18px 0; padding: 8px 11px;
  border-radius: 8px; font-size: 11px; font-weight: 600; color: var(--warn, #b45309);
  background: var(--warn-bg, #fffbeb); border: 1px solid color-mix(in srgb, var(--warn, #b45309) 38%, transparent); }

.ops-body { flex: 1 1 auto; min-height: 0; overflow-y: auto; padding: 16px 18px; }

.ops-foot { display: flex; align-items: center; justify-content: space-between; gap: 10px;
  padding: 12px 18px; background: var(--surface, #fff); border-top: 1px solid var(--border, #e2e8f0); }
.ops-fl { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.ops-fr { display: flex; align-items: center; gap: 8px; }
.ops-ok { font-size: 12px; font-family: var(--font-family-mono, monospace); color: var(--ok, #059669); }
.ops-recap { font-size: 12px; font-family: var(--font-family-mono, monospace); color: var(--text-2, #475569); }

.btn-ok { display: inline-flex; align-items: center; gap: 6px; padding: 9px 18px; border: none;
  border-radius: 8px; background: var(--accent, #3e7bfa); color: #fff; font-size: 13px; font-weight: 700;
  cursor: pointer; transition: background .12s, box-shadow .12s; }
.btn-ok:hover { background: var(--accent-strong, #2b5fd9); box-shadow: var(--shadow-glow, 0 6px 16px rgba(62,123,250,.3)); }
.btn-ok:disabled { opacity: .55; cursor: not-allowed; box-shadow: none; }
.btn-ghost { display: inline-flex; align-items: center; gap: 6px; padding: 8px 14px; border: 1px solid var(--border, #e2e8f0);
  border-radius: 8px; background: var(--surface, #fff); color: var(--text-2, #475569); font-size: 13px; font-weight: 700;
  cursor: pointer; transition: all .12s; }
.btn-ghost:hover { background: var(--accent-soft, rgba(62,123,250,.1)); color: var(--accent, #3e7bfa); }
.btn-ghost.danger:hover { background: var(--danger-soft, #fef2f2); color: var(--danger, #dc2626); border-color: color-mix(in srgb, var(--danger, #dc2626) 32%, transparent); }
.btn-ghost.draft { border-color: color-mix(in srgb, var(--warn) 55%, transparent); color: var(--warn); background: var(--warn-soft, #fffbeb); }
.btn-ghost.draft:hover { border-color: var(--warn); color: #fff; background: var(--warn); }
.btn-ghost:disabled { opacity: .5; cursor: not-allowed; }

/* Restyling of the shared form pieces inside the wizard body */
.ops-body .ds-label { font-size: 8px; font-weight: 800; text-transform: uppercase; letter-spacing: .06em; color: var(--muted, #5b6b85); }
.req { color: var(--danger, #dc2626); font-weight: 700; }
.ops-body .ds-input { background: transparent; border: none; border-bottom: 1.5px solid var(--border, #e2e8f0);
  border-radius: 0; padding: 4px 2px; font-size: 13px; }
.ops-body .ds-input:focus { border-bottom-color: var(--accent, #3e7bfa); box-shadow: none; }
.ops-body thead tr { background: var(--accent-band, #0d3b37); }
.ops-body thead th { color: var(--accent-ink, #fff); }
.ops-body tfoot tr { background: var(--accent-soft, #eaf0ff); }

/* ——— Mockup App3 pasada: pipeline (s1) + teal ——— */
.pipe { background: var(--surface-0, #fff); border: 1px solid var(--border); }
.pipe-hd { border-bottom: 1px solid var(--border-2); }
.pipe-total { display: inline-flex; align-items: center; justify-content: center; min-width: 18px; height: 22px;
  padding: 0 7px; border-radius: 999px; font-size: 12px; font-weight: 800;
  color: var(--accent); background: color-mix(in srgb, var(--accent) 12%, transparent); }
.pipe-segs { gap: 6px; }
.seg { position: relative; display: flex; flex-direction: column; align-items: stretch; gap: 2px;
  padding: 8px 10px 6px; border: 1.5px solid transparent; border-radius: 10px;
  background: var(--surface-2, #f8f9fb); cursor: pointer; overflow: hidden; transition: all .15s; }
.seg:hover { background: var(--surface-3, #eef2f7); }
.seg.on { outline: 2px solid var(--accent); background: color-mix(in srgb, var(--accent) 8%, transparent); }
.seg.empty { opacity: .45; }
.seg-n { font-size: 18px; font-weight: 800; line-height: 1; text-align: center; }
.seg-l { font-size: 10px; font-weight: 700; text-transform: uppercase; letter-spacing: .04em;
  color: var(--muted); text-align: center; white-space: nowrap; }
.seg-bar { height: 3px; border-radius: 2px; margin-top: 2px; }

@media (max-width: 640px) {
  .ops-overlay { padding: 8px; }
  .ops-modal { border-radius: 8px; max-height: 94dvh; max-width: 100%; min-width: 280px; min-height: 0; resize: none; }
  .ops-modal::after { display: none; }
  .ops-slbl { display: none; }
  .ops-sdot { width: 24px; height: 24px; font-size: 11px; }
  .ops-body { padding: 10px 12px; }
  .ops-err, .ops-warn { margin-left: 12px; margin-right: 12px; }
  .ops-hdr { padding: 10px 12px; }
  .ops-foot { padding: 10px 12px; }
}

/* ── Tablet (≤1023px): el wizard ocupa la pantalla y los controles crecen
   para uso táctil. Antes el modal mantenía el ancho de escritorio (940px) y
   las tarjetas quedaban apretadas en iPad / tablets Android. */
@media (max-width: 1023px) {
  .ops-overlay { padding: 8px; align-items: stretch; }
  .ops-modal { width: 100%; max-width: 100%; height: calc(100dvh - 16px);
    max-height: calc(100dvh - 16px); min-height: 0; resize: none; border-radius: 10px; }
  .ops-modal::after { display: none; }
  .ops-hdr { padding: 10px 12px; gap: 8px; flex-wrap: wrap; }
  .ops-tt { font-size: 13.5px; }
  .ops-sub { font-size: 10.5px; }
  .ops-steps { padding: 9px 12px; }
  .ops-sdot { width: 26px; height: 26px; }
  .ops-slbl { font-size: 8px; }
  .ops-sln { width: 16px; }
  .ops-body { padding: 12px; }
  .ops-err, .ops-warn { margin-left: 12px; margin-right: 12px; }
  .ops-foot { padding: 10px 12px; flex-wrap: wrap; gap: 8px; }
  .ops-fl { gap: 6px; flex: 1 1 auto; }
  .ops-fr { flex: 1 1 100%; }
  .ops-fr .btn-ok { width: 100%; justify-content: center; padding: 12px 16px; font-size: 14px; }
  .btn-ghost { min-height: 36px; padding: 8px 12px; }

  /* Cockpit: 3 columnas legibles en vez de 6 apretadas */
  .rcp-cockpit-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 6px; }
  .rcp-kpi-v { font-size: 14px; }
  .rcp-kpi small { font-size: 10px; }
  .rcp-delta { font-size: 12px; padding: 8px 10px; }
  .rcp-panel-hd { padding: 9px 12px; flex-wrap: wrap; }
  .rcp-panel > .grid, .rcp-panel > .mt-2, .rcp-panel > .space-y-2, .rcp-temp { padding: 10px 12px; }

  /* Objetivos táctiles: checkbox y filas del checklist */
  .rcp-panel label input[type="checkbox"] { width: 18px; height: 18px; }
  .rcp-panel label { min-height: 34px; }
  .rcp-cockpit-hd { padding: 8px 12px; }
}

/* Móvil: titulares más pequeños y cockpit en 2 columnas */
@media (max-width: 640px) {
  .rcp-cockpit-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .rcp-kpi-v { font-size: 13px; }
  .rcp-panel-hd { align-items: flex-start; }
  .rcp-panel-tt { font-size: 10.5px; }
}

/* ── Cockpit de recepción + checklist / cadena de frío ────────────────── */
.rcp-cockpit { margin-top: 10px; border: 1px solid var(--border); border-radius: 8px; overflow: hidden; background: var(--surface, #fff); }
.rcp-cockpit-hd { display: flex; align-items: center; justify-content: space-between; gap: 8px;
  padding: 7px 10px; background: color-mix(in srgb, var(--accent) 8%, transparent);
  border-bottom: 1px solid var(--border); }
.rcp-cockpit-tt { font-size: 11px; font-weight: 800; text-transform: uppercase; letter-spacing: .05em; color: var(--text); }
.rcp-cockpit-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; padding: 9px 10px; }
@media (min-width: 640px) { .rcp-cockpit-grid { grid-template-columns: repeat(6, minmax(0, 1fr)); } }
.rcp-kpi { display: flex; flex-direction: column; gap: 1px; min-width: 0;
  padding: 6px 7px; border: 1px solid var(--border-2, #e2e8f0); border-radius: 6px; background: var(--surface-2, #f8fafc); }
.rcp-kpi-accent { border-color: color-mix(in srgb, var(--accent) 45%, transparent);
  background: color-mix(in srgb, var(--accent) 7%, transparent); }
.rcp-kpi-l { font-size: 9.5px; font-weight: 700; text-transform: uppercase; letter-spacing: .04em;
  color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.rcp-kpi-v { font-size: 13px; font-weight: 800; font-variant-numeric: tabular-nums; color: var(--text); white-space: nowrap; }
.rcp-kpi small { font-size: 9.5px; color: var(--text-2); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.rcp-delta { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 14px;
  padding: 7px 10px; border-top: 1px solid var(--border); font-size: 11.5px; }
.rcp-delta.ok { background: color-mix(in srgb, #10b981 9%, transparent); color: #047857; }
.rcp-delta.bad { background: color-mix(in srgb, #f59e0b 12%, transparent); color: #b45309; }
.rcp-badge { display: inline-flex; align-items: center; gap: 4px; padding: 2px 8px; border-radius: 999px;
  font-size: 10px; font-weight: 800; text-transform: uppercase; letter-spacing: .04em; white-space: nowrap; }
.rcp-badge.ok { background: color-mix(in srgb, #10b981 16%, transparent); color: #047857; border: 1px solid color-mix(in srgb, #10b981 40%, transparent); }
.rcp-badge.warn { background: color-mix(in srgb, #f59e0b 18%, transparent); color: #b45309; border: 1px solid color-mix(in srgb, #f59e0b 40%, transparent); }
.rcp-badge.bad { background: color-mix(in srgb, #ef4444 16%, transparent); color: #b91c1c; border: 1px solid color-mix(in srgb, #ef4444 40%, transparent); }
.rcp-toggle { display: inline-flex; align-items: center; justify-content: center; width: 26px; height: 26px;
  border: 1px solid var(--border); border-radius: 6px; background: var(--surface); color: var(--text-2);
  font-size: 12px; line-height: 1; cursor: pointer; }
.rcp-toggle:hover { background: var(--surface-hover, #eef2f7); color: var(--text); }

/* Temperatura de recepción (se persiste en el recibo) */
.rcp-temp { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 8px 12px;
  padding: 9px 10px; border-bottom: 1px dashed var(--border); }
.rcp-temp-input { max-width: 140px; font-variant-numeric: tabular-nums; }
.rcp-temp-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.rcp-btn-mini { display: inline-flex; align-items: center; gap: 5px; padding: 6px 11px; border-radius: 6px;
  border: 1px solid color-mix(in srgb, var(--accent) 45%, transparent);
  background: color-mix(in srgb, var(--accent) 9%, transparent); color: var(--accent);
  font-size: 11px; font-weight: 800; text-transform: uppercase; letter-spacing: .03em;
  cursor: pointer; transition: all .12s; }
.rcp-btn-mini:hover { background: var(--accent); color: #fff; border-color: var(--accent); }
.rcp-btn-mini:active { transform: scale(.97); }

.rcp-panel { border: 1px solid var(--border); border-radius: 8px; background: var(--surface, #fff); overflow: hidden; }
.rcp-panel-hd { display: flex; align-items: center; justify-content: space-between; gap: 8px;
  padding: 7px 10px; background: color-mix(in srgb, var(--accent) 6%, transparent);
  border-bottom: 1px solid var(--border); }
.rcp-panel-tt { font-size: 11px; font-weight: 800; text-transform: uppercase; letter-spacing: .05em; color: var(--text); }
.rcp-panel > .grid, .rcp-panel > .mt-2, .rcp-panel > .space-y-2 { padding: 9px 10px; }
[data-theme='tokyo'] .rcp-delta.ok,
[data-theme='tokyo'] .rcp-badge.ok { color: #6ee7b7; }
[data-theme='tokyo'] .rcp-delta.bad,
[data-theme='tokyo'] .rcp-badge.warn { color: #fbbf24; }
[data-theme='tokyo'] .rcp-badge.bad { color: #fca5a5; }

/* ═══════════════════════════════════════════════════════════════════════
   POPUP DE RECEPCIÓN — diseño tablet-first (cabecera industrial oscura,
   raíl de pasos, tres vistas: operativa / skids / previa oficial).
   Solo redefine la cáscara: el contenido de los pasos no se toca.
   ═══════════════════════════════════════════════════════════════════════ */
.ops-overlay { padding: 14px; }
.ops-modal.rcp-pop { width: min(1120px, 100%); max-width: calc(100vw - 28px);
  max-height: calc(100dvh - 28px); min-height: 460px;
  border: 1px solid var(--border, #e2e8f0); border-top: none; border-radius: 16px;
  background: var(--surface-2, #f6f8fd); resize: none;
  box-shadow: 0 32px 80px -32px rgba(15, 23, 42, .55); }
.ops-modal.rcp-pop::after { display: none; }

/* — Cabecera — */
.rcp-pop-hdr { display: grid; grid-template-columns: minmax(0, 1fr) auto auto; align-items: center;
  gap: 12px 18px; padding: 13px 18px; color: #f8fafc;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 58%, #0f2b2a 100%);
  border-bottom: 3px solid var(--accent, #3e7bfa); }
.rcp-pop-id { min-width: 0; }
.rcp-pop-kicker { display: block; font-size: 9.5px; font-weight: 800; letter-spacing: .14em;
  text-transform: uppercase; color: #94a3b8; }
.rcp-pop-awbline { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin: 2px 0; }
.rcp-pop-awb { margin: 0; font-family: var(--font-family-mono, monospace); font-size: 23px;
  line-height: 1.1; font-weight: 800; color: #ffffff; letter-spacing: .01em; }
.rcp-pop-chip { padding: 2px 8px; border-radius: 999px; font-size: 9.5px; font-weight: 800;
  text-transform: uppercase; letter-spacing: .05em; white-space: nowrap;
  background: rgba(255, 255, 255, .10); color: #e2e8f0; border: 1px solid rgba(255, 255, 255, .16); }
.rcp-pop-chip.ok { background: rgba(16, 185, 129, .18); color: #6ee7b7; border-color: rgba(16, 185, 129, .45); }
.rcp-pop-chip.warn { background: rgba(245, 158, 11, .18); color: #fcd34d; border-color: rgba(245, 158, 11, .45); }
.rcp-pop-meta { display: block; font-size: 11.5px; color: #cbd5e1;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.rcp-pop-kpis { display: flex; align-items: stretch; gap: 8px; }
.rcp-pop-kpi { display: flex; flex-direction: column; gap: 1px; min-width: 62px;
  padding: 6px 10px; border-radius: 10px;
  background: rgba(255, 255, 255, .06); border: 1px solid rgba(255, 255, 255, .12); }
.rcp-pop-kpi small { font-size: 9px; font-weight: 700; text-transform: uppercase;
  letter-spacing: .08em; color: #94a3b8; white-space: nowrap; }
.rcp-pop-kpi strong { font-size: 15px; font-weight: 800; color: #f8fafc;
  font-variant-numeric: tabular-nums; white-space: nowrap; }
.rcp-pop-kpi strong i { font-style: normal; font-size: 10px; font-weight: 700; color: #94a3b8; }
.rcp-pop-tools { display: flex; align-items: center; gap: 8px; }
.rcp-viewtog { display: flex; gap: 2px; padding: 3px; border-radius: 10px;
  background: rgba(255, 255, 255, .07); border: 1px solid rgba(255, 255, 255, .14); }
.rcp-viewtog button { padding: 6px 12px; border: none; border-radius: 8px; background: transparent;
  color: #cbd5e1; font-size: 11.5px; font-weight: 800; letter-spacing: .02em; white-space: nowrap;
  cursor: pointer; transition: background .15s, color .15s; }
.rcp-viewtog button:hover { background: rgba(255, 255, 255, .10); color: #ffffff; }
.rcp-viewtog button.on { background: #f8fafc; color: #0f172a; box-shadow: 0 2px 8px rgba(0, 0, 0, .35); }
.rcp-icon-btn { display: inline-flex; align-items: center; justify-content: center;
  width: 36px; height: 36px; border-radius: 10px; font-size: 15px; cursor: pointer;
  background: rgba(255, 255, 255, .07); border: 1px solid rgba(255, 255, 255, .16);
  color: #e2e8f0; transition: background .14s, color .14s, border-color .14s; }
.rcp-icon-btn:hover { background: rgba(255, 255, 255, .16); color: #ffffff; }
.rcp-icon-btn.x:hover { background: rgba(239, 68, 68, .88); border-color: rgba(239, 68, 68, .9); color: #ffffff; }
.rcp-fn-btn.on { background: rgba(255, 255, 255, .22); border-color: rgba(255, 255, 255, .45); color: #ffffff; }

/* — Funciones opcionales: un interruptor por función, dentro de la vista — */
/* flex: 0 0 auto — el cajón nunca se comprime dentro del modal (que es flex
   column): si se deja encoger, el max-height/overflow táctil lo colapsa a una
   ventanita de ~45 px y los interruptores quedan fuera de la vista. */
.rcp-fn-bar { flex: 0 0 auto; padding: 10px 18px 12px; background: var(--surface, #fff);
  border-bottom: 1px solid var(--border, #e2e8f0); }
.rcp-fn-hd { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.rcp-fn-tt { display: block; font-size: 11px; font-weight: 800; text-transform: uppercase;
  letter-spacing: .08em; color: var(--text, #0f172a); }
.rcp-fn-sub { margin: 2px 0 0; font-size: 11px; line-height: 1.35; color: var(--muted, #5b6b85); }
.rcp-fn-close { flex-shrink: 0; width: 26px; height: 26px; border-radius: 8px; cursor: pointer;
  display: inline-flex; align-items: center; justify-content: center; font-size: 12px;
  color: var(--muted, #5b6b85); background: transparent; border: 1px solid var(--border, #e2e8f0);
  transition: color .14s, border-color .14s; }
.rcp-fn-close:hover { color: var(--text, #0f172a); border-color: var(--accent, #3e7bfa); }
.rcp-fn-rows { display: grid; grid-template-columns: repeat(auto-fit, minmax(270px, 1fr));
  gap: 8px; margin-top: 8px; }
.rcp-fn-row { display: flex; align-items: center; justify-content: space-between; gap: 10px;
  padding: 8px 10px; border-radius: 10px;
  border: 1px solid var(--border, #e2e8f0); background: var(--surface-2, #f6f8fd); }
.rcp-fn-lbl { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.rcp-fn-lbl strong { font-size: 12px; font-weight: 700; color: var(--text, #0f172a); }
.rcp-fn-lbl small { font-size: 10.5px; line-height: 1.3; color: var(--muted, #5b6b85); }
.rcp-switch { display: inline-flex; align-items: center; gap: 6px; flex-shrink: 0;
  padding: 5px 9px 5px 6px; border-radius: 999px; cursor: pointer; white-space: nowrap;
  font-size: 9.5px; font-weight: 800; text-transform: uppercase; letter-spacing: .06em;
  color: var(--muted, #5b6b85); background: var(--surface, #fff);
  border: 1px solid var(--border-2, #cbd5e1); transition: color .16s, border-color .16s, background .16s; }
.rcp-switch-dot { position: relative; width: 26px; height: 14px; border-radius: 999px;
  background: color-mix(in srgb, var(--muted, #5b6b85) 28%, transparent); transition: background .16s; }
.rcp-switch-dot::after { content: ''; position: absolute; top: 2px; left: 2px; width: 10px; height: 10px;
  border-radius: 50%; background: #fff; box-shadow: 0 1px 2px rgba(15, 23, 42, .35);
  transition: transform .16s; }
.rcp-switch.on { color: var(--ok, #047857); border-color: color-mix(in srgb, var(--ok, #047857) 45%, transparent);
  background: color-mix(in srgb, var(--ok, #047857) 10%, transparent); }
.rcp-switch.on .rcp-switch-dot { background: var(--ok, #047857); }
.rcp-switch.on .rcp-switch-dot::after { transform: translateX(12px); }
.rcp-switch:disabled { cursor: progress; opacity: .65; }

/* — Raíl de pasos — */
.rcp-pop .ops-steps { flex-wrap: nowrap; padding: 9px 16px; overflow-x: auto;
  scrollbar-width: none; background: var(--surface, #fff); }
.rcp-pop .ops-steps::-webkit-scrollbar { height: 0; }
.rcp-pop .ops-step { flex: 0 0 auto; }
.rcp-pop .ops-sdot { width: 25px; height: 25px; border-radius: 8px; font-size: 11.5px; }
.rcp-pop .ops-slbl { font-size: 10px; letter-spacing: .05em; white-space: nowrap; }
.rcp-pop .ops-sln { width: 14px; }

/* — Cuerpo y pie — */
.rcp-pop .ops-body { padding: 14px 18px; background: var(--surface-2, #f6f8fd); }
/* Cabeceras de tabla en el mismo tono pizarra que la cabecera del popup:
   el acento del tema teñía de morado/dorado las tablas del formulario. */
.rcp-pop .ops-body .data-table thead th { background: #1e293b; background-image: none; color: #f8fafc; }
.rcp-pop .ops-body .ds-input { background: var(--surface, #fff); border: 1px solid var(--border, #e2e8f0);
  border-radius: 9px; padding: 8px 10px; min-height: 38px; font-size: 13.5px; }
.rcp-pop .ops-body .ds-input:focus { border-color: var(--accent, #3e7bfa);
  box-shadow: 0 0 0 3px var(--accent-soft, rgba(62, 123, 250, .12)); }
.rcp-pop .ops-foot { padding: 11px 18px; background: var(--surface, #fff);
  box-shadow: 0 -10px 22px -18px rgba(15, 23, 42, .45); }
.rcp-pop .btn-ghost, .rcp-pop .btn-ok { min-height: 40px; }

/* — Campo heredado del booking — */
.rcp-inh { font-size: 8px; font-weight: 700; text-transform: none; letter-spacing: 0;
  color: var(--muted, #5b6b85); opacity: .9; }
.rcp-inh-field { display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  padding: 8px 10px; border-radius: 9px; background: var(--surface, #fff);
  border: 1px solid var(--border, #e2e8f0); }
.rcp-inh-val { font-family: var(--font-family-mono, monospace); font-size: 12.5px;
  font-weight: 800; color: var(--text, #0f172a); }
.rcp-inh-hint { font-size: 10.5px; color: var(--muted, #5b6b85); }

/* — Campos fantasma / reservados (sello, muelle) — */
.rcp-ghost-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 8px; }
.rcp-ghost { padding: 7px 10px 9px; border-radius: 10px; opacity: .6;
  border: 1.5px dashed color-mix(in srgb, var(--muted, #5b6b85) 55%, transparent);
  background: color-mix(in srgb, var(--muted, #5b6b85) 4%, transparent); }
.rcp-ghost .ds-label { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.rcp-ghost input.ds-input { background: transparent; border-style: dashed; cursor: not-allowed; }
.rcp-ghost-tag { padding: 1px 6px; border-radius: 999px; font-size: 8px; font-weight: 800;
  text-transform: uppercase; letter-spacing: .06em; color: var(--muted, #5b6b85);
  border: 1px dashed currentColor; }
/* Un campo habilitado por el SUPER_USER deja de ser fantasma y se captura. */
.rcp-ghost--live { opacity: 1; border-style: solid;
  border-color: color-mix(in srgb, var(--accent, #3e7bfa) 40%, var(--border, #e2e8f0));
  background: var(--surface, #fff); }
.rcp-ghost--live input.ds-input { background: var(--surface, #fff); border-style: solid; cursor: text; }
.rcp-ghost-tag--on { color: var(--ok, #047857); border-style: solid;
  background: color-mix(in srgb, var(--ok, #047857) 12%, transparent); }
.rcp-ghost-note { margin-top: 4px; font-size: 10.5px; line-height: 1.35;
  color: var(--muted, #5b6b85); }

/* — Vista: detalle de skids — */
.rcp-vw-hd { display: flex; align-items: flex-start; justify-content: space-between;
  gap: 10px 14px; flex-wrap: wrap; margin-bottom: 10px; }
.rcp-vw-tt { margin: 0; font-size: 15px; font-weight: 800; color: var(--text, #0f172a); }
.rcp-vw-sub { margin: 2px 0 0; font-size: 11.5px; color: var(--muted, #5b6b85); }
.ops-body thead tr.rcp-sheet-thead { background: #1e293b; }
.ops-body thead tr.rcp-sheet-thead th { color: #f8fafc; font-size: 10.5px; font-weight: 800;
  text-transform: uppercase; letter-spacing: .05em; padding: 8px 6px; white-space: nowrap; }
.rcp-num { text-align: right; font-variant-numeric: tabular-nums; }
.rcp-cell-strong { font-weight: 800; color: var(--accent, #3e7bfa); }
.ops-body tfoot tr.rcp-sheet-tfoot { background: var(--accent-soft, #eaf0ff); font-weight: 800; }
.ops-body tfoot tr.rcp-sheet-tfoot td { color: var(--text, #0f172a); font-weight: 800; padding: 7px 6px; }

/* — Vista: previa del recibo oficial (hoja clara, como el impreso) — */
.rcp-prev-wrap { background: var(--surface-3, #eef2f7); }
.rcp-sheet { max-width: 860px; margin: 0 auto; background: #ffffff; color: #0f172a;
  border: 1px solid #d8e0ea; border-radius: 12px; overflow: hidden;
  box-shadow: 0 20px 44px -30px rgba(15, 23, 42, .55); }
.rcp-sheet-hd { display: flex; align-items: flex-start; justify-content: space-between;
  gap: 14px; flex-wrap: wrap; padding: 15px 18px; color: #f8fafc;
  background: linear-gradient(135deg, #0f172a, #1e293b); }
.rcp-sheet-brand { font-size: 9.5px; font-weight: 800; letter-spacing: .16em;
  text-transform: uppercase; color: #94a3b8; }
.rcp-sheet-awb { font-family: var(--font-family-mono, monospace); font-size: 22px;
  font-weight: 800; line-height: 1.15; margin-top: 2px; }
.rcp-sheet-sub { font-size: 11px; color: #cbd5e1; }
.rcp-sheet-meta { display: flex; gap: 18px; flex-wrap: wrap; }
.rcp-sheet-meta div { display: flex; flex-direction: column; gap: 1px; }
.rcp-sheet-meta span { font-size: 9px; font-weight: 800; text-transform: uppercase;
  letter-spacing: .08em; color: #94a3b8; }
.rcp-sheet-meta strong { font-size: 12px; color: #f1f5f9; }
.rcp-sheet-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 18px; padding: 13px 18px; border-bottom: 1px solid #e2e8f0; }
.rcp-sheet-fields div { display: flex; flex-direction: column; gap: 1px; min-width: 0; }
.rcp-sheet-fields span { font-size: 9px; font-weight: 800; text-transform: uppercase;
  letter-spacing: .07em; color: #64748b; }
.rcp-sheet-fields strong { font-size: 13px; font-weight: 700;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rcp-sheet-block { padding: 11px 18px; border-bottom: 1px solid #eef2f7; }
.rcp-sheet-block h4 { margin: 0 0 8px; font-size: 9.5px; font-weight: 800;
  text-transform: uppercase; letter-spacing: .1em; color: #475569; }
.rcp-sheet-kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(108px, 1fr)); gap: 8px; }
.rcp-sheet-kpis div { padding: 7px 10px; border-radius: 8px;
  background: #f8fafc; border: 1px solid #e2e8f0; }
.rcp-sheet-kpis div.accent { background: #eff6ff; border-color: #bfdbfe; }
.rcp-sheet-kpis small { display: block; font-size: 9px; font-weight: 800;
  text-transform: uppercase; letter-spacing: .06em; color: #64748b; }
.rcp-sheet-kpis strong { font-size: 14px; font-weight: 800; font-variant-numeric: tabular-nums; }
.rcp-sheet-table { width: 100%; border-collapse: collapse;
  font-size: 11.5px; font-family: var(--font-family-mono, monospace); }
.rcp-sheet-table th { text-align: left; font-size: 9px; font-weight: 800;
  text-transform: uppercase; letter-spacing: .05em; color: #475569;
  padding: 5px 6px; border-bottom: 1.5px solid #cbd5e1; white-space: nowrap; }
.rcp-sheet-table td { padding: 5px 6px; border-bottom: 1px solid #eef2f7; }
.rcp-sheet-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.rcp-sheet-tag { padding: 3px 9px; border-radius: 999px; font-size: 11px; font-weight: 700;
  color: #334155; background: #f1f5f9; border: 1px solid #dbe3ec; }
.rcp-sheet-tag.ok { background: #ecfdf5; border-color: #a7f3d0; color: #047857; }
.rcp-sheet-sigs { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px; padding: 15px 18px; background: #f8fafc; }
.rcp-sig { display: flex; flex-direction: column; gap: 4px; justify-content: flex-end;
  min-height: 76px; padding-bottom: 4px; border-bottom: 1px solid #cbd5e1; }
.rcp-sig img { max-height: 54px; object-fit: contain; align-self: flex-start; }
.rcp-sig span { font-size: 10.5px; font-weight: 700; color: #475569; }

/* ——— Tablet: popup a pantalla completa con controles táctiles ——— */
@media (max-width: 1023px) {
  .ops-overlay { padding: 0; }
  .ops-modal.rcp-pop { width: 100%; max-width: 100%; height: 100dvh;
    max-height: 100dvh; min-height: 0; border-radius: 0; border-left: none; border-right: none; }
  .rcp-pop-hdr { grid-template-columns: minmax(0, 1fr) auto;
    grid-template-areas: "id kpis" "tools tools"; gap: 8px 12px; padding: 10px 12px; }
  .rcp-pop-id { grid-area: id; }
  .rcp-pop-kpis { grid-area: kpis; }
  .rcp-pop-tools { grid-area: tools; justify-content: space-between; }
  .rcp-pop-awb { font-size: 19px; }
  .rcp-pop-meta { font-size: 10.5px; }
  .rcp-pop-kpi { min-width: 50px; padding: 4px 8px; }
  .rcp-pop-kpi strong { font-size: 13px; }
  .rcp-viewtog { flex: 1 1 auto; }
  .rcp-viewtog button { flex: 1 1 0; padding: 9px 8px; font-size: 12.5px; }
  .rcp-icon-btn { width: 42px; height: 42px; font-size: 16px; }
  .rcp-pop .ops-steps { padding: 8px 10px; }
  .rcp-pop .ops-sdot { width: 27px; height: 27px; }
  .rcp-pop .ops-slbl { font-size: 9.5px; }
  .rcp-pop .ops-sln { width: 10px; }
  .rcp-pop .ops-body { padding: 12px; }
  /* 16px evita el zoom automático de iOS al enfocar un input */
  .rcp-pop .ops-body .ds-input { font-size: 16px; min-height: 44px; }
  .rcp-pop .ops-foot { padding: 10px 12px; }
  .rcp-pop .btn-ghost, .rcp-pop .btn-ok { min-height: 44px; }
  .rcp-vw-hd .btn-ghost { width: 100%; justify-content: center; }
  .rcp-ghost-grid { grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); }
  .rcp-fn-bar { padding: 10px 12px 12px; }
  .rcp-fn-rows { grid-template-columns: 1fr; }
}

@media (max-width: 640px) {
  .rcp-pop-hdr { grid-template-columns: minmax(0, 1fr);
    grid-template-areas: "id" "kpis" "tools"; }
  .rcp-pop-kpis { justify-content: space-between; }
  .rcp-pop-awb { font-size: 17px; }
  .rcp-pop .ops-slbl { font-size: 8.5px; letter-spacing: .02em; }
  .rcp-pop .ops-sdot { width: 24px; height: 24px; }
  .rcp-sheet-fields { grid-template-columns: 1fr; }
  .rcp-sheet-kpis { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

/* ——— Táctil (tablet en cualquier orientación): funciones opcionales cómodas.
   Cada interruptor es un objetivo de toque de 44 px y el cajón se desplaza
   solo cuando hay muchas funciones, sin comerse el cuerpo del recibo. ——— */
@media (max-width: 1023px), (any-pointer: coarse) {
  .rcp-fn-bar { padding: 12px 14px 14px; max-height: 44dvh;
    overflow-y: auto; overscroll-behavior: contain; }
  .rcp-fn-row { padding: 10px 12px; gap: 12px; min-height: 64px; }
  .rcp-fn-lbl strong { font-size: 13px; }
  .rcp-fn-lbl small { font-size: 11.5px; line-height: 1.4; }
  .rcp-fn-close { width: 44px; height: 44px; border-radius: 12px; font-size: 16px; }
  .rcp-switch { min-height: 44px; padding: 8px 14px 8px 10px; gap: 8px; font-size: 10.5px; }
  .rcp-switch-dot { width: 34px; height: 18px; }
  .rcp-switch-dot::after { width: 14px; height: 14px; }
  .rcp-switch.on .rcp-switch-dot::after { transform: translateX(16px); }
  .rcp-icon-btn { width: 44px; height: 44px; }
  .rcp-ghost { padding: 9px 11px 11px; }
  .rcp-pop .ops-body .rcp-ghost input.ds-input { min-height: 44px; }
  .rcp-ghost-tag { font-size: 9px; padding: 2px 7px; }
  .rcp-ghost-note { font-size: 11.5px; line-height: 1.45; }
}
</style>
