import { Box, Typography } from '@mui/material'

interface ComingSoonProps {
  title: string
}

/**
 * Generic placeholder for a route whose feature area hasn't been built yet (F002+ each
 * replace their own route's placeholder with the real screen - see F001 spec's route table).
 */
export function ComingSoon({ title }: ComingSoonProps) {
  return (
    <Box sx={{ py: 8, textAlign: 'center' }}>
      <Typography variant="h5" component="h1" gutterBottom>
        {title}
      </Typography>
      <Typography color="text.secondary">Coming soon.</Typography>
    </Box>
  )
}
