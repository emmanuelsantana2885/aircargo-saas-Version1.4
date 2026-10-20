<template>
  <Teleport to="body">
    <div
      v-if="show"
      class="fixed inset-0 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4"
      :style="{ zIndex }"
      @click.self="close"
    >
      <div
        class="ds-modal-panel w-full overflow-hidden flex flex-col"
        style="max-width: 1180px; max-height: calc(100dvh - 24px);"
        role="dialog"
        aria-modal="true"
        :aria-label="t('ulds.label.title')"
      >
        <div class="flex items-center justify-between gap-2 px-4 py-2.5 border-b border-strong shrink-0">
          <span class="flex items-center gap-2 text-[13px] font-mono font-bold uppercase tracking-widest text-primary min-w-0">
            <component :is="icons.Pallet" :size="15" class="shrink-0" />
            <span class="truncate">{{ t('ulds.label.title') }}</span>
            <span class="hidden md:inline text-[11px] font-normal normal-case tracking-normal text-tertiary truncate">· {{ t('ulds.label.subtitle') }}</span>
          </span>
          <div class="flex items-center gap-2 shrink-0">
            <button type="button" class="ds-btn-secondary text-[11px] !py-1.5 !px-2.5 flex items-center gap-1.5" @click="printLabel">
              <component :is="icons.Printer" :size="14" />
              <span class="hidden sm:inline">{{ t('ulds.label.print') }}</span>
            </button>
            <button
              type="button"
              @click="close"
              :aria-label="t('common.close')"
              class="text-tertiary hover:text-primary transition text-base leading-none px-1"
            >✕</button>
          </div>
        </div>

        <div v-if="saveSuccess" class="px-4 pt-2 shrink-0">
          <div class="flex items-center gap-2 px-3 py-2 rounded-lg text-[12px] border border-strong bg-surface-hover text-primary">
            <component :is="icons.CheckCircle" :size="15" />
            {{ t('ulds.label.saved') }}
          </div>
        </div>

        <div class="flex-1 grid grid-cols-1 lg:grid-cols-12 gap-4 p-4 overflow-y-auto" style="min-height: 0;">
          <div class="lg:col-span-6 flex flex-col gap-3 min-w-0">
            <div class="flex flex-wrap gap-1">
              <button
                v-for="tb in tabs"
                :key="tb.id"
                type="button"
                class="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-[11px] font-mono font-bold uppercase tracking-wider border transition"
                :class="activeTab === tb.id ? 'border-strong text-accent-strong' : 'border-transparent text-tertiary hover:text-secondary hover:bg-surface-hover'"
                :style="activeTab === tb.id ? { background: 'var(--accent-soft)' } : null"
                @click="activeTab = tb.id"
              >
                <component :is="icons[tb.icon]" :size="13" />
                {{ t(tb.key) }}
                <span
                  v-if="tb.id === 'signoffs'"
                  class="w-1.5 h-1.5 rounded-full shrink-0"
                  :class="signoffsComplete ? 'bg-emerald-500' : 'bg-amber-500'"
                  :title="signoffsComplete ? t('ulds.label.readyToSave') : t('ulds.label.requiredHint', { n: requiredMissing.length })"
                ></span>
              </button>
            </div>

            <div
              v-if="validationError"
              class="flex items-center justify-between gap-2 px-3 py-2 rounded-lg border border-strong bg-surface-hover text-[12px] text-primary"
            >
              <span class="flex items-center gap-2 min-w-0">
                <component :is="icons.AlertTriangle" :size="15" class="shrink-0" />
                <span class="truncate">{{ validationError }}</span>
              </span>
              <button type="button" class="text-tertiary hover:text-primary shrink-0" @click="validationError = null">✕</button>
            </div>

            <div class="rounded-xl border border-strong bg-surface p-3 flex-1 space-y-3 overflow-y-auto" style="min-height: 380px;">
              <div v-if="activeTab === 'baseline'" class="space-y-3">
                <h3 class="text-[11px] font-mono font-bold uppercase tracking-widest text-secondary flex items-center gap-1.5 border-b border-strong pb-1.5">
                  <component :is="icons.FileDescription" :size="14" />
                  {{ t('ulds.label.baseline.heading') }}
                </h3>
                <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <label class="flex flex-col gap-1">
                    <span class="ds-label">{{ t('ulds.label.baseline.uldId') }}</span>
                    <input v-model="label.uldId" class="ds-input font-mono font-bold" :placeholder="t('ulds.label.baseline.uldIdPh')" @input="onUppercase('uldId')" />
                  </label>
                  <label class="flex flex-col gap-1">
                    <span class="ds-label">{{ t('ulds.label.baseline.uldType') }}</span>
                    <input v-model="label.type" class="ds-input font-mono" :placeholder="t('ulds.label.baseline.uldTypePh')" />
                  </label>
                </div>
                <div class="grid grid-cols-3 gap-2">
                  <label class="flex flex-col gap-1">
                    <span class="ds-label">{{ t('ulds.label.baseline.origin') }}</span>
                    <input v-model="label.origin" maxlength="3" class="ds-input font-mono font-bold text-center" @input="onUppercase('origin')" />
                  </label>
                  <label class="flex flex-col gap-1">
                    <span class="ds-label">{{ t('ulds.label.baseline.flightNumber') }}</span>
                    <input v-model="label.flightNumber" class="ds-input font-mono text-center" @input="onUppercase('flightNumber')" />
                  </label>
                  <label class="flex flex-col gap-1">
                    <span class="ds-label">{{ t('ulds.label.baseline.destination') }}</span>
                    <input v-model="label.destination" maxlength="3" class="ds-input font-mono font-bold text-center" @input="onUppercase('destination')" />
                  </label>
                </div>
                <div class="grid grid-cols-2 gap-3">
                  <label class="flex flex-col gap-1">
                    <span class="ds-label">{{ t('ulds.label.baseline.position') }}</span>
                    <input v-model="label.position" class="ds-input font-mono" />
                  </label>
                  <label class="flex flex-col gap-1">
                    <span class="ds-label">{{ t('ulds.label.baseline.flightDate') }}</span>
                    <input v-model="label.flightDate" type="date" class="ds-input font-mono" />
                  </label>
                </div>
                <div class="space-y-1">
                  <span class="ds-label flex items-center gap-1.5">
                    <component :is="icons.Lock" :size="12" />
                    {{ t('ulds.label.baseline.sealNumber') }}
                  </span>
                  <div class="flex gap-2">
                    <input v-model="label.sealNumber" class="ds-input font-mono flex-1" :placeholder="t('ulds.label.baseline.sealPh')" />
                    <button type="button" class="ds-btn-secondary text-[11px] shrink-0" @click="label.sealNumber = generateSeal(label.origin)">
                      {{ t('ulds.label.baseline.generateSeal') }}
                    </button>
                  </div>
                </div>
                <div class="space-y-2 border-t border-strong pt-3">
                  <span class="ds-label flex items-center gap-1.5">
                    <component :is="icons.Scale" :size="12" />
                    {{ t('ulds.label.baseline.weightUnit') }}
                  </span>
                  <div class="flex gap-1.5">
                    <button
                      type="button"
                      class="text-[11px] flex-1"
                      :class="label.weightUnit === 'LB' ? 'ds-btn-primary' : 'ds-btn-secondary'"
                      @click="label.weightUnit = 'LB'"
                    >{{ t('ulds.label.baseline.unitLb') }}</button>
                    <button
                      type="button"
                      class="text-[11px] flex-1"
                      :class="label.weightUnit === 'KG' ? 'ds-btn-primary' : 'ds-btn-secondary'"
                      @click="label.weightUnit = 'KG'"
                    >{{ t('ulds.label.baseline.unitKg') }}</button>
                  </div>
                </div>
                <div class="space-y-2 border-t border-strong pt-3">
                  <span class="ds-label flex items-center gap-1.5">
                    <component :is="icons.Printer" :size="12" />
                    {{ t('ulds.label.printLayout.size') }}
                  </span>
                  <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <label class="flex flex-col gap-1">
                      <select v-model="label.labelSize" class="ds-input font-mono">
                        <option v-for="s in LABEL_SIZES" :key="s.id" :value="s.id">{{ s.label }}</option>
                      </select>
                    </label>
                    <label class="flex flex-col gap-1">
                      <span class="ds-label">{{ t('ulds.label.printLayout.orientation') }}</span>
                      <select v-model="label.orientation" class="ds-input font-mono">
                        <option value="portrait">{{ t('ulds.label.printLayout.portrait') }}</option>
                        <option value="landscape">{{ t('ulds.label.printLayout.landscape') }}</option>
                      </select>
                    </label>
                  </div>
                  <div v-if="isCustomSize" class="grid grid-cols-2 gap-3">
                    <label class="flex flex-col gap-1">
                      <span class="ds-label">{{ t('ulds.label.printLayout.customW') }}</span>
                      <input v-model.number="label.customLabelW" type="number" min="1" step="0.1" class="ds-input font-mono" />
                    </label>
                    <label class="flex flex-col gap-1">
                      <span class="ds-label">{{ t('ulds.label.printLayout.customH') }}</span>
                      <input v-model.number="label.customLabelH" type="number" min="1" step="0.1" class="ds-input font-mono" />
                    </label>
                  </div>
                </div>
              </div>

              <div v-else-if="activeTab === 'signoffs'" class="space-y-3">
                <h3 class="text-[11px] font-mono font-bold uppercase tracking-widest text-secondary flex items-center gap-1.5 border-b border-strong pb-1.5">
                  <component :is="icons.CheckCircle" :size="14" />
                  {{ t('ulds.label.signoffs.heading') }}
                </h3>
                <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <label class="flex flex-col gap-1">
                    <span class="ds-label">{{ t('ulds.label.signoffs.builtBy') }} *</span>
                    <input v-model="label.builtBy" class="ds-input" />
                  </label>
                  <label class="flex flex-col gap-1">
                    <span class="ds-label">{{ t('ulds.label.signoffs.weighedBy') }} *</span>
                    <input v-model="label.weighedBy" class="ds-input" />
                  </label>
                  <label class="flex flex-col gap-1">
                    <span class="ds-label">{{ t('ulds.label.signoffs.sealedBy') }} *</span>
                    <input v-model="label.sealedBy" class="ds-input" />
                  </label>
                  <label class="flex flex-col gap-1">
                    <span class="ds-label">{{ t('ulds.label.baseline.sealNumber') }} *</span>
                    <input v-model="label.sealNumber" class="ds-input font-mono" />
                  </label>
                </div>
                <label class="flex flex-col gap-1">
                  <span class="ds-label">{{ t('ulds.label.signoffs.remarks') }} *</span>
                  <textarea v-model="label.remarks" rows="2" class="ds-input" :placeholder="t('ulds.label.signoffs.remarksPh')"></textarea>
                </label>
                <label class="flex items-start gap-2 px-3 py-2 rounded-lg border border-strong bg-surface-hover cursor-pointer">
                  <input v-model="label.spxAccepted" type="checkbox" class="w-4 h-4 mt-0.5 shrink-0" :style="{ accentColor: 'var(--accent)' }" />
                  <span class="text-[12px] text-primary leading-snug">{{ t('ulds.label.signoffs.spxAccept') }}</span>
                </label>
              </div>

              <div v-else-if="activeTab === 'customFields'" class="space-y-3">
                <h3 class="text-[11px] font-mono font-bold uppercase tracking-widest text-secondary flex items-center gap-1.5 border-b border-strong pb-1.5">
                  <component :is="icons.Plus" :size="14" />
                  {{ t('ulds.label.custom.heading') }}
                </h3>
                <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  <input v-model="newField.label" class="ds-input" :placeholder="t('ulds.label.custom.labelPh')" />
                  <input v-model="newField.value" class="ds-input font-mono" :placeholder="t('ulds.label.custom.valuePh')" />
                </div>
                <div class="grid grid-cols-2 gap-2">
                  <label class="flex flex-col gap-1">
                    <span class="ds-label">{{ t('ulds.label.custom.section') }}</span>
                    <select v-model="newField.section" class="ds-input">
                      <option v-for="s in CUSTOM_FIELD_SECTIONS" :key="s" :value="s">{{ t('ulds.label.custom.sections.' + s) }}</option>
                    </select>
                  </label>
                  <label class="flex flex-col gap-1">
                    <span class="ds-label">{{ t('ulds.label.custom.color') }}</span>
                    <select v-model="newField.badgeColor" class="ds-input">
                      <option v-for="c in CUSTOM_FIELD_COLORS" :key="c" :value="c">{{ t('ulds.label.custom.colors.' + c) }}</option>
                    </select>
                  </label>
                </div>
                <button type="button" class="ds-btn-primary text-[11px]" @click="addCustomField">{{ t('ulds.label.custom.add') }}</button>

                <div class="space-y-1.5">
                  <span class="text-[11px] text-tertiary">{{ t('ulds.label.custom.presetsTitle') }}</span>
                  <div class="flex flex-wrap gap-1.5">
                    <button
                      v-for="(p, i) in FIELD_PRESETS"
                      :key="i"
                      type="button"
                      class="px-2 py-1 rounded border border-strong text-[10px] font-mono text-secondary hover:text-primary hover:bg-surface-hover transition"
                      @click="addPreset(p)"
                    >+ {{ p.label }}</button>
                  </div>
                </div>

                <p v-if="!label.customFields.length" class="text-[11px] text-tertiary">{{ t('ulds.label.custom.empty') }}</p>
                <div v-for="(f, idx) in label.customFields" :key="f.id" class="rounded-lg border border-strong p-2.5 space-y-2">
                  <div class="flex items-center gap-2">
                    <input v-model="f.label" class="ds-input flex-1" />
                    <button type="button" class="text-tertiary hover:text-primary shrink-0" @click="removeCustomField(idx)">
                      <component :is="icons.Trash" :size="15" />
                    </button>
                  </div>
                  <input v-model="f.value" class="ds-input font-mono" />
                  <div class="grid grid-cols-2 gap-2">
                    <select v-model="f.section" class="ds-input">
                      <option v-for="s in CUSTOM_FIELD_SECTIONS" :key="s" :value="s">{{ t('ulds.label.custom.sections.' + s) }}</option>
                    </select>
                    <select v-model="f.badgeColor" class="ds-input">
                      <option v-for="c in CUSTOM_FIELD_COLORS" :key="c" :value="c">{{ t('ulds.label.custom.colors.' + c) }}</option>
                    </select>
                  </div>
                </div>
              </div>

              <div v-else-if="activeTab === 'branding'" class="space-y-3">
                <h3 class="text-[11px] font-mono font-bold uppercase tracking-widest text-secondary flex items-center gap-1.5 border-b border-strong pb-1.5">
                  <component :is="icons.Palette" :size="14" />
                  {{ t('ulds.label.branding.heading') }}
                </h3>
                <label class="flex flex-col gap-1">
                  <span class="ds-label">{{ t('ulds.label.branding.carrier') }}</span>
                  <select v-model="label.carrierCode" class="ds-input">
                    <option v-for="c in PRESET_CARRIERS" :key="c.code" :value="c.code">{{ c.name }}</option>
                    <option value="CUSTOM">{{ t('ulds.label.branding.carrierCustom') }}</option>
                  </select>
                </label>
                <label v-if="label.carrierCode === 'CUSTOM'" class="flex flex-col gap-1">
                  <span class="ds-label">{{ t('ulds.label.branding.logoUrl') }}</span>
                  <input v-model="label.customLogoUrl" class="ds-input font-mono" :placeholder="t('ulds.label.branding.logoUrlPh')" />
                </label>
                <div class="grid grid-cols-2 gap-2">
                  <label class="flex flex-col gap-1">
                    <span class="ds-label">{{ t('ulds.label.branding.logoPosition') }}</span>
                    <select v-model="label.logoPosition" class="ds-input">
                      <option value="left">{{ t('ulds.label.branding.posLeft') }}</option>
                      <option value="center">{{ t('ulds.label.branding.posCenter') }}</option>
                      <option value="right">{{ t('ulds.label.branding.posRight') }}</option>
                    </select>
                  </label>
                  <label v-if="label.carrierCode === 'CUSTOM'" class="flex flex-col gap-1">
                    <span class="ds-label">{{ t('ulds.label.branding.logoHeight') }}</span>
                    <input v-model.number="label.logoHeightPx" type="number" min="16" max="120" class="ds-input font-mono" />
                  </label>
                </div>
                <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  <label class="flex flex-col gap-1">
                    <span class="ds-label">{{ t('ulds.label.branding.headerTitle') }}</span>
                    <input v-model="label.headerTitle" class="ds-input" />
                  </label>
                  <label class="flex flex-col gap-1">
                    <span class="ds-label">{{ t('ulds.label.branding.headerSubtitle') }}</span>
                    <input v-model="label.headerSubtitle" class="ds-input" />
                  </label>
                </div>
                <label class="flex flex-col gap-1">
                  <span class="ds-label">{{ t('ulds.label.branding.secondaryBadge') }}</span>
                  <input v-model="label.secondaryBadge" class="ds-input font-mono" />
                </label>
                <div class="space-y-1.5">
                  <span class="text-[11px] font-mono font-bold uppercase tracking-wider text-secondary">{{ t('ulds.label.branding.handlingTitle') }}</span>
                  <div class="grid grid-cols-2 sm:grid-cols-3 gap-2">
                    <button
                      v-for="ic in PRESET_HANDLING_ICONS"
                      :key="ic.id"
                      type="button"
                      class="flex flex-col items-center gap-0.5 px-2 py-1.5 rounded-lg border transition"
                      :class="handlingSelected(ic.id) ? 'border-strong bg-surface-hover text-primary' : 'border-transparent text-tertiary hover:text-secondary'"
                      @click="toggleHandlingIcon(ic.id)"
                    >
                      <span class="text-lg leading-none">{{ ic.icon }}</span>
                      <span class="text-[9px] text-center leading-tight">{{ ic.label }}</span>
                    </button>
                  </div>
                </div>
                <div class="space-y-1.5">
                  <span class="text-[11px] font-mono font-bold uppercase tracking-wider text-secondary">{{ t('ulds.label.branding.blocksTitle') }}</span>
                  <div class="grid grid-cols-1 sm:grid-cols-2 gap-1">
                    <label v-for="bk in blockKeys" :key="bk" class="flex items-center gap-2 text-[12px] text-primary cursor-pointer">
                      <input v-model="label.visibleBlocks[bk]" type="checkbox" class="w-4 h-4" :style="{ accentColor: 'var(--accent)' }" />
                      {{ t('ulds.label.branding.blocks.' + bk) }}
                    </label>
                  </div>
                </div>
              </div>

              <div v-else-if="activeTab === 'legends'" class="space-y-3">
                <h3 class="text-[11px] font-mono font-bold uppercase tracking-widest text-secondary flex items-center gap-1.5 border-b border-strong pb-1.5">
                  <component :is="icons.Shield" :size="14" />
                  {{ t('ulds.label.legends.heading') }}
                </h3>
                <p class="text-[11px] text-tertiary">{{ t('ulds.label.legends.hint') }}</p>
                <span class="text-[11px] font-mono font-bold uppercase tracking-wider text-secondary">{{ t('ulds.label.legends.listTitle') }}</span>
                <div class="space-y-2">
                  <div
                    v-for="lg in allLegends"
                    :key="lg.id"
                    class="rounded-xl border border-strong p-2.5 flex items-start gap-2.5"
                    :class="isLegendActive(lg) ? 'bg-surface-hover' : 'opacity-60'"
                  >
                    <input
                      type="checkbox"
                      class="w-4 h-4 mt-0.5 shrink-0"
                      :style="{ accentColor: 'var(--accent)' }"
                      :checked="isLegendActive(lg)"
                      @change="toggleLegend(lg)"
                    />
                    <span class="w-3 h-3 rounded-full mt-1 shrink-0" :style="{ background: legendColor(lg.colorScheme) }"></span>
                    <div class="flex-1 min-w-0 space-y-1">
                      <template v-if="lg.type === 'CUSTOM'">
                        <input v-model="lg.title" class="ds-input font-mono" />
                        <input v-model="lg.subtitle" class="ds-input" />
                        <input v-model="lg.complianceText" class="ds-input text-[11px]" />
                      </template>
                      <template v-else>
                        <div class="text-[12px] font-bold text-primary">{{ lg.title }}</div>
                        <div class="text-[10px] font-mono uppercase tracking-wide text-secondary">{{ lg.subtitle }}</div>
                        <div class="text-[10px] text-tertiary">{{ lg.complianceText }}</div>
                      </template>
                    </div>
                    <button
                      v-if="lg.type === 'CUSTOM'"
                      type="button"
                      class="text-tertiary hover:text-primary shrink-0"
                      :title="t('ulds.label.legends.remove')"
                      @click="removeCustomLegend(lg.id)"
                    >
                      <component :is="icons.Trash" :size="15" />
                    </button>
                  </div>
                </div>
                <div class="rounded-xl border border-strong p-3 space-y-2">
                  <span class="text-[11px] font-mono font-bold uppercase tracking-wider text-secondary">{{ t('ulds.label.legends.createTitle') }}</span>
                  <input v-model="newLegend.title" class="ds-input font-mono" :placeholder="t('ulds.label.legends.titlePh')" />
                  <input v-model="newLegend.subtitle" class="ds-input" :placeholder="t('ulds.label.legends.subtitlePh')" />
                  <input v-model="newLegend.complianceText" class="ds-input text-[11px]" :placeholder="t('ulds.label.legends.compliancePh')" />
                  <label class="flex flex-col gap-1">
                    <span class="ds-label">{{ t('ulds.label.legends.color') }}</span>
                    <select v-model="newLegend.colorScheme" class="ds-input">
                      <option v-for="s in LEGEND_COLOR_SCHEMES" :key="s" :value="s">{{ t('ulds.label.legends.colorSchemes.' + s) }}</option>
                    </select>
                  </label>
                  <button type="button" class="ds-btn-primary text-[11px]" @click="addLegend">{{ t('ulds.label.legends.create') }}</button>
                </div>
              </div>

              <div v-else-if="activeTab === 'templates'" class="space-y-3">
                <h3 class="text-[11px] font-mono font-bold uppercase tracking-widest text-secondary flex items-center gap-1.5 border-b border-strong pb-1.5">
                  <component :is="icons.LayoutGrid" :size="14" />
                  {{ t('ulds.label.templates.heading') }}
                </h3>
                <p class="text-[11px] text-tertiary">{{ t('ulds.label.templates.hint') }}</p>
                <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div v-for="tpl in LABEL_TEMPLATES" :key="tpl.id" class="rounded-xl border border-strong bg-surface p-3 flex flex-col gap-2">
                    <div class="flex items-center justify-between gap-2">
                      <span class="text-[12px] font-bold text-primary">{{ templateName(tpl) }}</span>
                      <span class="text-[10px] font-mono text-tertiary">{{ tpl.carrierCode }}</span>
                    </div>
                    <p class="text-[11px] text-secondary leading-snug flex-1">{{ templateDesc(tpl) }}</p>
                    <button type="button" class="ds-btn-secondary text-[11px] self-start" @click="onApplyTemplate(tpl.id)">{{ t('ulds.label.templates.apply') }}</button>
                  </div>
                </div>
                <p v-if="templateApplied" class="text-[11px] text-secondary">{{ t('ulds.label.templates.applied') }}</p>
              </div>
            </div>

          </div>

          <div class="lg:col-span-6 min-w-0">
            <div class="rounded-xl border border-strong bg-surface-hover p-3 h-full flex flex-col gap-2">
              <div class="flex items-center justify-between gap-2 shrink-0">
                <span class="text-[11px] font-mono font-bold uppercase tracking-widest text-secondary">{{ t('ulds.label.previewTitle') }}</span>
                <div class="flex items-center gap-0 rounded-lg border border-strong overflow-hidden">
                  <button
                    v-for="zl in zoomLevels"
                    :key="zl"
                    type="button"
                    class="px-2.5 py-0.5 text-[10px] font-mono transition"
                    :class="previewZoom === zl ? 'bg-surface text-primary font-bold' : 'text-tertiary hover:text-secondary hover:bg-surface-hover'"
                    @click="previewZoom = zl"
                  >{{ Math.round(zl * 100) }}%</button>
                </div>
              </div>
              <div class="flex-1 overflow-auto rounded-lg" style="min-height: 0;">
                <div class="printable-pallet-label" :style="labelStyle">
                  <!-- UPS Air Cargo pallet identification tag (designStyle === 'ups') -->
                  <template v-if="label.designStyle === 'ups'">
                    <div
                      v-if="label.visibleBlocks.header"
                      style="display:flex; align-items:center; justify-content:space-between; gap:8px; background:#351C15; color:#fff; padding:8px 10px; margin:-14px -14px 0;"
                    >
                      <div style="display:flex; align-items:center; gap:8px; min-width:0;">
                        <img
                          v-if="carrierLogo"
                          :src="carrierLogo"
                          style="height:34px; width:auto; object-fit:contain; background:#fff; padding:2px 5px; border-radius:2px;"
                          alt=""
                        />
                        <span v-else style="background:#fff; color:#351C15; font-weight:900; font-size:14px; padding:3px 7px; border-radius:2px;">{{ label.carrierCode || 'UPS' }}</span>
                        <div style="min-width:0;">
                          <div style="font-size:15px; font-weight:900; letter-spacing:1px; line-height:1.1;">{{ label.headerTitle }}</div>
                          <div style="font-size:9px; font-weight:800; letter-spacing:1px; color:#FFB500;">{{ label.headerSubtitle }}</div>
                        </div>
                      </div>
                      <span v-if="label.secondaryBadge" style="border:1.5px solid #fff; padding:2px 6px; font-size:10px; font-weight:800; letter-spacing:1px;">{{ label.secondaryBadge }}</span>
                    </div>

                    <div v-if="label.visibleBlocks.uldId">
                      <div style="font-size:9px; font-weight:800; letter-spacing:1px; background:#f1f5f9; padding:1px 5px;">ULD NUMBER</div>
                      <div style="font-size:40px; font-weight:900; line-height:1; word-break:break-all; border:2.5px solid #000; padding:6px 9px;">{{ label.uldId || '—' }}</div>
                      <div style="display:flex; justify-content:space-between; gap:6px; font-size:10px; font-weight:800; padding-top:3px;">
                        <span>TYPE: {{ label.type || '—' }}</span>
                        <span v-if="label.contour">CONTOUR: {{ label.contour }}</span>
                        <span v-if="label.position">POS: {{ label.position }}</span>
                      </div>
                    </div>

                    <div v-if="label.visibleBlocks.weights" style="display:grid; grid-template-columns:repeat(3,1fr); border:2px solid #000;">
                      <div
                        v-for="(w, i) in weightCells"
                        :key="'uw' + w.k"
                        :style="{ textAlign: 'center', padding: '4px 2px', borderLeft: i ? '1.5px solid #000' : 'none' }"
                      >
                        <div style="font-size:9px; font-weight:800;">{{ w.k }}</div>
                        <div style="font-size:24px; font-weight:900; line-height:1.05;">{{ w.v }}</div>
                        <div style="font-size:10px; font-weight:800;">{{ weightUnitLabel }}</div>
                      </div>
                    </div>

                    <div v-if="label.visibleBlocks.routing" style="display:flex; align-items:stretch; border:2px solid #000;">
                      <div style="flex:1; text-align:center; padding:4px;">
                        <div style="font-size:8px; font-weight:800; letter-spacing:1px;">ORIGIN</div>
                        <div style="font-size:30px; font-weight:900; line-height:1;">{{ label.origin || '—' }}</div>
                      </div>
                      <div style="display:flex; align-items:center; padding:0 6px; font-size:20px; font-weight:900;">&#8594;</div>
                      <div style="flex:1; text-align:center; padding:4px;">
                        <div style="font-size:8px; font-weight:800; letter-spacing:1px;">DESTINATION</div>
                        <div style="font-size:30px; font-weight:900; line-height:1;">{{ label.destination || '—' }}</div>
                      </div>
                    </div>

                    <div v-if="label.visibleBlocks.routing" style="display:flex; justify-content:space-between; gap:8px; font-size:11px; font-weight:800; border-bottom:1.5px dashed #000; padding-bottom:5px;">
                      <span>FLIGHT: {{ label.flightNumber || '—' }}</span>
                      <span>DATE: {{ label.flightDate }}</span>
                    </div>

                    <div v-if="label.visibleBlocks.signoffs" style="border:2px solid #000; padding:5px 8px; display:flex; flex-direction:column; gap:3px;">
                      <div v-for="s in signoffRows" :key="'us' + s.k" style="display:flex; justify-content:space-between; gap:8px; font-size:11px;">
                        <span style="font-weight:800;">{{ s.k }}</span>
                        <span :style="s.seal ? 'font-weight:900; color:#dc2626; letter-spacing:1px;' : ''">{{ s.v || '________________' }}</span>
                      </div>
                    </div>

                    <div
                      v-for="(f, i) in (label.visibleBlocks.custom ? label.customFields : [])"
                      :key="'uc' + i"
                      :style="badgeStyle(f.badgeColor)"
                    >
                      <span style="font-size:9px; font-weight:700; text-transform:uppercase;">{{ f.label }}</span>
                      <span style="font-size:11px; font-weight:900; text-align:right;">{{ f.value }}</span>
                    </div>

                    <div v-if="label.remarks" style="border:1.5px solid #000; padding:4px 6px;">
                      <div style="font-size:8px; font-weight:700;">{{ t('ulds.label.preview.remarks') }}</div>
                      <div style="font-size:10px; font-weight:700;">{{ label.remarks }}</div>
                    </div>

                    <div v-if="label.visibleBlocks.handling && selectedHandling.length" style="display:flex; flex-wrap:wrap; gap:6px;">
                      <div v-for="ic in selectedHandling" :key="'uh' + ic.id" style="border:1.5px solid #000; padding:3px 6px; text-align:center; min-width:54px;">
                        <div style="font-size:18px; line-height:1;">{{ ic.icon }}</div>
                        <div style="font-size:7px; font-weight:700;">{{ ic.label }}</div>
                      </div>
                    </div>

                    <div v-if="label.visibleBlocks.legends && activeLegends.length" style="display:flex; flex-direction:column; gap:4px;">
                      <div v-for="lg in activeLegends" :key="'ul' + lg.id" :style="legendBannerStyle(lg.colorScheme)">
                        <div style="display:flex; align-items:center; gap:6px;">
                          <span :style="{ background: legendColor(lg.colorScheme), color: '#fff', fontWeight: 900, fontSize: '10px', padding: '2px 6px', borderRadius: '2px' }">{{ lg.title }}</span>
                          <span style="font-size:9px; font-weight:800;">{{ lg.subtitle }}</span>
                        </div>
                        <div style="font-size:8px; margin-top:2px;">{{ lg.complianceText }}</div>
                      </div>
                    </div>

                    <div v-if="label.visibleBlocks.barcode" style="display:flex; align-items:flex-end; gap:2px; height:30px;">
                      <span v-for="(b, i) in BARCODE_BARS" :key="'ub' + i" :style="{ width: (b * 2) + 'px', height: '26px', background: '#000' }"></span>
                      <span style="margin-left:6px; font-size:9px; letter-spacing:1px;">{{ barcodeText.slice(0, 30) }}</span>
                    </div>

                    <div style="margin-top:auto; border-top:2px solid #351C15; padding-top:5px; display:flex; justify-content:space-between; align-items:center; gap:6px; font-size:8px; font-weight:700;">
                      <span>{{ t('ulds.label.preview.secured') }}</span>
                      <span>{{ t('ulds.label.preview.issued') }}: {{ label.issuedAt || '—' }}</span>
                    </div>
                  </template>

                  <template v-else>
                  <div v-if="label.visibleBlocks.header" style="display:flex; justify-content:space-between; align-items:flex-start; gap:8px; border-bottom:2px solid #000; padding-bottom:6px;">
                    <div style="display:flex; align-items:center; gap:8px; min-width:0;">
                      <img
                        v-if="carrierLogo"
                        :src="carrierLogo"
                        :style="{ height: (label.carrierCode === 'CUSTOM' ? (label.logoHeightPx || 40) : 40) + 'px' }"
                        style="object-fit:contain; max-width:180px;"
                        alt=""
                      />
                      <template v-else>
                        <span :style="{ background: carrierColor, color: '#fff', fontWeight: 800, fontSize: '11px', padding: '4px 7px', borderRadius: '3px' }">{{ label.carrierCode === 'CUSTOM' ? 'LOGO' : label.carrierCode }}</span>
                        <span style="font-size:10px; font-weight:700; letter-spacing:.5px;">{{ carrierName }}</span>
                      </template>
                    </div>
                    <span v-if="label.secondaryBadge" style="border:1.5px solid #000; padding:2px 6px; font-size:10px; font-weight:800; letter-spacing:1px;">{{ label.secondaryBadge }}</span>
                  </div>

                  <div v-if="label.visibleBlocks.header" style="display:flex; justify-content:space-between; align-items:baseline; gap:8px;">
                    <div>
                      <div style="font-size:13px; font-weight:900; letter-spacing:1.5px;">{{ label.headerTitle }}</div>
                      <div style="font-size:9px; font-weight:700; color:#334155;">{{ label.headerSubtitle }}</div>
                    </div>
                    <div style="font-size:8px; font-weight:700; color:#334155;">IATA RES. 651</div>
                  </div>

                  <div
                    v-for="(f, i) in (label.visibleBlocks.custom ? customBySection.header : [])"
                    :key="'h' + i"
                    :style="badgeStyle(f.badgeColor)"
                  >
                    <span style="font-size:9px; font-weight:700; text-transform:uppercase;">{{ f.label }}</span>
                    <span style="font-size:11px; font-weight:900; text-align:right;">{{ f.value }}</span>
                  </div>

                  <div v-if="label.visibleBlocks.barcode" style="display:flex; align-items:flex-end; gap:2px; height:30px;">
                    <span v-for="(b, i) in BARCODE_BARS" :key="i" :style="{ width: (b * 2) + 'px', height: '26px', background: '#000' }"></span>
                    <span style="margin-left:6px; font-size:9px; letter-spacing:1px;">{{ barcodeText.slice(0, 30) }}</span>
                  </div>

                  <div v-if="label.visibleBlocks.uldId" style="display:flex; justify-content:space-between; align-items:flex-end; gap:8px; border-bottom:2px solid #000; padding-bottom:8px;">
                    <div style="min-width:0;">
                      <div style="font-size:9px; font-weight:700; letter-spacing:1px;">ULD IDENTIFICATION</div>
                      <div style="font-size:44px; font-weight:900; line-height:1; word-break:break-all;">{{ label.uldId || '—' }}</div>
                      <div style="font-size:10px; font-weight:700;">{{ label.type || '—' }}<template v-if="label.contour"> · {{ label.contour }}</template></div>
                    </div>
                    <div v-if="label.position" style="text-align:right; shrink:0;">
                      <div style="font-size:9px; font-weight:700;">{{ t('ulds.label.preview.position') }}</div>
                      <div style="border:1.5px solid #000; padding:2px 8px; font-size:14px; font-weight:900; display:inline-block;">{{ label.position }}</div>
                    </div>
                  </div>

                  <div
                    v-for="(f, i) in (label.visibleBlocks.custom ? customBySection.details : [])"
                    :key="'d' + i"
                    :style="badgeStyle(f.badgeColor)"
                  >
                    <span style="font-size:9px; font-weight:700; text-transform:uppercase;">{{ f.label }}</span>
                    <span style="font-size:11px; font-weight:900; text-align:right;">{{ f.value }}</span>
                  </div>

                  <div v-if="label.visibleBlocks.weights" style="display:grid; grid-template-columns:repeat(3,1fr); gap:6px; border:1.5px solid #000; padding:6px;">
                    <div v-for="w in weightCells" :key="w.k" style="text-align:center;">
                      <div style="font-size:9px; font-weight:700;">{{ w.k }}</div>
                      <div style="font-size:22px; font-weight:900; line-height:1.1;">{{ w.v }}</div>
                      <div style="font-size:10px; font-weight:700;">{{ weightUnitLabel }}</div>
                    </div>
                  </div>

                  <div
                    v-for="(f, i) in (label.visibleBlocks.custom ? customBySection.weights : [])"
                    :key="'w' + i"
                    :style="badgeStyle(f.badgeColor)"
                  >
                    <span style="font-size:9px; font-weight:700; text-transform:uppercase;">{{ f.label }}</span>
                    <span style="font-size:11px; font-weight:900; text-align:right;">{{ f.value }}</span>
                  </div>

                  <div v-if="label.visibleBlocks.routing" style="display:flex; justify-content:space-between; align-items:center; gap:8px; border-bottom:1.5px dashed #000; padding-bottom:6px;">
                    <div>
                      <div style="font-size:9px; font-weight:700;">ROUTING</div>
                      <div style="font-size:34px; font-weight:900; line-height:1.1;">{{ label.origin || '—' }} &#8594; {{ label.destination || '—' }}</div>
                    </div>
                    <div style="text-align:right;">
                      <div style="font-size:8px; font-weight:700;">FLIGHT</div>
                      <div style="font-size:14px; font-weight:900;">{{ label.flightNumber || '—' }}</div>
                      <div style="font-size:9px;">{{ label.flightDate }}</div>
                    </div>
                  </div>

                  <div
                    v-for="(f, i) in (label.visibleBlocks.custom ? customBySection.routing : [])"
                    :key="'r' + i"
                    :style="badgeStyle(f.badgeColor)"
                  >
                    <span style="font-size:9px; font-weight:700; text-transform:uppercase;">{{ f.label }}</span>
                    <span style="font-size:11px; font-weight:900; text-align:right;">{{ f.value }}</span>
                  </div>

                  <div v-if="label.visibleBlocks.signoffs" style="display:flex; flex-direction:column; gap:3px;">
                    <div
                      v-for="s in signoffRows"
                      :key="s.k"
                      style="display:flex; justify-content:space-between; border-bottom:1px solid #cbd5e1; padding:2px 0; font-size:10px;"
                    >
                      <span style="font-weight:700;">{{ s.k }}</span>
                      <span :style="s.seal ? 'font-weight:900; color:#dc2626; letter-spacing:1px;' : ''">{{ s.v || '________________' }}</span>
                    </div>
                  </div>

                  <div
                    v-for="(f, i) in (label.visibleBlocks.custom ? customBySection.signoff : [])"
                    :key="'s' + i"
                    :style="badgeStyle(f.badgeColor)"
                  >
                    <span style="font-size:9px; font-weight:700; text-transform:uppercase;">{{ f.label }}</span>
                    <span style="font-size:11px; font-weight:900; text-align:right;">{{ f.value }}</span>
                  </div>

                  <div v-if="label.remarks" style="border:1.5px solid #000; padding:4px 6px;">
                    <div style="font-size:8px; font-weight:700;">{{ t('ulds.label.preview.remarks') }}</div>
                    <div style="font-size:10px; font-weight:700;">{{ label.remarks }}</div>
                  </div>

                  <div
                    v-for="(f, i) in (label.visibleBlocks.custom ? customBySection.footer : [])"
                    :key="'f' + i"
                    :style="badgeStyle(f.badgeColor)"
                  >
                    <span style="font-size:9px; font-weight:700; text-transform:uppercase;">{{ f.label }}</span>
                    <span style="font-size:11px; font-weight:900; text-align:right;">{{ f.value }}</span>
                  </div>

                  <div v-if="label.visibleBlocks.handling && selectedHandling.length" style="display:flex; flex-wrap:wrap; gap:6px;">
                    <div v-for="ic in selectedHandling" :key="ic.id" style="border:1.5px solid #000; padding:3px 6px; text-align:center; min-width:54px;">
                      <div style="font-size:18px; line-height:1;">{{ ic.icon }}</div>
                      <div style="font-size:7px; font-weight:700;">{{ ic.label }}</div>
                    </div>
                  </div>

                  <div v-if="label.visibleBlocks.legends && activeLegends.length" style="display:flex; flex-direction:column; gap:4px;">
                    <div v-for="lg in activeLegends" :key="lg.id" :style="legendBannerStyle(lg.colorScheme)">
                      <div style="display:flex; align-items:center; gap:6px;">
                        <span :style="{ background: legendColor(lg.colorScheme), color: '#fff', fontWeight: 900, fontSize: '10px', padding: '2px 6px', borderRadius: '2px' }">{{ lg.title }}</span>
                        <span style="font-size:9px; font-weight:800;">{{ lg.subtitle }}</span>
                      </div>
                      <div style="font-size:8px; margin-top:2px;">{{ lg.complianceText }}</div>
                    </div>
                  </div>

                  <div style="margin-top:auto; border-top:2px solid #000; padding-top:5px; display:flex; justify-content:space-between; align-items:center; gap:6px; font-size:8px; font-weight:700;">
                    <span>{{ t('ulds.label.preview.secured') }}</span>
                    <span>{{ t('ulds.label.preview.issued') }}: {{ label.issuedAt || '—' }}</span>
                  </div>
                  </template>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="flex items-center justify-between gap-3 px-4 py-3 border-t border-strong bg-surface shrink-0">
          <span
            class="flex items-center gap-1.5 text-[11px] font-mono min-w-0 truncate"
            :class="signoffsComplete ? 'text-emerald-600' : 'text-amber-600'"
            :title="signoffsComplete ? t('ulds.label.readyToSave') : requiredMissingLabels"
          >
            <component :is="signoffsComplete ? icons.CheckCircle : icons.AlertTriangle" :size="13" class="shrink-0" />
            <span class="truncate">{{ signoffsComplete ? t('ulds.label.readyToSave') : t('ulds.label.requiredHint', { n: requiredMissing.length }) }}</span>
          </span>
          <div class="flex justify-end gap-2 shrink-0">
            <button type="button" class="ds-btn-secondary" @click="close">{{ t('common.cancel') }}</button>
            <button type="button" class="ds-btn-primary" @click="handleSave">{{ t('ulds.label.save') }}</button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useIcons } from '../composables/useIcons'
import {
  PRESET_CARRIERS,
  PRESET_HANDLING_ICONS,
  PRESET_LEGENDS,
  LABEL_TEMPLATES,
  CUSTOM_FIELD_COLORS,
  CUSTOM_FIELD_SECTIONS,
  LEGEND_COLOR_SCHEMES,
  LABEL_SIZES,
  resolveLabelSize,
  createEmptyLabel,
  grossWeightKg,
  kgToLbs,
  makeCustomField,
  makeLegend,
  applyTemplate as applyLabelTemplate,
  generateSeal,
  buildBarcodeValue,
  getStoredLabel,
  saveStoredLabel,
} from '../utils/uldLabel'

const props = defineProps({
  show: { type: Boolean, default: false },
  uld: { type: Object, default: null },
  ctx: { type: Object, default: () => ({}) },
  zIndex: { type: [Number, String], default: 120 },
})

const emit = defineEmits(['close', 'saved'])

const { t } = useI18n()
const icons = useIcons()

const activeTab = ref('baseline')
const validationError = ref(null)
const saveSuccess = ref(false)
const previewZoom = ref(1)
const templateApplied = ref(false)
const label = ref(createEmptyLabel({}, {}))

const zoomLevels = [0.8, 1, 1.25]

const labelSize = computed(() => resolveLabelSize(label.value))
const isCustomSize = computed(() => label.value.labelSize === 'custom')

const requiredMissing = computed(() => {
  const l = label.value || {}
  const missing = []
  if (!String(l.builtBy || '').trim()) missing.push('builtBy')
  if (!String(l.weighedBy || '').trim()) missing.push('weighedBy')
  if (!String(l.sealNumber || '').trim()) missing.push('sealNumber')
  if (!String(l.sealedBy || '').trim()) missing.push('sealedBy')
  if (!String(l.remarks || '').trim()) missing.push('remarks')
  if (!l.spxAccepted) missing.push('spxAccept')
  return missing
})

const requiredMissingLabels = computed(() => {
  const keyByField = {
    builtBy: t('ulds.label.signoffs.builtBy'),
    weighedBy: t('ulds.label.signoffs.weighedBy'),
    sealNumber: t('ulds.label.baseline.sealNumber'),
    sealedBy: t('ulds.label.signoffs.sealedBy'),
    remarks: t('ulds.label.signoffs.remarks'),
    spxAccept: t('ulds.label.signoffs.spxAccept'),
  }
  return requiredMissing.value.map((k) => keyByField[k]).join(', ')
})

const signoffsComplete = computed(() => requiredMissing.value.length === 0)

const tabs = [
  { id: 'baseline', key: 'ulds.label.tabs.baseline', icon: 'FileDescription' },
  { id: 'signoffs', key: 'ulds.label.tabs.signoffs', icon: 'CheckCircle' },
  { id: 'customFields', key: 'ulds.label.tabs.customFields', icon: 'Plus' },
  { id: 'branding', key: 'ulds.label.tabs.branding', icon: 'Palette' },
  { id: 'legends', key: 'ulds.label.tabs.legends', icon: 'Shield' },
  { id: 'templates', key: 'ulds.label.tabs.templates', icon: 'LayoutGrid' },
]

const blockKeys = ['barcode', 'header', 'uldId', 'weights', 'routing', 'signoffs', 'handling', 'custom', 'legends']

const BARCODE_BARS = [3, 1, 2, 4, 1, 3, 2, 1, 4, 2, 3, 1, 2, 4, 1, 2, 3, 2, 1, 4, 2, 1, 3, 1, 4, 2, 3, 1]

const FIELD_PRESETS = [
  { label: 'Destino Final (Transfer)', value: 'MIA Cargo Hub', badgeColor: 'slate', section: 'routing' },
  { label: 'Código IATA SHC', value: 'COL / CRT / ICE', badgeColor: 'blue', section: 'header' },
  { label: 'Número de Precinto Reforzado', value: 'SL-HIGH-99812', badgeColor: 'red', section: 'signoff' },
  { label: 'Shipper / Expedidor', value: 'Novartis Pharma AG', badgeColor: 'slate', section: 'footer' },
  { label: 'Temperatura Exigida', value: '+2°C a +8°C', badgeColor: 'blue', section: 'details' },
]

const newField = ref({ label: '', value: '', section: 'details', badgeColor: 'slate' })
const newLegend = ref({ title: '', subtitle: '', complianceText: '', colorScheme: 'black' })

const COLOR_HEX = {
  green: '#16a34a',
  amber: '#d97706',
  red: '#dc2626',
  blue: '#2563eb',
  cyan: '#0891b2',
  purple: '#9333ea',
  black: '#0f172a',
  slate: '#64748b',
  emerald: '#059669',
}

function legendColor(scheme) {
  return COLOR_HEX[scheme] || COLOR_HEX.black
}

function badgeStyle(color) {
  const map = {
    blue: { background: '#eff6ff', borderColor: '#1e40af', color: '#172554' },
    red: { background: '#fef2f2', borderColor: '#991b1b', color: '#450a0a' },
    amber: { background: '#fffbeb', borderColor: '#92400e', color: '#451a03' },
    emerald: { background: '#ecfdf5', borderColor: '#065f46', color: '#022c22' },
    slate: { background: '#f8fafc', borderColor: '#94a3b8', color: '#0f172a' },
  }
  const s = map[color] || map.slate
  return {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: '6px',
    border: `1.5px solid ${s.borderColor}`,
    background: s.background,
    color: s.color,
    padding: '3px 6px',
  }
}

function legendBannerStyle(scheme) {
  const hex = legendColor(scheme)
  return { border: `1.5px solid ${hex}`, borderLeft: `6px solid ${hex}`, padding: '4px 6px' }
}

const allLegends = computed(() => [...PRESET_LEGENDS, ...(label.value.customLegends || [])])

function isLegendActive(lg) {
  if (lg.type === 'CUSTOM') return lg.active !== false
  return label.value.activeLegends.includes(lg.id)
}

const activeLegends = computed(() => allLegends.value.filter((l) => isLegendActive(l)))

const selectedHandling = computed(() => PRESET_HANDLING_ICONS.filter((i) => label.value.handlingIcons.includes(i.id)))

const carrierColor = computed(() => {
  const c = PRESET_CARRIERS.find((x) => x.code === label.value.carrierCode)
  return c ? c.color : '#0f172a'
})

const carrierName = computed(() => {
  const c = PRESET_CARRIERS.find((x) => x.code === label.value.carrierCode)
  if (c) return c.name
  return label.value.carrierCode === 'CUSTOM' ? 'Custom Logo' : 'IATA Standard Cargo'
})

// Resolve the header brand mark: an uploaded custom logo, else the preset
// carrier's bundled SVG. Empty string falls back to the code badge.
const carrierLogo = computed(() => {
  if (label.value.carrierCode === 'CUSTOM') return label.value.customLogoUrl || ''
  const c = PRESET_CARRIERS.find((x) => x.code === label.value.carrierCode)
  return (c && c.logoUrl) || ''
})

const barcodeText = computed(() => label.value.barcodeValue || buildBarcodeValue(label.value))

const weightUnitLabel = computed(() => (label.value.weightUnit === 'KG' ? 'KG' : 'LB'))

const weightCells = computed(() => {
  const toKg = (v) => Math.round(Number(v) || 0)
  // prefer the exact source pounds (avoids kg round-trip drift); fall back to
  // converting the stored kg for labels saved before the lbs fields existed.
  const toLbs = (lbsField, kgField) =>
    lbsField != null ? Math.round(Number(lbsField) || 0) : kgToLbs(kgField)
  if (label.value.weightUnit === 'KG') {
    return [
      { k: 'TARA', v: toKg(label.value.tareWeightKg) },
      { k: 'NETO', v: toKg(label.value.netWeightKg) },
      { k: 'BRUTO', v: toKg(grossWeightKg(label.value)) },
    ]
  }
  const tare = toLbs(label.value.tareWeightLbs, label.value.tareWeightKg)
  const net = toLbs(label.value.netWeightLbs, label.value.netWeightKg)
  const gross = label.value.customGrossWeight != null ? kgToLbs(label.value.customGrossWeight) : tare + net
  return [
    { k: 'TARA', v: tare },
    { k: 'NETO', v: net },
    { k: 'BRUTO', v: gross },
  ]
})

const signoffRows = computed(() => [
  { k: t('ulds.label.preview.signoffLoaded'), v: label.value.builtBy },
  { k: t('ulds.label.preview.signoffWeighed'), v: label.value.weighedBy },
  { k: t('ulds.label.preview.signoffSealNumber'), v: label.value.sealNumber, seal: true },
  { k: t('ulds.label.preview.signoffSealed'), v: label.value.sealedBy },
])

const customBySection = computed(() => {
  const groups = { header: [], details: [], weights: [], routing: [], signoff: [], footer: [] }
  for (const f of label.value.customFields || []) {
    const s = CUSTOM_FIELD_SECTIONS.includes(f.section) ? f.section : 'details'
    groups[s].push(f)
  }
  return groups
})

const labelStyle = computed(() => {
  const { w, h } = labelSize.value
  const longest = Math.max(w, h) || 1
  const scale = 520 / longest
  return {
    background: '#ffffff',
    color: '#000000',
    border: '3px solid #000000',
    padding: '14px',
    fontFamily: "'JetBrains Mono', 'Courier New', monospace",
    width: `${Math.round(w * scale)}px`,
    minHeight: `${Math.round(h * scale)}px`,
    boxSizing: 'border-box',
    display: 'flex',
    flexDirection: 'column',
    gap: '10px',
    zoom: previewZoom.value,
  }
})

function resetLabel() {
  const base = createEmptyLabel(props.uld || {}, props.ctx || {})
  const stored = getStoredLabel(props.uld || {})
  const merged = { ...base, ...(stored || {}) }
  merged.visibleBlocks = { ...base.visibleBlocks, ...((stored && stored.visibleBlocks) || {}) }
  merged.customLegends = (stored && stored.customLegends) || []
  label.value = merged
  activeTab.value = 'baseline'
  validationError.value = null
  saveSuccess.value = false
  previewZoom.value = 1
}

function close() {
  emit('close')
}

function printLabel() {
  const { w, h } = labelSize.value
  const styleId = 'uld-label-print'
  let el = document.getElementById(styleId)
  if (!el) {
    el = document.createElement('style')
    el.id = styleId
    document.head.appendChild(el)
  }
  el.textContent = `@page { size: ${w}in ${h}in; margin: 0; }`
  const cleanup = () => {
    const node = document.getElementById(styleId)
    if (node) node.remove()
    window.removeEventListener('afterprint', cleanup)
  }
  window.addEventListener('afterprint', cleanup)
  window.print()
  setTimeout(cleanup, 1500)
}

function onUppercase(field) {
  label.value[field] = String(label.value[field] || '').toUpperCase()
}

function handlingSelected(id) {
  return label.value.handlingIcons.includes(id)
}

function toggleHandlingIcon(id) {
  const arr = label.value.handlingIcons
  const i = arr.indexOf(id)
  if (i >= 0) arr.splice(i, 1)
  else arr.push(id)
}

function addCustomField() {
  if (!String(newField.value.label || '').trim() || !String(newField.value.value || '').trim()) return
  label.value.customFields.push(makeCustomField(newField.value))
  newField.value = { label: '', value: '', section: 'details', badgeColor: 'slate' }
}

function addPreset(preset) {
  label.value.customFields.push(makeCustomField(preset))
}

function removeCustomField(idx) {
  label.value.customFields.splice(idx, 1)
}

function addLegend() {
  if (!String(newLegend.value.title || '').trim()) return
  label.value.customLegends.push(makeLegend(newLegend.value))
  newLegend.value = { title: '', subtitle: '', complianceText: '', colorScheme: 'black' }
}

function removeCustomLegend(id) {
  const i = label.value.customLegends.findIndex((l) => l.id === id)
  if (i >= 0) label.value.customLegends.splice(i, 1)
}

function toggleLegend(lg) {
  if (lg.type === 'CUSTOM') {
    lg.active = !(lg.active !== false)
    return
  }
  const arr = label.value.activeLegends
  const i = arr.indexOf(lg.id)
  if (i >= 0) arr.splice(i, 1)
  else arr.push(lg.id)
}

function onApplyTemplate(tplId) {
  applyLabelTemplate(label.value, tplId)
  templateApplied.value = true
  setTimeout(() => { templateApplied.value = false }, 1600)
}

// Localised template label/description. Falls back to the template id when the
// translation key is missing so new templates still render a readable name.
const TEMPLATE_I18N = { standard: 'standard', val: 'val', 'ups-pallet': 'ups' }

function templateI18nKey(tpl) {
  return TEMPLATE_I18N[tpl.id] || tpl.id
}

function templateName(tpl) {
  return t(`ulds.label.templates.${templateI18nKey(tpl)}Name`)
}

function templateDesc(tpl) {
  return t(`ulds.label.templates.${templateI18nKey(tpl)}Desc`)
}

function buildLabelForSave() {
  const l = JSON.parse(JSON.stringify(label.value))
  l.uldId = String(l.uldId || '').trim().toUpperCase()
  l.flightNumber = String(l.flightNumber || '').trim().toUpperCase()
  l.origin = String(l.origin || '').trim().toUpperCase()
  l.destination = String(l.destination || '').trim().toUpperCase()
  l.id = l.id || `lbl-${Date.now()}`
  const leg = activeLegends.value[0]
  l.securityStatus = leg ? leg.title : 'SPX'
  l.issuedAt = new Date().toISOString().replace('T', ' ').slice(0, 16) + ' UTC'
  l.barcodeValue = buildBarcodeValue(l)
  return l
}

function handleSave() {
  validationError.value = null
  const l = label.value
  if (!String(l.builtBy || '').trim()) {
    validationError.value = t('ulds.label.signoffs.errBuilt')
    activeTab.value = 'signoffs'
    return
  }
  if (!String(l.weighedBy || '').trim()) {
    validationError.value = t('ulds.label.signoffs.errWeighed')
    activeTab.value = 'signoffs'
    return
  }
  if (!String(l.sealNumber || '').trim()) {
    validationError.value = t('ulds.label.signoffs.errSeal')
    activeTab.value = 'signoffs'
    return
  }
  if (!String(l.sealedBy || '').trim()) {
    validationError.value = t('ulds.label.signoffs.errSealed')
    activeTab.value = 'signoffs'
    return
  }
  if (!String(l.remarks || '').trim()) {
    validationError.value = t('ulds.label.signoffs.errRemarks')
    activeTab.value = 'signoffs'
    return
  }
  if (!l.spxAccepted) {
    validationError.value = t('ulds.label.signoffs.validationRequired')
    activeTab.value = 'signoffs'
    return
  }
  const saved = buildLabelForSave()
  saveStoredLabel(props.uld || {}, saved)
  label.value.id = saved.id
  label.value.issuedAt = saved.issuedAt
  label.value.barcodeValue = saved.barcodeValue
  label.value.securityStatus = saved.securityStatus
  saveSuccess.value = true
  emit('saved', saved)
  setTimeout(() => {
    saveSuccess.value = false
    close()
  }, 900)
}

watch(() => props.show, (val) => {
  if (val) resetLabel()
})
</script>
