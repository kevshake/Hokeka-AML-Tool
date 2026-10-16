import TwPagination from "./TwPagination";

interface Props {
  page: number;
  totalPages: number;
  totalCount: number;
  rowsPerPage: number;
  nextCursor?: string | null;
  onPageChange: (page: number, cursor?: string | null) => void;
  onRowsPerPageChange: (size: number) => void;
}

/**
 * Pagination control that preserves numeric pages while passing opaque keyset cursors on forward navigation.
 */
export default function TwKeysetPagination({
  page,
  totalPages,
  totalCount,
  rowsPerPage,
  nextCursor,
  onPageChange,
  onRowsPerPageChange,
}: Props) {
  return (
    <TwPagination
      page={page}
      totalPages={totalPages}
      totalCount={totalCount}
      rowsPerPage={rowsPerPage}
      onPageChange={(nextPage) => {
        if (nextPage > page && nextCursor) {
          onPageChange(nextPage, nextCursor);
        } else {
          onPageChange(nextPage, null);
        }
      }}
      onRowsPerPageChange={onRowsPerPageChange}
    />
  );
}
