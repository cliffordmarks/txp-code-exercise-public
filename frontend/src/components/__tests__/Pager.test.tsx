import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Pager } from '../Pager';

describe('Pager', () => {
  it('renders nothing when there is only one page', () => {
    const { container } = render(<Pager page={0} totalPages={1} onPageChange={vi.fn()} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('disables Previous on the first page and calls onPageChange for Next', () => {
    const onPageChange = vi.fn();
    render(<Pager page={0} totalPages={3} onPageChange={onPageChange} />);

    expect((screen.getByRole('button', { name: 'Previous' }) as HTMLButtonElement).disabled).toBe(true);
    fireEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(onPageChange).toHaveBeenCalledWith(1);
    expect(screen.getByText('Page 1 of 3')).toBeTruthy();
  });

  it('disables Next on the last page', () => {
    render(<Pager page={2} totalPages={3} onPageChange={vi.fn()} />);
    expect((screen.getByRole('button', { name: 'Next' }) as HTMLButtonElement).disabled).toBe(true);
  });
});