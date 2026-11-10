import { describe, expect, it, vi } from 'vitest'
import { getSnapshot, publish, resetNotifications, subscribe } from './notificationStore'

describe('notificationStore', () => {
  it('starts empty', () => {
    expect(getSnapshot()).toBeNull()
  })

  it('holds the published message', () => {
    publish('Saved')

    expect(getSnapshot()?.message).toBe('Saved')
  })

  it('replaces the current message with a new one under a new id', () => {
    publish('First')
    const first = getSnapshot()
    publish('Second')

    expect(getSnapshot()?.message).toBe('Second')
    expect(getSnapshot()?.id).not.toBe(first?.id)
  })

  it('gives a repeated identical message a new id, so it shows again', () => {
    publish('Saved')
    const first = getSnapshot()
    publish('Saved')

    expect(getSnapshot()).not.toBe(first)
    expect(getSnapshot()?.id).not.toBe(first?.id)
  })

  it('notifies subscribers on publish and stops after unsubscribe', () => {
    const listener = vi.fn()
    const unsubscribe = subscribe(listener)

    publish('Saved')
    expect(listener).toHaveBeenCalledTimes(1)

    unsubscribe()
    publish('Again')
    expect(listener).toHaveBeenCalledTimes(1)
  })

  it('returns a stable snapshot between publishes (useSyncExternalStore needs it)', () => {
    publish('Saved')

    expect(getSnapshot()).toBe(getSnapshot())
  })

  it('reset empties the store and notifies subscribers', () => {
    publish('Saved')
    const listener = vi.fn()
    subscribe(listener)

    resetNotifications()

    expect(getSnapshot()).toBeNull()
    expect(listener).toHaveBeenCalledTimes(1)
  })
})
