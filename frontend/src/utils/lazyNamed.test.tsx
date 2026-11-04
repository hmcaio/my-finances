import { Component, Suspense, type ReactNode } from 'react'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { lazyNamed } from './lazyNamed'

function Hello() {
  return <p>hello page</p>
}

const fakeModule = { Hello, NOT_A_COMPONENT: 42 }

class Catcher extends Component<{ children: ReactNode }, { message: string | null }> {
  state = { message: null as string | null }

  static getDerivedStateFromError(error: Error) {
    return { message: error.message }
  }

  render() {
    return this.state.message !== null ? <p>caught: {this.state.message}</p> : this.props.children
  }
}

describe('lazyNamed', () => {
  it('resolves the named export as the lazy component', async () => {
    const Page = lazyNamed(() => Promise.resolve(fakeModule), 'Hello')

    render(
      <Suspense fallback={<p>loading</p>}>
        <Page />
      </Suspense>,
    )

    expect(screen.getByText('loading')).toBeInTheDocument()
    expect(await screen.findByText('hello page')).toBeInTheDocument()
  })

  it('calls the loader only when first rendered, not when declared', () => {
    const load = vi.fn(() => Promise.resolve(fakeModule))

    lazyNamed(load, 'Hello')

    expect(load).not.toHaveBeenCalled()
  })

  it('fails with a clear error when the module lacks the export', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    // A typo'd name is a compile error; this simulates one slipping past the types at runtime.
    const Page = lazyNamed(() => Promise.resolve(fakeModule), 'Missing' as 'Hello')

    render(
      <Catcher>
        <Suspense fallback={<p>loading</p>}>
          <Page />
        </Suspense>
      </Catcher>,
    )

    expect(await screen.findByText(/caught: .*"Missing"/)).toBeInTheDocument()
    vi.restoreAllMocks()
  })

  it('rejects a name that is missing or not a component at compile time', () => {
    // @ts-expect-error 'Nope' is not an export of the module
    lazyNamed(() => Promise.resolve(fakeModule), 'Nope')
    // @ts-expect-error 'NOT_A_COMPONENT' is exported but is not a component
    lazyNamed(() => Promise.resolve(fakeModule), 'NOT_A_COMPONENT')
  })
})
