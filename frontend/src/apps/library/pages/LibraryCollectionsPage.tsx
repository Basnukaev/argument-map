import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router';
import { ArrowLeft, BookOpen, Heart, Loader2, Trash2 } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import Header from '@/shared/components/layout/Header';
import { apiGetRaw, apiDeleteRaw, ApiError, formatApiError } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { useT } from '@/shared/i18n';
import type { AsyncState } from '@/shared/types/async';
import type { components } from '@/shared/api/types';

// CollectionEntryResponse - inline type (backend not yet regenerated).
// После backend restart + npm run generate-api - заменить на
// components['schemas']['CollectionEntryResponse']
interface CollectionEntry {
  id: string;
  bookId: string;
  collectionName: string;
  addedAt: string;
}
type Book = components['schemas']['BookSummaryResponse'];

interface CollectionGroup {
  name: string;
  entries: Array<CollectionEntry & { book?: Book }>;
}

/**
 * Vision 49d Section 2.2 — dedicated page /library/collections с
 * personal collections user'а. Sidebar - список collection names с
 * counts, main area - книги в selected collection.
 */
function LibraryCollectionsPage() {
  const t = useT();
  const [state, setState] = useState<AsyncState<CollectionGroup[]>>({ kind: 'loading' });
  const [selectedName, setSelectedName] = useState<string>('Избранное');
  const [removingId, setRemovingId] = useState<string | null>(null);

  const fetchAll = useCallback(async () => {
    setState({ kind: 'loading' });
    try {
      const [entries, names] = await Promise.all([
        apiGetRaw<CollectionEntry[]>('/api/v1/library/collections'),
        apiGetRaw<string[]>('/api/v1/library/collections/names'),
      ]);
      // fetch books для всех unique bookId одним walkthrough - server
      // ещё не имеет bulk book lookup, делаем по одному (acceptable
      // для small collections, future: batch endpoint)
      const uniqueBookIds = Array.from(new Set(entries.map((e) => e.bookId)));
      const bookMap = new Map<string, Book>();
      await Promise.all(
        uniqueBookIds.map(async (id) => {
          try {
            const book = await apiGetRaw<Book>(`/api/v1/library/books/${id}`);
            if (book.id) bookMap.set(book.id, book);
          } catch {
            // book mb deleted - keep entry but no book details
          }
        }),
      );
      const groups: CollectionGroup[] = names.map((name) => ({
        name,
        entries: entries
          .filter((e) => e.collectionName === name)
          .map((e) => ({ ...e, book: bookMap.get(e.bookId) })),
      }));
      // если нет ни одной collection - default empty "Избранное" group
      if (groups.length === 0) {
        groups.push({ name: 'Избранное', entries: [] });
      }
      setState({ kind: 'success', data: groups });
    } catch (e: unknown) {
      const message =
        e instanceof ApiError
          ? `${e.problem.title}${e.problem.detail ? ': ' + e.problem.detail : ''}`
          : e instanceof Error
            ? e.message
            : 'failed';
      setState({ kind: 'error', message });
    }
  }, []);

  useEffect(() => {
    void fetchAll();
  }, [fetchAll]);

  const handleRemove = useCallback(
    async (bookId: string, collectionName: string) => {
      setRemovingId(bookId);
      try {
        await apiDeleteRaw(
          `/api/v1/library/collections/${bookId}?name=${encodeURIComponent(collectionName)}`,
        );
        // optimistic: refetch минимально - просто убираем из state
        setState((prev) => {
          if (prev.kind !== 'success') return prev;
          return {
            kind: 'success',
            data: prev.data.map((g) =>
              g.name === collectionName
                ? { ...g, entries: g.entries.filter((e) => e.bookId !== bookId) }
                : g,
            ),
          };
        });
      } catch (e) {
        toast.error(formatApiError(e, t('common.error')));
      } finally {
        setRemovingId(null);
      }
    },
    [t],
  );

  const selected = useMemo(() => {
    if (state.kind !== 'success') return null;
    return state.data.find((g) => g.name === selectedName) ?? state.data[0] ?? null;
  }, [state, selectedName]);

  return (
    <main className="min-h-screen bg-bg">
      <Header />
      <div className="mx-auto max-w-[1380px] px-3 py-6 sm:px-6 sm:py-8">
        <div className="mb-6 flex items-center gap-3">
          <Link to="/books" className="text-sm text-ink-500 hover:text-accent-700">
            <span className="inline-flex items-center gap-1">
              <ArrowLeft size={14} aria-hidden /> {t('nav.library')}
            </span>
          </Link>
        </div>
        <header className="mb-6">
          <div className="flex items-center gap-2 text-xs uppercase tracking-wider text-ink-500">
            <Heart size={12} aria-hidden /> {t('library.collections.title')}
          </div>
          <h1 className="mt-1 text-2xl font-semibold text-ink-900">
            {t('library.collections.title')}
          </h1>
          <p className="mt-1 text-sm text-ink-600">{t('library.collections.subtitle')}</p>
        </header>

        {state.kind === 'loading' && (
          <div className="flex items-center gap-2 py-12 text-sm text-ink-500">
            <Loader2 size={16} className="animate-spin" aria-hidden /> {t('common.loading')}
          </div>
        )}

        {state.kind === 'error' && (
          <Card className="border-err-500/40 bg-err-100 p-5">
            <div className="text-sm text-ink-900">{state.message}</div>
          </Card>
        )}

        {state.kind === 'success' && (
          <div className="grid grid-cols-1 gap-6 md:grid-cols-[220px_1fr]">
            {/* Sidebar - collection names */}
            <aside>
              <ul className="space-y-1">
                {state.data.map((g) => (
                  <li key={g.name}>
                    <button
                      type="button"
                      onClick={() => setSelectedName(g.name)}
                      className={`flex w-full items-center justify-between rounded-md px-3 py-2 text-start text-sm transition-colors ${
                        g.name === selectedName
                          ? 'bg-accent-50 text-accent-700 font-semibold'
                          : 'text-ink-700 hover:bg-ink-100'
                      }`}
                    >
                      <span className="truncate">{g.name}</span>
                      <span className="ms-2 text-xs text-ink-500">{g.entries.length}</span>
                    </button>
                  </li>
                ))}
              </ul>
            </aside>

            {/* Main grid */}
            <section>
              {!selected || selected.entries.length === 0 ? (
                <div className="rounded-md border border-dashed border-border-strong bg-elevated/50 p-8 text-center text-sm text-ink-500">
                  {t('library.collections.empty')}
                </div>
              ) : (
                <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                  {selected.entries.map((entry) => (
                    <li key={entry.id} className="relative">
                      <button
                        type="button"
                        onClick={() => handleRemove(entry.bookId, selected.name)}
                        disabled={removingId === entry.bookId}
                        title={t('library.fav.remove')}
                        aria-label={t('library.fav.remove')}
                        className="absolute end-2 top-2 z-10 grid h-7 w-7 place-items-center rounded-sm bg-elevated/90 text-ink-600 shadow-sh1 backdrop-blur hover:text-err-500 disabled:opacity-50"
                      >
                        {removingId === entry.bookId ? (
                          <Loader2 size={13} className="animate-spin" aria-hidden />
                        ) : (
                          <Trash2 size={13} aria-hidden />
                        )}
                      </button>
                      <Link
                        to={`/books/${entry.bookId}`}
                        className="group block focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500"
                      >
                        <Card interactive className="h-full overflow-hidden">
                          <Card.Body>
                            <div className="flex items-start gap-3">
                              <BookOpen size={18} className="mt-0.5 text-ink-400" aria-hidden />
                              <div className="flex-1 min-w-0">
                                <div className="text-sm font-semibold text-ink-900 truncate" dir="auto">
                                  {entry.book?.title ?? entry.bookId}
                                </div>
                                <div className="mt-1 text-xs text-ink-500">
                                  {new Date(entry.addedAt).toLocaleDateString()}
                                </div>
                              </div>
                            </div>
                          </Card.Body>
                        </Card>
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>
        )}
      </div>
    </main>
  );
}

export default LibraryCollectionsPage;
