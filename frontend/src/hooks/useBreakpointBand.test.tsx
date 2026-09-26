import { afterEach, describe, expect, it } from 'vitest'
import { renderHook } from '@testing-library/react'
import { restoreViewport, setViewportWidth } from '../test/viewport'
import { useBreakpointBand, useIsMobile } from './useBreakpointBand'

describe('useBreakpointBand', () => {
  afterEach(restoreViewport)

  it.each([
    [360, 'mobile'],
    [599, 'mobile'],
    [600, 'tablet'],
    [1199, 'tablet'],
    [1200, 'desktop'],
    [1280, 'desktop'],
  ])('maps a %ipx viewport to %s', (width, band) => {
    setViewportWidth(width)
    expect(renderHook(() => useBreakpointBand()).result.current).toBe(band)
  })

  it('useIsMobile is true only below sm', () => {
    setViewportWidth(599)
    expect(renderHook(() => useIsMobile()).result.current).toBe(true)
    setViewportWidth(600)
    expect(renderHook(() => useIsMobile()).result.current).toBe(false)
  })
})
