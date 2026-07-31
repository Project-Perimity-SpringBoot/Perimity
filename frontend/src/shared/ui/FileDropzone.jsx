import { useRef, useState } from 'react';

/** Idle, dragging, selected, parsing, error - all five, because a bulk upload
 *  that silently does nothing is the single most reported "bug" in any admin
 *  tool ever built. */
export default function FileDropzone({
  accept, maxMb = 5, onFile, file, parsing, error, hint,
}) {
  const [over, setOver] = useState(false);
  const input = useRef(null);

  const take = (f) => {
    if (!f) return;
    if (f.size > maxMb * 1024 * 1024) {
      onFile?.(null, `That file is ${(f.size / 1024 / 1024).toFixed(1)} MB. The maximum is ${maxMb} MB.`);
      return;
    }
    onFile?.(f, null);
  };

  return (
    <div>
      <div
        className={`p-drop ${over ? 'p-drop--over' : ''} ${error ? 'p-drop--error' : ''}`}
        onDragOver={(e) => { e.preventDefault(); setOver(true); }}
        onDragLeave={() => setOver(false)}
        onDrop={(e) => { e.preventDefault(); setOver(false); take(e.dataTransfer.files?.[0]); }}
        onClick={() => input.current?.click()}
        role="button" tabIndex={0}
        onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && input.current?.click()}
      >
        <input ref={input} type="file" accept={accept} hidden
               onChange={(e) => take(e.target.files?.[0])} />

        {parsing ? (
          <div className="p-stack" style={{ alignItems: 'center' }}>
            <span className="p-spin" style={{ color: 'var(--brand-600)' }} aria-hidden />
            <span className="p-small">Checking rows…</span>
          </div>
        ) : file ? (
          <div className="p-stack" style={{ alignItems: 'center', gap: 'var(--s-1)' }}>
            <span className="p-h3">{file.name}</span>
            <span className="p-caption">{(file.size / 1024).toFixed(0)} KB · click to replace</span>
          </div>
        ) : (
          <div className="p-stack" style={{ alignItems: 'center', gap: 'var(--s-1)' }}>
            <span className="p-h3">{over ? 'Drop it here' : 'Drag a file here, or click to choose'}</span>
            <span className="p-caption">{hint ?? `Maximum ${maxMb} MB`}</span>
          </div>
        )}
      </div>
      {error && <span className="p-field__error" style={{ marginTop: 'var(--s-2)', display: 'block' }}>{error}</span>}
    </div>
  );
}
