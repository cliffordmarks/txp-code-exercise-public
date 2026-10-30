import { useState, useCallback, useEffect } from 'react';
import { api } from '../services/api';
import type { ShortenUrlResponse, UrlListItem } from '../types/api';

const PAGE_SIZE = 10;

interface UseUrlShortenerReturn {
  urls: UrlListItem[];
  page: number; // zero-based
  pageSize: number;
  total: number;
  loading: boolean;
  error: string | null;
  lastCreated: ShortenUrlResponse | null;
  shorten: (fullUrl: string, customAlias?: string) => Promise<void>;
  deleteUrl: (alias: string) => Promise<void>;
  goToPage: (page: number) => Promise<void>;
  clearLastCreated: () => void;
}

export function useUrlShortener(): UseUrlShortenerReturn {
  const [urls, setUrls] = useState<UrlListItem[]>([]);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastCreated, setLastCreated] = useState<ShortenUrlResponse | null>(null);

  const fetchPage = useCallback(async (requested: number) => {
    setLoading(true);
    setError(null);
    try {
      let target = requested;
      let result = await api.listPage(target, PAGE_SIZE);

      // The page may no longer exist (e.g. the last item on it was deleted): step back
      const lastPage = Math.max(Math.ceil(result.total / PAGE_SIZE) - 1, 0);
      if (target > lastPage) {
        target = lastPage;
        result = await api.listPage(target, PAGE_SIZE);
      }

      setUrls(result.items);
      setTotal(result.total);
      setPage(target);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load URLs.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchPage(0);
  }, [fetchPage]);

  const shorten = useCallback(async (fullUrl: string, customAlias?: string) => {
    setError(null);
    setLastCreated(null);
    try {
      const result = await api.shorten({ fullUrl, customAlias: customAlias || undefined });
      setLastCreated(result);
      // Newest links come first, so jump to page 1 to show the new one
      await fetchPage(0);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to shorten URL.');
      // If you added `throw e;` here (so the form keeps its input on failure), keep it
    }
  }, [fetchPage]);

  const deleteUrl = useCallback(async (alias: string) => {
    setError(null);
    try {
      await api.delete(alias);
      // Reload the current page so the total, the page contents and the page count stay correct
      await fetchPage(page);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to delete URL.');
    }
  }, [fetchPage, page]);

  const goToPage = useCallback((target: number) => fetchPage(Math.max(target, 0)), [fetchPage]);

  const clearLastCreated = useCallback(() => setLastCreated(null), []);

  return {
    urls, page, pageSize: PAGE_SIZE, total, loading, error, lastCreated,
    shorten, deleteUrl, goToPage, clearLastCreated,
  };
}