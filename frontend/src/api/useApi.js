import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * The only data-fetching hook in the app.
 *
 * Day 19 asks for "loading states, empty states and error states everywhere".
 * Written by hand that is thirty near-identical try/catch blocks, thirty
 * chances to forget the catch, and thirty screens that each fail differently.
 * Here it is one place.
 *
 *   const { data, loading, error, reload } = useApi(() => gatepass.myPasses(), []);
 *
 * `deps` behaves like useEffect's. The abort guard matters more than it looks:
 * a user who clicks three nav items in a second has three requests in flight,
 * and without it the SLOWEST one wins and paints stale data over fresh.
 */
export function useApi(fn, deps = [], { skip = false } = {}) {
  const [state, setState] = useState({ data: null, loading: !skip, error: null });
  const seq = useRef(0);

  const run = useCallback(async () => {
    if (skip) return;
    const mine = ++seq.current;
    setState((s) => ({ ...s, loading: true, error: null }));
    try {
      const data = await fn();
      if (mine === seq.current) setState({ data, loading: false, error: null });
    } catch (error) {
      if (mine === seq.current) setState({ data: null, loading: false, error });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [skip, ...deps]);

  useEffect(() => { run(); return () => { seq.current++; }; }, [run]);

  return { ...state, reload: run };
}

/**
 * Same thing, but re-fetches on an interval until `stop` says otherwise.
 *
 * Used by exactly one screen — Bulk Progress — and the stop condition is the
 * important half. A progress poller with no terminal condition is a request
 * every two seconds for as long as the tab stays open, which is how a demo
 * machine ends up with 40,000 log lines.
 */
export function usePolling(fn, { intervalMs = 2000, stop = () => false } = {}) {
  const [state, setState] = useState({ data: null, loading: true, error: null });
  const alive = useRef(true);

  useEffect(() => {
    alive.current = true;
    let timer;

    const tick = async () => {
      try {
        const data = await fn();
        if (!alive.current) return;
        setState({ data, loading: false, error: null });
        if (!stop(data)) timer = setTimeout(tick, intervalMs);
      } catch (error) {
        if (!alive.current) return;
        // A single failed poll is not a failed batch. Keep going, but slower —
        // hammering a service that just errored helps nobody.
        setState((s) => ({ ...s, loading: false, error }));
        timer = setTimeout(tick, intervalMs * 3);
      }
    };

    tick();
    return () => { alive.current = false; clearTimeout(timer); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return state;
}
