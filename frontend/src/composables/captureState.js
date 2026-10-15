import { ref } from 'vue'

export const isCapturing = ref(false)

export function setCapturing(val) {
  isCapturing.value = val
}