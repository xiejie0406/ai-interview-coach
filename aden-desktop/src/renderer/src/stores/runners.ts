import { defineStore } from 'pinia'
import type { RunnerSummary } from '../../../shared/generated/operator-contracts'

export const useRunnersStore = defineStore('runners', {
  state: () => ({ items: [] as RunnerSummary[] }),
  actions: { replace(items: readonly RunnerSummary[]): void { this.items = [...items] } }
})
