import { describe, expect, it } from 'vitest'
import { resolveRuntimeConfig } from '../src/main/runtime-config'

describe('resolveRuntimeConfig', () => {
  it('allows loopback HTTP only during development', () => {
    expect(resolveRuntimeConfig('http://127.0.0.1:8081/', true).apiBaseUrl)
      .toBe('http://127.0.0.1:8081')
    expect(() => resolveRuntimeConfig('http://127.0.0.1:8081', false)).toThrow(/HTTPS/)
  })

  it('allows HTTPS in packaged mode', () => {
    expect(resolveRuntimeConfig('https://aden.example.test/api/', false).apiBaseUrl)
      .toBe('https://aden.example.test/api')
  })

  it('allows only exact loopback HTTP for the local test package', () => {
    expect(resolveRuntimeConfig('http://127.0.0.1:8081', false, true).apiBaseUrl)
      .toBe('http://127.0.0.1:8081')
    expect(() => resolveRuntimeConfig('http://192.168.1.10:8081', false, true)).toThrow(/loopback/)
    expect(() => resolveRuntimeConfig('http://example.test', false, true)).toThrow(/loopback/)
    expect(() => resolveRuntimeConfig('https://aden.example.test', false, true)).toThrow(/loopback/)
    expect(() => resolveRuntimeConfig('http://user:secret@127.0.0.1:8081', false, true)).toThrow()
  })

  it('requires an explicit API URL in packaged mode', () => {
    expect(() => resolveRuntimeConfig(undefined, false)).toThrow(/必须显式配置/)
    expect(resolveRuntimeConfig(undefined, true).apiBaseUrl).toBe('http://127.0.0.1:8081')
  })

  it('rejects credentials and query fragments in the base URL', () => {
    expect(() => resolveRuntimeConfig('https://user:secret@example.test', false)).toThrow()
    expect(() => resolveRuntimeConfig('https://example.test?token=secret', false)).toThrow()
    expect(() => resolveRuntimeConfig('https://example.test/#token', false)).toThrow()
  })

  it('rejects non-loopback development HTTP', () => {
    expect(() => resolveRuntimeConfig('http://192.168.1.10:8081', true)).toThrow(/HTTPS/)
  })
})
