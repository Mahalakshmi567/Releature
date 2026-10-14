import { useEffect, useRef, useState } from "react";
import { getRollbackPreview, rollbackEntry } from "./api.js";
import ConfigDiff from "./ConfigDiff.jsx";

export default function RollbackDialog({ entry, onClose, onDone }) {
  const dialog = useRef(null);
  const [preview, setPreview] = useState(null);
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => { dialog.current?.showModal(); }, []);

  useEffect(() => {
    getRollbackPreview(entry.id).then(setPreview).catch((e) => setError(e.message));
  }, [entry.id]);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await rollbackEntry(entry.id, reason.trim());
      onDone();
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  };

  const production = entry.environment === "production";
  const scope = entry.environment ? ` in ${entry.environment}` : "";

  return (
    <dialog ref={dialog} onClose={onClose} className="dialog">
      <form onSubmit={submit}>
        <h2>Restore previous configuration</h2>
        <p className="sub">{entry.flagName}{scope}: put it back as it was before this change by {entry.actor}.</p>

        {!preview && !error && <p className="hint">Checking the current configuration…</p>}

        {preview && !preview.possible && <p className="banner" role="alert">{preview.problem}</p>}

        {preview?.possible && (
          <>
            <div className="section-title">What will change</div>
            <ConfigDiff previous={preview.current} next={preview.restoreTo} />

            {preview.changedSince && (
              <p className="banner">
                This configuration has changed since that entry. Restoring replaces the whole configuration, so it also undoes those later changes.
              </p>
            )}
            {production && <p className="banner">This is Production. The change applies to live users.</p>}
            <p className="note">
              This restores configuration only. It doesn't undo anything the feature already did while it was on,
              such as data created for users. The restore is added to the audit history as a new entry; the original entry stays.
            </p>

            <label>
              Reason for change
              <textarea
                value={reason} onChange={(e) => setReason(e.target.value)} rows={3} maxLength={500} required
                placeholder="Why are you restoring this configuration?"
              />
            </label>
          </>
        )}

        {error && <p className="banner" role="alert">{error}</p>}

        <div className="actions">
          <button type="button" className="btn" onClick={() => dialog.current?.close()}>
            {preview?.possible ? "Cancel" : "Close"}
          </button>
          {preview?.possible && (
            <button type="submit" className="btn primary" disabled={busy || !reason.trim()}>
              {busy ? "Restoring…" : "Restore configuration"}
            </button>
          )}
        </div>
      </form>
    </dialog>
  );
}
