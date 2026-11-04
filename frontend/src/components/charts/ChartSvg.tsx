import type { ReactNode } from 'react'
import { Box } from '@mui/material'

/** The fixed viewBox shared by the line charts; the svg itself scales fluidly. */
export const CHART_WIDTH = 640
export const CHART_HEIGHT = 260

interface ChartSvgProps {
  /** Accessible name of the chart. */
  label: string
  children: ReactNode
}

/** The fluid, accessible `<svg>` frame of the hand-drawn line charts. */
export function ChartSvg({ label, children }: ChartSvgProps) {
  return (
    <Box
      component="svg"
      viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
      role="img"
      aria-label={label}
      sx={{ width: '100%', maxWidth: CHART_WIDTH, height: 'auto', display: 'block' }}
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
}: YGridProps) {
  return (
    <>
      {ticks.map((tick) => (
        <g key={tick}>
          <line
            x1={marginLeft}
            x2={CHART_WIDTH - marginRight}
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
