import type { ReactNode } from 'react'
import { TableContainer, type TableContainerProps } from '@mui/material'
import { cn } from '../../lib/utils'

/**
 * Glass shell for MUI Table / TablePagination stacks — matches `.hokeka-table-wrap` tokens.
 */
export default function HokekaMuiTable({
  className,
  children,
  ...props
}: TableContainerProps & { children: ReactNode }) {
  return (
    <TableContainer
      className={cn('hokeka-mui-table-shell', className)}
      {...props}
    >
      {children}
    </TableContainer>
  )
}
