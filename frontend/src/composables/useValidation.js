import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'

export function useFieldValidation(initialValue = '', validators = []) {
  const { t } = useI18n()
  const value = ref(initialValue)
  const touched = ref(false)
  const dirty = ref(false)

  const errors = computed(() => {
    if (!touched.value && !dirty.value) return []
    return validators
      .map(v => v(value.value))
      .filter(Boolean)
      .map(e => (typeof e === 'string' ? e : t(e)))
  })

  const isValid = computed(() => errors.value.length === 0 && (touched.value || dirty.value))
  const hasError = computed(() => errors.value.length > 0)

  function validate() {
    touched.value = true
    dirty.value = true
    return errors.value.length === 0
  }

  function setValue(v) {
    value.value = v
    dirty.value = true
  }

  function reset() {
    value.value = initialValue
    touched.value = false
    dirty.value = false
  }

  function blur() {
    touched.value = true
  }

  return {
    value,
    errors,
    isValid,
    hasError,
    touched,
    dirty,
    validate,
    setValue,
    reset,
    blur,
    firstError: computed(() => errors.value[0]),
  }
}

export function useFormValidation(fields = {}) {
  const fieldStates = {}
  Object.keys(fields).forEach(key => {
    fieldStates[key] = useFieldValidation(fields[key].initial || '', fields[key].validators || [])
  })

  const isValid = computed(() => Object.values(fieldStates).every(f => f.isValid.value))
  const hasErrors = computed(() => Object.values(fieldStates).some(f => f.hasError.value))
  const firstError = computed(() => {
    for (const f of Object.values(fieldStates)) {
      if (f.firstError.value) return f.firstError.value
    }
    return null
  })

  function validateAll() {
    let valid = true
    Object.values(fieldStates).forEach(f => { if (!f.validate()) valid = false })
    return valid
  }

  function resetAll() {
    Object.values(fieldStates).forEach(f => f.reset())
  }

  function getField(key) {
    return fieldStates[key]
  }

  return {
    fields: fieldStates,
    isValid,
    hasErrors,
    firstError,
    validateAll,
    resetAll,
    getField,
  }
}

export const validators = {
  required: (msg = 'validation.required') => v => !v || (typeof v === 'string' && !v.trim()) ? msg : null,
  minLength: (len, msg) => v => v && v.length < len ? msg || `validation.minLength ${len}` : null,
  maxLength: (len, msg) => v => v && v.length > len ? msg || `validation.maxLength ${len}` : null,
  email: (msg = 'validation.email') => v => v && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v) ? msg : null,
  numeric: (msg = 'validation.numeric') => v => v && !/^\d+$/.test(v) ? msg : null,
  mawbFormat: (msg = 'validation.mawbFormat') => v => v && !/^\d{3}-\d{8}$/.test(v.replace(/[\s-]/g, '')) ? msg : null,
  hawbFormat: (msg = 'validation.hawbFormat') => v => v && !/^\d{3}-\d{8}$/.test(v.replace(/[\s-]/g, '')) ? msg : null,
  passwordStrength: (msg = 'validation.weakPassword') => v => {
    if (!v) return null
    const hasUpper = /[A-Z]/.test(v)
    const hasLower = /[a-z]/.test(v)
    const hasDigit = /\d/.test(v)
    const hasSpecial = /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/.test(v)
    const longEnough = v.length >= 12
    if (hasUpper && hasLower && hasDigit && hasSpecial && longEnough) return null
    return msg
  },
  confirm: (getTargetValue, msg = 'validation.mismatch') => v => v !== getTargetValue() ? msg : null,
}