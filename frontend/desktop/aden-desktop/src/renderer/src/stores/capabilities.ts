import { defineStore } from 'pinia'
import type { CapabilityProjection } from '../../../shared/generated/operator-contracts'

export const useCapabilitiesStore = defineStore('capabilities', {
  state: () => ({ items: [] as CapabilityProjection[], filter: null as string | null }),
  actions: {
    replace(items: readonly CapabilityProjection[]): void { this.items = [...items] },
    setFilter(value: string | null): void { this.filter = value }
  }
})
