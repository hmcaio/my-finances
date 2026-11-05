/** Emotion's rules for the current document, as one string. jsdom does not evaluate `@media`, so
 * a responsive `sx` value is asserted on the emitted rule text instead of on the computed style. */
export function emittedCss() {
  return Array.from(document.querySelectorAll('style'))
    .map((s) => s.textContent)
    .join('')
}

/** The emotion class (`css-...`) of an element rendered through `sx`. */
export function emotionClass(element: Element) {
  return element.className.split(' ').find((c) => c.startsWith('css-'))
}
