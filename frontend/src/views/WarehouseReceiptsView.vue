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
    <section v-if="attentionQueue.length" class="exq mx-3 mb-2 rounded-lg overflow-hidden">
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
      </div>
      <div class="overflow-x-auto shrink-0">
        <div class="bg-[#0d3b37] border-b border-[#12332f] receipt-list-header sticky top-0 z-10" style="min-width: 1000px">
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
      <EmptyState v-else-if="filteredMawbs.length === 0" :title="t('warehouse.emptyFilter')" :hint="t('warehouse.emptyHint')" :icon="icons.Search" />
      <div v-else class="divide-y divide-[var(--border-2)] text-[12.5px] text-[var(--text)] overflow-y-auto flex-1 min-h-0 thin-scrollbar">
        <template v-for="m in filteredMawbs" :key="m.id">
          <div v-if="tableMode === 'byFlight' && flightGroupStarts().has(m.id)" class="fl-group-hd"
            :class="{ closed: isCollapsedGroup(flightKeyOf(m)) }" @click="toggleGroup(flightKeyOf(m))">
            <span class="fl-chip">{{ fmtFlight(m) }}</span>
            <span class="fl-sub">{{ flightSub(flightKeyOf(m)) }}</span>
            <span class="fl-count">{{ groupByKey(flightKeyOf(m))?.mawbs.length || 0 }}</span>
            <span class="fl-caret">{{ isCollapsedGroup(flightKeyOf(m)) ? '&#9656;' : '&#9662;' }}</span>
          </div>
          <div v-if="tableMode === 'master' || !isCollapsedGroup(flightKeyOf(m))" class="flex flex-col">
          <div class="overflow-x-auto">
          <div class="grid grid-cols-12 items-center py-1 px-4 transition-all duration-150 cursor-pointer border-t hover:bg-[var(--surface-2)]"
            :class="[
              expandedId === m.id ? 'row-selected' : '',
              rowFlash.has(m.id) ? 'row-flash' : '',
            ]"
            style="border-color: var(--border); min-width: 1000px;"
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
            <div class="col-span-2 flex items-center justify-center gap-1 relative z-10">
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
                    {{ t('warehouse.status.receive') }}
                  </button>
                  <span v-else-if="deriveMawbOperationalStatus(m) === 'CARGADA'" class="flex items-center gap-1.5 flex-wrap">
                    <span class="tag-warn" :title="t('warehouse.status.mountedNoReceipt')">&#9888; {{ t('warehouse.status.mountedNoReceipt') }}</span>
                    <button @click.stop="toggleExpand(m)"
                      class="btn-rec">
                      {{ t('warehouse.status.receive') }}
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
            <div class="ops-modal" role="dialog" aria-modal="true">
              <div class="ops-hdr">
                <div class="ops-fl">
                  <span class="ops-tt">{{ t('warehouse.title') }}</span>
                  <span class="ops-sub">{{ m.awbNumber || '—' }} · {{ m.shipperName || '—' }}</span>
                </div>
                <div class="ops-fr">
                  <button class="btn-ghost" @click="printPreview(m)" title="Vista previa para impresión">&#128424; PDF / Imprimir</button>
                  <button class="xbtn" @click="cancelForm" title="Cerrar">&#10005;</button>
                </div>
              </div>

              <div class="ops-steps">
                <div v-for="(step, si) in steps" :key="si" class="ops-step">
                  <div class="ops-sdot" :class="{ on: localStep === si + 1, dn: stepDone(si) }"
                    @click="localStep = si + 1">
                    <span v-if="stepDone(si)">&#10003;</span>
                    <span v-else-if="stepError(si)">&#33;</span>
                    <span v-else>{{ si + 1 }}</span>
                  </div>
                  <span class="ops-slbl" @click="localStep = si + 1">{{ step }}</span>
                  <div v-if="si < steps.length - 1" class="ops-sln" :class="{ dn: stepDone(si) || localStep > si + 1 }"></div>
                </div>
              </div>

              <!-- ═══ Scrollable step content ═══ -->
              <div class="ops-body thin-scrollbar overscroll-contain">
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
                      <label class="ds-label">{{ t('warehouse.form.shipperName') }}</label>
                      <div class="flex gap-2 items-center">
                        <input v-model="receiptForms[m.id].shipperName" type="text" placeholder="Shipper"
                          class="ds-input flex-1"
                          @blur="syncMawbName(m, 'shipperName')" />
                        <span class="text-[12px] text-slate-950 font-mono shrink-0">MAWB: {{ m.shipperName || '—' }}</span>
                      </div>
                    </div>
                    <div>
                      <label class="ds-label">Consignee Name</label>
                      <div class="flex gap-2 items-center">
                        <input v-model="receiptForms[m.id].consigneeName" type="text" placeholder="Consignee"
                          class="ds-input flex-1"
                          @blur="syncMawbName(m, 'consigneeName')" />
                        <span class="text-[12px] text-slate-950 font-mono shrink-0">MAWB: {{ m.consigneeName || '—' }}</span>
                      </div>
                    </div>
                    <div>
                      <label class="ds-label">MAWB Number</label>
                      <input :value="m.awbNumber || ''" readonly
                        class="ds-input bg-slate-100 text-slate-950" />
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
                    <div class="border-2 border-slate-700 rounded-lg bg-white overflow-hidden shadow-sm">
                      <div class="flex items-center justify-between bg-slate-700 px-3 py-1.5 border-b border-slate-800">
                        <span class="text-[12px] font-mono font-bold text-white uppercase tracking-wider">
                          HAWBs
                        </span>
                        <div class="flex items-center gap-2 text-[13px] font-mono">
                          <span class="text-slate-200">HAWBs: <strong class="text-white">{{ (receiptForms[m.id].hawbEntries || []).length }}</strong></span>
                          <button @click="addHawbEntry(m)"
                            class="ml-1 w-5 h-5 flex items-center justify-center rounded bg-white text-slate-700 hover:bg-slate-200 transition text-[14px] font-bold leading-none"
                            title="Agregar HAWB" aria-label="Agregar HAWB">+</button>
                          <span v-if="receiptHawbs[m.id] && receiptHawbs[m.id].length > 0"
                            class="text-slate-300 ml-1">({{ receiptHawbs[m.id].length }} en DB)</span>
                        </div>
                      </div>
                      <div class="overflow-x-auto p-2">
                        <table class="w-full text-[13px] font-mono border-collapse" style="min-width: 800px">
                          <thead>
                            <tr class="text-slate-800 font-bold uppercase tracking-wider">
                              <th class="px-2 py-1 text-left border-b-2 border-slate-200"># HAWB</th>
                              <th class="px-2 py-1 text-left border-b-2 border-slate-200">Consignee</th>
                              <th class="px-2 py-1 text-center border-b-2 border-slate-200">Pcs</th>
                              <th class="px-2 py-1 text-right border-b-2 border-slate-200">Kg</th>
                              <th class="px-2 py-1 text-center border-b-2 border-slate-200">Dest</th>
                              <th v-if="(receiptForms[m.id].hawbEntries || []).length > 1"
                                class="px-2 py-1 text-center border-b-2 border-slate-200"></th>
                            </tr>
                          </thead>
                          <tbody>
                            <tr v-for="(entry, ei) in receiptForms[m.id].hawbEntries" :key="ei"
                              class="hover:bg-slate-50">
                              <td class="px-2 py-1 border-b border-slate-100">
                                <input v-model="entry.hawbNumber" placeholder="HAWB #"
                                  class="w-20 border border-slate-200 rounded px-2 py-1 outline-none focus:border-slate-700 bg-white text-[13px] font-bold text-slate-950" />
                              </td>
                              <td class="px-2 py-1 border-b border-slate-100">
                                <input v-model="entry.consigneeName" placeholder="Consignee"
                                  class="w-full min-w-[100px] border border-slate-200 rounded px-2 py-1 outline-none focus:border-slate-700 bg-white text-[13px]" />
                              </td>
                              <td class="px-2 py-1 border-b border-slate-100">
                                <input v-model.number="entry.pieces" type="number" min="0" placeholder="0"
                                  class="w-14 text-center border border-slate-200 rounded px-2 py-1 outline-none focus:border-slate-700 bg-white text-[13px]" />
                              </td>
                              <td class="px-2 py-1 border-b border-slate-100">
                                <input v-model.number="entry.weightKg" type="number" step="0.1" min="0" placeholder="0"
                                  class="w-20 text-right border border-slate-200 rounded px-2 py-1 outline-none focus:border-slate-700 bg-white text-[13px]" />
                              </td>
                              <td class="px-2 py-1 border-b border-slate-100">
                                <input v-model="entry.destination" maxlength="3" placeholder="MIA"
                                  class="w-14 text-center border border-slate-200 rounded px-2 py-1 outline-none focus:border-slate-700 bg-white text-[13px] uppercase" />
                              </td>
                              <td v-if="(receiptForms[m.id].hawbEntries || []).length > 1"
                                class="px-2 py-1 text-center border-b border-slate-100">
                                <button @click="removeHawbEntry(m.id, ei)" aria-label="Eliminar HAWB"
                                  class="text-slate-400 hover:text-slate-600 transition text-[12px] font-bold">✕</button>
                              </td>
                            </tr>
                          </tbody>
                        </table>
                      </div>
                    </div>

                    <!-- Checkboxes group -->
                    <div class="border-2 border-slate-700 rounded-lg bg-white p-3 shadow-sm">
                      <div class="text-[12px] font-mono font-bold text-slate-800 uppercase tracking-wider mb-2">Flags / Marcas</div>
                      <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-1.5">
                        <label class="text-[12px] font-mono font-bold text-slate-950 flex items-center gap-1.5 cursor-pointer select-none">
                          <input type="checkbox" v-model="receiptForms[m.id].cashOnly" class="accent-slate-700 rounded w-3 h-3" />
                          <span>Cash Only</span>
                        </label>
                        <label class="text-[12px] font-mono font-bold text-slate-950 flex items-center gap-1.5 cursor-pointer select-none">
                          <input type="checkbox" v-model="receiptForms[m.id].bookedInAcoms" class="accent-slate-700 rounded w-3 h-3" />
                          <span>Booked in ACOMS</span>
                        </label>
                        <label class="text-[12px] font-mono font-bold text-slate-950 flex items-center gap-1.5 cursor-pointer select-none">
                          <input type="checkbox" v-model="receiptForms[m.id].docsProvided" class="accent-slate-700 rounded w-3 h-3" />
                          <span>Documents Provided</span>
                        </label>
                        <label class="text-[12px] font-mono font-bold text-slate-950 flex items-center gap-1.5 cursor-pointer select-none">
                          <input type="checkbox" v-model="receiptForms[m.id].customsCompleted" class="accent-slate-700 rounded w-3 h-3" />
                          <span>Export Customs Completed</span>
                        </label>
                        <label class="text-[12px] font-mono font-bold text-slate-950 flex items-center gap-1.5 cursor-pointer select-none">
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
                <div class="text-[12px] font-mono font-bold text-slate-950 uppercase tracking-wider flex items-center gap-2">
                  <span>Loose Tender — Dimensiones y Pesos</span>
                  <span class="text-[12px] font-mono font-normal text-slate-950 normal-case tracking-normal">
                    ((L x W x H) x #pcs) / 366 = Kg dimensional
                  </span>
                </div>

                <template v-if="(receiptHawbs[m.id] || []).length <= 1 && (receiptForms[m.id]?.hawbEntries || []).length <= 1">
                  <div class="overflow-x-auto border border-slate-400 rounded">
                    <table class="w-full text-[13px] font-mono border-collapse">
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
                          class="border-b border-slate-300 hover:bg-slate-50 transition-colors">
                          <td class="px-2 py-1 text-center text-slate-950 border-r border-slate-300">{{ pi + 1 }}</td>
                          <td class="px-2 py-1 border-r border-slate-300">
                            <input v-model.number="p.pieces" type="number" min="0"
                              class="w-full text-center border border-slate-400 rounded px-1.5 py-0.5 outline-none focus:border-slate-500 bg-white text-[13px]"
                              @input="calcPiece(m.id, pi)" />
                          </td>
                          <td class="px-2 py-1 border-r border-slate-300">
                            <input v-model.number="p.lengthIn" type="number" step="0.01" min="0"
                              class="w-full text-center border border-slate-400 rounded px-1.5 py-0.5 outline-none focus:border-slate-500 bg-white text-[13px]"
                              @input="calcPiece(m.id, pi)" />
                          </td>
                          <td class="px-2 py-1 border-r border-slate-300">
                            <input v-model.number="p.widthIn" type="number" step="0.01" min="0"
                              class="w-full text-center border border-slate-400 rounded px-1.5 py-0.5 outline-none focus:border-slate-500 bg-white text-[13px]"
                              @input="calcPiece(m.id, pi)" />
                          </td>
                          <td class="px-2 py-1 border-r border-slate-300">
                            <input v-model.number="p.heightIn" type="number" step="0.01" min="0"
                              class="w-full text-center border border-slate-400 rounded px-1.5 py-0.5 outline-none focus:border-slate-500 bg-white text-[13px]"
                              @input="calcPiece(m.id, pi)" />
                          </td>
                          <td class="px-2 py-1 border-r border-slate-300 text-right font-semibold text-slate-950">{{ p.dimWeight ? p.dimWeight.toFixed(1) : '—' }}</td>
                          <td class="px-2 py-1 border-r border-slate-300">
                            <input v-model.number="p.scaleWeightLbs" type="number" step="0.001" min="0"
                              class="w-full text-center border border-slate-400 rounded px-1.5 py-0.5 outline-none focus:border-slate-500 bg-white text-[13px]"
                              @input="calcPiece(m.id, pi)" />
                          </td>
                          <td class="px-2 py-1 border-r border-slate-300 text-right font-semibold text-slate-950">{{ p.dimWeightLbs ? p.dimWeightLbs.toFixed(1) : '—' }}</td>
                          <td class="px-2 py-1 border-r border-slate-300 text-right font-semibold text-slate-950">{{ (p.scaleWeightKg || 0).toFixed(2) }}</td>
                          <td class="px-2 py-1 border-r border-slate-300 text-right font-semibold">{{ p.dimWeightKg ? p.dimWeightKg.toFixed(2) : '—' }}</td>
                          <td class="px-2 py-1 border-r border-slate-300 text-right text-slate-900">{{ p.chargeableKg ? p.chargeableKg.toFixed(2) : '—' }}</td>
                          <td class="px-2 py-1 border-r border-slate-300 text-right text-slate-900">{{ p.chargeableLbs ? p.chargeableLbs.toFixed(2) : '—' }}</td>
                          <td class="px-2 py-1 text-center">
                            <button @click="removePiece(m.id, pi)" aria-label="Eliminar pieza" class="text-slate-400 hover:text-slate-600 transition text-[12px]">✕</button>
                          </td>
                        </tr>
                      </tbody>
                      <tfoot>
                        <tr class="bg-slate-100 text-slate-950 text-[13px]">
                          <td class="px-2 py-1 border-t border-slate-300 text-slate-950 text-center"></td>
                          <td class="px-2 py-1 border-t border-slate-300 text-center">{{ totalPieces(m.id, null) }}</td>
                          <td class="px-2 py-1 border-t border-slate-300 text-slate-950 text-[13px]" colspan="3">TOTAL</td>
                          <td class="px-2 py-1 border-t border-slate-300 text-right">{{ totalDimWeight(m.id, null).toFixed(1) }}</td>
                          <td class="px-2 py-1 border-t border-slate-300 text-right">{{ totalScaleLbs(m.id, null).toFixed(2) }}</td>
                          <td class="px-2 py-1 border-t border-slate-300 text-right">{{ totalDimLbs(m.id, null).toFixed(2) }}</td>
                          <td class="px-2 py-1 border-t border-slate-300 text-right">{{ totalScaleKg(m.id, null).toFixed(2) }}</td>
                          <td class="px-2 py-1 border-t border-slate-300 text-right">{{ totalDimKg(m.id, null).toFixed(2) }}</td>
                          <td class="px-2 py-1 border-t border-slate-300 text-right text-slate-950">{{ totalChargeableKg(m.id).toFixed(2) }}</td>
                          <td class="px-2 py-1 border-t border-slate-300 text-right text-slate-950">{{ totalChargeableLbs(m.id).toFixed(2) }}</td>
                          <td class="px-2 py-1 border-t border-slate-300"></td>
                        </tr>
                      </tfoot>
                    </table>
                  </div>
                    <div class="flex justify-between items-center">
                      <button @click="addPiece(m.id)" class="text-[12px] text-slate-950 font-mono uppercase tracking-wider hover:text-slate-950 transition">+ Agregar pieza</button>
                      <div class="flex gap-3 text-[12px] font-mono text-slate-950">
                      <span>Piece Count: <strong class="text-slate-900">{{ totalPieces(m.id, null) }}</strong></span>
                      <span>Actual: <strong class="text-slate-900">{{ totalScaleKg(m.id, null).toFixed(0) }} KGS / {{ totalScaleLbs(m.id, null).toFixed(0) }} LBS</strong></span>
                      <span>Chargeable: <strong class="text-slate-900">{{ totalChargeableKg(m.id).toFixed(0) }} KGS / {{ totalChargeableLbs(m.id).toFixed(0) }} LBS</strong></span>
                    </div>
                  </div>
                  <div class="text-[11px] text-slate-400 font-mono mt-1 text-right">* S.KGS = LBS ÷ 2.20462 (auto)</div>
                </template>

                <template v-else>
                  <div v-for="(h, hi) in hawbsForDisplay(m.id)" :key="h._hawbId || h.id" class="border border-slate-400 rounded overflow-hidden bg-white">
                    <div class="flex items-center justify-between bg-slate-100 px-3 py-1.5 border-b border-slate-400">
                      <span class="text-[12px] font-mono font-bold text-slate-950">
                        HAWB {{ hi + 1 }}: {{ h.hawbNumber || h.hawbNumber || '—' }} &mdash; {{ h.consigneeName || h.consigneeName || '—' }}
                        <span class="text-slate-950 font-normal ml-2">({{ piecesByHawb(m.id, h._hawbId || h.id).length }} pieza(s))</span>
                      </span>
                      <span class="text-[12px] font-mono text-slate-950 font-bold">{{ piecesByHawb(m.id, h._hawbId || h.id).reduce((s, p) => s + (p.pieces || 1), 0) }} pcs</span>
                    </div>
                    <div class="overflow-x-auto">
                      <table class="w-full text-[13px] font-mono border-collapse" style="min-width: 850px">
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
                            class="border-b border-slate-300 hover:bg-slate-50">
                            <td class="px-1 py-0.5 text-center text-slate-950 border-r border-slate-300">{{ pi + 1 }}</td>
                            <td class="px-1 py-0.5 border-r border-slate-300">
                              <input v-model.number="entry.piece.pieces" type="number" min="0"
                                class="w-full text-center border border-slate-400 rounded px-1 py-0.5 outline-none focus:border-slate-500 bg-white text-[13px]"
                                @input="calcPiece(m.id, entry.idx)" />
                            </td>
                            <td class="px-1 py-0.5 border-r border-slate-300">
                              <input v-model.number="entry.piece.lengthIn" type="number" step="0.01"
                                class="w-full text-center border border-slate-400 rounded px-1 py-0.5 outline-none focus:border-slate-500 bg-white text-[13px]"
                                @input="calcPiece(m.id, entry.idx)" />
                            </td>
                            <td class="px-1 py-0.5 border-r border-slate-300">
                              <input v-model.number="entry.piece.widthIn" type="number" step="0.01"
                                class="w-full text-center border border-slate-400 rounded px-1 py-0.5 outline-none focus:border-slate-500 bg-white text-[13px]"
                                @input="calcPiece(m.id, entry.idx)" />
                            </td>
                            <td class="px-1 py-0.5 border-r border-slate-300">
                              <input v-model.number="entry.piece.heightIn" type="number" step="0.01"
                                class="w-full text-center border border-slate-400 rounded px-1 py-0.5 outline-none focus:border-slate-500 bg-white text-[13px]"
                                @input="calcPiece(m.id, entry.idx)" />
                            </td>
                            <td class="px-1 py-0.5 border-r border-slate-300 text-right text-slate-950">{{ entry.piece.dimWeight ? entry.piece.dimWeight.toFixed(1) : '—' }}</td>
                            <td class="px-1 py-0.5 border-r border-slate-300">
                              <input v-model.number="entry.piece.scaleWeightLbs" type="number" step="0.001"
                                class="w-full text-center border border-slate-400 rounded px-1 py-0.5 outline-none focus:border-slate-500 bg-white text-[13px]"
                                @input="calcPiece(m.id, entry.idx)" />
                            </td>
                            <td class="px-1 py-0.5 border-r border-slate-300 text-right text-slate-950">{{ entry.piece.dimWeightLbs ? entry.piece.dimWeightLbs.toFixed(1) : '—' }}</td>
                            <td class="px-1 py-0.5 border-r border-slate-300 text-right text-slate-950">{{ (entry.piece.scaleWeightKg || 0).toFixed(2) }}</td>
                            <td class="px-1 py-0.5 border-r border-slate-300 text-right">{{ entry.piece.dimWeightKg ? entry.piece.dimWeightKg.toFixed(2) : '—' }}</td>
                            <td class="px-1 py-0.5 border-r border-slate-300 text-right">{{ entry.piece.chargeableKg ? entry.piece.chargeableKg.toFixed(2) : '—' }}</td>
                            <td class="px-1 py-0.5 border-r border-slate-300 text-right">{{ entry.piece.chargeableLbs ? entry.piece.chargeableLbs.toFixed(2) : '—' }}</td>
                            <td class="px-1 py-0.5 text-center">
                              <button @click="removePiece(m.id, entry.idx)" aria-label="Eliminar pieza" class="text-slate-400 hover:text-slate-600 text-[12px]">✕</button>
                            </td>
                          </tr>
                        </tbody>
                        <tfoot>
                          <tr class="bg-slate-100 text-slate-950 text-[13px]">
                            <td class="px-1 py-0.5 border-t border-slate-400"></td>
                            <td class="px-1 py-0.5 border-t border-slate-400 text-center">{{ hawbTotalPieces(m.id, (h._hawbId || h.id)) }}</td>
                            <td class="px-1 py-0.5 border-t border-slate-400" colspan="3">TOTAL</td>
                            <td class="px-1 py-0.5 border-t border-slate-400 text-right">{{ hawbDimWeight(m.id, (h._hawbId || h.id)).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t border-slate-400 text-right">{{ hawbScaleLbs(m.id, (h._hawbId || h.id)).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t border-slate-400 text-right">{{ hawbDimLbs(m.id, (h._hawbId || h.id)).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t border-slate-400 text-right">{{ hawbScaleKg(m.id, (h._hawbId || h.id)).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t border-slate-400 text-right">{{ hawbDimKg(m.id, (h._hawbId || h.id)).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t border-slate-400 text-right">{{ hawbChargeableKg(m.id, (h._hawbId || h.id)).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t border-slate-400 text-right">{{ hawbChargeableLbs(m.id, (h._hawbId || h.id)).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t border-slate-400"></td>
                          </tr>
                        </tfoot>
                      </table>
                    </div>
                    <div class="px-2 py-1 border-t border-slate-300">
                      <button @click="addPiece(m.id, (h._hawbId || h.id))" class="text-[12px] text-slate-950 font-mono uppercase tracking-wider hover:text-slate-950 transition">+ Agregar pieza a este HAWB</button>
                      <span class="text-[11px] text-slate-400 font-mono ml-4">* S.KGS = LBS ÷ 2.20462 (auto)</span>
                    </div>
                  </div>
                  <!-- Resumen General: agrupado por dimensión única LxWxH -->
                  <div class="border-2 border-slate-600 rounded overflow-hidden bg-white mt-2">
                    <div class="flex items-center justify-between bg-slate-600 px-3 py-1.5 border-b border-slate-700">
                      <span class="text-[12px] font-mono font-bold text-white uppercase tracking-wider">Resumen General — Todas las HAWBs</span>
                      <span class="text-[12px] font-mono text-slate-200">{{ totalPieces(m.id, null) }} piezas · {{ groupedSummary(m.id).length }} dim</span>
                    </div>
                    <div class="overflow-x-auto">
                      <table class="w-full text-[13px] font-mono border-collapse" style="min-width: 900px">
                        <thead>
                          <tr class="bg-slate-200 text-slate-800 text-[13px] uppercase tracking-wider">
                            <th class="px-1 py-0.5 border-r border-slate-300 w-5 text-center">#</th>
                            <th class="px-1 py-0.5 border-r border-slate-300 w-14 text-center">L</th>
                            <th class="px-1 py-0.5 border-r border-slate-300 w-14 text-center">W</th>
                            <th class="px-1 py-0.5 border-r border-slate-300 w-14 text-center">H</th>
                            <th class="px-1 py-0.5 border-r border-slate-300 w-10 text-center">Pcs</th>
                            <th class="px-1 py-0.5 border-r border-slate-300 w-12 text-right">DimWt</th>
                            <th class="px-1 py-0.5 border-r border-slate-300 w-16 text-right">S.LBS</th>
                            <th class="px-1 py-0.5 border-r border-slate-300 w-14 text-right">DLBS</th>
                            <th class="px-1 py-0.5 border-r border-slate-300 w-16 text-right">S.KGS</th>
                            <th class="px-1 py-0.5 border-r border-slate-300 w-14 text-right">DKGS</th>
                            <th class="px-1 py-0.5 border-r border-slate-300 w-14 text-right">CKGS</th>
                            <th class="px-1 py-0.5 w-14 text-right">CLBS</th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr v-for="(g, gi) in groupedSummary(m.id)" :key="gi"
                            class="border-b border-slate-200 hover:bg-slate-50">
                            <td class="px-1 py-0.5 text-center text-slate-500 border-r border-slate-200">{{ gi + 1 }}</td>
                            <td class="px-1 py-0.5 text-center border-r border-slate-200">{{ g.lengthIn || '—' }}</td>
                            <td class="px-1 py-0.5 text-center border-r border-slate-200">{{ g.widthIn || '—' }}</td>
                            <td class="px-1 py-0.5 text-center border-r border-slate-200">{{ g.heightIn || '—' }}</td>
                            <td class="px-1 py-0.5 text-center border-r border-slate-200 font-bold">{{ g.totalPieces }}</td>
                            <td class="px-1 py-0.5 text-right border-r border-slate-200">{{ g.totalDimWeight.toFixed(1) }}</td>
                            <td class="px-1 py-0.5 text-right border-r border-slate-200">{{ g.totalScaleLbs.toFixed(1) }}</td>
                            <td class="px-1 py-0.5 text-right border-r border-slate-200">{{ g.totalDimLbs.toFixed(1) }}</td>
                            <td class="px-1 py-0.5 text-right border-r border-slate-200">{{ g.totalScaleKg.toFixed(2) }}</td>
                            <td class="px-1 py-0.5 text-right border-r border-slate-200">{{ g.totalDimKg.toFixed(2) }}</td>
                            <td class="px-1 py-0.5 text-right border-r border-slate-200">{{ g.totalChargeableKg.toFixed(2) }}</td>
                            <td class="px-1 py-0.5 text-right">{{ g.totalChargeableLbs.toFixed(2) }}</td>
                          </tr>
                        </tbody>
                        <tfoot>
                          <tr class="bg-slate-100 text-slate-950 text-[13px] font-bold">
                            <td class="px-1 py-0.5 border-t-2 border-slate-400" colspan="4">TOTAL</td>
                            <td class="px-1 py-0.5 border-t-2 border-slate-400 text-center">{{ totalPieces(m.id, null) }}</td>
                            <td class="px-1 py-0.5 border-t-2 border-slate-400 text-right">{{ totalDimWeight(m.id, null).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t-2 border-slate-400 text-right">{{ totalScaleLbs(m.id, null).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t-2 border-slate-400 text-right">{{ totalDimLbs(m.id, null).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t-2 border-slate-400 text-right">{{ totalScaleKg(m.id, null).toFixed(2) }}</td>
                            <td class="px-1 py-0.5 border-t-2 border-slate-400 text-right">{{ totalDimKg(m.id, null).toFixed(2) }}</td>
                            <td class="px-1 py-0.5 border-t-2 border-slate-400 text-right">{{ totalChargeableKg(m.id, null).toFixed(2) }}</td>
                            <td class="px-1 py-0.5 border-t-2 border-slate-400 text-right">{{ totalChargeableLbs(m.id, null).toFixed(2) }}</td>
                          </tr>
                        </tfoot>
                      </table>
                    </div>
                  </div>
                </template>
              </div>

              <!-- ═══ STEP 3: REMARKS ═══ -->
              <div v-if="localStep === 3" class="space-y-1.5">
                <label class="ds-label">Remarks / Observaciones</label>
                <textarea v-model="receiptForms[m.id].remarks" rows="3" placeholder="Notas, observaciones, instrucciones especiales..."
                  class="ds-input resize-none"></textarea>
              </div>

              <!-- ═══ STEP 4: EVIDENCE ═══ -->
              <div v-if="localStep === 4" class="space-y-1.5">
                <p class="text-[12px] font-mono text-slate-950">Adjuntar fotos, documentos u otras evidencias</p>

                <!-- Evidencias del MAWB (desde base de datos) -->
                <div v-if="(receiptForms[m.id].mawbEvidence || []).length > 0">
                  <span class="text-[12px] font-mono font-bold text-slate-950 uppercase tracking-wider mb-1 block">{{ t('warehouse.evidence.mawbEvidence') }}</span>
                  <div class="grid grid-cols-2 sm:grid-cols-4 gap-2 mb-2">
                    <div v-for="(ev, ei) in receiptForms[m.id].mawbEvidence" :key="'mawb-' + ei"
                      class="relative border border-slate-200 rounded bg-slate-50/30 overflow-hidden group cursor-pointer" @click="previewEvidence(ev)">
                      <img v-if="ev.type === 'image' && ev.url" :src="ev.url" class="w-full h-20 object-cover" />
                      <div v-else-if="ev.type === 'text'" class="w-full h-20 flex items-center justify-center bg-slate-50 text-slate-950 text-[12px] font-mono px-2 text-center leading-tight">{{ ev.name }}</div>
                      <div v-else-if="isPdfUrl(ev.url)" class="w-full h-20 flex flex-col items-center justify-center bg-slate-100 text-slate-700 text-[12px] font-mono">
                        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="w-6 h-6 mb-0.5"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" /><polyline points="14 2 14 8 20 8" /><line x1="16" y1="13" x2="8" y2="13" /><line x1="16" y1="17" x2="8" y2="17" /><polyline points="10 9 9 9 8 9" /></svg>
                        <span class="text-[10px] leading-tight px-1 text-center truncate max-w-full">PDF</span>
                      </div>
                      <div v-else class="w-full h-20 flex items-center justify-center bg-slate-100 text-slate-950 text-[12px] font-mono">{{ ev.name }}</div>
                      <span class="block text-[12px] font-mono text-slate-950 px-1.5 py-0.5 leading-tight">{{ ev.name }}</span>
                    </div>
                  </div>
                </div>

                <!-- Nuevas evidencias (subidas en este formulario) -->
                <span class="text-[12px] font-mono font-bold text-slate-950 uppercase tracking-wider mb-1 block">Nuevas evidencias (este recibo)</span>
                <div class="grid grid-cols-2 sm:grid-cols-4 gap-2">
                  <div v-for="(ev, ei) in receiptForms[m.id].evidence" :key="'rec-' + ei"
                    class="relative border border-slate-400 rounded bg-white overflow-hidden group cursor-pointer" @click="previewEvidence(ev)">
                    <img v-if="ev.type === 'image'" :src="ev.url" class="w-full h-20 object-cover" />
                    <div v-else-if="isPdfUrl(ev.url)" class="w-full h-20 flex flex-col items-center justify-center bg-slate-100 text-slate-700 text-[12px] font-mono">
                      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="w-6 h-6 mb-0.5"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" /><polyline points="14 2 14 8 20 8" /><line x1="16" y1="13" x2="8" y2="13" /><line x1="16" y1="17" x2="8" y2="17" /><polyline points="10 9 9 9 8 9" /></svg>
                      <span class="text-[11px] leading-tight px-1 text-center truncate max-w-full">PDF</span>
                    </div>
                    <div v-else class="w-full h-20 flex items-center justify-center bg-slate-100 text-slate-950 text-[12px] font-mono">{{ ev.name }}</div>
                    <button @click.stop="removeEvidence(m.id, ei)" aria-label="Eliminar evidencia" class="absolute top-0.5 right-0.5 w-3.5 h-3.5 bg-slate-500 text-white rounded-full text-[12px] flex items-center justify-center opacity-0 group-hover:opacity-100 transition">✕</button>
                    <span class="block text-[12px] font-mono text-slate-950 px-1.5 py-0.5 truncate">{{ ev.name }}</span>
                  </div>
                  <div class="border-2 border-dashed border-slate-400 rounded flex flex-col items-center justify-center cursor-pointer hover:border-slate-950 transition group min-h-[80px]"
                    @click="addEvidence(m.id)">
                    <span class="text-[14px] text-slate-300 font-mono group-hover:text-slate-950 transition leading-none">+</span>
                    <span class="text-[12px] font-mono text-slate-950 mt-0.5 uppercase tracking-wider">Subir</span>
                  </div>
                  <div class="border-2 border-dashed border-slate-400 rounded flex flex-col items-center justify-center cursor-pointer hover:border-slate-950 transition group min-h-[80px]"
                    @click="openCamera(m.id)">
                    <component :is="icons.Camera" :size="16" class="text-slate-300 group-hover:text-slate-950 transition" />
                    <span class="text-[12px] font-mono text-slate-950 mt-0.5 uppercase tracking-wider">Cámara</span>
                  </div>
                </div>
                <CameraCapture :show="showCamera" @close="showCamera = false" @captured="onCameraCapture" />
              </div>

              <!-- ═══ STEP 5: SIGNATURES ═══ -->
              <div v-if="localStep === 5" class="space-y-1.5">
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
                <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 border-t border-slate-400 pt-2">
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
              </div>

              </div> <!-- end scrollable step content -->

              <div class="ops-foot">
                <div class="ops-fl">
                  <button class="btn-ghost" @click="prevStep" :disabled="localStep === 1">&#9664; Atras</button>
                  <button class="btn-ghost danger" @click="cancelForm">&#10005; Cancelar</button>
                  <span v-if="successMsg" class="ops-ok">{{ successMsg }}</span>
                </div>
                <div v-if="localStep < 5" class="ops-fr">
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
            <button @click="closeMawbEvidenceMgr" aria-label="Cerrar" class="text-slate-950 hover:text-slate-950 transition text-base">✕</button>
          </div>
          <div class="p-4 overflow-y-auto" style="max-height: calc(80vh - 120px);">
            <div v-if="mawbEvidenceMgr.docs.length === 0" class="text-[14px] font-mono text-slate-950 text-center py-6 uppercase tracking-widest">
              Sin evidencias documentales
            </div>
            <div v-else class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-2 mb-4">
              <div v-for="(doc, di) in mawbEvidenceMgr.docs" :key="di"
                class="relative border border-slate-400 rounded overflow-hidden bg-white group cursor-pointer" @click="previewEvidence(doc)">
                <img v-if="doc.type === 'image' && doc.url" :src="doc.url" class="w-full h-20 object-cover" />
                <div v-else-if="isPdfUrl(doc.url)" class="w-full h-20 flex flex-col items-center justify-center bg-slate-100 text-slate-700 font-mono">
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="w-7 h-7 mb-0.5"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" /><polyline points="14 2 14 8 20 8" /><line x1="16" y1="13" x2="8" y2="13" /><line x1="16" y1="17" x2="8" y2="17" /><polyline points="10 9 9 9 8 9" /></svg>
                  <span class="text-[12px] leading-tight px-1 text-center truncate max-w-full">PDF</span>
                </div>
                <div v-else class="w-full h-20 flex items-center justify-center bg-slate-100 text-slate-950 text-[13px] font-mono">{{ doc.name }}</div>
                <button @click.stop="removeMawbEvidence(di)" aria-label="Eliminar evidencia" class="absolute top-0.5 right-0.5 w-3.5 h-3.5 bg-slate-500 text-white rounded-full text-[12px] flex items-center justify-center opacity-0 group-hover:opacity-100 transition">✕</button>
                <span class="block text-[13px] font-mono text-slate-950 px-2 py-1 truncate">{{ doc.name }}</span>
              </div>
            </div>
            <div class="flex items-center gap-2 border-t border-slate-300 pt-3">
              <button @click="mawbEvidenceInput.click()"
                class="ds-btn-secondary text-[11px] px-2.5 py-1">
                + Subir archivo
              </button>
              <button @click="openMawbCamera()"
                class="ds-btn-secondary text-[11px] px-2.5 py-1 flex items-center gap-1">
                <component :is="icons.Camera" :size="12" /> Cámara
              </button>
              <button v-if="mawbEvidenceMgr.docs.length > 0" @click="downloadMawbEvidencePdf()"
                class="ml-auto ds-btn-primary text-[11px] px-2.5 py-1">
                &#128196; PDF
              </button>
            </div>
            <input type="file" ref="mawbEvidenceInput" @change="handleMawbEvidenceUpload" accept="image/*,.pdf" class="hidden" />
            <CameraCapture :show="mawbCameraOpen" @close="mawbCameraOpen = false" @captured="onMawbCameraCapture" />
          </div>
          <div class="flex justify-end px-4 py-2.5 border-t border-slate-200 bg-slate-50">
            <button @click="saveMawbEvidence()"
              class="ds-btn-primary text-[11px] px-3 py-1.5">
              Guardar cambios
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
            <button @click="showConfirmModal = false" aria-label="Cerrar" class="text-slate-950 hover:text-slate-950 transition text-base">✕</button>
          </div>
          <div class="overflow-y-auto" style="max-height: calc(80vh - 110px);">
            <template v-if="pendingSubmitMawb">
              <div class="px-4 py-3 space-y-2 text-[13px] font-mono text-slate-950">
                <div class="grid grid-cols-2 gap-x-4 gap-y-1.5">
                  <span class="text-slate-500 uppercase tracking-wider">MAWB</span>
                  <span class="font-bold text-right">{{ pendingSubmitMawb.awbNumber }}</span>
                  <span class="text-slate-500 uppercase tracking-wider">{{ t('warehouse.form.destination') }}</span>
                  <span class="font-bold text-right">{{ receiptForms[pendingSubmitMawb.id]?.destination || pendingSubmitMawb.destination || '—' }}</span>
                  <span class="text-slate-500 uppercase tracking-wider">{{ t('common.pieces') }}</span>
                  <span class="font-bold text-right">{{ (receiptForms[pendingSubmitMawb.id]?.pieces || []).reduce((s, p) => s + (p.pieces || 1), 0) }}</span>
                  <span class="text-slate-500 uppercase tracking-wider">Peso (Kg)</span>
                  <span class="font-bold text-right">{{ (receiptForms[pendingSubmitMawb.id]?.pieces || []).reduce((s, p) => s + (p.scaleWeightKg || 0), 0).toFixed(1) }}</span>
                  <span class="text-slate-500 uppercase tracking-wider">{{ t('warehouse.form.shipperName') }}</span>
                  <span class="font-bold text-right truncate">{{ receiptForms[pendingSubmitMawb.id]?.shipperName || pendingSubmitMawb.shipperName || '—' }}</span>
                  <span class="text-slate-500 uppercase tracking-wider">Consignee</span>
                  <span class="font-bold text-right truncate">{{ receiptForms[pendingSubmitMawb.id]?.consigneeName || pendingSubmitMawb.consigneeName || '—' }}</span>
                </div>
                <div class="border-t border-slate-300 pt-2 mt-2">
                  <div class="flex flex-wrap gap-2">
                    <span v-if="receiptForms[pendingSubmitMawb.id]?.cashOnly" class="bg-slate-100 text-slate-700 px-1.5 py-0.5 rounded text-[12px] uppercase tracking-wider">Cash Only</span>
                    <span v-if="receiptForms[pendingSubmitMawb.id]?.bookedInAcoms" class="bg-slate-100 text-slate-700 px-1.5 py-0.5 rounded text-[12px] uppercase tracking-wider">Booked in ACOMS</span>
                    <span v-if="receiptForms[pendingSubmitMawb.id]?.docsProvided" class="bg-slate-100 text-slate-700 px-1.5 py-0.5 rounded text-[12px] uppercase tracking-wider">Docs Provided</span>
                    <span v-if="receiptForms[pendingSubmitMawb.id]?.customsCompleted" class="bg-slate-100 text-slate-700 px-1.5 py-0.5 rounded text-[12px] uppercase tracking-wider">Customs Done</span>
                    <span v-if="receiptForms[pendingSubmitMawb.id]?.preBuilt" class="bg-slate-100 text-slate-700 px-1.5 py-0.5 rounded text-[12px] uppercase tracking-wider">Pre-built</span>
                  </div>
                </div>
                <div class="border-t border-slate-300 pt-2 mt-2 flex justify-between text-[12px] text-slate-500">
                  <span>{{ (receiptForms[pendingSubmitMawb.id]?.evidence || []).length }} evidencias</span>
                  <span>{{ (receiptForms[pendingSubmitMawb.id]?.pieces || []).filter(p => p.lengthIn).length }} piezas con dimensiones</span>
                </div>
              </div>
            </template>
          </div>
          <div class="flex items-center justify-end gap-2 px-4 py-2 border-t border-slate-200 bg-slate-50">
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
            <p class="text-[14px] font-mono text-slate-600">
              Las piezas recibidas superan las reservadas en el Booking. El sistema corregira automaticamente:
            </p>
            <div v-for="(c, idx) in pendingBookingCorrections" :key="idx"
              class="bg-amber-50 border border-amber-200 rounded-md px-3 py-2.5">
              <div class="text-[13px] font-mono text-amber-900" v-html="formatCorrection(c)"></div>
            </div>
            <p class="text-[13px] font-mono text-slate-500 italic">
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
            <button @click="closeEvidencePreview" aria-label="Cerrar" class="text-slate-950 hover:text-slate-950 transition text-base">✕</button>
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
          <div class="flex items-center justify-end gap-2 px-4 py-2 border-t border-slate-200 bg-slate-50">
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, reactive } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '../stores/app'

const { t } = useI18n()
import SignaturePad from '../components/SignaturePad.vue'
import CameraCapture from '../components/CameraCapture.vue'
import EditReceiptModal from '../components/EditReceiptModal.vue'
import LocaleDatePicker from '../components/LocaleDatePicker.vue'
import EmptyState from '../components/EmptyState.vue'
import { useIcons } from '../composables/useIcons'
import { hawbsApi } from '../api/hawbs'

const icons = useIcons()
import { mawbsApi } from '../api/mawbs'
import { receiptsApi } from '../api/receipts'
import { calcConfigApi } from '../api/receiptCalcConfig'
import { useToastStore } from '../stores/toast'
import { extractError } from '../utils/error'
import { useConfirm } from '../composables/useConfirm'
import { useLiveRefresh } from '../composables/useLiveRefresh'

const store = useAppStore()
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

function normAwbCode(s) {
  return (s || '').toUpperCase().replace(/[\s\-_/]/g, '')
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
  const targetCode = normAwbCode(m.awbNumber)

  // Obtener links de ULD-AWB para esta MAWB
  const links = (uldAwbs || []).filter(ua => {
    if (ua.mawbId && ua.mawbId === mawbId) return true
    return ua.mawbLabel && normAwbCode(ua.mawbLabel) === targetCode
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
      if (r.superseded) continue
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

function isReceiptCompletePure(m, receipts) {
  const rid = (receipts || []).find(r => (r.mawb?.id || r.mawbId) === m.id && !r.superseded)
  if (!rid) return false
  return !!rid && !!rid.pieceCount && rid.pieceCount > 0
}

function deriveMawbOperationalStatus(m) {
  return deriveMawbOperationalStatusPure(m, store.uldAwbs, store.flights, store.receipts)
}

function isReceiptComplete(m) {
  const rid = (store.receipts || []).find(r => (r.mawb?.id || r.mawbId) === m.id && !r.superseded)
  if (!rid) return false
  return !!rid.pieceCount && rid.pieceCount > 0
}

// True cuando el MAWB está en ULDs pero el total montado < piezas recibidas
// (carga parcial en curso — nota junto a DESPACHADA parcial y EN_PROCESO).
function isPartialLoadingPure(m, uldAwbs, receipts) {
  const mawbId = m.id
  const targetCode = normAwbCode(m.awbNumber)
  const links = (uldAwbs || []).filter(ua => {
    if (ua.mawbId && ua.mawbId === mawbId) return true
    return ua.mawbLabel && normAwbCode(ua.mawbLabel) === targetCode
  })
  if (!links.length) return false
  let onUld = 0
  for (const link of links) onUld += link.pieces || 0
  const receivedPieces = (() => {
    const totals = {}
    for (const r of receipts || []) {
      if (r.superseded) continue
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
  const map = {}
  for (const m of store.mawbs) {
    map[m.id] = deriveMawbOperationalStatusPure(m, store.uldAwbs, store.flights, store.receipts)
  }
  return map
})

// MAWBs registradas hace más de EXPIRED_PENDING_DAYS días sin recibo completado
// (aún PENDIENTE) se eliminan del registro de recibos pendientes: no aparecen en
// la tabla, el pipeline, los contadores ni los filtros. Se cuenta desde la fecha de
// registro (createdAt). El conteo es estrictamente > 7 días.
const EXPIRED_PENDING_DAYS = 7
function mawbExpiredDays(m) {
  if (!m.createdAt) return 0
  const t = new Date(m.createdAt).getTime()
  if (Number.isNaN(t)) return 0
  return (Date.now() - t) / 86400000
}
function isExpiredPending(m) {
  return mawbOperationalStatusMap.value[m.id] === 'PENDIENTE' && mawbExpiredDays(m) > EXPIRED_PENDING_DAYS
}
const expiredPendingIds = computed(() => new Set(store.mawbs.filter(isExpiredPending).map(m => m.id)))

const expandedId = ref(localStorage.getItem('WAREHOUSE_EXPANDED_MAWB') || null)
const queueExpanded = ref(true)
const localStep = ref(1)
const submitting = ref(false)
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
const showCamera = ref(false)
const cameraMawbId = ref(null)

const receiptForms = reactive({})
const receiptHawbs = reactive({})
const generatedReceiptId = ref(null)

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
      brokerName: f.brokerName,
      brokerIdNum: f.brokerIdNum,
      brokerSig: f.brokerSig || '',
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
    brokerName: draft.brokerName ?? f.brokerName,
    brokerIdNum: draft.brokerIdNum ?? f.brokerIdNum,
    brokerSig: draft.brokerSig ?? f.brokerSig,
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
  pipelineClock = setInterval(() => { refreshNow.value = Date.now() }, 5000)
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
  const link = (store.uldAwbs || []).find(x => (x.mawbId && x.mawbId === m.id) || normAwbCode(x.mawbLabel || '') === normAwbCode(m.awbNumber || ''))
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
  return [code, num].filter(Boolean).join('-') || f.id?.slice(0, 8)
}
function flightSub(key) {
  const g = groupByKey(key)
  const f = g?.flight
  if (!f) return t('warehouse.noFlight')
  const date = fmtFlightDate(f.flightDate)
  const route = [f.origin, f.destination].filter(Boolean).join(' → ')
  return [date, route].filter(Boolean).join(' · ')
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
    if (expiredPendingIds.value.has(m.id)) continue
    const s = mawbOperationalStatusMap.value[m.id]
    if (s && s in c) c[s]++
  }
  return c
})
const pipeTotal = computed(() => store.mawbs.length - expiredPendingIds.value.size)
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
    if (expiredPendingIds.value.has(m.id)) continue
    const k = flightKeyOf(m)
    if (!k || k === 'none') continue
    if (!seen.has(k)) seen.set(k, { key: k, label: fmtFlight(m) })
  }
  return [...seen.values()].sort((a, b) => a.label.localeCompare(b.label))
})

const filteredMawbs = computed(() => {
  let list = store.mawbs.filter(m => !expiredPendingIds.value.has(m.id))
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

const steps = ['HEADER', 'PIECES', 'REMARKS', 'EVIDENCE', 'SIGNATURES']

function editOrExpandReceipt(m) {
  const rid = receiptById.value[m.id]
  if (rid && editModalRef.value) {
    editModalRef.value.open(rid)
  } else {
    toggleExpand(m)
  }
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
  if (store.selectedFlightId) {
    await store.loadMawbs(store.selectedFlightId)
  } else {
    await store.loadAllMawbs()
  }
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
      receivedBySig: '',
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
  f.receivedByName = sourceReceipt.receivedByName ?? f.receivedByName
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
  if (si === 2) return true // remarks optional
  if (si === 3) return true // evidence optional
  if (si === 4) return !!(d.printName || d.dockSignature)
  return false
}

function stepError(_si) {
  return false // could add backend validation errors here
}

async function toggleExpand(m) {
  if (expandedId.value === m.id) {
    cancelForm()
  } else {
    expandedId.value = m.id
    localStep.value = 1
    generatedReceiptId.value = null
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

function nextStep() { if (localStep.value < 5) localStep.value++ }
function prevStep() { if (localStep.value > 1) localStep.value-- }

function openCamera(mawbId) {
  cameraMawbId.value = mawbId
  showCamera.value = true
}

function onCameraCapture(dataUrl) {
  const mawbId = cameraMawbId.value
  if (mawbId && dataUrl) {
    receiptForms[mawbId].evidence.push({
      name: 'Foto_' + new Date().toISOString().slice(0, 19).replace(/[T:]/g, '-') + '.jpg',
      type: 'image',
      url: dataUrl,
      file: null,
    })
  }
  cameraMawbId.value = null
}

function addEvidence(mawbId) {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/*,.pdf'
  input.style.cssText = 'position:fixed;left:-9999px;top:-9999px;width:0;height:0;opacity:0;pointer-events:none'
  const handler = (e) => {
    input.removeEventListener('change', handler)
    input.remove()
    handleEvidenceUpload(mawbId, e)
  }
  input.addEventListener('change', handler)
  document.body.appendChild(input)
  input.click()
}

function isPdfUrl(url) {
  if (!url) return false
  if (url.startsWith('data:application/pdf')) return true
  if (url.toLowerCase().endsWith('.pdf')) return true
  return false
}

function compressImage(dataUrl, maxDim = 720, quality = 0.4) {
  return new Promise(resolve => {
    const img = new Image()
    img.onload = () => {
      let w = img.width, h = img.height
      if (w > maxDim || h > maxDim) {
        const s = Math.min(maxDim / w, maxDim / h, 1)
        w = Math.round(w * s)
        h = Math.round(h * s)
      }
      const c = document.createElement('canvas')
      c.width = w; c.height = h
      c.getContext('2d').drawImage(img, 0, 0, w, h)
      resolve(c.toDataURL('image/jpeg', quality))
    }
    img.onerror = () => resolve(dataUrl)
    img.src = dataUrl
  })
}

async function handleEvidenceUpload(mawbId, e) {
  const files = e.target.files
  if (!files || !files.length) return
  for (const file of files) {
    const reader = new FileReader()
    reader.onload = async (ev) => {
      let url = ev.target.result
      if (file.type.startsWith('image/')) {
        url = await compressImage(url)
      }
      receiptForms[mawbId].evidence.push({
        name: file.name,
        type: file.type.startsWith('image/') ? 'image' : 'document',
        url,
        file: file,
      })
    }
    reader.readAsDataURL(file)
  }
  e.target.value = ''
}

function removeEvidence(mawbId, idx) {
  receiptForms[mawbId].evidence.splice(idx, 1)
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
  if (f.pieces.length === 0) {
    toast.warning('Ingresa al menos una pieza')
    return
  }
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

async function submitReceipt(m) {
  if (submitting.value) return
  const f = receiptForms[m.id]
  const hawbs = receiptHawbs[m.id] || []
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
    console.warn('[Submit] START', { mawbId: m.id, awbNumber: m.awbNumber, pieceCount: f.pieces.length, hawbCount: hawbs.length })
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

    function buildPayload(pieceList, remarkSuffix) {
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

    const payload = hawbs.length <= 1
      ? buildPayload(f.pieces, '')
      : buildPayload(f.pieces, 'RECIBO GENERAL')

    // ─── VALIDATE FIRST (dry-run) ────────────────────────
    const newPieceCount = f.pieces.reduce((s, p) => s + (p.pieces || 1), 0)
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

async function executeEmit(m, hawbs, payload) {
  const f = receiptForms[m.id]
  if (!f) return

  const res = await store.emitReceipt(payload)
  const receiptId = res?.id || null
  if (receiptId) generatedReceiptId.value = receiptId

  await store.loadReceipts()
  await loadExistingReceiptData(m)
  bumpFormVersion()
  localStep.value = 5
  clearDraft(m.id)

  if (store.selectedFlightId) {
    await store.loadMawbs(store.selectedFlightId)
  } else {
    await store.loadAllMawbs()
  }
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
    if (r.superseded) continue
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
    if ((st === 'CARGADA' || st === 'EN_PROCESO') && !isReceiptComplete(m)) {
      seen.add(m.id)
      items.push({ mawb: m, flight: flightMap[m.flightId] || null, whyKey: st === 'EN_PROCESO' ? 'warehouse.attention.whyPartial' : 'warehouse.attention.whyLoaded' })
    }
  }
  return items
})

const receiptById = computed(() => {
  const map = {}
  for (const r of store.receipts || []) {
    if (r.superseded) continue
    const mawbId = r.mawb?.id || r.mawbId
    if (mawbId) map[mawbId] = r.id
  }
  return map
})

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
const mawbEvidenceInput = ref(null)
const mawbCameraOpen = ref(false)

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

function handleMawbEvidenceUpload(e) {
  const files = e.target.files
  if (!files || !files.length) return
  for (const file of files) {
    const reader = new FileReader()
    reader.onload = (ev) => {
      mawbEvidenceMgr.docs.push({
        name: file.name,
        type: file.type.startsWith('image/') ? 'image' : 'document',
        url: ev.target.result,
      })
      mawbEvidenceMgr.dirty = true
    }
    reader.readAsDataURL(file)
  }
  e.target.value = ''
}

function openMawbCamera() {
  mawbCameraOpen.value = true
}

function onMawbCameraCapture(dataUrl) {
  if (dataUrl) {
    mawbEvidenceMgr.docs.push({
      name: 'Foto_' + new Date().toISOString().slice(0, 19).replace(/[T:]/g, '-') + '.jpg',
      type: 'image',
      url: dataUrl,
    })
    mawbEvidenceMgr.dirty = true
  }
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
    toast.error('Error guardando evidencias: ' + (e.response?.data?.error || e.message))
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
  if (!store.airlines.length) await store.loadAirlines()
  if (!store.flights.length) await store.loadFlights()
  await store.loadReceipts()
  await Promise.all([store.loadUldAwbs(), store.loadAllMawbs(), store.loadUlds()])
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
    store.selectedFlightId ? store.loadMawbs(store.selectedFlightId, { silent: true }) : store.loadAllMawbs({ silent: true }),
  ])
  statusSnapshot.value = takeStatusSnapshot()
  flashChangedRows(prev)
}

useLiveRefresh(refreshData,
{ interval: 45000, pauses: [submitting, showConfirmModal, showBookingCorrectionModal, showCamera] })
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
  min-width: 1000px; user-select: none; transition: background .12s; }
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
@media (min-width: 768px) { .receipt-form { --receipt-form-h: 58dvh; } }
@media (min-height: 800px) { .receipt-form { --receipt-form-h: 56dvh; } }

/* ── Responsive móvil: la lista MAWB principal fuerza min-width:700px con
   scroll horizontal. En <=640px dejamos que el grid colapse (wrap) para
   mostrar las columnas esenciales apiladas, y ocultamos las secundarias. */
@media (max-width: 640px) {
  .receipt-list-header { min-width: 0 !important; }
  .receipt-list-cell[data-col="shipper"],
  .receipt-list-cell[data-col="dest"],
  .receipt-list-cell[data-col="docs"],
  .receipt-list-cell[data-col="flight"] { display: none !important; }
  .receipt-list-cell { font-size: 11px; }
}

/* ── OPS modal wizard (OPS-RCA-v6) ─────────────────────────────── */
.ops-overlay { position: fixed; inset: 0; z-index: 60; background: rgba(24,25,42,.55);
  -webkit-backdrop-filter: blur(2px); backdrop-filter: blur(2px);
  display: flex; align-items: center; justify-content: center; padding: 18px; }
.ops-modal { width: min(940px,100%); height: auto; max-width: calc(100vw - 36px); max-height: calc(100dvh - 36px);
  min-width: 560px; min-height: 380px; display: flex; flex-direction: column;
  background: #F2F4F8; border: 1px solid #DDE1EC; border-top: 3px solid #2B5CE6;
  border-radius: 10px; box-shadow: 0 24px 60px rgba(15,23,42,.35);
  position: relative; overflow: hidden; resize: both; }
.ops-modal::after { content: ''; position: absolute; right: 3px; bottom: 3px; width: 14px; height: 14px;
  background: linear-gradient(135deg, transparent 0, transparent 5px, #C3C9DA 5px, #C3C9DA 7px, transparent 7px, transparent 12px, #C3C9DA 12px, #C3C9DA 14px);
  opacity: .6; pointer-events: none; }

.ops-hdr { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px 16px;
  background: #fff; border-bottom: 1px solid #ECEEF5; }
.ops-tt { font-size: 15px; font-weight: 800; color: #0B1220; line-height: 1.2; }
.ops-sub { font-size: 11px; font-family: var(--font-family-mono, monospace); color: #9298AF; }
.xbtn { display: inline-flex; align-items: center; justify-content: center; width: 32px; height: 32px;
  border-radius: 7px; border: 1px solid #ECEEF5; background: #F8F9FC; color: #5B6275;
  font-size: 15px; cursor: pointer; transition: background .12s, color .12s; }
.xbtn:hover { background: #FDE8E8; color: #C2372F; }

.ops-steps { display: flex; align-items: center; gap: 0; flex-wrap: wrap; padding: 10px 16px;
  background: #fff; border-bottom: 1px solid #ECEEF5; }
.ops-step { display: flex; align-items: center; gap: 8px; }
.ops-sdot { display: inline-flex; align-items: center; justify-content: center; width: 28px; height: 28px;
  border-radius: 50%; background: #E8EAF2; border: 1.5px solid #DDE1EC; color: #9298AF;
  font-size: 12px; font-weight: 700; cursor: pointer; user-select: none; transition: all .12s; }
.ops-sdot.on { background: #2B5CE6; border-color: #2B5CE6; color: #fff; box-shadow: 0 0 0 3px rgba(43,92,230,.15); }
.ops-sdot.dn { background: #1F9E5A; border-color: #1F9E5A; color: #fff; }
.ops-slbl { font-size: 8px; font-weight: 800; text-transform: uppercase; letter-spacing: .06em;
  color: #9298AF; cursor: pointer; transition: color .12s; }
.ops-sdot.on + .ops-slbl { color: #2B5CE6; }
.ops-sln { width: 26px; height: 2px; background: #DDE1EC; margin: 0 4px; border-radius: 2px; }
.ops-sln.dn { background: #1F9E5A; }

.ops-body { flex: 1 1 auto; min-height: 0; overflow-y: auto; padding: 14px 16px; }

.ops-foot { display: flex; align-items: center; justify-content: space-between; gap: 10px;
  padding: 12px 16px; background: #fff; border-top: 1px solid #ECEEF5; }
.ops-fl { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.ops-fr { display: flex; align-items: center; gap: 8px; }
.ops-ok { font-size: 12px; font-family: var(--font-family-mono, monospace); color: #1F9E5A; }

.btn-ok { display: inline-flex; align-items: center; gap: 6px; padding: 9px 18px; border: none;
  border-radius: 7px; background: #2B5CE6; color: #fff; font-size: 13px; font-weight: 700;
  cursor: pointer; transition: background .12s, box-shadow .12s; }
.btn-ok:hover { background: #4A74F0; box-shadow: 0 6px 16px rgba(43,92,230,.35); }
.btn-ok:disabled { opacity: .55; cursor: not-allowed; box-shadow: none; }
.btn-ghost { display: inline-flex; align-items: center; gap: 6px; padding: 8px 14px; border: 1px solid #DDE1EC;
  border-radius: 7px; background: #fff; color: #5B6275; font-size: 13px; font-weight: 700;
  cursor: pointer; transition: all .12s; }
.btn-ghost:hover { background: #F4F6FB; color: #2B5CE6; }
.btn-ghost.danger:hover { background: #FDE8E8; color: #C2372F; border-color: #F3C7C3; }
.btn-ghost:disabled { opacity: .5; cursor: not-allowed; }

/* Restyling of the shared form pieces inside the wizard body */
.ops-body .ds-label { font-size: 8px; font-weight: 800; text-transform: uppercase; letter-spacing: .06em; color: #9298AF; }
.ops-body .ds-input { background: transparent; border: none; border-bottom: 1.5px solid #DDE1EC;
  border-radius: 0; padding: 4px 2px; font-size: 13px; }
.ops-body .ds-input:focus { border-bottom-color: #2B5CE6; box-shadow: none; }
.ops-body thead tr { background: #2B5CE6; }
.ops-body thead th { color: #fff; }
.ops-body tfoot tr { background: #EAF0FF; }

/* ——— Mockup App3 pasada: pipeline (s1) + teal ——— */
.pipe { background: var(--surface-0, #fff); border: 1px solid var(--border); }
.pipe-hd { border-bottom: 1px solid var(--border-2); }
.pipe-total { display: inline-flex; align-items: center; justify-content: center; min-width: 22px; height: 22px;
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
/* Wizard teal pass (reemplaza el azul #2B5CE6 del boceto) */
.ops-sdot.on { background: #0d9488; border-color: #0d9488; box-shadow: 0 0 0 3px rgba(13,148,136,.15); }
.ops-sdot.on + .ops-slbl { color: #0d9488; }
.ops-body thead tr { background: #0d3b37; }
.btn-ok { background: #0d9488; }
.btn-ok:hover { background: #0f766e; box-shadow: 0 6px 16px rgba(13,148,136,.35); }
.btn-ghost:hover { color: #0d9488; }

@media (max-width: 640px) {
  .ops-overlay { padding: 8px; }
  .ops-modal { border-radius: 8px; max-height: 94dvh; max-width: 100%; min-width: 0; min-height: 0; resize: none; }
  .ops-modal::after { display: none; }
  .ops-slbl { display: none; }
  .ops-sdot { width: 24px; height: 24px; font-size: 11px; }
  .ops-body { padding: 10px 12px; }
  .ops-hdr { padding: 10px 12px; }
  .ops-foot { padding: 10px 12px; }
}
</style>
