import type { ReactNode } from 'react'
import { Box } from '@mui/material'

/** The fixed viewBox shared by the line charts; the svg itself scales fluidly. */
export const CHART_WIDTH = 640
export const CHART_HEIGHT = 260

interface ChartSvgProps {
  /** Accessible name of the chart. */
  label: string
  children: ReactNode
  /**
   * A chart that measures its container (`useElementWidth`) passes its own viewBox size and fills
   * the width: it is drawn 1:1, so text and strokes keep their size at any width. Without these the
   * frame is the fixed `CHART_WIDTH` x `CHART_HEIGHT`, scaled down to fit and capped at that width.
   */
  width?: number
  height?: number
  fillWidth?: boolean
}

/** The fluid, accessible `<svg>` frame of the hand-drawn line charts. */
export function ChartSvg({
  label,
  children,
  width = CHART_WIDTH,
  height = CHART_HEIGHT,
  fillWidth = false,
}: ChartSvgProps) {
  return (
    <Box
      component="svg"
      viewBox={`0 0 ${width} ${height}`}
      role="img"
      aria-label={label}
      sx={{
        width: '100%',
        maxWidth: fillWidth ? 'none' : CHART_WIDTH,
        height: 'auto',
        display: 'block',
      }}
    >
      {children}
    </Box>
  )
}

interface YGridProps {
  ticks: number[]
  y: (value: number) => number
  marginLeft: number
  marginRight: number
  gridColor: string
  axisColor: string
  formatTick: (value: number) => string
  /** The frame's viewBox width; defaults to `CHART_WIDTH`. */
  width?: number
}

/** Horizontal gridlines with a right-aligned value label at the left of each. */
export function YGrid({
  ticks,
  y,
  marginLeft,
  marginRight,
  gridColor,
  axisColor,
  formatTick,
  width = CHART_WIDTH,
}: YGridProps) {
  return (
    <>
      {ticks.map((tick) => (
        <g key={tick}>
          <line
            x1={marginLeft}
            x2={width - marginRight}
            y1={y(tick)}
            y2={y(tick)}
            stroke={gridColor}
          />
          <text
            x={marginLeft - 8}
            y={y(tick)}
            textAnchor="end"
            dominantBaseline="middle"
            fontSize="11"
            fill={axisColor}
          >
            {formatTick(tick)}
          </text>
        </g>
      ))}
    </>
  )
}
