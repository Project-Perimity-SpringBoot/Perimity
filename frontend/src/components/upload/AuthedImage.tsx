import { useEffect, useState } from 'react';
import { userClient } from '@lib/api/client';
import { needsToken } from '@lib/api/download';
import { cn } from '@lib/utils/cn';

/**
 * An <img> for a URL that needs the user's token.
 *
 * ==========================================================================
 * WHY THIS HAS TO EXIST
 * ==========================================================================
 * In development, storage is local and a photo URL points at
 * /api/user/storage/local/**, which sits behind the JWT filter — deliberately,
 * because that directory holds people's photographs and identity documents,
 * and an open endpoint over it means anyone who learns a key can read somebody
 * else's ID proof. Keys travel in ordinary API responses, so that is not a
 * remote possibility.
 *
 * A browser does not attach an Authorization header to <img src="...">. So a
 * plain img against that URL is an unauthenticated request, gets a 401 and
 * renders as a broken image. LocalStorageController's own javadoc says exactly
 * this and says the frontend must fetch the bytes with the API client and turn
 * them into a blob URL. That step was never built.
 *
 * It went unnoticed because the only screen showing a photo used Avatar, which
 * silently falls back to initials when the image fails — so a broken photo
 * looked like a student who simply had not uploaded one.
 *
 * ==========================================================================
 * S3 MODE MUST STILL WORK
 * ==========================================================================
 * When STORAGE_TYPE=s3 the URL is a real presigned S3 link on another origin.
 * It carries its own signature, needs no token, and must NOT be fetched
 * through userClient — that would attach our Authorization header to a request
 * to Amazon and, more practically, break on CORS.
 *
 * So: relative URLs are fetched with the token; absolute URLs are handed
 * straight to the img. needsToken decides.
 */
/*
 * needsToken lives in lib/api/download.ts. The Documents page needs the same
 * rule to decide whether it can point a tab straight at a URL, and two copies
 * of it would eventually disagree.
 */

export interface AuthedImageProps {
  /** The presigned or local URL. Null while it is still being fetched. */
  url: string | null | undefined;
  alt: string;
  className?: string;
  /** Rendered when there is no URL, or the fetch failed. */
  fallback?: React.ReactNode;
}

export function AuthedImage({ url, alt, className, fallback = null }: AuthedImageProps) {
  const [src, setSrc] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (!url) { setSrc(null); return undefined; }

    if (!needsToken(url)) { setSrc(url); setFailed(false); return undefined; }

    let objectUrl: string | null = null;
    let cancelled = false;

    void (async () => {
      try {
        /*
         * The URL is absolute against the user-service origin, and userClient
         * already has that baseURL. Passing the full URL to axios is fine — it
         * ignores baseURL when the argument is absolute — and the request
         * interceptor still attaches the token either way.
         */
        const response = await userClient.get<Blob>(url, { responseType: 'blob' });
        if (cancelled) return;
        objectUrl = URL.createObjectURL(response.data);
        setSrc(objectUrl);
        setFailed(false);
      } catch {
        if (!cancelled) { setSrc(null); setFailed(true); }
      }
    })();

    return () => {
      cancelled = true;
      // Without this every re-render leaks a blob for the lifetime of the tab.
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [url]);

  if (!url || failed || !src) {
    return <>{fallback}</>;
  }

  return <img src={src} alt={alt} className={cn('object-cover', className)} />;
}
