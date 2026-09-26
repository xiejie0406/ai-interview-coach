import { beforeEach, describe, expect, it, vi } from 'vitest'

const { requestMock } = vi.hoisted(() => ({
  requestMock: vi.fn()
}))

vi.mock('@/utils/request', () => ({
  default: requestMock
}))

import {
  FashionApiError,
  fashionRequest,
  normalizeFashionApiError,
  resolveFashionApiPath
} from '@/api/fashion/client'
import { rejectBusinessError } from '@/utils/structuredBusinessError'

describe('Fashion Java API client', () => {
  beforeEach(() => {
    requestMock.mockReset()
  })

  it('只把相对业务路径发送到 Java 的 /fashion 前缀', async () => {
    requestMock.mockResolvedValue({ code: 200, data: { status: 'ok' } })

    await fashionRequest({
      path: '/health',
      params: { verbose: true },
      correlationId: 'corr-fashion-0001'
    })

    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      url: '/fashion/health',
      method: 'get',
      params: { verbose: true },
      preserveBusinessError: true,
      headers: {
        Accept: 'application/json',
        'X-Correlation-ID': 'corr-fashion-0001'
      }
    }))
  })

  it.each([
    'http://127.0.0.1:8000/internal/v1/runs',
    '//python-runtime/internal/v1/runs',
    '/',
    '../internal/v1/runs',
    '%2e%2e/internal/v1/runs',
    '%2e%2e%2finternal/v1/runs',
    '%252e%252e/internal/v1/runs',
    '%255cinternal/v1/runs',
    './internal/v1/runs',
    'internal\\v1\\runs',
    'internal//v1/runs',
    'internal/v1/runs/'
  ])('拒绝可能绕过 Java 边界的路径：%s', path => {
    expect(() => resolveFashionApiPath(path)).toThrow()
    expect(requestMock).not.toHaveBeenCalled()
  })

  it('只透传允许的业务幂等与并发请求头', async () => {
    requestMock.mockResolvedValue({ code: 200 })

    await fashionRequest({
      path: 'quotes/quote-1',
      method: 'put',
      body: { title: '秋季方案' },
      correlationId: 'corr-fashion-0002',
      idempotencyKey: 'idem-fashion-00000001',
      ifMatch: '"v3"'
    })

    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      url: '/fashion/quotes/quote-1',
      headers: {
        Accept: 'application/json',
        'X-Correlation-ID': 'corr-fashion-0002',
        'Idempotency-Key': 'idem-fashion-00000001',
        'If-Match': '"v3"'
      }
    }))
  })

  it('把稳定错误信封归一为可判断的客户端错误', () => {
    const transportError = Object.assign(new Error('Request failed with status code 503'), {
      code: 'ERR_BAD_RESPONSE',
      response: {
        status: 503,
        headers: { 'x-correlation-id': 'corr-fashion-error' },
        data: {
          error: {
            code: 'AI_RUNTIME_UNAVAILABLE',
            userMessage: '智能能力暂不可用',
            retryable: true
          }
        }
      }
    })
    const normalized = normalizeFashionApiError(transportError)

    expect(normalized).toBeInstanceOf(FashionApiError)
    expect(normalized).toMatchObject({
      status: 503,
      code: 'AI_RUNTIME_UNAVAILABLE',
      correlationId: 'corr-fashion-error',
      retryable: true
    })
    expect(normalized.message).toBe('智能能力暂不可用')
  })

  it('兼容冻结的 snake_case 错误信封与若依 HTTP 200 业务码', () => {
    const transportError = Object.assign(new Error('业务请求失败'), {
      response: {
        status: 200,
        headers: {},
        data: {
          code: 429,
          correlation_id: 'corr-fashion-rate-limit',
          error: {
            code: 'RATE_LIMITED',
            message: '请求过于频繁',
            retryable: true
          }
        }
      }
    })

    expect(normalizeFashionApiError(transportError)).toMatchObject({
      status: 429,
      code: 'RATE_LIMITED',
      correlationId: 'corr-fashion-rate-limit',
      retryable: true,
      message: '请求过于频繁'
    })
  })

  it('只在 Fashion 显式选择时保留若依业务错误响应', async () => {
    const response = {
      status: 200,
      config: { preserveBusinessError: true },
      data: { code: 503, error: { code: 'PROVIDER_DISABLED' } }
    }

    await expect(rejectBusinessError(response, '能力未启用', 'error')).rejects.toMatchObject({
      status: 503,
      message: '能力未启用',
      response
    })
    await expect(rejectBusinessError(
      { ...response, config: {} },
      '能力未启用',
      'legacy-error'
    )).rejects.toBe('legacy-error')
  })

  it('没有服务端明确许可时不把 504 或 503 判定为可重试', () => {
    for (const status of [503, 504]) {
      const normalized = normalizeFashionApiError({
        response: {
          status,
          data: { error: { code: 'UPSTREAM_TIMEOUT', message: '结果状态未知' } }
        }
      })
      expect(normalized.retryable).toBe(false)
    }
  })

  it('校验超时范围并原样透传 AbortSignal', async () => {
    const controller = new AbortController()
    requestMock.mockResolvedValue({ code: 200 })

    await fashionRequest({
      path: 'quotes/quote-1',
      timeoutMs: 30_000,
      signal: controller.signal
    })

    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      timeout: 30_000,
      signal: controller.signal
    }))
    await expect(fashionRequest({ path: 'quotes/quote-1', timeoutMs: 0 })).rejects.toThrow()
    await expect(fashionRequest({ path: 'quotes/quote-1', timeoutMs: 120_001 })).rejects.toThrow()
  })
})
