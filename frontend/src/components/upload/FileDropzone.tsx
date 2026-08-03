import { useCallback, useRef, useState } from 'react';
import { FileUp, X } from 'lucide-react';
import { Button } from '@ui/index';
import { cn } from '@lib/utils/cn';

export interface UploadRule {
  maxBytes: number;
  accept: readonly string[];
  label: string;
}

export interface FileDropzoneProps {
  rule: UploadRule;
  onSelect: (file: File) => void;
  onClear?: () => void;
  file?: File | null;
  parsing?: boolean;
  disabled?: boolean;
  className?: string;
}

/**
 * Type and size are checked here as well as on the server. The server re-checks
 * by magic bytes regardless — this exists so the user gets an actionable
 * message instead of the servlet's multipart error.
 */
export function FileDropzone({
  rule, onSelect, onClear, file, parsing, disabled, className,
}: FileDropzoneProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const validate = useCallback(
    (candidate: File): string | null => {
      if (candidate.size > rule.maxBytes) {
        return `That file is ${(candidate.size / 1024 / 1024).toFixed(1)} MB. The limit is ${rule.maxBytes / 1024 / 1024} MB.`;
      }
      if (candidate.type && !rule.accept.includes(candidate.type)) {
        return `Accepted formats: ${rule.label}.`;
      }
      return null;
    },
    [rule],
  );

  const accept = useCallback(
    (candidate: File | undefined) => {
      if (!candidate) return;
      const problem = validate(candidate);
      setError(problem);
      if (!problem) onSelect(candidate);
    },
    [validate, onSelect],
  );

  return (
    <div className={className}>
      <div
        onDragOver={(e) => {
          e.preventDefault();
          if (!disabled) setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragging(false);
          if (!disabled) accept(e.dataTransfer.files[0]);
        }}
        className={cn(
          'flex flex-col items-center justify-center gap-[var(--sp-2)] rounded-[var(--r-md)]',
          'border-2 border-dashed p-[var(--sp-8)] text-center transition-colors',
          dragging ? 'border-[var(--brand-600)] bg-[var(--brand-50)]' : 'border-[var(--border-strong)]',
          disabled && 'opacity-60',
        )}
      >
        <FileUp className="size-6 text-[var(--ink-400)]" aria-hidden />
        {file ? (
          <div className="flex items-center gap-[var(--sp-2)]">
            <span className="text-body-md text-[var(--ink-900)]">{file.name}</span>
            {onClear && (
              <Button variant="ghost" size="icon" aria-label="Remove file" onClick={onClear} disabled={parsing}>
                <X aria-hidden />
              </Button>
            )}
          </div>
        ) : (
          <>
            <p className="text-body text-[var(--ink-700)]">Drop a file here, or</p>
            <Button
              variant="secondary"
              size="sm"
              disabled={disabled}
              onClick={() => inputRef.current?.click()}
            >
              Choose a file
            </Button>
          </>
        )}
        <p className="text-caption text-[var(--ink-500)]">{rule.label}</p>
        <input
          ref={inputRef}
          type="file"
          className="sr-only"
          accept={rule.accept.join(',')}
          disabled={disabled}
          onChange={(e) => accept(e.target.files?.[0])}
        />
      </div>
      {error && (
        <p role="alert" className="text-caption mt-[var(--sp-2)] text-[var(--deny-fg)]">
          {error}
        </p>
      )}
    </div>
  );
}
