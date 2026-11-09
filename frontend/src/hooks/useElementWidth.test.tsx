import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import { useElementWidth } from './useElementWidth'

type ObserverCallback = (entries: { contentRect: { width: number } }[]) => void

class FakeResizeObserver {
  static instances: FakeResizeObserver[] = []
  observed: Element[] = []
  disconnected = false

  private readonly callback: ObserverCallback

  constructor(callback: ObserverCallback) {
    this.callback = callback
    FakeResizeObserver.instances.push(this)
  }

  observe(element: Element) {
    this.observed.push(element)
  }

  disconnect() {
    this.disconnected = true
  }

  report(width: number) {
    this.callback([{ contentRect: { width } }])
  }
}

function Probe() {
  const [ref, width] = useElementWidth<HTMLDivElement>()
  return (
    <div ref={ref} data-testid="box">
      {width === null ? 'unmeasured' : `width ${width}`}
    </div>
  )
}

describe('useElementWidth', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    FakeResizeObserver.instances = []
  })

  it('is null until the element has been measured', () => {
    vi.stubGlobal('ResizeObserver', FakeResizeObserver)
    render(<Probe />)

    expect(screen.getByTestId('box')).toHaveTextContent('unmeasured')
    expect(FakeResizeObserver.instances[0].observed).toEqual([screen.getByTestId('box')])
  })

  it('reports the width in whole pixels and follows resizes', () => {
    vi.stubGlobal('ResizeObserver', FakeResizeObserver)
    render(<Probe />)

    act(() => FakeResizeObserver.instances[0].report(912.7))
    expect(screen.getByTestId('box')).toHaveTextContent('width 912')
    act(() => FakeResizeObserver.instances[0].report(340))
    expect(screen.getByTestId('box')).toHaveTextContent('width 340')
  })

  it('stays null where ResizeObserver does not exist', () => {
    vi.stubGlobal('ResizeObserver', undefined)
    render(<Probe />)

    expect(screen.getByTestId('box')).toHaveTextContent('unmeasured')
  })

  it('stops observing when it unmounts', () => {
    vi.stubGlobal('ResizeObserver', FakeResizeObserver)
    const { unmount } = render(<Probe />)

    unmount()

    expect(FakeResizeObserver.instances[0].disconnected).toBe(true)
  })
})
