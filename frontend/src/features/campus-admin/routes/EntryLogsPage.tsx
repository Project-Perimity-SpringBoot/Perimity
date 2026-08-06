import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import dayjs from 'dayjs';
import { Badge, Input, NativeSelect } from '@ui/index';
import { DataTable, PageHeader, SearchFilterBar, StatCard } from '@components/data';
import { ErrorState } from '@components/feedback';
import { entryLogApi } from '@lib/api/services/guard.api';
import { guardKeys } from '@lib/query/keys';
import { MAX_ENTRY_LOG_RANGE_DAYS, formatDateTime, toServerDateTime } from '@lib/format/datetime';
import { SCAN_RESULTS, type ScanResult } from '@/types/enums';
import type { EntryLogFilterRequest, EntryLogResponse } from '@/types/guard.types';
import { useAuth } from '@hooks/useAuth';
import { useDebouncedValue } from '@hooks/useDebouncedValue';
import { useUrlPagination } from '@hooks/useUrlPagination';

const isScanResult = (value: string): value is ScanResult =>
  (SCAN_RESULTS as readonly string[]).includes(value);

/**
 * Campus-wide entry logs.
 *
 * ENTRY ONLY. No exit column, no duration, no direction — this product does not
 * scan people out, and a column implying otherwise would be a lie in a table
 * people export and quote.
 *
 * The 90-day cap is enforced server-side by an @AssertTrue on the filter DTO.
 * It is mirrored here so an admin who picks a wider range is told before the
 * round trip rather than by a 400 naming a DTO.
 */
export default function EntryLogsPage() {
  const { campusId } = useAuth();
  /*
   * 10 rows a page.
   *
   * The pager hides itself while there is only one page, so at the old size of
   * 50 the table just grew as entries accumulated and no controls ever
   * appeared - it read as missing pagination rather than as one long page. At
   * 10 the second page shows up as soon as there is one, and the screen stops
   * growing after that.
   *
   * Paging is by click. Nothing loads more on scroll, so the table stays the
   * same height however many entries the campus collects.
   *
   * The server default stays 50 for callers that do not ask - this is a
   * screen-level choice, sent as ?size=.
   */
  const { request: pageRequest, params, setPage, setFilter } = useUrlPagination(10);
  const [search, setSearch] = useState('');
  const debounced = useDebouncedValue(search, 300);

  const defaultFrom = dayjs().subtract(7, 'day').startOf('day');
  const from = params.get('from') ?? defaultFrom.format('YYYY-MM-DD');
  const to = params.get('to') ?? dayjs().format('YYYY-MM-DD');
  const resultParam = params.get('result') ?? '';

  const spanDays = dayjs(to).diff(dayjs(from), 'day');
  const rangeValid = spanDays >= 0 && spanDays <= MAX_ENTRY_LOG_RANGE_DAYS;

  const filter: EntryLogFilterRequest = {
    campusId: campusId ?? 0,
    from: toServerDateTime(dayjs(from).startOf('day')),
    to: toServerDateTime(dayjs(to).endOf('day')),
    ...(isScanResult(resultParam) ? { scanResult: resultParam } : {}),
    ...(debounced.trim() ? { query: debounced.trim() } : {}),
  };

  const enabled = campusId !== null && rangeValid;

  const logs = useQuery({
    queryKey: guardKeys.entryLogSearch(filter, pageRequest),
    queryFn: () => entryLogApi.search(filter, pageRequest),
    enabled,
  });

  const stats = useQuery({
    queryKey: guardKeys.entryLogStats(filter),
    queryFn: () => entryLogApi.stats(filter),
    enabled,
  });

  /*
   * The server already applied the search, so this is just what came back.
   *
   * The filter that used to live here ran over the loaded page only, which was
   * a quiet lie: it reported "no results" for people who were in the register
   * but not on the page you happened to be looking at.
   */
  const visible = logs.data?.items ?? [];

  /*
   * A new term starts at page one. Without this, searching while on page 3
   * asks for the third page of a result set that may only have one, and the
   * table comes back empty for a term that does match.
   */
  useEffect(() => {
    setPage(0);
  }, [debounced]);

  const columns: ColumnDef<EntryLogResponse, unknown>[] = [
    {
      id: 'scannedAt',
      header: 'When',
      accessorKey: 'scannedAt',
      cell: (info) => formatDateTime(info.row.original.scannedAt),
    },
    { id: 'holderName', header: 'Person', cell: (info) => info.row.original.holderName ?? '—' },
    { id: 'gateName', header: 'Gate', accessorKey: 'gateName' },
    { id: 'passType', header: 'Pass', cell: (info) => info.row.original.passType ?? '—' },
    {
      id: 'eventAttributed',
      header: 'Event',
      cell: (info) => (info.row.original.eventAttributed ? 'Attributed' : '—'),
    },
    {
      id: 'scanResult',
      header: 'Result',
      cell: (info) => {
        const log = info.row.original;
        return (
          <Badge>
            {log.scanResult === 'DENIED'
              ? `Refused · ${(log.denialReason ?? '').replace(/_/g, ' ').toLowerCase()}`
              : log.scanResult === 'AMBER'
                ? 'Repeat entry'
                : 'Allowed'}
          </Badge>
        );
      },
    },
  ];

  if (logs.isError) return <ErrorState error={logs.error} onRetry={() => void logs.refetch()} />;

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        title="Entry logs"
        description="Every scan at every gate. Entries only — this product does not scan people out."
      />

      <div className="grid gap-[var(--sp-4)] sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Entries permitted" value={stats.data?.entriesPermitted ?? null} loading={stats.isPending} />
        <StatCard label="Allowed" value={stats.data?.allowedCount ?? null} loading={stats.isPending} />
        <StatCard label="Repeat scans" value={stats.data?.amberCount ?? null} loading={stats.isPending}
                  hint="Seen already that day. Still recorded as an entry." />
        <StatCard label="Refused" value={stats.data?.deniedCount ?? null} loading={stats.isPending}
                  hint="A spike usually means a configuration change, not an incident." />
      </div>

      <SearchFilterBar
        value={search}
        onChange={setSearch}
        placeholder="Search person or gate"
        resultCount={visible.length}
        filters={
          <>
            <Input
              type="date" aria-label="From" className="sm:w-40" value={from}
              onChange={(event) => setFilter('from', event.target.value)}
            />
            <Input
              type="date" aria-label="To" className="sm:w-40" value={to}
              onChange={(event) => setFilter('to', event.target.value)}
            />
            <NativeSelect
              aria-label="Filter by result" className="sm:w-40" value={resultParam}
              onChange={(event) => setFilter('result', event.target.value || null)}
            >
              <option value="">All results</option>
              {SCAN_RESULTS.map((value) => (
                <option key={value} value={value}>
                  {value === 'AMBER' ? 'Repeat entry' : value.charAt(0) + value.slice(1).toLowerCase()}
                </option>
              ))}
            </NativeSelect>
          </>
        }
      />

      {!rangeValid ? (
        <p className="text-body rounded-[var(--r-sm)] border border-[var(--border-strong)] bg-[var(--surface-subtle)] px-[var(--sp-4)] py-[var(--sp-3)] text-[var(--ink-700)]">
          {spanDays < 0
            ? 'The end date is before the start date.'
            : `Entry logs are kept for ${MAX_ENTRY_LOG_RANGE_DAYS} days, so a range may not be longer than that. Narrow the dates to search.`}
        </p>
      ) : (
        <div className="surface-card overflow-hidden">
          <DataTable
            columns={columns}
            data={visible}
            loading={logs.isPending}
            {...(logs.data ? { page: logs.data } : {})}
            onPageChange={setPage}
            mobilePrimaryColumn="scannedAt"
            getRowId={(row) => row.id}
            emptyHeading="No entries in this period"
            emptyDescription="Widen the dates, or clear the result filter."
          />
        </div>
      )}
    </div>
  );
}
