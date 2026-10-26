import type { UrlListItem } from '../types/api';

interface UrlTableProps {
  urls: UrlListItem[];
  onDelete: (alias: string) => void;
}

export function UrlTable({ urls, onDelete }: UrlTableProps) {
  // TODO: Build URL table UI and wire delete actions.
  void urls;
  void onDelete;

  return (
    <div className="empty-state">
      <p>TODO: Display shortened URLs here.</p>
    </div>
  );
}
