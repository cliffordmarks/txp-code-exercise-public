import type { UrlListItem } from '../types/api';

interface UrlTableProps {
  urls: UrlListItem[];
  onDelete: (alias: string) => void;
}

export function UrlTable({ urls, onDelete }: UrlTableProps) {
  if (urls.length === 0) {
    return (
      <div className="empty-state">
        <p>No shortened URLs yet. Create one above.</p>
      </div>
    );
  }

  return (
    <div className="url-table-wrapper">
      <table className="url-table">
        <thead>
          <tr>
            <th scope="col">Short URL</th>
            <th scope="col">Original URL</th>
            <th scope="col"><span className="sr-only">Actions</span></th>
          </tr>
        </thead>
        <tbody>
          {urls.map((u) => (
            <tr key={u.alias}>
              <td>
                <a href={u.shortUrl} target="_blank" rel="noopener noreferrer" className="short-url-link">
                  {u.shortUrl}
                </a>
              </td>
              <td className="full-url" title={u.fullUrl}>{u.fullUrl}</td>
              <td className="actions-cell">
                <button
                  type="button"
                  className="btn-danger"
                  onClick={() => onDelete(u.alias)}
                  aria-label={`Delete ${u.alias}`}
                >
                  Delete
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}