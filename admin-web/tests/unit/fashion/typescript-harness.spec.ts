import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

describe('Fashion incremental TypeScript harness', () => {
  it('可通过 Vue Test Utils 挂载 TypeScript 组件', () => {
    const component = defineComponent({
      name: 'FashionHarness',
      setup: () => () => h('output', { 'data-testid': 'fashion-status' }, 'ready')
    })

    const wrapper = mount(component)

    expect(wrapper.get('[data-testid="fashion-status"]').text()).toBe('ready')
  })
})
