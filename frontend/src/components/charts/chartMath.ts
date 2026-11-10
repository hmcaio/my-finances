import { CHART_HEIGHT, CHART_WIDTH } from './ChartSvg'

const DAY_MS = 24 * 60 * 60 * 1000

/** Rounds a raw axis range up to a "nice" tick step (1, 2, 5 x 10^n). */
export function niceStep(range: number, ticks: number): number {
  const rough = range / ticks
  const magnitude = 10 ** Math.floor(Math.log10(rough))
  const normalized = rough / magnitude
  const factor = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10
  return factor * magnitude
}

/** `YYYY-MM-DD` as a day count, timezone-free (parsed as UTC on purpose: only differences matter). */
export function dayNumber(date: string): number {
  return Date.parse(`${date}T00:00:00Z`) / DAY_MS
}

export interface YScaleOptions {
  /** Smallest and largest raw value the chart must show (callers add a 0 baseline themselves). */
  min: number
  max: number
  /** Total svg height and the top/bottom margins that bound the plot area. */
  height: number
  margin: { top: number; bottom: number }
  /** Approximate number of gridlines (default 4). */
  tickCount?: number
}

export interface YScale {
  step: number
  /** The nice axis bounds the raw range was widened to. */
  top: number
  bottom: number
  /** Gridline values, `bottom` to `top`. */
  ticks: number[]
  /** Value to svg y coordinate. */
  y: (value: number) => number
}

/** The shared vertical axis of the line charts: nice bounds, gridline values and the y mapping. */
export function createYScale({ min, max, height, margin, tickCount = 4 }: YScaleOptions): YScale {
  const step = niceStep(max - min || 1, tickCount)
  const top = Math.ceil(max / step) * step
  const bottom = Math.floor(min / step) * step
  const innerHeight = height - margin.top - margin.bottom
  const y = (v: number) => margin.top + ((top - v) / (top - bottom || 1)) * innerHeight
  const ticks: number[] = []
  for (let tick = bottom; tick <= top + step / 2; tick += step) ticks.push(tick)
  return { step, top, bottom, ticks, y }
}

/**
 * Date (`YYYY-MM-DD`) to svg x coordinate, proportional to elapsed days between `firstDate` and
 * `lastDate`. A zero span (a single date, or all points on one day) sits in the middle.
 */
export function createDayScale(
  firstDate: string,
  lastDate: string,
  left: number,
  innerWidth: number,
): (date: string) => number {
  const firstDay = dayNumber(firstDate)
  const span = dayNumber(lastDate) - firstDay
  return (date) =>
    left + (span === 0 ? innerWidth / 2 : ((dayNumber(date) - firstDay) / span) * innerWidth)
}

/** A container-fitted drawing never gets narrower than this, so the plot stays readable. */
const MIN_CHART_WIDTH = 280
/** The fitted drawing is about this tall per unit of width, within the bounds below. */
const HEIGHT_RATIO = 0.35
const MAX_CHART_HEIGHT = 400

/**
 * The viewBox size of a chart that fills its container (`useElementWidth`, `ChartSvg`'s
 * `fillWidth`): the measured width and a height that follows it, never below the default frame's
 * or above 400. `null` (not measured yet, or no `ResizeObserver`) keeps the default 640 by 260.
 */
export function fitChartSize(availableWidth: number | null): { width: number; height: number } {
  if (availableWidth === null) return { width: CHART_WIDTH, height: CHART_HEIGHT }
  const width = Math.max(MIN_CHART_WIDTH, availableWidth)
  const height = Math.min(
    MAX_CHART_HEIGHT,
    Math.max(CHART_HEIGHT, Math.round(width * HEIGHT_RATIO)),
  )
  return { width, height }
}
