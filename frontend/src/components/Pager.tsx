interface PagerProps {
  page: number; // zero-based
  totalPages: number;
  onPageChange: (page: number) => void;
  disabled?: boolean;
}

export function Pager({ page, totalPages, onPageChange, disabled = false }: PagerProps) {
  if (totalPages <= 1) return null;

  return (
    <nav className="pager" aria-label="Pagination">
      <button
        type="button"
        className="btn-secondary"
        onClick={() => onPageChange(page - 1)}
        disabled={disabled || page === 0}
      >
        Previous
      </button>
      <span className="pager__status" aria-live="polite">
        Page {page + 1} of {totalPages}
      </span>
      <button
        type="button"
        className="btn-secondary"
        onClick={() => onPageChange(page + 1)}
        disabled={disabled || page >= totalPages - 1}
      >
        Next
      </button>
    </nav>
  );
}