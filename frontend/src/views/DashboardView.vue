<template>
  <div class="ds-page">
    <header class="ds-section-header">
      <div>
        <h1 class="ds-title">{{ t('dashboard.title') }}</h1>
        <p class="ds-subtitle">{{ t('dashboard.subtitle') }}</p>
      </div>
      <div class="flex items-center gap-2 text-[12px] font-mono font-bold flex-wrap">
        <span class="ds-stat-chip">
          <span class="h-2 w-2 rounded-full" style="background: var(--accent)"></span> {{ t('dashboard.live') }}
        </span>
        <span class="ds-chip">{{ t('dashboard.flightsCount', { n: filteredFlights.length }) }}</span>
        <button @click="descargarReporte" class="ds-btn-primary">
         <span class="text-[14px] font-semibold leading-none">↓</span> {{ t('dashboard.downloadReport') }}
        </button>
      </div>
    </header>

    <QuickActionsPanel class="mb-2" />

    <div class="ds-tabs mb-2">
      <button @click="activeTab = 'flights'" class="ds-tab" :class="activeTab === 'flights' ? 'ds-tab-active' : ''">
        {{ t('dashboard.tabs.flights') }}
      </button>
      <button @click="activeTab = 'weight-report'" class="ds-tab" :class="activeTab === 'weight-report' ? 'ds-tab-active' : ''">
        {{ t('dashboard.tabs.weightReport') }}
      </button>
    </div>

    <div v-if="activeTab === 'flights'" class="flex-1 min-h-0 flex flex-col gap-2">
    <FilterBar
      v-model:date-from="dateFrom"
      v-model:date-to="dateTo"
      :show-date-from="true"
      :show-date-to="true"
      :show-search="false"
      container-class="shrink-0"
    />
    <div class="flex items-center gap-2 shrink-0">
      <label for="dashboard-flight-select" class="text-[11px] font-mono font-bold uppercase tracking-wider text-secondary shrink-0">{{ t('dashboard.flightSelector') }}</label>
      <select id="dashboard-flight-select" v-model="dashboardFlightId" @change="onFlightSelect" class="ds-input cursor-pointer min-w-[220px] max-w-full text-[12px] font-mono font-semibold">
        <option value="">{{ t('dashboard.allFlights') }}</option>
        <option v-for="f in flightOptions" :key="f.id" :value="f.id">{{ flightOptionLabel(f) }}</option>
      </select>
    </div>
    <section class="grid grid-cols-1 sm:grid-cols-3 gap-3 shrink-0">
      <div class="ds-card border-l-emerald-500 flex items-center gap-3">
        <span class="shrink-0 w-8 h-8 rounded-lg bg-emerald-50 text-emerald-700 flex items-center justify-center"><component :is="icons.Scale" :size="16" :stroke-width="2.2" /></span>
        <div class="min-w-0">
          <div class="ds-card-label">{{ t('dashboard.totalNet') }}</div>
          <div class="ds-card-value text-emerald-700 truncate">{{ totalNetAll }} <span class="text-[12px] font-semibold text-tertiary">{{ t('common.lbs') }}</span></div>
        </div>
      </div>
      <div class="ds-card border-l-blue-500 flex items-center gap-3">
        <span class="shrink-0 w-8 h-8 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center"><component :is="icons.Package" :size="16" :stroke-width="2.2" /></span>
        <div class="min-w-0">
          <div class="ds-card-label">{{ t('dashboard.totalUlds') }}</div>
          <div class="ds-card-value text-blue-700">{{ totalUldsCount }} <span class="text-[12px] font-semibold text-tertiary">{{ t('dashboard.uldShort') }}</span></div>
        </div>
      </div>
      <div class="ds-card border-l-slate-800 flex items-center gap-3">
        <span class="shrink-0 w-8 h-8 rounded-lg bg-surface-hover text-primary flex items-center justify-center"><component :is="icons.PlaneDeparture" :size="16" :stroke-width="2.2" /></span>
        <div class="min-w-0">
          <div class="ds-card-label">{{ t('dashboard.totalMawbs') }}</div>
          <div class="ds-card-value text-primary">{{ reservedMawbsCount }} <span class="text-[12px] font-semibold text-tertiary">{{ t('dashboard.mawbShort') }}</span></div>
        </div>
      </div>
    </section>

    <section class="ds-table-section mb-0">
      <EmptyState v-if="loading" :title="t('dashboard.loadingData')" loading />
      <EmptyState v-else-if="filteredFlights.length === 0" :title="t('dashboard.noFlightsInRange')" :hint="t('dashboard.emptyHint')" :icon="icons.PlaneDeparture" />
      <div v-else ref="tableWrapper" class="overflow-auto flex-1 min-h-0 scrollbar-none">
        <div class="table-scroll-wrapper h-full">
        <table class="data-table data-table--light w-full border-collapse text-[13px] font-mono flight-table" :style="{ minWidth: tableMinWidth + 'px' }">
          <thead class="sticky top-0 z-20">
            <tr class="bg-surface-hover text-primary text-[13px] font-bold uppercase tracking-wider border-b-2 border-strong font-mono [&>th]:px-2 [&>th]:py-3 [&>th]:whitespace-nowrap">
              <th class="text-center px-2 py-3 whitespace-nowrap w-8 bg-surface-hover text-secondary">#</th>
              <th class="text-center px-2 py-3 whitespace-nowrap w-8 bg-surface-hover">
                <button @click="toggleAllExpanded" class="flex items-center justify-center gap-1 hover:opacity-70 transition"
                  :title="allExpanded ? t('dashboard.collapseAll') : t('dashboard.expandAll')">
                  <span class="text-[14px]">{{ allExpanded ? '▲' : '▼' }}</span>
                </button>
              </th>
              <th class="text-left px-2 py-3 whitespace-nowrap bg-surface-hover relative">
                <span @click="hf.toggleHeaderFilter('flight')" class="cursor-pointer select-none" :class="hf.columnFilters.flight ? 'text-indigo-600' : 'hover:text-primary'">
                  {{ t('dashboard.table.flight') }} <span class="text-[10px]" :class="hf.columnFilters.flight ? 'opacity-100' : 'opacity-40'">&#9660;</span>
                </span>
                <div v-if="hf.headerFilterOpen === 'flight'" class="absolute top-full left-0 mt-1 bg-surface border border-strong rounded shadow-lg z-50 min-w-[160px] max-h-[220px] overflow-y-auto text-[13px] text-primary font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('flight', null)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover font-bold" :class="!hf.columnFilters.flight ? 'bg-surface-hover' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in flightUniq.flight" :key="v" @click="hf.setColumnFilter('flight', v)"
                    class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover truncate" :class="hf.columnFilters.flight === v ? 'bg-surface-hover text-primary font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-center px-2 py-3 whitespace-nowrap bg-surface-hover relative">
                <span @click="hf.toggleHeaderFilter('route')" class="cursor-pointer select-none" :class="hf.columnFilters.route ? 'text-indigo-600' : 'hover:text-primary'">
                  {{ t('dashboard.table.route') }} <span class="text-[10px]" :class="hf.columnFilters.route ? 'opacity-100' : 'opacity-40'">&#9660;</span>
                </span>
                <div v-if="hf.headerFilterOpen === 'route'" class="absolute top-full left-1/2 -translate-x-1/2 mt-1 bg-surface border border-strong rounded shadow-lg z-50 min-w-[180px] max-h-[220px] overflow-y-auto text-[13px] text-primary font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('route', null)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover font-bold" :class="!hf.columnFilters.route ? 'bg-surface-hover' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in flightUniq.route" :key="v" @click="hf.setColumnFilter('route', v)"
                    class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover truncate" :class="hf.columnFilters.route === v ? 'bg-surface-hover text-primary font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-center px-2 py-3 whitespace-nowrap bg-surface-hover relative">
                <span @click="hf.toggleHeaderFilter('date')" class="cursor-pointer select-none" :class="hf.columnFilters.date ? 'text-indigo-600' : 'hover:text-primary'">
                  {{ t('dashboard.table.date') }} <span class="text-[10px]" :class="hf.columnFilters.date ? 'opacity-100' : 'opacity-40'">&#9660;</span>
                </span>
                <div v-if="hf.headerFilterOpen === 'date'" class="absolute top-full left-1/2 -translate-x-1/2 mt-1 bg-surface border border-strong rounded shadow-lg z-50 min-w-[160px] max-h-[220px] overflow-y-auto text-[13px] text-primary font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('date', null)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover font-bold" :class="!hf.columnFilters.date ? 'bg-surface-hover' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in flightUniq.date" :key="v" @click="hf.setColumnFilter('date', v)"
                    class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover truncate" :class="hf.columnFilters.date === v ? 'bg-surface-hover text-primary font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-center px-2 py-2.5 whitespace-nowrap relative">
                <span @click="hf.toggleHeaderFilter('status')" class="cursor-pointer select-none" :class="hf.columnFilters.status ? 'text-indigo-600' : 'hover:text-primary'">
                  {{ t('dashboard.table.status') }} <span class="text-[10px]" :class="hf.columnFilters.status ? 'opacity-100' : 'opacity-40'">&#9660;</span>
                </span>
                <div v-if="hf.headerFilterOpen === 'status'" class="absolute top-full left-1/2 -translate-x-1/2 mt-1 bg-surface border border-strong rounded shadow-lg z-50 min-w-[140px] text-[13px] text-primary font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('status', null)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover font-bold" :class="!hf.columnFilters.status ? 'bg-surface-hover' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in flightUniq.status" :key="v" @click="hf.setColumnFilter('status', v)"
                    class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover text-center" :class="hf.columnFilters.status === v ? 'bg-surface-hover text-primary font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-center px-2 py-2.5 whitespace-nowrap w-16 relative">
                <span @click="hf.toggleHeaderFilter('ulds')" class="cursor-pointer select-none" :class="hf.columnFilters.ulds ? 'text-indigo-600' : 'hover:text-primary'">
                  {{ t('dashboard.table.ulds') }} <span class="text-[10px]" :class="hf.columnFilters.ulds ? 'opacity-100' : 'opacity-40'">&#9660;</span>
                </span>
                <div v-if="hf.headerFilterOpen === 'ulds'" class="absolute top-full left-1/2 -translate-x-1/2 mt-1 bg-surface border border-strong rounded shadow-lg z-50 min-w-[120px] max-h-[220px] overflow-y-auto text-[13px] text-primary font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('ulds', null)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover font-bold text-center" :class="!hf.columnFilters.ulds ? 'bg-surface-hover' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in flightUniq.ulds" :key="v" @click="hf.setColumnFilter('ulds', v)"
                    class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover text-center" :class="hf.columnFilters.ulds === v ? 'bg-surface-hover text-primary font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-center px-2 py-2.5 whitespace-nowrap w-14 relative">
                <span @click="hf.toggleHeaderFilter('pos')" class="cursor-pointer select-none" :class="hf.columnFilters.pos ? 'text-indigo-600' : 'hover:text-primary'">
                  {{ t('dashboard.table.pos') }} <span class="text-[10px]" :class="hf.columnFilters.pos ? 'opacity-100' : 'opacity-40'">&#9660;</span>
                </span>
                <div v-if="hf.headerFilterOpen === 'pos'" class="absolute top-full left-1/2 -translate-x-1/2 mt-1 bg-surface border border-strong rounded shadow-lg z-50 min-w-[120px] max-h-[220px] overflow-y-auto text-[13px] text-primary font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('pos', null)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover font-bold text-center" :class="!hf.columnFilters.pos ? 'bg-surface-hover' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in flightUniq.pos" :key="v" @click="hf.setColumnFilter('pos', v)"
                    class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover text-center" :class="hf.columnFilters.pos === v ? 'bg-surface-hover text-primary font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-right px-2 py-2.5 whitespace-nowrap w-24 relative">
                <span @click="hf.toggleHeaderFilter('gross')" class="cursor-pointer select-none" :class="hf.columnFilters.gross ? 'text-indigo-600' : 'hover:text-primary'">
                  {{ t('dashboard.table.gross') }} <span class="text-[10px]" :class="hf.columnFilters.gross ? 'opacity-100' : 'opacity-40'">&#9660;</span>
                </span>
                <div v-if="hf.headerFilterOpen === 'gross'" class="absolute top-full right-0 mt-1 bg-surface border border-strong rounded shadow-lg z-50 min-w-[120px] max-h-[220px] overflow-y-auto text-[13px] text-primary font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('gross', null)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover font-bold text-center" :class="!hf.columnFilters.gross ? 'bg-surface-hover' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in flightUniq.gross" :key="v" @click="hf.setColumnFilter('gross', v)"
                    class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover text-center" :class="hf.columnFilters.gross === v ? 'bg-surface-hover text-primary font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-right px-2 py-2.5 whitespace-nowrap w-24 relative">
                <span @click="hf.toggleHeaderFilter('tare')" class="cursor-pointer select-none" :class="hf.columnFilters.tare ? 'text-indigo-600' : 'hover:text-primary'">
                  {{ t('dashboard.table.tare') }} <span class="text-[10px]" :class="hf.columnFilters.tare ? 'opacity-100' : 'opacity-40'">&#9660;</span>
                </span>
                <div v-if="hf.headerFilterOpen === 'tare'" class="absolute top-full right-0 mt-1 bg-surface border border-strong rounded shadow-lg z-50 min-w-[120px] max-h-[220px] overflow-y-auto text-[13px] text-primary font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('tare', null)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover font-bold text-center" :class="!hf.columnFilters.tare ? 'bg-surface-hover' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in flightUniq.tare" :key="v" @click="hf.setColumnFilter('tare', v)"
                    class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover text-center" :class="hf.columnFilters.tare === v ? 'bg-surface-hover text-primary font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-right px-2 py-2.5 whitespace-nowrap w-24 relative">
                <span @click="hf.toggleHeaderFilter('net')" class="cursor-pointer select-none" :class="hf.columnFilters.net ? 'text-indigo-600' : 'hover:text-primary'">
                  {{ t('dashboard.table.net') }} <span class="text-[10px]" :class="hf.columnFilters.net ? 'opacity-100' : 'opacity-40'">&#9660;</span>
                </span>
                <div v-if="hf.headerFilterOpen === 'net'" class="absolute top-full right-0 mt-1 bg-surface border border-strong rounded shadow-lg z-50 min-w-[120px] max-h-[220px] overflow-y-auto text-[13px] text-primary font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('net', null)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover font-bold text-center" :class="!hf.columnFilters.net ? 'bg-surface-hover' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in flightUniq.net" :key="v" @click="hf.setColumnFilter('net', v)"
                    class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover text-center" :class="hf.columnFilters.net === v ? 'bg-surface-hover text-primary font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-right px-2 py-2.5 whitespace-nowrap w-24 text-emerald-600 relative">
                <span @click="hf.toggleHeaderFilter('payload')" class="cursor-pointer select-none" :class="hf.columnFilters.payload ? 'text-indigo-600' : 'hover:text-primary'">
                  {{ t('dashboard.table.payload') }} <span class="text-[10px]" :class="hf.columnFilters.payload ? 'opacity-100' : 'opacity-40'">&#9660;</span>
                </span>
                <div v-if="hf.headerFilterOpen === 'payload'" class="absolute top-full right-0 mt-1 bg-surface border border-strong rounded shadow-lg z-50 min-w-[120px] max-h-[220px] overflow-y-auto text-[13px] text-primary font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('payload', null)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover font-bold text-center" :class="!hf.columnFilters.payload ? 'bg-surface-hover' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in flightUniq.payload" :key="v" @click="hf.setColumnFilter('payload', v)"
                    class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover text-center" :class="hf.columnFilters.payload === v ? 'bg-surface-hover text-primary font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <!-- Commodity columns - dynamic based on filtered flights -->
              <th v-for="c in visibleCommodities" :key="c.type"
                class="text-right px-2 py-2.5 whitespace-nowrap w-20 text-[12px]"
                :style="{ background: c.color + '20', borderLeft: '1px solid ' + c.color + '40' }"
                :title="c.label">
                <div class="flex items-center justify-end gap-1">
                  <span class="w-1.5 h-1.5 rounded-full" :style="{ background: c.color }"></span>
                  <span class="font-mono">{{ c.short }}</span>
                </div>
              </th>
            </tr>
          </thead>
          <tbody>
            <template v-for="(f, fi) in filteredFlights" :key="f.id">
              <tr class="border-b border-default transition-colors duration-150 hover:bg-surface-hover/80"
                :class="{ 'bg-surface-hover/50': isExpanded(f.id) }">
                <td class="text-center px-2 py-2 text-tertiary">{{ fi + 1 }}</td>
                <td class="text-center px-2 py-2">
                  <button @click="toggleExpand(f.id)"
                    class="flex items-center justify-center w-6 h-6 rounded hover:bg-surface-hover transition text-secondary hover:text-primary"
                    :aria-expanded="isExpanded(f.id)"
                    :title="isExpanded(f.id) ? t('dashboard.collapseDetail') : t('dashboard.expandDetail')" :aria-label="isExpanded(f.id) ? t('dashboard.collapseDetail') : t('dashboard.expandDetail')">
                    <span class="text-[12px] transition-transform duration-200" :style="{ transform: isExpanded(f.id) ? 'rotate(180deg)' : '' }">▼</span>
                  </button>
                </td>
                <td class="px-2 py-2 font-mono text-primary">UPS-{{ f.flightNumber }}</td>
                <td class="text-center px-2 py-2 text-primary">{{ f.origin }}→{{ f.destination }}</td>
                <td class="text-center px-2 py-2 text-secondary">{{ f.flightDate }}</td>
                <td class="text-center px-2 py-2">
                  <span class="inline-flex items-center gap-1">
                    <span :class="getStatusDot(f.status)" class="inline-block w-2 h-2 rounded-full"></span>
                    <span class="px-1.5 py-0.5 rounded text-[12px] font-medium" :style="statusStyle(f.status)">{{ statusLabel(f.status) }}</span>
                  </span>
                </td>
                <td class="text-center px-2 py-2 font-mono text-primary">{{ flightUlds(f.id).length }}</td>
                <td class="text-center px-2 py-2 font-mono text-secondary">{{ flightPositions(f.id) }}<span class="text-tertiary">/</span>{{ f.totalPositions || '—' }}</td>
                <td class="text-right px-2 py-2 font-mono text-primary">{{ grossLbs(f.id) }}</td>
                <td class="text-right px-2 py-2 font-mono text-secondary">{{ totalTareLbs(f.id) }}</td>
                <td class="text-right px-2 py-2 font-mono text-primary">{{ netLbs(f.id) }}</td>
                <td class="text-right px-2 py-2 font-bold text-emerald-700" style="font-family: 'SF Mono', 'Fira Code', monospace;">{{ payloadLbs(f.id) }}</td>
                <!-- Commodity payload columns -->
                <td v-for="c in visibleCommodities" :key="c.type"
                  class="text-right px-2 py-2 font-mono text-primary tabular-nums"
                  :style="{ background: c.color + '08' }"
                  :title="commodityTooltip(f.id, c.type)">
                  <template v-if="commodityPayload(f.id, c.type) && typeof commodityPayload(f.id, c.type) === 'object' && commodityPayload(f.id, c.type).pcs">
                    <span class="text-accent">{{ commodityPayload(f.id, c.type).pcs }} pcs</span>
                  </template>
                  <template v-else>
                    {{ commodityPayload(f.id, c.type) || '—' }}
                  </template>
                </td>
              </tr>

              <!-- Drill-down row -->
              <tr v-show="isExpanded(f.id)" class="bg-surface-hover/30 border-t border-strong">
                <td :colspan="13 + visibleCommodities.length" class="p-0">
                  <div class="p-3 md:p-4 border-t border-strong" style="animation: slideDown 0.2s ease-out;">
                    <FlightDetail :flight="f" :flight-id="f.id" />
                  </div>
                </td>
              </tr>
            </template>

            <!-- Totals row -->
            <tr class="bg-surface-hover border-t-2 border-strong font-bold hover:bg-surface-hover transition-colors">
              <td class="text-center px-2 py-2 text-tertiary">Σ</td>
              <td class="text-center px-2 py-2"></td>
              <td class="px-2 py-2 text-secondary bg-surface-hover">{{ t('dashboard.table.total') }}</td>
              <td class="text-center px-2 py-2 bg-surface-hover"></td>
              <td class="text-center px-2 py-2 bg-surface-hover"></td>
              <td class="text-center px-2 py-2"></td>
              <td class="text-center px-2 py-2">{{ totalUldsCount }}</td>
              <td class="text-center px-2 py-2">{{ totalPositionsAll }}<span class="text-tertiary">/</span>{{ totalMaxPositionsAll }}</td>
              <td class="text-right px-2 py-2">{{ totalGrossAll }}</td>
              <td class="text-right px-2 py-2">{{ totalTareAll }}</td>
              <td class="text-right px-2 py-2">{{ totalNetAll }}</td>
              <td class="text-right px-2 py-2 text-emerald-700">{{ totalNetPayload }}</td>
              <td v-for="c in visibleCommodities" :key="c.type"
                class="text-right px-2 py-2 text-primary tabular-nums"
                :style="{ background: c.color + '15' }">
                <template v-if="LEGACY_COMMODITY_CODES.includes(c.type)">
                  {{ totalCommodityPieces(c.type) }} pcs
                </template>
                <template v-else>
                  {{ totalCommodityPayload(c.type) }}
                </template>
              </td>
            </tr>
          </tbody>
        </table>
        </div>
      </div>
    </section>
    </div>

    <div v-if="activeTab === 'weight-report'" class="flex-1 min-h-0 flex flex-col gap-2">
    <div class="flex items-end gap-2 shrink-0">
      <FilterBar
        v-model:date-from="wrDateFrom"
        v-model:date-to="wrDateTo"
        v-model:commodity="wrCommodity"
        v-model:search-text="wrFlightNumber"
        :show-period-presets="true"
        :show-date-from="true"
        :show-date-to="true"
        :show-commodity="true"
        :show-search="true"
        :show-search-button="true"
        :show-clear="true"
        :loading="wrLoading"
        :search-label="t('common.flight')"
        :search-placeholder="t('dashboard.wr.searchPlaceholder')"
        :search-button-label="t('common.search')"
        @search="loadWeightReport"
        @clear="wrDateFrom = ''; wrDateTo = ''; wrCommodity = ''; wrFlightNumber = ''; loadWeightReport()"
      />
      <button v-if="wrRows.length" @click="exportWeightCSV" class="ds-btn-secondary mb-0.5">
        <span class="text-[14px] font-semibold leading-none">&#8595;</span> {{ t('dashboard.wr.exportCsv') }}
      </button>
    </div>

    <!-- Summary Cards -->
    <div v-if="wrSummary" class="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-4">
      <div class="ds-card flex items-center gap-3">
        <span class="shrink-0 w-8 h-8 rounded-lg bg-surface-hover text-primary flex items-center justify-center"><component :is="icons.LayoutGrid" :size="16" :stroke-width="2.2" /></span>
        <div class="min-w-0">
          <div class="ds-card-label">{{ t('dashboard.wr.summary.totalRows') }}</div>
          <div class="ds-card-value text-primary">{{ wrSummary.totalRows }}</div>
        </div>
      </div>
      <div class="ds-card border-l-blue-500 flex items-center gap-3">
        <span class="shrink-0 w-8 h-8 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center"><component :is="icons.Package" :size="16" :stroke-width="2.2" /></span>
        <div class="min-w-0">
          <div class="ds-card-label">{{ t('dashboard.wr.summary.receivedPieces') }}</div>
          <div class="ds-card-value text-blue-700">{{ wrSummary.totalReceivedPieces }}</div>
        </div>
      </div>
      <div class="ds-card border-l-emerald-500 flex items-center gap-3">
        <span class="shrink-0 w-8 h-8 rounded-lg bg-emerald-50 text-emerald-700 flex items-center justify-center"><component :is="icons.Scale" :size="16" :stroke-width="2.2" /></span>
        <div class="min-w-0">
          <div class="ds-card-label">{{ t('dashboard.wr.summary.physicalWeightLbs') }}</div>
          <div class="ds-card-value text-emerald-700">{{ formatNum(wrSummary.totalPhysicalWeightLbs) }}</div>
        </div>
      </div>
      <div class="ds-card border-l-amber-500 flex items-center gap-3">
        <span class="shrink-0 w-8 h-8 rounded-lg bg-amber-50 text-amber-700 flex items-center justify-center"><component :is="icons.PlaneDeparture" :size="16" :stroke-width="2.2" /></span>
        <div class="min-w-0">
          <div class="ds-card-label">{{ t('dashboard.wr.summary.dispatchedWeightLbs') }}</div>
          <div class="ds-card-value text-amber-700">{{ formatNum(wrSummary.totalDispatchedWeightLbs) }}</div>
        </div>
      </div>
    </div>

    <!-- Per-Commodity Breakdown -->
    <div v-if="wrSummary?.byCommodity && Object.keys(wrSummary.byCommodity).length > 1" class="mb-4">
      <div class="text-[12px] font-bold text-secondary uppercase tracking-wider mb-2">{{ t('dashboard.wr.byCommodity') }}</div>
      <div class="flex flex-wrap gap-2">
        <div v-for="(data, code) in wrSummary.byCommodity" :key="code"
          class="inline-flex items-center gap-2 px-3 py-1.5 rounded-lg border border-strong bg-surface text-[12px] shadow-sm">
          <span class="w-2.5 h-2.5 rounded-full flex-shrink-0" :style="{ background: commodityColor(code) }"></span>
          <span class="font-bold text-slate-800">{{ code }}</span>
          <span class="text-tertiary">|</span>
          <span class="text-secondary">{{ t('dashboard.wr.piecesUnit', { n: data.totalReceivedPieces }) }}</span>
          <span class="text-tertiary">|</span>
          <span class="font-semibold text-emerald-700">{{ formatNum(data.totalPhysicalWeightLbs) }} {{ t('common.lbs') }}</span>
          <span class="text-tertiary">→</span>
          <span class="font-semibold text-amber-700">{{ formatNum(data.totalDispatchedWeightLbs) }} {{ t('common.lbs') }}</span>
          <span class="text-tertiary">·</span>
          <span class="w-px h-3 bg-slate-200"></span>
          <span class="text-[11px] text-secondary">{{ data.totalDispatchedPieces }} {{ t('common.pcs') }}</span>
        </div>
      </div>
    </div>

    <!-- Weight Report Table -->
    <section class="ds-table-section">
      <EmptyState v-if="wrLoading && !wrRows.length" :title="t('common.loading')" loading />
      <EmptyState v-else-if="!wrRows.length" :title="t('dashboard.wr.noData')" :icon="icons.Gauge" />
      <div v-else class="overflow-auto flex-1 min-h-0 scrollbar-none">
        <table class="data-table data-table--light w-full border-collapse text-[12px] font-mono" style="min-width: 1100px">
          <thead class="bg-surface-hover text-primary sticky top-0 z-10">
            <tr>
              <th class="text-left px-3 py-2.5 text-[12px] font-bold text-primary border-b border-strong uppercase tracking-wider relative">
                <span @click="hf.toggleHeaderFilter('wr_awb')" class="cursor-pointer select-none" :class="hf.columnFilters.wr_awb ? 'text-indigo-600' : 'hover:text-primary'">{{ t('dashboard.wr.table.awb') }} <span class="text-[10px]" :class="hf.columnFilters.wr_awb ? 'opacity-100' : 'opacity-40'">&#9660;</span></span>
                <div v-if="hf.headerFilterOpen === 'wr_awb'" class="absolute top-full left-0 mt-1 bg-surface border border-strong rounded shadow-lg z-50 min-w-[160px] max-h-[220px] overflow-y-auto text-[13px] text-primary font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('wr_awb', null)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover font-bold" :class="!hf.columnFilters.wr_awb ? 'bg-surface-hover' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in wrUniq.awb" :key="v" @click="hf.setColumnFilter('wr_awb', v)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover truncate" :class="hf.columnFilters.wr_awb === v ? 'bg-surface-hover text-primary font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-left px-3 py-2.5 text-[12px] font-bold text-primary border-b border-strong uppercase tracking-wider relative">
                <span @click="hf.toggleHeaderFilter('wr_shipper')" class="cursor-pointer select-none" :class="hf.columnFilters.wr_shipper ? 'text-indigo-600' : 'hover:text-primary'">{{ t('dashboard.wr.table.shipper') }} <span class="text-[10px]" :class="hf.columnFilters.wr_shipper ? 'opacity-100' : 'opacity-40'">&#9660;</span></span>
                <div v-if="hf.headerFilterOpen === 'wr_shipper'" class="absolute top-full left-0 mt-1 bg-surface border border-strong rounded shadow-lg z-50 min-w-[180px] max-h-[220px] overflow-y-auto text-[13px] text-primary font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('wr_shipper', null)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover font-bold" :class="!hf.columnFilters.wr_shipper ? 'bg-surface-hover' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in wrUniq.shipper" :key="v" @click="hf.setColumnFilter('wr_shipper', v)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover truncate" :class="hf.columnFilters.wr_shipper === v ? 'bg-surface-hover text-primary font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-left px-3 py-2.5 text-[12px] font-bold text-primary border-b border-strong uppercase tracking-wider relative">
                <span @click="hf.toggleHeaderFilter('wr_consignee')" class="cursor-pointer select-none" :class="hf.columnFilters.wr_consignee ? 'text-indigo-600' : 'hover:text-primary'">{{ t('dashboard.wr.table.consignee') }} <span class="text-[10px]" :class="hf.columnFilters.wr_consignee ? 'opacity-100' : 'opacity-40'">&#9660;</span></span>
                <div v-if="hf.headerFilterOpen === 'wr_consignee'" class="absolute top-full left-0 mt-1 bg-surface border border-strong rounded shadow-lg z-50 min-w-[180px] max-h-[220px] overflow-y-auto text-[13px] text-primary font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('wr_consignee', null)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover font-bold" :class="!hf.columnFilters.wr_consignee ? 'bg-surface-hover' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in wrUniq.consignee" :key="v" @click="hf.setColumnFilter('wr_consignee', v)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover truncate" :class="hf.columnFilters.wr_consignee === v ? 'bg-surface-hover text-primary font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-center px-3 py-2.5 text-[12px] font-bold text-primary border-b border-strong uppercase tracking-wider relative">
                <span @click="hf.toggleHeaderFilter('wr_dest')" class="cursor-pointer select-none" :class="hf.columnFilters.wr_dest ? 'text-indigo-600' : 'hover:text-primary'">{{ t('dashboard.wr.table.dest') }} <span class="text-[10px]" :class="hf.columnFilters.wr_dest ? 'opacity-100' : 'opacity-40'">&#9660;</span></span>
                <div v-if="hf.headerFilterOpen === 'wr_dest'" class="absolute top-full left-1/2 -translate-x-1/2 mt-1 bg-surface border border-strong rounded shadow-lg z-50 min-w-[120px] max-h-[220px] overflow-y-auto text-[13px] text-primary font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('wr_dest', null)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover font-bold text-center" :class="!hf.columnFilters.wr_dest ? 'bg-surface-hover' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in wrUniq.dest" :key="v" @click="hf.setColumnFilter('wr_dest', v)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover text-center" :class="hf.columnFilters.wr_dest === v ? 'bg-surface-hover text-primary font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-center px-3 py-2.5 text-[12px] font-bold text-primary border-b border-strong uppercase tracking-wider relative">
                <span @click="hf.toggleHeaderFilter('wr_commodity')" class="cursor-pointer select-none" :class="hf.columnFilters.wr_commodity ? 'text-indigo-600' : 'hover:text-primary'">{{ t('dashboard.wr.table.commodity') }} <span class="text-[10px]" :class="hf.columnFilters.wr_commodity ? 'opacity-100' : 'opacity-40'">&#9660;</span></span>
                <div v-if="hf.headerFilterOpen === 'wr_commodity'" class="absolute top-full left-1/2 -translate-x-1/2 mt-1 bg-surface border border-strong rounded shadow-lg z-50 min-w-[140px] max-h-[220px] overflow-y-auto text-[13px] text-primary font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('wr_commodity', null)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover font-bold text-center" :class="!hf.columnFilters.wr_commodity ? 'bg-surface-hover' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in wrUniq.commodity" :key="v" @click="hf.setColumnFilter('wr_commodity', v)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover text-center" :class="hf.columnFilters.wr_commodity === v ? 'bg-surface-hover text-primary font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-center px-3 py-2.5 text-[12px] font-bold text-primary border-b border-strong uppercase tracking-wider relative">
                <span @click="hf.toggleHeaderFilter('wr_flight')" class="cursor-pointer select-none" :class="hf.columnFilters.wr_flight ? 'text-indigo-600' : 'hover:text-primary'">{{ t('dashboard.wr.table.flight') }} <span class="text-[10px]" :class="hf.columnFilters.wr_flight ? 'opacity-100' : 'opacity-40'">&#9660;</span></span>
                <div v-if="hf.headerFilterOpen === 'wr_flight'" class="absolute top-full left-1/2 -translate-x-1/2 mt-1 bg-surface border border-strong rounded shadow-lg z-50 min-w-[120px] max-h-[220px] overflow-y-auto text-[13px] text-primary font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('wr_flight', null)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover font-bold text-center" :class="!hf.columnFilters.wr_flight ? 'bg-surface-hover' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in wrUniq.flight" :key="v" @click="hf.setColumnFilter('wr_flight', v)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover text-center" :class="hf.columnFilters.wr_flight === v ? 'bg-surface-hover text-primary font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-center px-3 py-2.5 text-[12px] font-bold text-primary border-b border-strong uppercase tracking-wider relative">
                <span @click="hf.toggleHeaderFilter('wr_date')" class="cursor-pointer select-none" :class="hf.columnFilters.wr_date ? 'text-indigo-600' : 'hover:text-primary'">{{ t('dashboard.wr.table.date') }} <span class="text-[10px]" :class="hf.columnFilters.wr_date ? 'opacity-100' : 'opacity-40'">&#9660;</span></span>
                <div v-if="hf.headerFilterOpen === 'wr_date'" class="absolute top-full left-1/2 -translate-x-1/2 mt-1 bg-surface border border-strong rounded shadow-lg z-50 min-w-[140px] max-h-[220px] overflow-y-auto text-[13px] text-primary font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('wr_date', null)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover font-bold text-center" :class="!hf.columnFilters.wr_date ? 'bg-surface-hover' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in wrUniq.date" :key="v" @click="hf.setColumnFilter('wr_date', v)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover text-center" :class="hf.columnFilters.wr_date === v ? 'bg-surface-hover text-primary font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-right px-3 py-2.5 text-[12px] font-bold text-primary border-b border-strong uppercase tracking-wider relative">
                <span @click="hf.toggleHeaderFilter('wr_pcsRec')" class="cursor-pointer select-none" :class="hf.columnFilters.wr_pcsRec ? 'text-indigo-600' : 'hover:text-primary'">{{ t('dashboard.wr.table.pcsRec') }} <span class="text-[10px]" :class="hf.columnFilters.wr_pcsRec ? 'opacity-100' : 'opacity-40'">&#9660;</span></span>
                <div v-if="hf.headerFilterOpen === 'wr_pcsRec'" class="absolute top-full right-0 mt-1 bg-surface border border-strong rounded shadow-lg z-50 min-w-[120px] max-h-[220px] overflow-y-auto text-[13px] text-primary font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('wr_pcsRec', null)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover font-bold text-center" :class="!hf.columnFilters.wr_pcsRec ? 'bg-surface-hover' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in wrUniq.pcsRec" :key="v" @click="hf.setColumnFilter('wr_pcsRec', v)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover text-center" :class="hf.columnFilters.wr_pcsRec === v ? 'bg-surface-hover text-primary font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-right px-3 py-2.5 text-[12px] font-bold text-primary border-b border-strong uppercase tracking-wider relative">
                <span @click="hf.toggleHeaderFilter('wr_physical')" class="cursor-pointer select-none" :class="hf.columnFilters.wr_physical ? 'text-indigo-600' : 'hover:text-primary'">{{ t('dashboard.wr.table.physicalLbs') }} <span class="text-[10px]" :class="hf.columnFilters.wr_physical ? 'opacity-100' : 'opacity-40'">&#9660;</span></span>
                <div v-if="hf.headerFilterOpen === 'wr_physical'" class="absolute top-full right-0 mt-1 bg-surface border border-strong rounded shadow-lg z-50 min-w-[120px] max-h-[220px] overflow-y-auto text-[13px] text-primary font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('wr_physical', null)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover font-bold text-center" :class="!hf.columnFilters.wr_physical ? 'bg-surface-hover' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in wrUniq.physical" :key="v" @click="hf.setColumnFilter('wr_physical', v)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover text-center" :class="hf.columnFilters.wr_physical === v ? 'bg-surface-hover text-primary font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-right px-3 py-2.5 text-[12px] font-bold text-primary border-b border-strong uppercase tracking-wider relative">
                <span @click="hf.toggleHeaderFilter('wr_dispatched')" class="cursor-pointer select-none" :class="hf.columnFilters.wr_dispatched ? 'text-indigo-600' : 'hover:text-primary'">{{ t('dashboard.wr.table.dispatchedLbs') }} <span class="text-[10px]" :class="hf.columnFilters.wr_dispatched ? 'opacity-100' : 'opacity-40'">&#9660;</span></span>
                <div v-if="hf.headerFilterOpen === 'wr_dispatched'" class="absolute top-full right-0 mt-1 bg-surface border border-strong rounded shadow-lg z-50 min-w-[120px] max-h-[220px] overflow-y-auto text-[13px] text-primary font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('wr_dispatched', null)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover font-bold text-center" :class="!hf.columnFilters.wr_dispatched ? 'bg-surface-hover' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in wrUniq.dispatched" :key="v" @click="hf.setColumnFilter('wr_dispatched', v)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover text-center" :class="hf.columnFilters.wr_dispatched === v ? 'bg-surface-hover text-primary font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-right px-3 py-2.5 text-[12px] font-bold text-primary border-b border-strong uppercase tracking-wider relative">
                <span @click="hf.toggleHeaderFilter('wr_pcsDisp')" class="cursor-pointer select-none" :class="hf.columnFilters.wr_pcsDisp ? 'text-indigo-600' : 'hover:text-primary'">{{ t('dashboard.wr.table.pcsDisp') }} <span class="text-[10px]" :class="hf.columnFilters.wr_pcsDisp ? 'opacity-100' : 'opacity-40'">&#9660;</span></span>
                <div v-if="hf.headerFilterOpen === 'wr_pcsDisp'" class="absolute top-full right-0 mt-1 bg-surface border border-strong rounded shadow-lg z-50 min-w-[120px] max-h-[220px] overflow-y-auto text-[13px] text-primary font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('wr_pcsDisp', null)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover font-bold text-center" :class="!hf.columnFilters.wr_pcsDisp ? 'bg-surface-hover' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in wrUniq.pcsDisp" :key="v" @click="hf.setColumnFilter('wr_pcsDisp', v)" class="px-3 py-1.5 cursor-pointer hover:bg-surface-hover text-center" :class="hf.columnFilters.wr_pcsDisp === v ? 'bg-surface-hover text-primary font-bold' : ''">{{ v }}</div>
                </div>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, idx) in wrVisibleRows" :key="idx"
              :class="idx % 2 === 1 ? 'bg-surface-hover/60' : 'bg-surface'"
              class="hover:bg-surface-hover/80 border-b border-default transition-colors">
              <td class="px-3 py-2 font-bold text-primary">{{ row.awbNumber }}</td>
              <td class="px-3 py-2 text-secondary">{{ row.shipperName }}</td>
              <td class="px-3 py-2 text-secondary">{{ row.consigneeName }}</td>
              <td class="text-center px-3 py-2"><span class="px-1.5 py-0.5 rounded text-[10px] font-bold bg-surface-hover text-primary">{{ row.destination }}</span></td>
              <td class="text-center px-3 py-2">
                <span v-if="row.commodityType"
                  class="inline-block px-2 py-0.5 rounded-full text-[10px] font-bold"
                  :style="{ background: commodityColor(row.commodityType) + '18', color: commodityColor(row.commodityType) }"
                  :title="tooltipOf(row.commodityType)">
                  {{ labelOf(row.commodityType) }}
                </span>
              </td>
              <td class="text-center px-3 py-2 font-semibold text-slate-800">{{ row.flightNumber }}</td>
              <td class="text-center px-3 py-2 text-secondary">{{ row.flightDate }}</td>
              <td class="text-right px-3 py-2 tabular-nums">{{ row.receivedPieces }}</td>
              <td class="text-right px-3 py-2 tabular-nums font-semibold text-emerald-700">{{ formatNum(row.physicalWeightLbs) }}</td>
              <td class="text-right px-3 py-2 tabular-nums font-semibold text-amber-700">{{ formatNum(row.dispatchedWeightLbs) }}</td>
              <td class="text-right px-3 py-2 tabular-nums">{{ row.dispatchedPieces }}</td>
            </tr>
            <tr v-if="wrVisibleRows.length" class="bg-surface-hover/80 border-t-2 border-strong font-bold hover:bg-surface-hover transition-colors">
              <td class="px-3 py-2.5 text-secondary" colspan="7">{{ t('dashboard.table.total') }}</td>
              <td class="text-right px-3 py-2.5 tabular-nums text-primary">{{ formatNum(wrTotals.receivedPieces) }}</td>
              <td class="text-right px-3 py-2.5 tabular-nums text-emerald-800">{{ formatNum(wrTotals.physicalWeightLbs) }}</td>
              <td class="text-right px-3 py-2.5 tabular-nums text-amber-800">{{ formatNum(wrTotals.dispatchedWeightLbs) }}</td>
              <td class="text-right px-3 py-2.5 tabular-nums text-primary">{{ formatNum(wrTotals.dispatchedPieces) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
    </div>

  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '../stores/app'
import { downloadCSV } from '../utils/csv'
import FlightDetail from '../components/FlightDetail.vue'
import { useCommodities } from '../composables/useCommodities'
import { biApi } from '../api/bi'
import FilterBar from '../components/FilterBar.vue'
import EmptyState from '../components/EmptyState.vue'
import { useIcons } from '../composables/useIcons'
import { useHeaderFilters } from '../composables/useHeaderFilters'
import { useLiveRefresh } from '../composables/useLiveRefresh'
import QuickActionsPanel from '../components/QuickActionsPanel.vue'


// Legacy commodity types that may not be in DB but exist in historical data (FCC, EMPTY_BAGS, etc.)

const { t } = useI18n()
const icons = useIcons()
const appStore = useAppStore()
const { commodities: dbCommodities, loadCommodities, labelOf, tooltipOf } = useCommodities()
const hf = useHeaderFilters({ containerSelector: '.ds-table-section' })

const dateFrom = ref('')
const dateTo = ref('')
const loading = ref(false)
const expandedFlights = ref(new Set())
const dashboardFlightId = ref('')
const activeTab = ref('flights')

watch(activeTab, (tab) => {
  if (tab === 'weight-report' && !wrRows.value.length && !wrLoading.value) {
    loadWeightReport()
  }
})

// Weight report state
const wrDateFrom = ref('')
const wrDateTo = ref('')
const wrCommodity = ref('')
const wrFlightNumber = ref('')
const wrLoading = ref(false)
const wrRows = ref([])
const wrSummary = ref(null)

const wrVisibleRows = computed(() => {
  const cf = hf.columnFilters
  let list = wrRows.value
  const eq = (k, c) => (c !== null && c !== undefined && list) ? list.filter(r => String(r[k]) === String(c)) : list
  list = eq('awbNumber', cf.wr_awb)
  list = eq('shipperName', cf.wr_shipper)
  list = eq('consigneeName', cf.wr_consignee)
  list = eq('destination', cf.wr_dest)
  list = eq('commodityType', cf.wr_commodity)
  list = eq('flightNumber', cf.wr_flight)
  list = eq('flightDate', cf.wr_date)
  const num = (k, c) => (c !== null && c !== undefined) ? list.filter(r => Number(r[k]) === Number(c)) : list
  list = num('receivedPieces', cf.wr_pcsRec)
  list = num('physicalWeightLbs', cf.wr_physical)
  list = num('dispatchedWeightLbs', cf.wr_dispatched)
  list = num('dispatchedPieces', cf.wr_pcsDisp)
  return list
})

const wrUniq = computed(() => ({
  awb: hf.uniqueValues(wrRows.value, r => r.awbNumber),
  shipper: hf.uniqueValues(wrRows.value, r => r.shipperName),
  consignee: hf.uniqueValues(wrRows.value, r => r.consigneeName),
  dest: hf.uniqueValues(wrRows.value, r => r.destination),
  commodity: hf.uniqueValues(wrRows.value, r => r.commodityType),
  flight: hf.uniqueValues(wrRows.value, r => r.flightNumber),
  date: hf.uniqueValues(wrRows.value, r => r.flightDate),
  pcsRec: hf.uniqueValues(wrRows.value, r => Number(r.receivedPieces)),
  physical: hf.uniqueValues(wrRows.value, r => Number(r.physicalWeightLbs)),
  dispatched: hf.uniqueValues(wrRows.value, r => Number(r.dispatchedWeightLbs)),
  pcsDisp: hf.uniqueValues(wrRows.value, r => Number(r.dispatchedPieces)),
}))

function formatNum(v) {
  if (v == null) return '0'
  const n = Number(v)
  return isNaN(n) ? '0' : n.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 1 })
}

const wrTotals = computed(() => {
  const rows = wrVisibleRows.value
  return {
    receivedPieces: rows.reduce((s, r) => s + (Number(r.receivedPieces) || 0), 0),
    physicalWeightLbs: rows.reduce((s, r) => s + (Number(r.physicalWeightLbs) || 0), 0),
    dispatchedWeightLbs: rows.reduce((s, r) => s + (Number(r.dispatchedWeightLbs) || 0), 0),
    dispatchedPieces: rows.reduce((s, r) => s + (Number(r.dispatchedPieces) || 0), 0),
  }
})

function commodityColor(code) {
  const c = dbCommodities.value.find(x => x.code === code)
  return c?.color || '#6b7280'
}

async function loadWeightReport() {
  wrLoading.value = true
  try {
    const params = {}
    if (wrDateFrom.value) params.dateFrom = wrDateFrom.value
    if (wrDateTo.value) params.dateTo = wrDateTo.value
    if (wrCommodity.value) params.commodityType = wrCommodity.value
    if (wrFlightNumber.value) params.awbNumber = wrFlightNumber.value
    const [rowsRes, sumRes] = await Promise.all([
      biApi.getWeightReport(params),
      biApi.getWeightSummary(params),
    ])
    wrRows.value = rowsRes.data
    wrSummary.value = sumRes.data
  } catch (e) {
    console.error('Weight report error:', e)
    wrRows.value = []
    wrSummary.value = null
  } finally {
    wrLoading.value = false
  }
}

function exportWeightCSV() {
  if (!wrRows.value.length) return
  const headers = [
    t('dashboard.wr.csvHeaders.awb'),
    t('dashboard.wr.csvHeaders.shipper'),
    t('dashboard.wr.csvHeaders.consignee'),
    t('dashboard.wr.csvHeaders.dest'),
    t('dashboard.wr.csvHeaders.commodity'),
    t('dashboard.wr.csvHeaders.flight'),
    t('dashboard.wr.csvHeaders.date'),
    t('dashboard.wr.csvHeaders.pcsRec'),
    t('dashboard.wr.csvHeaders.physicalLbs'),
    t('dashboard.wr.csvHeaders.dispatchedLbs'),
    t('dashboard.wr.csvHeaders.pcsDisp'),
  ]
  const csvRows = [headers.join(',')]
  for (const r of wrRows.value) {
    csvRows.push([
      r.awbNumber, r.shipperName, r.consigneeName, r.destination,
      r.commodityType, r.flightNumber, r.flightDate,
      r.receivedPieces, r.physicalWeightLbs, r.dispatchedWeightLbs, r.dispatchedPieces
    ].map(v => `"${String(v ?? '').replace(/"/g, '""')}"`).join(','))
  }
  const blob = new Blob([csvRows.join('\n')], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${t('dashboard.wr.csvFilename')}-${new Date().toISOString().slice(0,10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

const filteredFlights = computed(() => {
  const flights = Array.isArray(appStore.flights) ? appStore.flights : []
  if (dashboardFlightId.value) {
    return flights.filter(f => f.id === dashboardFlightId.value)
  }
  let list = flights
  if (dateFrom.value) {
    list = list.filter(f => f.flightDate >= dateFrom.value)
  }
  if (dateTo.value) {
    list = list.filter(f => f.flightDate <= dateTo.value)
  }
  const cf = hf.columnFilters
  if (cf.flight) list = list.filter(f => `UPS-${f.flightNumber}` === cf.flight)
  if (cf.route) list = list.filter(f => `${f.origin}→${f.destination}` === cf.route)
  if (cf.date) list = list.filter(f => f.flightDate === cf.date)
  if (cf.status) list = list.filter(f => statusLabel(f.status) === cf.status)
  if (cf.ulds !== null && cf.ulds !== undefined) list = list.filter(f => flightUlds(f.id).length === Number(cf.ulds))
  if (cf.pos !== null && cf.pos !== undefined) list = list.filter(f => flightPositions(f.id) === Number(cf.pos))
  if (cf.gross !== null && cf.gross !== undefined) list = list.filter(f => grossLbs(f.id) === Number(cf.gross))
  if (cf.tare !== null && cf.tare !== undefined) list = list.filter(f => totalTareLbs(f.id) === Number(cf.tare))
  if (cf.net !== null && cf.net !== undefined) list = list.filter(f => netLbs(f.id) === Number(cf.net))
  if (cf.payload !== null && cf.payload !== undefined) list = list.filter(f => payloadLbs(f.id) === Number(cf.payload))
  return list
})

const flightOptions = computed(() =>
  [...appStore.flights].sort(
    (a, b) => String(b.flightDate || '').localeCompare(String(a.flightDate || ''))
      || String(a.flightNumber).localeCompare(String(b.flightNumber), undefined, { numeric: true })
  )
)

function flightOptionLabel(f) {
  return `UPS-${f.flightNumber} · ${f.origin || ''}→${f.destination || ''} · ${f.flightDate || ''}`
}

function onFlightSelect() {
  hf.setColumnFilter('flight', null)
}

const flightUniq = computed(() => {
  const rows = appStore.flights
  return {
    flight: hf.uniqueValues(rows, f => `UPS-${f.flightNumber}`),
    route: hf.uniqueValues(rows, f => `${f.origin}→${f.destination}`),
    date: hf.uniqueValues(rows, f => f.flightDate),
    status: hf.uniqueValues(rows, f => statusLabel(f.status)),
    ulds: hf.uniqueValues(rows, f => flightUlds(f.id).length),
    pos: hf.uniqueValues(rows, f => flightPositions(f.id)),
    gross: hf.uniqueValues(rows, f => grossLbs(f.id)),
    tare: hf.uniqueValues(rows, f => totalTareLbs(f.id)),
    net: hf.uniqueValues(rows, f => netLbs(f.id)),
    payload: hf.uniqueValues(rows, f => payloadLbs(f.id)),
  }
})

const allUlDs = computed(() => appStore.ulds)
const allMawbs = computed(() => appStore.mawbs)

function flightUlds(flightId) {
  return allUlDs.value.filter(u => u.flightId === flightId)
}

function flightPositions(flightId) {
  const ulds = flightUlds(flightId)
  return new Set(ulds.map(u => u.position).filter(Boolean)).size
}

function grossLbs(flightId) {
  const ulds = flightUlds(flightId)
  return ulds.reduce((s, u) => s + (Number(u.grossWeightLbs) || 0), 0)
}

function isBellyPosition(position) {
  if (!position) return false
  const p = position.toString().trim().toUpperCase()
  return p === '31' || p === '34' || p === 'AB' || p === 'A' || p === 'B' || p === 'LOOSE' || p === 'BULK' || p.includes('BELLY')
}

function totalTareLbs(flightId) {
  const ulds = flightUlds(flightId)
  return ulds.reduce((s, u) => s + (Number(u.tareLbs) || 0), 0)
}

function bellyTareLbs(flightId) {
  const ulds = flightUlds(flightId)
  return ulds
    .filter(u => isBellyPosition(u.position))
    .reduce((s, u) => s + (Number(u.tareLbs) || 0), 0)
}

function netLbs(flightId) {
  return grossLbs(flightId) - totalTareLbs(flightId)
}

function payloadLbs(flightId) {
  return grossLbs(flightId) - bellyTareLbs(flightId)
}

// ── Commodity definitions & ordering (dynamic from DB + legacy) ──────────────────────────
// Legacy commodity types that may not be in DB but exist in historical data


const COMMODITY_ORDER = computed(() => {
  const dbCodes = dbCommodities.value.map(c => c.code)
  // Merge DB codes with legacy codes, preserving DB order and appending missing legacy
  const seen = new Set(dbCodes)
  const merged = [...dbCodes]
  for (const code of LEGACY_COMMODITY_CODES) {
    if (!seen.has(code)) merged.push(code)
  }
  return merged
})

const COMMODITY_MAP = computed(() => {
  const map = {}
  for (const c of dbCommodities.value) {
    const shortLen = Math.min(c.code.length, 4)
    map[c.code] = { label: c.label, short: c.code.slice(0, shortLen), color: c.color || '#94a3b8' }
  }
  // Add legacy commodities not in DB
  for (const code of LEGACY_COMMODITY_CODES) {
    if (!map[code]) {
      map[code] = { label: code, short: code.slice(0, 4), color: '#9ca3af' }
    }
  }
  return map
})

// ULD IDs per flight (cached for fast lookup during commodity calculations)
const _uldIdCache = new Map()
function flightUldIdSet(flightId) {
  if (!_uldIdCache.has(flightId)) {
    _uldIdCache.set(flightId, new Set(appStore.ulds.filter(u => u.flightId === flightId).map(u => u.id)))
  }
  return _uldIdCache.get(flightId)
}

// Invalidate cache when ulds change. Dimos: la caché de un vuelo depende de la
// asignación (id, flightId) de cada ULD. Rastrear solo `.length` (como antes)
// dejaba la caché stale cuando un ULD existente se reasigna a otro vuelo (la
// cantidad no cambia). Usamos una firma de todas las asignaciones, así una
// reasignación invalida el vuelo correcto bajo demanda.
const uldFlightSignature = computed(() =>
  appStore.ulds.map(u => u.id + ':' + (u.flightId || '')).join('|')
)
watch(uldFlightSignature, () => _uldIdCache.clear())

// Normalize AWB codes (covers historical labels with mixed formats:
// "40605857585" vs "406-05857585" vs "406 05857585" vs "406/05857585")
const normAwb = (raw) => String(raw || '').toUpperCase().replace(/[\s\-_/]/g, '')

// Active (non-superseded) warehouse receipt for a MAWB, matched by mawbId or
// normalized AWB number. Source of the REAL physical weight of the pieces.
const activeReceiptForMawb = (mawb) => {
  const awb = normAwb(mawb?.awbNumber)
  return (appStore.receipts || []).find(r =>
    !r.superseded &&
    ((r.mawbId && r.mawbId === mawb?.id) || (r.mawbNumber && normAwb(r.mawbNumber) === awb))
  )
}

// Dispatched weight per MAWB within a specific flight:
// only counts ULD-AWB links whose ULD belongs to that flight.
// Formula: (physicalWeight / receivedPieces) * dispatchedPieces
// Basis: the receipt's ACTUAL weight (physical, in lbs) when a non-superseded
// receipt exists; otherwise falls back to reported/chargeable kg from the MAWB
// (declared weight — only a proxy when no physical receipt exists yet).
function mawbDispatchedWeightLbs(mawb, flightId) {
  const rec = activeReceiptForMawb(mawb)
  let receivedLbs = 0
  let receivedPcs = Number(mawb.pieces || 0)
  if (rec && Number(rec.actualWeightLbs) > 0) {
    receivedLbs = Number(rec.actualWeightLbs)
    receivedPcs = Number(rec.pieceCount || receivedPcs)
  } else {
    const receivedKg = Number(mawb.reportedWeightKg || mawb.chargeableWeightKg || 0)
    receivedLbs = receivedKg ? receivedKg * 2.20462 : 0
  }
  if (!receivedLbs || !receivedPcs) return 0
  const uldIds = flightUldIdSet(flightId)
  const links = appStore.uldAwbs?.filter?.(l => normAwb(l.mawbLabel) === normAwb(mawb.awbNumber) && uldIds.has(l.uldId)) || []
  const dispatchedPcs = Math.min(links.reduce((s, l) => s + (Number(l.pieces) || 0), 0), receivedPcs)
  if (!dispatchedPcs) return 0
  return (receivedLbs / receivedPcs) * dispatchedPcs
}

// MAWBs that actually have pieces dispatched on this flight's ULDs
// (via ULD-AWB links). Source of truth for commodity weights: a MAWB
// can be split across several flights, so its own flightId is not
// enough to know which flights carry its pieces.
function mawbsDispatchedOnFlight(flightId) {
  const uldIds = flightUldIdSet(flightId)
  const byAwb = new Map(allMawbs.value.map(m => [normAwb(m.awbNumber), m]))
  const ids = new Set()
  for (const l of (appStore.uldAwbs || [])) {
    if (!uldIds.has(l.uldId)) continue
    const m = byAwb.get(normAwb(l.mawbLabel))
    if (m) ids.add(m.id)
  }
  return allMawbs.value.filter(m => ids.has(m.id))
}

// Commodity payload per flight (sum of per-MAWB dispatched weights)
const LEGACY_COMMODITY_CODES = ['FCC', 'EMPTY_BAGS', 'EMPTY_NETS', 'NETS', 'EMPTY_ULD', 'SDQ_SDF', 'SDQ_MIA', 'WWEF']

function commodityPayload(flightId, commodityType) {
  const mawbs = mawbsDispatchedOnFlight(flightId)
  const items = mawbs.filter(m => (m.commodityType || 'DRY_CARGO') === commodityType)
  const totalLbs = items.reduce((s, m) => s + mawbDispatchedWeightLbs(m, flightId), 0)
  // For legacy/empty commodities (FCC, EMPTY_BAGS, NETS, etc.), weight is 0 but pieces exist
  if (totalLbs === 0 && LEGACY_COMMODITY_CODES.includes(commodityType)) {
    const uldIds = flightUldIdSet(flightId)
    const totalPcs = items.reduce((s, m) => {
      const links = appStore.uldAwbs?.filter?.(l => normAwb(l.mawbLabel) === normAwb(m.awbNumber) && uldIds.has(l.uldId)) || []
      return s + links.reduce((ps, l) => ps + (Number(l.pieces) || 0), 0)
    }, 0)
    return totalPcs > 0 ? { pcs: totalPcs } : null
  }
  return totalLbs > 0 ? Math.round(totalLbs) : null
}

function commodityTooltip(flightId, commodityType) {
  const mawbs = mawbsDispatchedOnFlight(flightId)
  const items = mawbs.filter(m => (m.commodityType || 'DRY_CARGO') === commodityType)
  if (!items.length) return t('dashboard.tooltip.commodityZero', { label: COMMODITY_MAP.value[commodityType]?.label || commodityType })
  const totalLbs = items.reduce((s, m) => s + mawbDispatchedWeightLbs(m, flightId), 0)
  const uldIds = flightUldIdSet(flightId)
  const totalPcs = items.reduce((s, m) => {
    const links = appStore.uldAwbs?.filter?.(l => normAwb(l.mawbLabel) === normAwb(m.awbNumber) && uldIds.has(l.uldId)) || []
    return s + links.reduce((ps, l) => ps + (Number(l.pieces) || 0), 0)
  }, 0)
  const mawbCount = items.length
  return t('dashboard.tooltip.commodity', { label: COMMODITY_MAP.value[commodityType]?.label || commodityType, lbs: Math.round(totalLbs), pcs: totalPcs, n: mawbCount })
}

// Visible commodities = those with dispatched payload > 0 in ANY filtered flight
const visibleCommodities = computed(() => {
  const activeTypes = new Set()
  filteredFlights.value.forEach(f => {
    // mawbsDispatchedOnFlight ya devuelve MAWBs que tienen piezas en los ULDs del vuelo
    mawbsDispatchedOnFlight(f.id).forEach(m => {
      const type = m.commodityType || 'DRY_CARGO'
      activeTypes.add(type)
    })
  })
  return COMMODITY_ORDER.value
    .filter(t => activeTypes.has(t))
    .map(t => ({ type: t, ...COMMODITY_MAP.value[t] }))
})

// Table min-width for horizontal scroll
const tableMinWidth = computed(() => {
  const isMobile = typeof window !== 'undefined' && window.innerWidth <= 640
  const base = isMobile ? 720 : 940 // fixed columns (menos en móvil: sin sticky)
  const commodityCols = visibleCommodities.value.length * 80 // 80px per commodity col
  return base + commodityCols
})

// Totals
const totalNetPayload = computed(() => {
  return filteredFlights.value.reduce((s, f) => s + payloadLbs(f.id), 0)
})

const totalUldsCount = computed(() => {
  return filteredFlights.value.reduce((s, f) => s + flightUlds(f.id).length, 0)
})

const reservedMawbsCount = computed(() => {
  const bookedIds = new Set((appStore.bookings || []).map(b => b.mawbId).filter(Boolean))
  if (!bookedIds.size || !appStore.mawbs.length) return 0
  const flightIds = new Set(filteredFlights.value.map(f => f.id))
  return new Set(
    appStore.mawbs
      .filter(m => flightIds.has(m.flightId) && bookedIds.has(m.id))
      .map(m => m.id)
  ).size
})

const totalPositionsAll = computed(() => {
  return filteredFlights.value.reduce((s, f) => s + flightPositions(f.id), 0)
})

const totalMaxPositionsAll = computed(() => {
  return filteredFlights.value.reduce((s, f) => s + (f.totalPositions || 0), 0)
})

const totalGrossAll = computed(() => {
  return filteredFlights.value.reduce((s, f) => s + grossLbs(f.id), 0)
})

const totalTareAll = computed(() => {
  return filteredFlights.value.reduce((s, f) => s + totalTareLbs(f.id), 0)
})

const totalNetAll = computed(() => {
  return filteredFlights.value.reduce((s, f) => s + netLbs(f.id), 0)
})

function totalCommodityPayload(commodityType) {
  const totalLbs = filteredFlights.value.reduce((s, f) => {
    const val = commodityPayload(f.id, commodityType)
    if (val && typeof val === 'object' && val.pcs) return s // pieces don't add to weight total
    return s + (val || 0)
  }, 0)
  return totalLbs > 0 ? totalLbs : '—'
}

function totalCommodityPieces(commodityType) {
  const total = filteredFlights.value.reduce((s, f) => {
    const val = commodityPayload(f.id, commodityType)
    if (val && typeof val === 'object' && val.pcs) return s + val.pcs
    return s
  }, 0)
  return total > 0 ? total : '—'
}

// Expand logic
const allExpanded = computed(() => {
  return filteredFlights.value.length > 0 && filteredFlights.value.every(f => expandedFlights.value.has(f.id))
})

function toggleExpand(flightId) {
  if (expandedFlights.value.has(flightId)) {
    expandedFlights.value.delete(flightId)
  } else {
    expandedFlights.value.add(flightId)
  }
}

function toggleAllExpanded() {
  if (allExpanded.value) {
    expandedFlights.value.clear()
  } else {
    filteredFlights.value.forEach(f => expandedFlights.value.add(f.id))
  }
}

function isExpanded(flightId) {
  return expandedFlights.value.has(flightId)
}

function descargarReporte() {
  // Build headers: fixed + commodity columns
  const fixedHeaders = [
    t('dashboard.csvHeaders.flightNumber'),
    t('dashboard.csvHeaders.route'),
    t('dashboard.csvHeaders.date'),
    t('dashboard.csvHeaders.status'),
    t('dashboard.csvHeaders.uldCount'),
    t('dashboard.csvHeaders.positions'),
    t('dashboard.csvHeaders.grossLbs'),
    t('dashboard.csvHeaders.tareLbs'),
    t('dashboard.csvHeaders.netLbs'),
    t('dashboard.csvHeaders.payloadLbs'),
  ]
  const commodityHeaders = visibleCommodities.value.map(c => c.short)
  const headers = [...fixedHeaders, ...commodityHeaders]

  const rows = filteredFlights.value.map(f => {
    const fixed = [
      `UPS-${f.flightNumber}`,
      `${f.origin}→${f.destination}`,
      f.flightDate || '',
      statusLabel(f.status),
      flightUlds(f.id).length,
      flightPositions(f.id),
      grossLbs(f.id),
      totalTareLbs(f.id),
      netLbs(f.id),
      payloadLbs(f.id),
    ]
    const commodityVals = visibleCommodities.value.map(c => {
      const val = commodityPayload(f.id, c.type)
      if (typeof val === 'object' && val.pcs) return val.pcs + ' pcs'
      return val || ''
    })
    return [...fixed, ...commodityVals]
  })
  downloadCSV(headers, rows, `${t('dashboard.csvFilename')}-${new Date().toISOString().slice(0, 10)}.csv`)
}

function getStatusDot(status) {
  if (status === 'SCHEDULED') return 'bg-slate-300'
  if (status === 'BOARDING') return 'bg-slate-400'
  if (status === 'DEPARTED') return 'bg-slate-600'
  if (status === 'ARRIVED') return 'bg-slate-800'
  if (status === 'CANCELLED') return 'bg-slate-200'
  if (status === 'DELAYED') return 'bg-slate-400'
  return 'bg-slate-200'
}

function statusStyle(status) {
  const map = {
    SCHEDULED: { background: '#e2e8f0', color: '#475569' },
    BOARDING: { background: '#e2e8f0', color: '#475569' },
    DEPARTED: { background: '#94a3b8', color: '#fff' },
    ARRIVED: { background: '#1e293b', color: '#fff' },
    CANCELLED: { background: '#f1f5f9', color: '#94a3b8' },
    DELAYED: { background: '#fef08a', color: '#854d0e' },
  }
  return map[status] || { background: '#e2e8f0', color: '#475569' }
}

function statusLabel(status) {
  const map = {
    SCHEDULED: 'SCH',
    BOARDING: 'BRD',
    DEPARTED: 'DPT',
    ARRIVED: 'ARR',
    CANCELLED: 'CNL',
    DELAYED: 'DLY',
  }
  return map[status] || status?.slice(0, 3) || '—'
}

onMounted(async () => {
  loading.value = true
  try {
    await loadCommodities()
    await appStore.loadFlights()
    if (appStore.flights?.length) {
      await Promise.all([
        appStore.loadUlds(),
        appStore.loadAllMawbs(),
        appStore.loadUldAwbs(),
        appStore.loadBookings(null, { page: 0, size: 500, silent: true }),
        appStore.loadReceipts({ silent: true }),
      ])
    }
  } catch (e) {
    console.error('Dashboard load error:', e)
  } finally {
    loading.value = false
  }
})

useLiveRefresh(() => {
  if (!appStore.flights.length) return
  const tasks = [
    appStore.loadFlights({ silent: true }),
    appStore.loadUlds({ silent: true }),
    appStore.loadAllMawbs({ silent: true }),
    appStore.loadUldAwbs(),
    appStore.loadBookings(null, { page: 0, size: 500, silent: true }),
    appStore.loadReceipts({ silent: true }),
  ]
  if (activeTab.value === 'weight-report') tasks.push(loadWeightReport())
  return Promise.all(tasks)
}, { interval: 30000 })
</script>

<style scoped>
/* La tabla de vuelos ya no usa columnas sticky horizontales: fluye con el ancho
   natural y solo la cabecera permanece fija (sticky top) al hacer scroll vertical. */

.ds-card {
  background: var(--surface);
  border: 1px solid var(--border-strong);
  border-radius: 0.75rem; /* rounded-xl */
  padding: 0.5rem 0.75rem; /* py-2 px-3 */
  box-shadow: 0 1px 2px 0 rgb(15 23 42 / 0.05), 0 0 0 1px rgb(15 23 42 / 0.05);
  border-left: 4px solid #1e293b; /* border-l-4 border-l-slate-800 */
  transition: box-shadow 0.15s ease;
}
.ds-card:hover {
  box-shadow: 0 4px 6px -1px rgb(15 23 42 / 0.1), 0 2px 4px -2px rgb(15 23 42 / 0.1);
}
.ds-card-label {
  font-size: 9.5px;
  font-family: var(--font-family-mono);
  font-weight: 600;
  color: var(--text-2);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 0.125rem;
}
.ds-card-value {
  font-size: 15px;
  font-family: var(--font-family-mono);
  font-weight: 700;
  color: var(--text);
  letter-spacing: -0.025em;
  line-height: 1.1;
}
.ds-btn-secondary {
  padding: 0.375rem 0.75rem; /* py-1.5 px-3 */
  border-radius: 0.5rem; /* rounded-lg */
  font-size: 12px;
  font-weight: 600;
  font-family: var(--font-family-mono);
  border: 1px solid var(--border-strong);
  background: var(--surface);
  color: var(--text-2);
  transition: background 0.15s ease;
}
.ds-btn-secondary:hover {
  background: var(--surface-hover);
}
</style>
