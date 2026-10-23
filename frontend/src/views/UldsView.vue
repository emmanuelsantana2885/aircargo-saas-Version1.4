<template>
  <div class="ds-page">
    <header class="ds-section-header">
      <div class="flex items-center gap-4">
        <div>
          <h1 class="ds-title">{{ t('ulds.title') }}</h1>
          <p class="ds-subtitle">{{ t('ulds.subtitle') }}</p>
        </div>
      </div>
      <div class="flex items-center gap-2 flex-wrap">
        <span v-if="pendingReceiptCount > 0"
          class="text-[13px] font-mono font-bold text-amber-800 bg-amber-50 border border-amber-200 px-2 py-1 rounded-lg">
          &#9888; {{ t('ulds.pendingReceipts', { n: pendingReceiptCount }) }}
        </span>
        <span class="ds-chip">{{ t('ulds.listCount', filteredUlDs.length) }}</span>
        <FilterBar
          v-model:status="statusFilter"
          v-model:search-text="searchText"
          v-model:destination="destFilter"
          :show-status="true"
          :show-search="true"
          :show-destination="true"
          :status-options="uldStatusOptions"
          :search-placeholder="t('ulds.searchPlaceholder')"
          container-class="!gap-1"
        >
          <div class="flex flex-col gap-0.5">
            <label class="ds-label">{{ t('ulds.flightFilter') }}</label>
            <select
              v-model="flightFilterId"
              class="ds-input w-auto min-w-[180px]"
              :title="t('ulds.flightFilterHint')"
            >
              <option value="">{{ t('ulds.allFlights') }}</option>
              <option v-for="f in flightOptions" :key="f.id" :value="f.id">
                {{ flightOptionLabel(f) }}
              </option>
            </select>
          </div>
        </FilterBar>
        <button @click="createNewBlankUld"
          class="ds-btn-primary">
          <span class="text-[12px] font-sans">&#65291;</span> {{ t('ulds.createUld') }}
        </button>
      </div>
    </header>

    <section class="ds-table-section mb-1.5">
      <EmptyState v-if="appStore.loading && !localUlds.length" :title="t('ulds.loading')" loading />

      <EmptyState v-else-if="filteredUlDs.length === 0" :title="t('ulds.emptyCreate')" :hint="t('ulds.emptyHint')" :icon="icons.Package">
        <button @click="createNewBlankUld" class="ds-btn-primary mt-1">
          <span class="text-[12px] font-sans">&#65291;</span> {{ t('ulds.createUld') }}
        </button>
      </EmptyState>

      <div v-else class="flex-1 min-h-0 flex flex-col lg:flex-row overflow-hidden">
        <div ref="detailPanel" class="ds-split-detail">
          <template v-if="expandedUld">
            <div v-for="uld in [expandedUld]" :key="'f-'+uld.uid" class="p-4">
              <div class="bg-surface border border-strong rounded shadow-sm max-w-5xl mx-auto p-3 pb-44 md:p-6 md:pb-44 lg:p-6 font-mono text-sm relative">
                  <div class="flex justify-between items-center border-b border-strong pb-3 mb-5">
                    <div class="flex items-center gap-2">
                      <span class="text-[13px] font-bold text-primary uppercase tracking-wider">{{ t('ulds.palletSheetHeader') }}</span>
                  </div>
                  <div class="flex items-center gap-1.5 bg-emerald-50 border border-emerald-300 rounded-full pl-2.5 pr-2 py-1">
                    <span class="text-emerald-600 font-bold text-[13px]">&#10003;</span>
                    <span class="text-[12px] font-bold text-emerald-800 uppercase tracking-wide">{{ t('ulds.volume') }}</span>
                    <input v-model.number="uld.volumePct" type="number" min="0" max="100" inputmode="decimal"
                      class="w-12 text-center bg-transparent border-0 rounded font-bold text-emerald-900 focus:outline-none focus:ring-1 focus:ring-emerald-400 text-[14px] pct-input" />
                    <span class="text-[13px] font-bold text-emerald-800">%</span>
                  </div>
                </div>

                <div class="grid grid-cols-2 gap-3 sm:gap-5 mb-6">
                  <div>
                    <label class="ds-label block mb-1 flex items-center gap-1.5">
                      {{ scanFocus === 'uld' ? t('scan.field.uldScanning') : t('ulds.form.uldCode') }} *
                      <span v-if="creationStep === 2" class="text-[10px] bg-emerald-100 text-emerald-700 px-1.5 py-px rounded font-bold">{{ t('ulds.scanOrType') }}</span>
                    </label>
                    <div class="relative">
                      <input ref="uldCodeInput" v-model="uld.uldNumber" type="text" placeholder="PMC-XXXXX"
                        class="ds-input uppercase transition-all duration-300 pr-9"
                        :class="scanFieldClass('uld', uld.uldNumber, creationStep === 2 ? 'ring-2 ring-emerald-400 bg-emerald-50 border-emerald-400' : '')" />
                      <span v-if="uld.uldNumber && scanFocus !== 'uld'"
                        class="absolute right-3 top-1/2 -translate-y-1/2 text-emerald-600 font-bold text-[15px] pointer-events-none">&#10003;</span>
                    </div>
                    <div v-if="scanFieldError === 'uld'" class="mt-1 text-[11px] font-bold text-red-600">{{ t('scan.field.uldError') }}</div>
                  </div>
                  <div>
                    <label class="ds-label block mb-1">{{ t('ulds.form.configType') }}</label>
                    <UldTypeDropdown
                      v-model="uld.uldType"
                      :items="uldCatalogItems"
                      :title="t('ulds.form.configType')"
                      :placeholder="t('common.selectType')"
                    />
                  </div>
                  <div>
                    <label class="ds-label block mb-1">{{ t('ulds.form.position') }}</label>
                    <input v-model="uld.position" type="text" placeholder="1L" class="ds-input uppercase" />
                  </div>
                  <div>
                    <label class="ds-label block mb-1 flex items-center gap-1.5">
                      {{ scanFocus === 'seal' ? t('scan.field.sealScanning') : t('ulds.form.sealNumber') }}
                    </label>
                    <div class="relative">
                      <input ref="sealInput" v-model="uld.sealNumber" type="text" placeholder="SC-XXXXXXXX"
                        class="ds-input font-bold transition-all duration-300 pr-9"
                        :class="scanFieldClass('seal', uld.sealNumber)" />
                      <span v-if="uld.sealNumber && scanFocus !== 'seal'"
                        class="absolute right-3 top-1/2 -translate-y-1/2 text-emerald-600 font-bold text-[15px] pointer-events-none">&#10003;</span>
                    </div>
                    <div v-if="scanFieldError === 'seal'" class="mt-1 text-[11px] font-bold text-red-600">{{ t('scan.field.sealError') }}</div>
                  </div>
                </div>

                <!-- SCAN PANEL -->
                <ScanPanel
                  :active="scanMode"
                  :uld-id="uld.backendId || ''"
                  :uld-number="uld.uldNumber || ''"
                  :seal-number="uld.sealNumber || ''"
                  @piece-added="onScanPieceAdded"
                  @piece-removed="onScanPieceRemoved"
                  @exit-scan="scanMode = false"
                  @uld-number-scanned="onUldNumberScanned"
                  @seal-scanned="onSealScanned"
                  @scan-focus="onScanFocus"
                  @scan-error="onScanError"
                />

                <!-- Manifiesto: cabecera con contador + barra de progreso (mockup) -->
                <button type="button" class="w-full flex items-baseline justify-between gap-2 mb-1.5 text-left"
                  @click="sections.mawb = !sections.mawb">
                  <span class="text-[14px] font-bold text-primary">{{ t('ulds.section.mawb') }}</span>
                  <span class="flex items-center gap-1.5 text-[13px] font-mono text-secondary">
                    {{ t('ulds.manifestCount', { received: totalUldReceivedPieces(uld), assigned: totalUldPieces(uld) }) }}
                    <span class="md:hidden text-[12px] text-slate-400">{{ sections.mawb ? '▾' : '▸' }}</span>
                  </span>
                </button>
                <div class="h-2 rounded-full bg-slate-200 overflow-hidden mb-3">
                  <div class="h-full rounded-full transition-all duration-300"
                    :class="progressPct(uld) >= 100 ? 'bg-emerald-500' : 'bg-slate-600'"
                    :style="{ width: progressPct(uld) + '%' }"></div>
                </div>

                <!-- MAWB TABLE -->
                <div ref="mawbTableRef" class="border border-strong rounded overflow-hidden mb-6" :class="sections.mawb ? '' : 'hidden md:block'">
                  <div class="hidden md:block overflow-x-auto">
                  <div class="table-scroll-wrapper">
                  <div class="ds-table-header grid grid-cols-13 py-3 px-5 items-center gap-2" style="min-width: 750px">
                    <div class="col-span-3">MAWB</div>
                    <div class="col-span-1">{{ t('ulds.table.status') }}</div>
                    <div class="col-span-2">{{ t('ulds.table.description') }}</div>
                    <div class="col-span-1 text-right">{{ t('ulds.table.pcsAssigned') }}</div>
                    <div class="col-span-1 text-right">{{ t('ulds.table.pcsReceived') }}</div>
                    <div class="col-span-1 text-center">%</div>
                    <div class="col-span-1 text-right">{{ t('ulds.table.dest') }}</div>
                    <div class="col-span-2 text-center">{{ t('ulds.table.receipt') }}</div>
                    <div class="col-span-1"></div>
                  </div>
                  <div class="divide-y divide-slate-100 max-h-[240px] overflow-y-auto scrollbar-none" style="min-width: 750px">
                    <div v-for="(mawb, mIdx) in uld.mawbs" :key="mawb._rowId" class="ds-table-row grid grid-cols-13 gap-2 text-sm">
                      <div class="col-span-3 min-w-0">
                        <select
                          v-model="mawb.awbNumber"
                          @change="onMawbDropdownChange(uld, mIdx)"
                          class="w-full border-b border-strong focus:outline-none focus:border-slate-950 py-1 bg-transparent font-bold tracking-tight text-primary text-[13px] uppercase"
                        >
                          <option value="" disabled>{{ t('ulds.form.selectMawb') }}</option>
                          <template v-for="g in mawbSelectGroups(uld, mIdx)" :key="g.label">
                            <optgroup :label="g.label">
                              <option v-for="o in g.options" :key="o.awbNumber" :value="o.awbNumber">
                                {{ mawbOptionLabel(o) }}
                              </option>
                            </optgroup>
                          </template>
                        </select>
                        <div v-if="mawb.awbNumber && !mawb._isSpecial && !mawbInBookings(mawb.awbNumber)"
                          class="mt-0.5 text-[10px] text-amber-600 font-mono flex items-center gap-0.5">
                          &#9888; {{ t('ulds.notInBookings') }}
                        </div>
                        <div v-if="commodityHintFor(mawb)"
                          class="mt-0.5 text-[10px] text-secondary font-mono truncate"
                          :title="commodityHintFor(mawb)">
                          {{ commodityHintFor(mawb) }}
                        </div>
                      </div>
                      <div class="col-span-1 flex items-center">
                        <div v-if="mawb.awbNumber && !mawb._isSpecial" class="flex items-center gap-1">
                          <span class="w-2 h-2 rounded-full shrink-0" :class="mawbStatusDotClass(mawb.awbNumber)"></span>
                          <span class="text-[10px] font-mono font-bold uppercase leading-none" :class="mawbStatusTextClass(mawb.awbNumber)">{{ mawbStatusLabel(mawb.awbNumber) }}</span>
                        </div>
                        <span v-else class="text-[10px] text-tertiary">—</span>
                      </div>
                      <div class="col-span-2">
                        <input v-model="mawb.commodityType" type="text" :placeholder="mawb.commodityHint || t('ulds.dryCargoHint')"
                          class="w-full border-b border-strong focus:outline-none focus:border-slate-950 py-1 bg-transparent font-medium text-primary text-[13px]" />
                      </div>
                      <div class="col-span-1 flex flex-col items-end gap-0.5">
                        <input v-model.number="mawb.pieces" type="number" min="0"
                          @focus="onPiecesFocus(uld, mIdx)"
                          @change="onPiecesChange(uld, mIdx)"
                          class="w-full border-b border-strong focus:border-slate-950 py-1 text-right bg-transparent font-bold text-[13px]" />
                        <span v-if="overPrealertInfo(mawb)" class="text-[9px] leading-none font-mono font-bold text-amber-600 whitespace-nowrap"
                          :title="t('ulds.overPrealertTooltip', overPrealertInfo(mawb))">
                          &#9888; {{ t('ulds.overPrealertBadge') }}
                        </span>
                      </div>
                      <div class="col-span-1 text-right font-mono text-[13px] flex items-center justify-end gap-1"
                        :class="mawb.receivedPieces != null ? 'text-secondary' : 'text-tertiary'">
                        <template v-if="mawb.receivedPieces != null">{{ mawb.receivedPieces }}</template>
                        <span v-else>&mdash;</span>
                      </div>
                      <div class="col-span-1 text-center flex items-center justify-center gap-1">
                        <input v-model.number="mawb.piecesPct" type="number" min="0" max="100" inputmode="decimal"
                          class="w-14 border-b border-strong focus:outline-none focus:border-slate-950 py-1 text-center bg-transparent font-bold text-secondary text-[13px] pct-input" />
                        <span class="text-[13px] text-tertiary">%</span>
                      </div>
                      <div class="col-span-1 text-right">
                        <input v-model="mawb.destination" type="text" maxlength="3"
                          class="w-full border-b border-strong focus:outline-none focus:border-slate-950 py-1 text-right bg-transparent uppercase font-bold text-primary text-[13px]" />
                      </div>
                      <div class="col-span-2 flex justify-center items-center gap-1 text-[13px] font-mono">
                        <span v-if="mawb.hasReceipt"
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 rounded font-bold text-secondary bg-surface-hover border border-strong">
                          {{ t('ulds.receivedBadge') }}
                        </span>
                        <span v-else
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 rounded font-bold text-secondary bg-surface-hover border border-strong cursor-help"
                          :title="t('ulds.noReceiptTooltip')">
                          &#9888; {{ t('ulds.pendingShort') }}
                        </span>
                      </div>
                      <div class="col-span-1 text-center">
                        <button @click="removeMawbRow(uld, mIdx)" class="text-tertiary hover:text-secondary text-sm">&#10005;</button>
                      </div>
                    </div>
                  </div>
                  </div>
                  </div>
                  <!-- Mobile: cards apiladas por MAWB (mockup + SDD §4.2: toca para ver el detalle) -->
                  <div class="md:hidden divide-y divide-slate-100">
                    <div v-for="(mawb, mIdx) in uld.mawbs" :key="'card-'+mawb._rowId"
                      class="border-l-[3px] transition-colors" :class="mawbCardBorder(mawb)">
                      <button type="button"
                        class="w-full min-h-[58px] flex items-center justify-between gap-2.5 px-3 py-2.5 text-left"
                        @click="toggleMawbCard(mawb._rowId)">
                        <span class="min-w-0">
                          <span class="block text-[15px] font-bold font-mono text-primary truncate">{{ mawb.awbNumber || '—' }}</span>
                          <span class="block text-[12px] text-secondary truncate">
                            {{ t('ulds.manifestCount', { received: mawb.receivedPieces != null ? mawb.receivedPieces : 0, assigned: mawb.pieces || 0 }) }}
                            <template v-if="mawb.destination"> · {{ mawb.destination }}</template>
                          </span>
                        </span>
                        <span class="shrink-0 text-[11px] font-bold uppercase px-2 py-1 rounded-full border"
                          :class="mawbCardTagClass(mawb)">
                          {{ t('ulds.card.' + mawbCardState(mawb)) }}
                        </span>
                        <span class="shrink-0 text-[13px] text-slate-400">{{ expandedMawbRowId === mawb._rowId ? '▾' : '▸' }}</span>
                      </button>
                      <div v-if="expandedMawbRowId === mawb._rowId" class="px-3 pt-3 pb-3 space-y-2.5 border-t border-slate-100">
                        <div class="flex items-center gap-2">
                          <select
                            v-model="mawb.awbNumber"
                            @change="onMawbDropdownChange(uld, mIdx)"
                            class="flex-1 min-w-0 border border-strong rounded px-2 py-2.5 bg-transparent font-bold text-[13px] uppercase"
                          >
                            <option value="" disabled>{{ t('ulds.form.selectMawb') }}</option>
                            <template v-for="g in mawbSelectGroups(uld, mIdx)" :key="'card-'+g.label">
                              <optgroup :label="g.label">
                                <option v-for="o in g.options" :key="'card-'+o.awbNumber" :value="o.awbNumber">
                                  {{ mawbOptionLabel(o) }}
                                </option>
                              </optgroup>
                            </template>
                          </select>
                          <button @click="removeMawbRow(uld, mIdx)"
                            class="shrink-0 w-11 h-11 border border-strong rounded text-tertiary hover:text-secondary text-base font-bold">
                            &#10005;
                          </button>
                        </div>
                        <div v-if="commodityHintFor(mawb)" class="text-[10px] text-secondary font-mono truncate">{{ commodityHintFor(mawb) }}</div>
                        <div class="grid grid-cols-2 gap-x-3 gap-y-2">
                          <div>
                            <span class="text-[10px] font-bold uppercase tracking-wider text-tertiary">{{ t('ulds.table.pcsAssigned') }}</span>
                            <input v-model.number="mawb.pieces" type="number" min="0"
                              @focus="onPiecesFocus(uld, mIdx)"
                              @change="onPiecesChange(uld, mIdx)"
                              class="w-full border-b border-strong focus:outline-none focus:border-slate-950 py-1.5 bg-transparent font-bold text-[14px]" />
                          </div>
                          <div>
                            <span class="text-[10px] font-bold uppercase tracking-wider text-tertiary">{{ t('ulds.table.pcsReceived') }}</span>
                            <div class="py-1.5 font-mono text-[14px] font-bold"
                              :class="mawb.receivedPieces != null ? 'text-secondary' : 'text-tertiary'">
                              {{ mawb.receivedPieces != null ? mawb.receivedPieces : '—' }}
                            </div>
                          </div>
                          <div>
                            <span class="text-[10px] font-bold uppercase tracking-wider text-tertiary">{{ t('ulds.table.dest') }}</span>
                            <input v-model="mawb.destination" type="text" maxlength="3"
                              class="w-full border-b border-strong focus:outline-none focus:border-slate-950 py-1.5 bg-transparent uppercase font-bold text-[14px]" />
                          </div>
                          <div>
                            <span class="text-[10px] font-bold uppercase tracking-wider text-tertiary">{{ t('ulds.table.status') }}</span>
                            <div class="py-1.5 text-[13px] font-mono">
                              <span v-if="mawb.awbNumber && !mawb._isSpecial" class="inline-flex items-center gap-1">
                                <span class="w-2 h-2 rounded-full shrink-0" :class="mawbStatusDotClass(mawb.awbNumber)"></span>
                                <span class="text-[10px] font-mono font-bold uppercase leading-none" :class="mawbStatusTextClass(mawb.awbNumber)">{{ mawbStatusLabel(mawb.awbNumber) }}</span>
                              </span>
                              <span v-else class="text-tertiary">—</span>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                    <div v-if="!uld.mawbs.length" class="p-4 text-center text-[12px] text-tertiary">{{ t('ulds.mawbEmpty') }}</div>
                  </div>
                  <div class="p-2 bg-surface-hover border-t border-default">
                    <button @click="addMawbRow(uld)"
                      class="w-full md:w-auto py-3 px-4 min-h-[48px] border border-dashed rounded text-center transition-colors font-bold text-[14px] uppercase
                             border-strong text-secondary hover:text-primary
                             max-md:!border-blue-500 max-md:!text-blue-700 max-md:hover:bg-blue-50 max-md:text-[15px]">
                      + MAWB
                    </button>
                  </div>
                </div>

                <button type="button" @click="sections.weights = !sections.weights"
                  class="md:hidden w-full flex items-center justify-between gap-3 min-h-[52px] px-4 mb-3 border border-strong rounded-xl bg-surface text-[14px] font-bold text-primary">
                  <span>{{ t('ulds.section.weights') }}</span>
                  <span class="flex items-center gap-2 min-w-0 text-[13px] font-medium text-secondary">
                    <span class="truncate">{{ t('ulds.sectionValue.gross', { n: Number(uld.grossWeightLbs || 0).toLocaleString() }) }}</span>
                    <span class="text-slate-400 shrink-0">{{ sections.weights ? '▾' : '▸' }}</span>
                  </span>
                </button>
                <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5 mb-6" :class="sections.weights ? '' : 'hidden md:grid'">
                  <div>
                    <label class="ds-label block mb-1">{{ t('ulds.tare.label') }} <span class="text-primary">(lbs)</span></label>
                    <div class="relative">
                      <input v-model.number="uld.tareLbs" type="number" step="0.1"
                        class="ds-input" />
                    </div>
                    <div v-if="suggestedTareLbs" class="mt-1 text-[11px] text-tertiary font-mono">
                      {{ t('ulds.tare.suggested', { tare: suggestedTareLbs }) }}
                      <button @click="uld.tareLbs = suggestedTareLbs"
                        class="text-secondary hover:text-slate-700 underline ml-1">{{ t('ulds.tare.use') }}</button>
                    </div>
                  </div>
                  <div>
                    <label class="ds-label block mb-1">{{ t('ulds.form.grossLbs') }}</label>
                    <input v-model.number="uld.grossWeightLbs" type="number" class="ds-input" />
                  </div>
                  <div>
                    <label class="ds-label block mb-1">{{ t('ulds.form.status') }}</label>
                      <select v-model="uld.status" class="ds-input font-bold">
                        <option value="OPEN">OPEN ({{ t('ulds.status.OPEN') }})</option>
                        <option value="BUILT">BUILT ({{ t('ulds.status.BUILT') }})</option>
                        <option value="SEALED">SEALED ({{ t('ulds.status.SEALED') }})</option>
                        <option value="LOADED">LOADED ({{ t('ulds.status.LOADED') }})</option>
                        <option value="LEFT_BEHIND">LEFT BEHIND ({{ t('ulds.status.LEFT_BEHIND') }})</option>
                      </select>
                  </div>
                  <div class="bg-surface-hover flex flex-col justify-center rounded px-3 py-2 border border-strong lg:sticky lg:top-2 lg:self-start z-10">
                    <span class="text-sm font-bold text-secondary uppercase tracking-wider">{{ t('ulds.netWeight') }}</span>
                    <span class="text-sm font-bold text-slate-800">{{ ((uld.grossWeightLbs || 0) - (uld.tareLbs || 0)).toLocaleString() }} lbs</span>
                  </div>
                </div>

                <button type="button" @click="sections.details = !sections.details"
                  class="md:hidden w-full flex items-center justify-between gap-3 min-h-[52px] px-4 mb-3 border border-strong rounded-xl bg-surface text-[14px] font-bold text-primary">
                  <span>{{ t('ulds.section.details') }}</span>
                  <span class="flex items-center gap-2 min-w-0 text-[13px] font-medium text-secondary">
                    <span class="truncate uppercase">{{ uld.destination || '—' }}</span>
                    <span class="text-slate-400 shrink-0">{{ sections.details ? '▾' : '▸' }}</span>
                  </span>
                </button>
                <div class="grid grid-cols-1 sm:grid-cols-4 xl:grid-cols-5 gap-4 border-t border-strong pt-5" :class="sections.details ? '' : 'hidden md:grid'">
                  <div>
                    <label class="ds-label block mb-1">{{ t('ulds.form.destination') }}</label>
                    <input v-model="uld.destination" type="text" :placeholder="t('ulds.form.destinationPlaceholder')" class="ds-input uppercase" />
                  </div>
<div>
                      <label class="ds-label block mb-1">{{ t('ulds.form.loadedBy') }}</label>
                      <input v-model="uld.loadedBy" type="text" :placeholder="t('ulds.form.loadedByPlaceholder')" class="ds-input font-bold" />
                    </div>
                   <div>
                      <label class="ds-label block mb-1">{{ t('ulds.form.weighedBy') }}</label>
                      <input v-model="uld.weighedBy" type="text" :placeholder="t('ulds.form.weighedByPlaceholder')" class="ds-input font-bold" />
                    </div>
                  <div>
                    <label class="ds-label block mb-1">{{ t('ulds.form.confirmedWith') }}</label>
                    <input v-model="uld.confirmedWith" type="text" :placeholder="t('ulds.form.confirmedWithPlaceholder')" class="ds-input" />
                  </div>
                  <div>
                    <label class="ds-label block mb-1">{{ t('ulds.form.notes') }}</label>
                    <input v-model="uld.notes" type="text" :placeholder="t('ulds.form.notesPlaceholder')" class="ds-input" />
                  </div>
                </div>


                <!-- Flight + acciones destructivas: visible < lg (mockup: sección "Flight" con valor) -->
                <div class="lg:hidden mb-4">
                  <button type="button" @click="sections.flight = !sections.flight"
                    class="w-full flex items-center justify-between gap-3 min-h-[52px] px-4 border border-strong rounded-xl bg-surface text-[14px] font-bold text-primary">
                    <span>{{ t('ulds.form.flight') }}</span>
                    <span class="flex items-center gap-2 min-w-0 text-[13px] font-medium text-secondary">
                      <span class="truncate">{{ flightSummaryFor(uld) }}</span>
                      <span class="text-slate-400 shrink-0">{{ sections.flight ? '▾' : '▸' }}</span>
                    </span>
                  </button>
                  <div v-if="sections.flight" class="mt-3 p-3 border border-strong rounded-xl bg-surface space-y-3">
                    <div>
                      <span class="ds-label block mb-1" :class="creationStep === 1 ? 'text-amber-700' : ''">
                        {{ t('ulds.form.flight') }}
                        <span v-if="creationStep === 1" class="ml-1 text-[10px] bg-amber-200 text-amber-800 px-1.5 py-px rounded">{{ t('ulds.step', { n: 1 }) }}</span>
                      </span>
                      <select v-model="uld.saveFlightId"
                        class="ds-input uppercase text-[13px] w-full transition-all duration-300"
                        :class="creationStep === 1 ? 'ring-2 ring-amber-400 bg-amber-50 border-amber-400' : ''">
                        <option value="" disabled>{{ t('common.selectFlight') }}</option>
                        <option v-for="f in appStore.flights" :key="'m-'+f.id" :value="f.id">
                          {{ airlineCodeById(f.airlineId) }}-{{ f.flightNumber }} ({{ f.origin }}&#8594;{{ f.destination }}) {{ f.flightDate }}
                        </option>
                      </select>
                    </div>
                    <div class="flex items-end justify-between gap-2">
                      <div>
                        <span class="ds-label block mb-1">{{ t('ulds.created') }}</span>
                        <span class="text-[13px] font-bold text-primary">{{ uld.createdAt ? formatDate(uld.createdAt) : '—' }}</span>
                      </div>
                      <div class="flex items-center gap-2">
                        <button @click="deleteUld(uld)"
                          class="ds-btn-secondary text-tertiary hover:text-slate-700 min-h-[44px]"
                          :title="uld.backendId ? t('ulds.actions.delete') : t('ulds.actions.discard')">
                          &#10005;
                        </button>
                        <button v-if="uld.backendId" @click="dismountUld(uld)"
                          class="ds-btn-secondary min-h-[44px]">
                          {{ t('ulds.actions.demount') }}
                        </button>
                      </div>
                    </div>
                  </div>
                </div>


                <!-- Step guide for new ULD creation -->
                <div v-if="!uld.backendId && creationStep > 0" class="flex items-center gap-3 mb-4 px-3 py-2 rounded-lg"
                  :class="creationStep === 1 ? 'bg-amber-50 ring-2 ring-amber-300' : creationStep === 2 ? 'bg-emerald-50 ring-2 ring-emerald-300' : 'bg-surface-hover'">
                  <div class="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider"
                    :class="creationStep >= 1 ? 'text-amber-700' : 'text-secondary'">
                    <span class="w-5 h-5 rounded-full flex items-center justify-center text-[10px] font-bold"
                      :class="creationStep > 1 ? 'bg-emerald-700 text-white' : creationStep === 1 ? 'bg-amber-700 text-white' : 'bg-slate-200 text-primary'">1</span>
                    {{ t('ulds.form.flight') }}
                    <span v-if="creationStep > 1" class="text-emerald-600 ml-1">✓</span>
                  </div>
                  <span class="text-tertiary text-[10px]">▸</span>
                  <div class="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider"
                    :class="creationStep >= 2 ? 'text-emerald-700' : 'text-secondary'">
                    <span class="w-5 h-5 rounded-full flex items-center justify-center text-[10px] font-bold"
                      :class="creationStep > 2 ? 'bg-emerald-700 text-white' : creationStep === 2 ? 'bg-emerald-700 text-white' : 'bg-slate-200 text-primary'">2</span>
                    {{ t('ulds.steps.scanUld') }}
                    <span v-if="creationStep > 2" class="text-emerald-600 ml-1">✓</span>
                  </div>
                  <span class="text-tertiary text-[10px]">▸</span>
                  <div class="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider"
                    :class="creationStep >= 3 ? 'text-primary' : 'text-secondary'">
                    <span class="w-5 h-5 rounded-full flex items-center justify-center text-[10px] font-bold"
                      :class="creationStep >= 3 ? 'bg-slate-900 text-white' : 'bg-slate-200 text-primary'">3</span>
                    {{ t('ulds.steps.registerPieces') }}
                  </div>
                </div>

                <div class="border-t border-strong pt-4 flex flex-wrap justify-end gap-2 bg-surface-hover/50 -mx-2 -mb-6 p-3 rounded-b
                  lg:-mx-6 lg:-mb-6 lg:p-6 lg:pt-5
                  max-lg:fixed max-lg:bottom-0 max-lg:left-0 max-lg:right-0 max-lg:z-30 max-lg:mx-0 max-lg:mb-0
                  max-lg:pb-[calc(0.75rem+env(safe-area-inset-bottom))]
                  max-lg:rounded-none max-lg:bg-surface max-lg:border-t-2 max-lg:shadow-[0_-4px_16px_rgba(15,23,42,0.12)]">
                  <div class="hidden lg:flex items-center gap-4 mr-auto">
                    <div class="flex flex-col">
                      <span class="text-[12px] font-bold uppercase tracking-widest flex items-center gap-1.5"
                        :class="creationStep === 1 ? 'text-amber-700' : 'text-tertiary'">
                        {{ t('ulds.form.flight') }}
                        <span v-if="creationStep === 1" class="text-[10px] bg-amber-200 text-amber-800 px-1.5 py-px rounded">{{ t('ulds.step', { n: 1 }) }}</span>
                      </span>
                      <select v-model="uld.saveFlightId"
                        class="ds-input uppercase text-[12px] min-w-[160px] transition-all duration-300"
                        :class="creationStep === 1 ? 'ring-2 ring-amber-400 bg-amber-50 border-amber-400' : ''">
                        <option value="" disabled>{{ t('common.selectFlight') }}</option>
                        <option v-for="f in appStore.flights" :key="f.id" :value="f.id">
                          {{ airlineCodeById(f.airlineId) }}-{{ f.flightNumber }} ({{ f.origin }}&#8594;{{ f.destination }}) {{ f.flightDate }}
                        </option>
                      </select>
                    </div>
                    <div class="flex flex-col">
                      <span class="text-[12px] font-bold text-tertiary uppercase tracking-widest">{{ t('ulds.created') }}</span>
                      <span class="text-[14px] font-bold text-primary">{{ uld.createdAt ? formatDate(uld.createdAt) : '—' }}</span>
                    </div>
                  </div>
                  <div class="flex items-center gap-2 max-lg:w-full max-lg:flex-wrap">
                    <button @click="toggleScanMode(uld)"
                      class="font-mono font-bold uppercase text-[12px] tracking-widest px-4 py-2.5 rounded shadow-md transition-all flex items-center justify-center gap-2
                             max-lg:w-full max-lg:h-14 max-lg:py-0 max-lg:text-[15px] max-lg:tracking-[0.04em]"
                      :class="creationStep === 2 ? 'bg-emerald-700 hover:bg-emerald-800 text-white ring-2 ring-emerald-300 animate-pulse' : scanMode ? 'bg-emerald-700 hover:bg-emerald-800 text-white ring-2 ring-emerald-300' : 'bg-blue-600 hover:bg-blue-700 text-white'">
                      <template v-if="creationStep === 2">{{ t('ulds.scanNow') }}</template>
                      <template v-else-if="scanMode">{{ t('ulds.scanning') }}</template>
                      <template v-else>📷 {{ t('ulds.scanMode') }}</template>
                    </button>
                    <button @click="openLabelGenerator(uld)"
                      class="ds-btn-secondary flex items-center gap-1.5 max-lg:flex-1 max-lg:h-11 max-lg:justify-center"
                      :class="labelStored(uld) ? '!border-emerald-300 !text-emerald-700' : ''"
                      :title="t('ulds.label.actionHint')">
                      <component :is="icons.Printer" :size="15" />
                      <span>{{ t('ulds.label.action') }}</span>
                      <span v-if="labelStored(uld)" class="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
                    </button>
                    <button @click="deleteUld(uld)"
                      class="ds-btn-secondary text-tertiary hover:text-slate-700 max-lg:hidden"
                      :title="uld.backendId ? t('ulds.actions.delete') : t('ulds.actions.discard')">
                      &#10005;
                    </button>
                    <button v-if="uld.backendId" @click="dismountUld(uld)"
                      class="ds-btn-secondary max-lg:hidden">
                      {{ t('ulds.actions.demount') }}
                    </button>
                    <button @click="saveUld(uld)"
                      class="ds-btn-primary max-lg:flex-[2] max-lg:h-11 max-lg:justify-center">
                      {{ uld.backendId ? t('ulds.actions.update') : t('ulds.sendToLoadPlanning') }}
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </template>

          <EmptyState v-if="!expandedUldId" :title="t('ulds.selectToEdit')" :hint="t('ulds.selectHint')" :icon="icons.Package" />
        </div>

        <aside class="shrink-0 w-full lg:w-[420px] border-t-2 lg:border-t-0 lg:border-l-2 border-strong bg-surface-hover/60 flex flex-col min-h-0">
          <div class="flex items-center gap-2 px-3 pt-2 pb-1.5 shrink-0 flex-wrap">
            <span class="text-[11px] font-bold uppercase tracking-wider text-secondary">{{ t('ulds.summary') }}</span>
            <span class="ds-chip !py-0 !px-1.5 text-[10px]">{{ t('ulds.listCount', filteredUlDs.length) }}</span>
            <span v-if="fullUldCount > 0" class="inline-flex items-center gap-1 text-[10px] font-bold px-1.5 rounded uppercase border border-emerald-200 bg-emerald-50 text-emerald-700">
              &#10003; {{ t('ulds.fullCount', fullUldCount) }}
            </span>
            <span v-if="nearFullUldCount > 0" class="inline-flex items-center gap-1 text-[10px] font-bold px-1.5 rounded uppercase border border-amber-200 bg-amber-50 text-amber-700">
              &#9673; {{ t('ulds.nearFullCount', nearFullUldCount) }}
            </span>
            <span class="ml-auto text-[9px] font-bold uppercase tracking-wider text-tertiary" title="Ordenados por peso bruto desc">&#8595; {{ t('ulds.byGross') }}</span>
          </div>
          <div class="flex-1 min-h-0 overflow-y-auto overflow-x-hidden px-1.5 pb-2 flex flex-col gap-1 content-start">
            <div v-for="uld in sortedUlDsByGross" :key="uld.uid"
              @click="toggleUldExpansion(uld.uid)"
              class="ds-list-card flex-wrap"
              :class="[expandedUldId === uld.uid
                ? 'border-slate-950 ring-1 ring-slate-950 row-selected'
                : 'border-strong hover:border-slate-400 hover:shadow-sm',
                uld._isFirstDated ? 'border-t-2 border-t-slate-950 mt-1' : '',
                uld.volumePct >= 100 ? 'border-emerald-300 ring-1 ring-emerald-100' : '']"
              :style="uldStatusBorderStyle(uld.status)">

              <span class="text-[13px] font-bold text-primary font-mono truncate min-w-0 leading-tight flex items-center gap-1.5 flex-1">
                {{ uld.uldNumber || t('ulds.newUld') }}
                <span v-if="uldAgeInDays(uld.createdAt) !== null"
                  class="text-[10px] font-bold px-1 py-px rounded leading-none"
                  :class="uldAgeBadgeClass(uldAgeInDays(uld.createdAt))">
                  {{ uldAgeInDays(uld.createdAt) }}d
                </span>
              </span>
              <span class="text-[10px] font-bold px-1 py-px rounded uppercase whitespace-nowrap leading-none shrink-0"
                :class="statusBadgeClass(uld.status)">{{ t('ulds.status.' + uld.status) }}</span>
              <button @click.stop="openLabelGenerator(uld)"
                class="shrink-0 inline-flex items-center justify-center w-5 h-5 rounded border transition-colors"
                :class="labelStored(uld)
                  ? 'border-emerald-300 text-emerald-600 hover:bg-emerald-50'
                  : 'border-strong text-secondary hover:text-primary hover:border-slate-400'"
                :title="t('ulds.label.actionHint')">
                <component :is="icons.Printer" :size="12" />
              </button>

              <div class="flex items-center gap-x-2 text-[11px] font-mono text-secondary truncate w-full leading-tight min-w-0">
                <span class="font-bold text-slate-700 tabular-nums">{{ Number(uld.grossWeightLbs || 0).toLocaleString() }} lb</span>
                <span class="text-tertiary">|</span>
                <span class="truncate">{{ flightLabel(uld) }}</span>
                <span v-if="uld.route" class="text-tertiary truncate">{{ uld.route.replace(' -> ', '→') }}</span>
                <span class="text-tertiary">·</span>
                <span class="shrink-0">{{ t('ulds.mawbCount', (uld.mawbs || []).length) }}</span>
              </div>

              <div class="flex items-center gap-x-1.5 text-[10px] font-mono text-secondary truncate w-full leading-tight min-w-0">
                <span class="whitespace-nowrap shrink-0">{{ t('ulds.form.weighedBy') }}: <b class="text-slate-700">{{ uld.weighedBy || '—' }}</b></span>
                <span class="text-tertiary">|</span>
                <span class="whitespace-nowrap shrink-0">{{ t('ulds.form.loadedBy') }}: <b class="text-slate-700">{{ uld.loadedBy || '—' }}</b></span>
                <span class="text-tertiary">|</span>
                <span class="truncate">{{ t('ulds.form.confirmedWith') }}: <b class="text-slate-700 truncate">{{ uld.confirmedWith || '—' }}</b></span>
              </div>

              <div class="flex items-center gap-1 w-full">
                <div class="flex-1 h-[3px] bg-surface-hover rounded-full overflow-hidden">
                  <div class="h-full rounded-full transition-all duration-300"
                    :class="uld.volumePct >= 100 ? 'bg-emerald-500' : uld.volumePct >= 90 ? 'bg-slate-600' : 'bg-slate-950'"
                    :style="{ width: Math.min(uld.volumePct, 100) + '%' }"></div>
                </div>
                <span class="text-[12px] font-mono font-bold leading-none"
                  :class="uld.volumePct >= 100 ? 'text-emerald-600' : 'text-tertiary'">{{ uld.volumePct }}%</span>
              </div>
            </div>
          </div>
        </aside>
      </div>
    </section>

    <UldLabelGeneratorModal
      :show="showLabelModal"
      :uld="labelModalUld"
      :ctx="labelModalCtx"
      @close="showLabelModal = false"
      @saved="onLabelSaved" />

  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { useUldsStore } from '../stores/ulds'
import { useAppStore } from '../stores/app'
import { uldsApi } from '../api/ulds'
import { uldAwbsApi } from '../api/uldAwbs'
import { uldTypeConfigApi } from '../api/uldTypeConfig'
import { uldTypeCatalogApi } from '../api/uldTypeCatalog'

import { useToastStore } from '../stores/toast'
import { extractError } from '../utils/error'
import { useI18n } from 'vue-i18n'
import ScanPanel from '../components/ScanPanel.vue'
import UldTypeDropdown from '../components/UldTypeDropdown.vue'
import { useCommodities } from '../composables/useCommodities'
import { useConfirm } from '../composables/useConfirm'
import FilterBar from '../components/FilterBar.vue'
import EmptyState from '../components/EmptyState.vue'
import UldLabelGeneratorModal from '../components/UldLabelGeneratorModal.vue'
import { useIcons } from '../composables/useIcons'
import { useLiveRefresh } from '../composables/useLiveRefresh'
import { normalizeAwb, sameAwb } from '../utils/awb'
import { hasStoredLabel } from '../utils/uldLabel'

const uldsStore = useUldsStore()
const appStore = useAppStore()
const toast = useToastStore()
const route = useRoute()
const { t, te } = useI18n()
const icons = useIcons()
const { confirm } = useConfirm()
const { commodities: dbCommodities, loadCommodities, labelOf, descriptionOf, tooltipOf } = useCommodities()

function airlineCodeById(airlineId) {
  const a = appStore.airlines.find(x => x.id === airlineId)
  return a?.code || 'AIR'
}

// Tipos ULD desde el catálogo dinámico (normas IATA); fallback a lista legacy
const LEGACY_ULD_TYPES = ['PMC','PAH','PAG','PAJ','AAY','AAZ','AAD','PIP','BULK','AMP','AMJ']
const uldTypes = ref([...LEGACY_ULD_TYPES])
const uldCatalogItems = ref(LEGACY_ULD_TYPES.map(code => ({ code, description: '' })))

async function loadUldCatalog() {
  try {
    const res = await uldTypeCatalogApi.getAll(true)
    const items = (res.data || []).map(x => ({ code: x.code, description: x.description || '' }))
    const codes = items.map(x => x.code)
    if (codes.length) {
      uldTypes.value = codes
      uldCatalogItems.value = items
    }
  } catch {
    // se conserva el fallback legacy
  }
}

// Filas especiales (equipos/cargas que no son MAWBs reales pero se cargan en el
// ULD). Solo el IDENTIDAD (awbNumber, destino, código) es fija porque se
// persiste en uld_awb.mawb_label; el TEXTO se resuelve siempre desde el
// catálogo para que editar label/description en Settings se propague aquí.
const SPECIAL_ITEMS = [
  { id: 'spc-sdq-sdf', awbNumber: 'SDQ/SDF', consigneeName: 'Ruta Doméstica SDQ→SDF', commodityType: 'SDQ_SDF', pieces: 0, destination: 'SDF', isSpecial: true },
  { id: 'spc-sdq-mia', awbNumber: 'SDQ/MIA', consigneeName: 'Ruta Doméstica SDQ→MIA', commodityType: 'SDQ_MIA', pieces: 0, destination: 'MIA', isSpecial: true },
  { id: 'spc-wwef', awbNumber: 'WWEF', consigneeName: 'WWEF', commodityType: 'WWEF', pieces: 0, destination: 'MIA', isSpecial: true },
  { id: 'spc-fcc', awbNumber: 'FCC', consigneeName: 'FCC Equipment', commodityType: 'FCC', pieces: 0, destination: '', isSpecial: true },
  { id: 'spc-empty-uld', awbNumber: 'EMPTY ULD', consigneeName: 'Empty ULD Equipment', commodityType: 'EMPTY_ULD', pieces: 0, destination: '', isSpecial: true },
  { id: 'spc-empty-bags', awbNumber: 'EMPTY BAGS', consigneeName: 'Empty Bags Equipment', commodityType: 'EMPTY_BAGS', pieces: 0, destination: '', isSpecial: true },
  { id: 'spc-nets', awbNumber: 'NETS', consigneeName: 'Cargo Nets Equipment', commodityType: 'NETS', pieces: 0, destination: '', isSpecial: true },
]

const specialItems = computed(() =>
  SPECIAL_ITEMS.map(it => ({
    ...it,
    shipperName: descriptionOf(it.commodityType) || labelOf(it.commodityType),
  }))
)

const VALID_COMMODITIES = computed(() => {
  const set = new Set(['DRY_CARGO'])
  for (const c of dbCommodities.value) set.add(c.code)
  return set
})

function normalizeCommodity(val) {
  const v = String(val || '').trim().toUpperCase()
  return VALID_COMMODITIES.value.has(v) ? v : 'GENERAL'
}

const expandedUldId = ref(null)
const detailPanel = ref(null)
const statusFilter = ref('')
const searchText = ref('')
const destFilter = ref('')
// Load-planning flight filter: '' = todos los ULDs; si tiene valor, la lista
// incluye los ULDs del vuelo (asignados o liberados tras el despacho).
const flightFilterId = ref('')
// ULDs crudos del vuelo filtrado (cargados aparte para no pisar appStore.ulds).
const planUlds = ref([])

function flightOptionLabel(f) {
  if (!f) return ''
  const code = airlineCodeById(f.airlineId)
  const route = f.origin && f.destination ? ` (${f.origin}\u2192${f.destination})` : ''
  return `${code}-${f.flightNumber || ''}${route}`
}

const flightOptions = computed(() => {
  const list = [...(appStore.flights || [])]
  list.sort((a, b) => String(b.flightDate || '').localeCompare(String(a.flightDate || '')))
  return list
})

async function loadPlanUlds() {
  const flightId = flightFilterId.value
  if (!flightId) {
    planUlds.value = []
    rebuildLocalList()
    return
  }
  const flight = appStore.flights.find(f => f.id === flightId)
  try {
    const res = await uldsApi.getByFlight(flightId, flight?.airlineId)
    planUlds.value = res.data?.content || res.data || []
  } catch {
    planUlds.value = []
  }
  rebuildLocalList()
}

const uldStatusOptions = computed(() => [
  { value: 'OPEN', label: t('ulds.status.OPEN'), dotClass: 'bg-slate-400' },
  { value: 'BUILT', label: t('ulds.status.BUILT'), dotClass: 'bg-blue-500' },
  { value: 'SEALED', label: t('ulds.status.SEALED'), dotClass: 'bg-amber-500' },
  { value: 'LOADED', label: t('ulds.status.LOADED'), dotClass: 'bg-emerald-600' },
  { value: 'OFFLOADED', label: t('ulds.status.OFFLOADED'), dotClass: 'bg-red-500' },
])

const TARE_MAP = {
  AAY: 460, AAD: 540, AAZ: 500, AMP: 600, AMJ: 610,
  PMC: 270, PAG: 250, PAH: 300, PIP: 250,
}

// ULD type config from backend (per airline), editable by ADMIN/SUPER_USER
const typeConfigs = ref([])

function currentAirlineId() {
  return appStore.selectedFlight?.airlineId || appStore.airlines[0]?.id || null
}

async function loadTypeConfig() {
  const airlineId = currentAirlineId()
  if (!airlineId) { typeConfigs.value = []; return }
  try {
    const res = await uldTypeConfigApi.getByAirline(airlineId)
    typeConfigs.value = res.data || []
  } catch {
    typeConfigs.value = []
  }
}

function defaultTareFor(uldType) {
  const type = (uldType || '').toUpperCase()
  const cfg = typeConfigs.value.find(c => String(c.uldType).toUpperCase() === type)
  if (cfg && cfg.defaultTareLbs != null && Number(cfg.defaultTareLbs) > 0) return Number(cfg.defaultTareLbs)
  return TARE_MAP[type] ?? 0
}

const suggestedTareLbs = computed(() => {
  const expanded = localUlds.value.find(u => u.uid === expandedUldId.value)
  if (!expanded || !expanded.uldType) return null
  return defaultTareFor(expanded.uldType) || TARE_MAP[expanded.uldType.toUpperCase()] || null
})

const filteredUlDs = computed(() => {
  let list = localUlds.value
  if (flightFilterId.value) {
    // ULDs del vuelo: tanto los asignados como los liberados tras el despacho.
    list = list.filter(u =>
      u.flightId === flightFilterId.value || u.dispatchedFlightId === flightFilterId.value
    )
  }
  if (statusFilter.value) {
    list = list.filter(u => u.status === statusFilter.value)
  }
  if (destFilter.value) {
    const d = destFilter.value.toUpperCase()
    list = list.filter(u => {
      const mawbs = u.mawbs || []
      return mawbs.some(m => (m.destination || '').toUpperCase() === d) || (u.route || '').toUpperCase().includes(d)
    })
  }
  const q = searchText.value.trim().toLowerCase()
  if (q) {
    list = list.filter(u => {
      const haystack = [
        u.uldNumber, u.status, u.route,
        ...(u.mawbs || []).map(m => m.awbNumber),
      ].filter(Boolean).join(' ').toLowerCase()
      return haystack.includes(q)
    })
  }
  return list
})

const fullUldCount = computed(() => filteredUlDs.value.filter(u => u.volumePct >= 100).length)
const nearFullUldCount = computed(() => filteredUlDs.value.filter(u => u.volumePct >= 90 && u.volumePct < 100).length)

// Listado vertical: del ULD más pesado (mayor gross weight) al más ligero
const sortedUlDsByGross = computed(() => {
  return [...filteredUlDs.value].sort((a, b) => (Number(b.grossWeightLbs) || 0) - (Number(a.grossWeightLbs) || 0))
})

function formatDate(iso) {
  if (!iso) return '—'
  const d = new Date(iso)
  return d.toLocaleDateString('es-DO', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

// Local ULD list derived from backend + new unsaved
const localUlds = ref([])

const expandedUld = computed(() => localUlds.value.find(u => u.uid === expandedUldId.value) || null)

// Scan mode state
const scanMode = ref(false)
const scanUldUid = ref(null)

// Estados de campo del scan contextual (SDD §6.3): escaneando (azul),
// completado (verde + ✓) y error (rojo + mensaje)
const scanFocus = ref('')
const scanFieldError = ref('')
const uldCodeInput = ref(null)
const sealInput = ref(null)
const mawbTableRef = ref(null)

function scanFieldClass(field, value, base = '') {
  if (scanFieldError.value === field) return `${base} !border-red-400 ring-4 !ring-red-200`.trim()
  if (scanFocus.value === field) return `${base} !border-blue-500 ring-4 !ring-blue-200`.trim()
  if (value) return `${base} !border-emerald-600`.trim()
  return base
}

function scrollToRef(el) {
  try { el?.scrollIntoView?.({ behavior: 'smooth', block: 'center' }) } catch { /* sin soporte */ }
}

// El panel avanza la secuencia guiada ULD → Sello → MAWBs (SDD §5) y aquí
// resaltamos y centramos el campo correspondiente del formulario (SDD §7)
function onScanFocus(field) {
  scanFocus.value = field
  scanFieldError.value = ''
  if (field === 'seal') nextTick(() => scrollToRef(sealInput.value))
  if (field === 'mawb') nextTick(() => scrollToRef(mawbTableRef.value))
}

function onScanError(field) {
  if (field === 'uld' || field === 'seal') {
    scanFieldError.value = field
    setTimeout(() => { scanFieldError.value = '' }, 3000)
  }
}

watch(scanMode, (on) => {
  if (!on) {
    scanFocus.value = ''
    scanFieldError.value = ''
  }
})

// Secciones colapsables en móvil (SDD §6.1/6.2); en desktop siempre visibles
const sections = ref({ mawb: true, weights: true, details: true, flight: true })

// Card MAWB en móvil: toca para expandir el detalle editable (mockup §4.2)
const expandedMawbRowId = ref('')

function toggleMawbCard(rowId) {
  expandedMawbRowId.value = expandedMawbRowId.value === rowId ? '' : rowId
}

// Estado de la pieza recibida vs asignada → tag de la card (Completo/Parcial/Pendiente)
function mawbCardState(mawb) {
  const assigned = mawb.pieces || 0
  const received = mawb.receivedPieces
  if (received != null && assigned > 0 && received >= assigned) return 'complete'
  if (received != null && received > 0) return 'partial'
  return 'pending'
}

const MAWB_CARD_TAG = {
  complete: 'bg-emerald-50 text-emerald-700 border-emerald-300',
  partial: 'bg-amber-50 text-amber-700 border-amber-300',
  pending: 'bg-slate-100 text-slate-600 border-slate-300',
}

function mawbCardTagClass(mawb) {
  return MAWB_CARD_TAG[mawbCardState(mawb)]
}

// Borde izquierdo de 3 px con el color del estado (SDD §4.2)
function mawbCardBorder(mawb) {
  const s = mawbCardState(mawb)
  if (s === 'complete') return 'border-l-emerald-500'
  if (s === 'partial') return 'border-l-amber-500'
  return 'border-l-slate-300'
}

// Resumen del vuelo para el cabecero de la sección Flight (móvil/tablet)
function flightSummaryFor(uld) {
  const id = uld.saveFlightId || uld.flightId || null
  if (id) {
    const f = appStore.flights.find(x => x.id === id)
    if (f) return `${airlineCodeById(f.airlineId)}-${f.flightNumber}`
  }
  return '—'
}

function progressPct(uld) {
  const assigned = totalUldPieces(uld)
  if (!assigned) return 0
  return Math.min(100, Math.round((totalUldReceivedPieces(uld) / assigned) * 100))
}

// Guided creation steps: 0=idle, 1=select flight, 2=scan ULD, 3=done
const creationStep = ref(0)

// Pallet / ULD label generator modal
const showLabelModal = ref(false)
const labelModalUld = ref(null)
const labelModalCtx = ref({})
const labelTick = ref(0)

function openLabelGenerator(uld) {
  const flightId = uld?.flightId || uld?.dispatchedFlightId || flightFilterId.value || null
  const f = appStore.flights.find(x => x.id === flightId)
  labelModalCtx.value = f
    ? {
        airlineCode: airlineCodeById(f.airlineId),
        flightNumber: f.flightNumber,
        origin: f.origin,
        destination: f.destination,
        flightDate: f.flightDate,
      }
    : {}
  labelModalUld.value = uld
  showLabelModal.value = true
}

function labelStored(uld) {
  if (!uld) return false
  // labelTick fuerza re-evaluación tras guardar una etiqueta
  void labelTick.value
  return hasStoredLabel(uld)
}

function onLabelSaved() {
  labelTick.value++
  toast.success(t('ulds.label.saved'))
}

function toggleScanMode(uld) {
  scanMode.value = !scanMode.value
  if (scanMode.value && uld) {
    scanUldUid.value = uld.uid
  } else {
    scanUldUid.value = null
  }
}

// Auto-advance: after flight selected → step 2 → activate scan mode
watch(expandedUldId, () => {
  creationStep.value = 0
  expandedMawbRowId.value = ''
})

watch(() => {
  const uld = localUlds.value.find(u => u.uid === expandedUldId.value)
  return uld?.saveFlightId
}, (flightId) => {
  if (flightId && creationStep.value === 1) {
    creationStep.value = 2
    const uld = localUlds.value.find(u => u.uid === expandedUldId.value)
    if (uld && !uld.backendId) {
      scanUldUid.value = uld.uid
      scanMode.value = true
    }
  }
})

// After ULD number is set → step 3 done
watch(() => {
  const uld = localUlds.value.find(u => u.uid === expandedUldId.value)
  return uld?.uldNumber
}, (num) => {
  if (num && creationStep.value === 2) {
    creationStep.value = 3
  }
})

// Auto-apply suggested tare when ULD type changes on an unsaved ULD
watch(() => {
  const uld = localUlds.value.find(u => u.uid === expandedUldId.value)
  return uld?.uldType
}, (type, oldType) => {
  const uld = localUlds.value.find(u => u.uid === expandedUldId.value)
  if (!uld || uld.backendId) return
  const prevAuto = oldType ? defaultTareFor(oldType) : null
  const isAutoValue = uld.tareLbs == null || uld.tareLbs === 0 || (prevAuto != null && uld.tareLbs === prevAuto)
  if (isAutoValue) {
    uld.tareLbs = defaultTareFor(type)
  }
})

// Reload ULD type config when the selected flight/airline changes
watch(() => appStore.selectedFlight?.airlineId, () => loadTypeConfig())

function onScanPieceAdded(result) {
  const uid = scanUldUid.value
  if (!uid) return
  const uld = localUlds.value.find(u => u.uid === uid)
  if (!uld) return

  let mawbRow = uld.mawbs.find(m => sameAwb(m.awbNumber, result.awbNumber))
  if (!mawbRow) {
    const mawbData = appStore.mawbs.find(m => sameAwb(m.awbNumber, result.awbNumber))
    mawbRow = {
      _rowId: Math.random().toString(36).slice(2),
      awbNumber: result.awbNumber,
      commodityType: normalizeCommodity(mawbData?.commodityType),
      commodityHint: mawbData?.commodityType || '',
      pieces: 0,
      piecesPct: 0,
      destination: mawbData?.destination || 'MIA',
      mawbId: mawbData?.id || null,
      hasReceipt: false,
      receivedPieces: 0,
      reservedPieces: mawbData?.pieces || 0,
      availablePieces: 0,
      _isSpecial: false,
      _prevPieces: 0,
      _ackOverPrealert: false,
    }
    uld.mawbs.push(mawbRow)
  }

  mawbRow.pieces = result.totalOnUld
}

function onScanPieceRemoved(data) {
  const uid = scanUldUid.value
  if (!uid) return
  const uld = localUlds.value.find(u => u.uid === uid)
  if (!uld) return
  const mawbRow = uld.mawbs.find(m => sameAwb(m.awbNumber, data.awbNumber))
  if (mawbRow && mawbRow.pieces > 0) {
    mawbRow.pieces--
  }
}

async function onUldNumberScanned(code) {
  const uid = scanUldUid.value
  if (!uid) return
  const uld = localUlds.value.find(u => u.uid === uid)
  if (!uld) return
  const upper = code.toUpperCase()
  uld.uldNumber = upper
  const prefix = upper.slice(0, 3)
  if (uldTypes.value.includes(prefix)) {
    uld.uldType = prefix
  }

  // Auto-save ULD so backendId is available for MAWB piece registration
  const flightId = uld.saveFlightId || appStore.selectedFlight?.id
  if (!uld.backendId && flightId) {
    try {
      const flight = appStore.flights.find(f => f.id === flightId)
      uld.airlineId = uld.airlineId || flight?.airlineId || appStore.selectedFlight?.airlineId
      uld.flightId = flightId
      uld._skipOperatorValidation = true
      const result = await uldsStore.dispatchUld(uld, flightId)
      uld.backendId = result?.id
      delete uld._skipOperatorValidation
      await appStore.loadUlds()
    } catch (e) {
      console.warn('[ULD Scan] Auto-save failed:', e)
    }
  } else if (uld.backendId) {
    // ULD ya guardado: persistir el número escaneado al instante (igual que el sello)
    try {
      const payload = { uldNumber: upper }
      if (uldTypes.value.includes(prefix)) payload.uldType = prefix
      await uldsApi.patch(uld.backendId, payload)
    } catch (e) {
      console.warn('[ULD Scan] ULD number save failed:', e)
      toast.error(t('scan.message.uldSaveError'))
    }
  }
}

// Escaneo de sello desde el panel: rellena el campo del formulario y,
// si el ULD ya existe en backend, lo persiste de inmediato (PATCH).
async function onSealScanned(code) {
  const uid = scanUldUid.value || expandedUldId.value
  if (!uid) return
  const uld = localUlds.value.find(u => u.uid === uid)
  if (!uld) return
  uld.sealNumber = code
  if (uld.backendId) {
    try {
      await uldsApi.patch(uld.backendId, { sealNumber: code })
    } catch (e) {
      console.warn('[ULD Scan] Seal save failed:', e)
      toast.error(t('scan.message.sealSaveError'))
    }
  }
}

// MAWB availability computation
// Piezas disponibles = prealertadas o recibidas, menos lo ya asignado en ULDs
function totalAssignedForAwb(awbNumber, excludeRowId) {
  return (localUlds.value || []).flatMap(u =>
    (u.mawbs || []).filter(mw => sameAwb(mw.awbNumber, awbNumber) && mw._rowId !== excludeRowId)
  ).reduce((s, mw) => s + (mw.pieces || 0), 0)
}

const availableMawbs = computed(() => {
  const mawbsWithAvailability = (appStore.mawbs || []).map(m => ({
    ...m,
    availablePieces: mawbReceiptInfo(m.awbNumber).availablePieces,
  }))
  return [...mawbsWithAvailability, ...specialItems.value]
})

// El texto del commodity sale del catálogo (label), nunca del código crudo:
// así una edición de label/description en Settings se refleja al instante.
// El <select> nativo no admite tooltip por <option>, por eso la description
// se muestra como línea de ayuda bajo el select (commodityHintFor).
function mawbOptionLabel(o) {
  const name = (o.shipperName || o.consigneeName || '').slice(0, 18)
  const comm = o.commodityType ? ` [${labelOf(o.commodityType)}]` : ''
  const avail = o.isSpecial ? '' : ` · ${t('ulds.availablePiecesShort', { n: o.availablePieces })}`
  return `${o.awbNumber} — ${name}${comm}${avail}`
}

function commodityHintFor(mawb) {
  if (!mawb?.commodityType) return ''
  return tooltipOf(mawb.commodityType)
}

function mawbSelectGroups(uld, mIdx) {
  const mawb = uld.mawbs[mIdx]
  const groups = []
  const available = availableMawbs.value.filter(m => !m.isSpecial && Number(m.availablePieces) > 0)
  if (available.length) groups.push({ label: t('ulds.form.mawbAvailableGroup'), options: available })
  if (specialItems.value.length) groups.push({ label: t('ulds.form.mawbSpecialGroup'), options: specialItems.value })
  const current = mawb?.awbNumber
  if (current) {
    const seen = groups.some(g => g.options.some(o => normalizeAwb(o.awbNumber) === normalizeAwb(current)))
    if (!seen) {
      const m = (appStore.mawbs || []).find(x => normalizeAwb(x.awbNumber) === normalizeAwb(current))
      groups.unshift({
        label: t('ulds.form.mawbCurrentGroup'),
        options: [m || { awbNumber: current, shipperName: '', consigneeName: '', commodityType: '' }],
      })
    }
  }
  return groups
}

function onMawbDropdownChange(uld, mIdx) {
  const mawb = uld.mawbs[mIdx]
  if (!mawb?.awbNumber) return
  const selected = availableMawbs.value.find(m => sameAwb(m.awbNumber, mawb.awbNumber))
  mawb._isSpecial = !!selected?.isSpecial
  onMawbSelect(uld, mIdx)
}

function overPrealertInfo(mawb) {
  if (!mawb || !mawb.awbNumber || mawb._isSpecial) return null
  const preAlerted = mawb.reservedPieces || 0
  if (preAlerted <= 0) return null
  const assignedElsewhere = totalAssignedForAwb(mawb.awbNumber, mawb._rowId)
  const total = assignedElsewhere + (mawb.pieces || 0)
  return total > preAlerted ? { total, preAlerted } : null
}

function onPiecesFocus(uld, mIdx) {
  const mawb = uld.mawbs[mIdx]
  if (mawb) mawb._prevPieces = mawb.pieces ?? 0
}

async function onPiecesChange(uld, mIdx) {
  const mawb = uld.mawbs[mIdx]
  if (!mawb?.awbNumber || mawb._isSpecial) return
  const preAlerted = mawb.reservedPieces || 0
  if (preAlerted <= 0 || mawb._ackOverPrealert) return
  const assignedElsewhere = totalAssignedForAwb(mawb.awbNumber, mawb._rowId)
  const total = assignedElsewhere + (mawb.pieces || 0)
  if (total <= preAlerted) return
  const ok = await confirm({
    title: t('ulds.overPrealertTitle'),
    message: t('ulds.overPrealertConfirm', { awb: mawb.awbNumber, pre: preAlerted, assigned: total }),
    confirmText: t('common.yes'),
    cancelText: t('common.no'),
    danger: true,
  })
  if (ok) {
    mawb._ackOverPrealert = true
  } else {
    const prev = mawb._prevPieces
    const remaining = Math.max(0, preAlerted - assignedElsewhere)
    mawb.pieces = prev != null ? Math.min(prev, remaining) : remaining
  }
}

const pendingReceiptCount = computed(() => {
  const allMawbs = localUlds.value.flatMap(u => u.mawbs || [])
  const uniqueAwbs = [...new Set(allMawbs.filter(m => m.awbNumber).map(m => m.awbNumber))]
  return uniqueAwbs.filter(awb => !mawbHasReceipt(awb)).length
})

function mawbReceiptInfo(awbNumber, excludeRowId) {
  const norm = normalizeAwb(awbNumber)
  const m = (appStore.mawbs || []).find(x => normalizeAwb(x.awbNumber) === norm)
  const receipt = (appStore.receipts || []).find(r => (r.mawb && r.mawb.id === m?.id) || r.mawbId === m?.id)
  const reservedPieces = m ? (m.pieces || 0) : 0
  const receivedPieces = receipt ? (receipt.pieceCount || receipt.receivedPieces || 0) : 0
  const reference = reservedPieces > 0 ? reservedPieces : receivedPieces
  const availablePieces = Math.max(0, reference - totalAssignedForAwb(awbNumber, excludeRowId))
  return {
    hasReceipt: !!receipt,
    receivedPieces: receivedPieces,
    reservedPieces: reservedPieces,
    availablePieces: availablePieces,
    mawbId: m?.id || null,
  }
}

function mawbHasReceipt(awbNumber) {
  return mawbReceiptInfo(awbNumber).hasReceipt
}

function mawbInBookings(awbNumber) {
  if (!awbNumber) return false
  const norm = normalizeAwb(awbNumber)
  return (appStore.bookings || []).some(b => normalizeAwb(b.awbNumber) === norm)
}

function mawbStatusForAwb(awbNumber) {
  if (!awbNumber) return null
  const m = (appStore.mawbs || []).find(x => sameAwb(x.awbNumber, awbNumber))
  return m?.status || null
}

function mawbStatusLabel(awbNumber) {
  const s = mawbStatusForAwb(awbNumber)
  if (!s) return '—'
  const key = 'ulds.mawbStatus.' + s
  return te(key) ? t(key) : s
}

function mawbStatusDotClass(awbNumber) {
  const s = mawbStatusForAwb(awbNumber)
  if (s === 'RECEIVED') return 'bg-amber-400'
  if (s === 'MANIFESTED') return 'bg-emerald-500'
  if (s === 'DEPARTED' || s === 'ARRIVED') return 'bg-blue-500'
  if (s === 'BOOKED') return 'bg-slate-400'
  return 'bg-slate-200'
}

function mawbStatusTextClass(awbNumber) {
  const s = mawbStatusForAwb(awbNumber)
  if (s === 'RECEIVED') return 'text-amber-600'
  if (s === 'MANIFESTED') return 'text-emerald-600'
  if (s === 'DEPARTED' || s === 'ARRIVED') return 'text-blue-600'
  if (s === 'BOOKED') return 'text-secondary'
  return 'text-tertiary'
}

function totalUldPieces(uld) {
  return (uld.mawbs || []).reduce((s, m) => s + (m.pieces || 0), 0)
}

function totalUldReceivedPieces(uld) {
  return (uld.mawbs || []).reduce((s, m) => s + ((m.receivedPieces != null ? m.receivedPieces : m.pieces) || 0), 0)
}

function liveCommodityFor(awbNumber, fallback) {
  if (!awbNumber) return fallback || ''
  const norm = normalizeAwb(awbNumber)
  const m = (appStore.mawbs || []).find(x => normalizeAwb(x.awbNumber) === norm)
  if (m?.commodityType) return m.commodityType
  const b = (appStore.bookings || []).find(x => normalizeAwb(x.awbNumber) === norm)
  return b?.commodityType || fallback || ''
}

function rebuildLocalList() {
  // Los ULDs de vuelos despachados se muestran igualmente (ya no se ocultan):
  // el despacho libera flight_id (queda dispatched_flight_id) y el ULD sigue
  // siendo consultable desde la vista. Se fusionan los ULDs del vuelo
  // filtrado (planUlds) con el listado global de appStore, deduplicando por id.
  const seen = new Set()
  const source = []
  for (const u of [...(planUlds.value || []), ...(appStore.ulds || [])]) {
    if (!u || u.id == null || seen.has(u.id)) continue
    seen.add(u.id)
    source.push(u)
  }
  const backend = source
    .map(u => {
      const resolvedFlightId = u.flightId || u.dispatchedFlightId || null
      const flight = appStore.flights.find(f => f.id === resolvedFlightId)
      return {
      uid: u.id,
      backendId: u.id,
      uldNumber: u.uldNumber,
      flightId: u.flightId,
      dispatchedFlightId: u.dispatchedFlightId || null,
      statusBeforeDispatch: u.statusBeforeDispatch || null,
      flightLabel: flight?.flightNumber || 'FLOTANTE',
      route: flight ? (flight.origin + ' -> ' + flight.destination) : t('ulds.noFlight'),
      uldType: u.uldType,
      config: u.config,
      position: u.position,
      sealNumber: u.sealNumber,
      tareLbs: u.tareLbs || u.tareWeight || 0,
      grossWeightLbs: u.grossWeightLbs || u.grossWeight || 0,
      status: u.status || 'OPEN',
      loadedBy: u.loadedBy || '',
      weighedBy: u.weighedBy || '',
      notes: u.notes || '',
      destination: u.destination || '',
      confirmedWith: u.confirmedWith || '',
      saveFlightId: u.flightId,
      awbs: (u.awbs || []),
      volumePct: 0,
      createdAt: u.createdAt,
      mawbs: (u.awbs || []).map(m => {
        const awbNumber = normalizeAwb(m.mawbLabel || '')
        const info = mawbReceiptInfo(awbNumber)
        return {
          _rowId: m.id || Math.random().toString(36).slice(2),
          awbNumber,
          _isSpecial: false,
          _prevPieces: m.pieces || 0,
          _ackOverPrealert: false,
          commodityType: liveCommodityFor(awbNumber, m.description) || 'DRY_CARGO',
          commodityHint: liveCommodityFor(awbNumber, m.description) || '',
          pieces: m.pieces || 0,
          piecesPct: m.piecesPct || 0,
          destination: m.destination || '-',
          mawbId: m.mawbId || null,
          ...info,
        }
      }),
    }
  })
  // Calculate volumePct as sum of piecesPct across all MAWBs, capped at 100
  backend.forEach(uld => {
    const total = (uld.mawbs || []).reduce((s, m) => s + (m.piecesPct || 0), 0)
    uld.volumePct = Math.min(total, 100)
  })
  // Merge with existing unsaved local ULDS
  const unsaved = localUlds.value.filter(u => !u.backendId)
  localUlds.value = [...unsaved, ...backend]
}

function createNewBlankUld() {
  localUlds.value.unshift({
    uid: 'new-' + Date.now(),
    backendId: null,
    uldNumber: '',
    flightId: null,
    saveFlightId: null,
    flightLabel: '',
    route: 'SDQ -> MIA',
    uldType: 'PMC',
    config: '',
    position: '',
    sealNumber: '',
    tareLbs: defaultTareFor('PMC'),
    grossWeightLbs: 0,
    status: 'OPEN',
    volumePct: 0,
    loadedBy: '',
    weighedBy: '',
    notes: '',
    destination: '',
    confirmedWith: '',
    mawbs: [],
  })
  expandedUldId.value = localUlds.value[0].uid
  creationStep.value = 1
  scanMode.value = false
  scanUldUid.value = null
}

async function dismountUld(uld) {
  if (!(await confirm({ message: t('ulds.confirmDemountFull', { number: uld.uldNumber || '' }) }))) return
  try {
    // 1. Delete all ULD-AWB links
    const existing = await uldAwbsApi.getByUld(uld.backendId)
    for (const link of (existing.data || [])) {
      await uldAwbsApi.delete(link.id)
    }
    // 2. Reset the ULD
    await uldsApi.update(uld.backendId, {
      airlineId: uld.airlineId || appStore.selectedFlight?.airlineId || null,
      uldNumber: uld.uldNumber,
      position: null,
      sealNumber: null,
      tareLbs: 0,
      grossWeightLbs: 0,
      status: 'OPEN',
      notes: null,
    })
    uld.tareLbs = 0
    // 3. Remove flight assignment
    await uldsApi.assignFlight(uld.backendId, null)
    // 4. Reload
    await appStore.loadUlds()
    rebuildLocalList()
    expandedUldId.value = null
  } catch (e) {
    toast.error(extractError(e))
  }
}

async function deleteUld(uld) {
  if (!uld.backendId) {
    // Local ULD not saved — just discard from list
    localUlds.value = localUlds.value.filter(u => u.uid !== uld.uid)
    if (expandedUldId.value === uld.uid) expandedUldId.value = null
    return
  }
  const hasCargo = (uld.mawbs || []).length > 0
  if (hasCargo) {
    if (!(await confirm({ message: t('ulds.confirmDeleteWithCargo', { number: uld.uldNumber || '', count: uld.mawbs.length }), danger: true }))) return
    // Dismount first
    try {
      const existing = await uldAwbsApi.getByUld(uld.backendId)
      for (const link of (existing.data || [])) {
        await uldAwbsApi.delete(link.id)
      }
      await uldsApi.update(uld.backendId, {
        airlineId: uld.airlineId || appStore.selectedFlight?.airlineId || null,
        uldNumber: uld.uldNumber,
        position: '',
        sealNumber: '',
        tareLbs: 0,
        grossWeightLbs: 0,
        status: 'OPEN',
        notes: '',
      })
      uld.tareLbs = 0
      await uldsApi.assignFlight(uld.backendId, null)
    } catch (e) {
      toast.error(extractError(e))
      return
    }
  } else {
    if (!(await confirm({ message: t('ulds.confirmDelete', { number: uld.uldNumber || '' }), danger: true }))) return
  }
  try {
    await uldsApi.delete(uld.backendId)
    localUlds.value = localUlds.value.filter(u => u.uid !== uld.uid)
    if (expandedUldId.value === uld.uid) expandedUldId.value = null
    await appStore.loadUlds()
    rebuildLocalList()
  } catch (e) {
    toast.error(extractError(e))
  }
}

async function saveUld(uld) {
  if (!uld.uldNumber) {
    toast.warning(t('ulds.uldNumberRequired'))
    return
  }
  const flightId = uld.saveFlightId
  delete uld._skipOperatorValidation

  if (flightId && (!uld.loadedBy?.trim() || !uld.weighedBy?.trim() || !uld.confirmedWith?.trim())) {
    toast.warning(t('ulds.operatorsRequired'))
    return
  }

  if (!flightId) {
    if (!uld.backendId) {
      toast.warning(t('ulds.selectFlightRequired'))
      return
    }
    // Floating ULD without flight change — just update fields
    try {
      // notes: solo lo que escriba el operador (sin autollenado)
      await uldsApi.update(uld.backendId, {
        airlineId: uld.airlineId || appStore.selectedFlight?.airlineId || null,
        uldNumber: uld.uldNumber,
        uldType: uld.uldType,
        config: uld.config || null,
        position: uld.position ?? null,
        sealNumber: uld.sealNumber ?? null,
        tareLbs: uld.tareLbs || 0,
        grossWeightLbs: uld.grossWeightLbs || 0,
        status: uld.status || 'OPEN',
        notes: uld.notes ?? null,
        destination: uld.destination ?? null,
        loadedBy: uld.loadedBy ?? null,
        weighedBy: uld.weighedBy ?? null,
        confirmedWith: uld.confirmedWith ?? null,
      })
      // Recreate ULD-AWB links
      if (uld.backendId) {
        const existing = await uldAwbsApi.getByUld(uld.backendId)
        for (const link of (existing.data || [])) {
          await uldAwbsApi.delete(link.id)
        }
      }
      for (const m of (uld.mawbs || [])) {
        if (m.awbNumber && uld.backendId) {
          const matchingMawb = appStore.mawbs.find(x => sameAwb(x.awbNumber, m.awbNumber))
          await uldAwbsApi.create({
            uldId: uld.backendId,
            mawbId: matchingMawb?.id || null,
            mawbLabel: m.awbNumber,
            description: normalizeCommodity(m.commodityType),
            destination: m.destination || 'MIA',
            pieces: m.pieces || 0,
            piecesPct: m.piecesPct || 0,
          })
        }
      }
      expandedUldId.value = null
      await appStore.loadUlds()
      rebuildLocalList()
    } catch (e) {
      toast.error(extractError(e))
    }
    return
  }


  try {
    uld.flightId = flightId
    // notes: solo lo que escriba el operador (sin autollenado)
    const result = await uldsStore.dispatchUld(uld, flightId)
    uld.backendId = result?.id || uld.backendId
    // Delete existing ULD-AWB links before recreating
    if (uld.backendId) {
      const existing = await uldAwbsApi.getByUld(uld.backendId)
      for (const link of (existing.data || [])) {
        await uldAwbsApi.delete(link.id)
      }
    }
    // Create ULD-AWB links for each MAWB
    for (const m of (uld.mawbs || [])) {
      if (m.awbNumber && result?.id) {
        const matchingMawb = appStore.mawbs.find(x => sameAwb(x.awbNumber, m.awbNumber))
        await uldAwbsApi.create({
          uldId: result.id,
          mawbId: matchingMawb?.id || null,
          mawbLabel: m.awbNumber,
          description: normalizeCommodity(m.commodityType),
          destination: m.destination || 'MIA',
          pieces: m.pieces || 0,
          piecesPct: m.piecesPct || 0,
        })
      }
    }
    expandedUldId.value = null
    await appStore.loadUlds()
    rebuildLocalList()
    await appStore.loadAllMawbs()
  } catch (e) {
    toast.error(extractError(e))
  }
}

function toggleUldExpansion(uid) {
  const opening = expandedUldId.value !== uid
  expandedUldId.value = opening ? uid : null
  if (opening && window.innerWidth < 1024) {
    requestAnimationFrame(() => detailPanel.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
  }
}

function addMawbRow(uld) {
  uld.mawbs.push({
    _rowId: Math.random().toString(36).slice(2),
    awbNumber: '',
    commodityType: 'DRY_CARGO',
    commodityHint: '',
    pieces: 0,
    piecesPct: 0,
    destination: 'MIA',
    mawbId: null,
    hasReceipt: false,
    receivedPieces: 0,
    reservedPieces: 0,
    availablePieces: 0,
    _isSpecial: false,
    _prevPieces: 0,
    _ackOverPrealert: false,
  })
}

function removeMawbRow(uld, index) {
  uld.mawbs.splice(index, 1)
}

function onMawbSelect(uld, mIdx) {
  const mawb = uld.mawbs[mIdx]
  const selected = availableMawbs.value.find(m => normalizeAwb(m.awbNumber) === normalizeAwb(mawb.awbNumber))
  if (!selected) return
  mawb.commodityType = normalizeCommodity(selected.commodityType)
  mawb.commodityHint = selected.commodityType || ''
  mawb.destination = selected.destination || 'MIA'
  mawb.mawbId = selected.isSpecial ? null : selected.id
  mawb._ackOverPrealert = false
  if (selected.isSpecial) {
    mawb.pieces = 0
    mawb.hasReceipt = false
    mawb.receivedPieces = 0
    mawb.reservedPieces = 0
    mawb.availablePieces = 0
    return
  }
  const info = mawbReceiptInfo(selected.awbNumber, mawb._rowId)
  mawb.hasReceipt = info.hasReceipt
  mawb.receivedPieces = info.receivedPieces
  mawb.reservedPieces = info.reservedPieces
  mawb.availablePieces = info.availablePieces
  // Auto-fill piezas: las que quedan disponibles (no se excede la prealerta)
  if (info.availablePieces > 0) {
    mawb.pieces = info.availablePieces
  } else if (info.receivedPieces > 0) {
    mawb.pieces = info.receivedPieces
  } else if (info.reservedPieces > 0) {
    mawb.pieces = info.reservedPieces
  }
}

function statusBadgeClass(status) {
  switch (status) {
    case 'OPEN': return 'bg-surface-hover text-secondary border border-strong'
    case 'BUILT': return 'bg-blue-50 text-blue-700 border border-blue-100'
    case 'SEALED': return 'bg-amber-50 text-amber-700 border border-amber-100'
    case 'LOADED': return 'bg-emerald-50 text-emerald-700 border border-emerald-100'
    case 'LEFT_BEHIND': return 'bg-rose-50 text-rose-700 border border-rose-100'
    default: return 'bg-surface-hover text-primary border border-strong'
  }
}

function flightLabel(uld) {
  if (!uld.flightLabel) return '---'
  if (uld.flightLabel === 'FLOTANTE') return t('ulds.floating')
  if (uld.flightLabel === 'TBD') return 'TBD'
  const num = parseInt(uld.flightLabel, 10)
  if (!isNaN(num)) return airlineCodeById(uld.airlineId) + '-' + uld.flightLabel
  return uld.flightLabel
}

function uldAgeInDays(createdAt) {
  if (!createdAt) return null
  const created = new Date(createdAt)
  const now = new Date()
  return Math.floor((now - created) / (1000 * 60 * 60 * 24))
}

function uldAgeBadgeClass(days) {
  if (days === null) return ''
  if (days < 7) return 'bg-surface-hover text-secondary border border-strong'
  if (days <= 30) return 'bg-surface-hover text-secondary border border-strong'
  return 'bg-surface-hover text-secondary border border-strong'
}

function uldStatusBorderStyle(status) {
  const map = {
    SEALED: 'border-l-2 border-l-amber-400',
    LOADED: 'border-l-2 border-l-emerald-500',
    LEFT_BEHIND: 'border-l-2 border-l-rose-500',
    BUILT: 'border-l-2 border-l-blue-400',
  }
  return map[status] || ''
}

onMounted(async () => {
  if (!appStore.airlines.length) await appStore.loadAirlines()
  await Promise.all([
    appStore.loadFlights(),
    appStore.loadAllMawbs(),
    appStore.loadReceipts(),
    appStore.loadBookings(),
    appStore.loadUlds(),
    loadCommodities(),
  ])
  loadTypeConfig()
  loadUldCatalog()
  rebuildLocalList()
  if (route.query.new === '1') createNewBlankUld()
})

watch(() => appStore.ulds, () => rebuildLocalList(), { deep: true })

// Al cambiar el vuelo seleccionado, recargar los ULDs de su plan de carga.
watch(flightFilterId, () => { loadPlanUlds() })

useLiveRefresh(() => {
  if (!appStore.flights.length) return
  const tasks = [
    appStore.loadUlds(undefined, { silent: true }),
    appStore.loadAllMawbs({ silent: true }),
    appStore.loadBookings(undefined, { silent: true }),
  ]
  if (flightFilterId.value) tasks.push(loadPlanUlds())
  return Promise.all(tasks)
}, { interval: 60000, pauses: [() => !!expandedUldId.value] })
</script>

<style scoped>
input[type="number"]::-webkit-inner-spin-button,
input[type="number"]::-webkit-outer-spin-button { -webkit-appearance: none; margin: 0; }
input[type="number"] { -moz-appearance: textfield; appearance: textfield; }
.row-selected { background: #f8fafc; }
</style>