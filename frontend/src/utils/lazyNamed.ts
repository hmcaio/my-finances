import { lazy, type ComponentType, type LazyExoticComponent } from 'react'

/** The keys of a module whose export is a React component. */
type ComponentKeys<M> = {
  [K in keyof M]: M[K] extends ComponentType<never> ? K : never
}[keyof M] &
  string

/** The component type of an export, with its props preserved. */
type ComponentOf<C> = C extends ComponentType<infer P> ? ComponentType<P> : never

/**
 * `React.lazy` for a named export: `lazyNamed(() => import('./features/x/XPage'), 'XPage')`.
 *
 * Pages are named exports, not default (docs/conventions/frontend-structure.md), and `lazy` wants
 * a default one. The import stays a literal `import()` at the call site, so the bundler still
 * splits each page into its own chunk. The name is checked against the module: a typo, or an
 * export that is not a component, is a compile error.
 */
export function lazyNamed<M, K extends ComponentKeys<M>>(
  load: () => Promise<M>,
  name: K,
): LazyExoticComponent<ComponentOf<M[K]>> {
  return lazy(async () => {
    const module = await load()
    const component = module[name]
    if (component === undefined || component === null) {
      throw new Error(`lazyNamed: the module has no export "${name}"`)
    }
    return { default: component as ComponentOf<M[K]> }
  })
}
