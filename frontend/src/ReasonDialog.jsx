import { useEffect, useRef, useState } from "react";

/** Asks for the reason behind a change. The reason is saved in the audit history. */
export default function ReasonDialog({ title, description, danger, confirmLabel = "Save change", onSubmit, onCancel }) {
  const dialog = useRef(null);
  const [reason, setReason] = useState("");

  useEffect(() => { dialog.current?.showModal(); }, []);

  const submit = (e) => {
    e.preventDefault();
    if (reason.trim()) onSubmit(reason.trim());
  };

  return (
    <dialog ref={dialog} onClose={onCancel} className="dialog">
      <form onSubmit={submit}>
        <h2>{title}</h2>
        {description && <p className={danger ? "banner" : "note"}>{description}</p>}
        <label>
          Reason for change
          <textarea
            value={reason} onChange={(e) => setReason(e.target.value)} rows={3} maxLength={500}
            placeholder="Why are you making this change?" required autoFocus
          />
          <span className="hint">Saved in the audit history with your username and the time.</span>
        </label>
        <div className="actions">
          <button type="button" className="btn" onClick={() => dialog.current?.close()}>Cancel</button>
          <button type="submit" className="btn primary" disabled={!reason.trim()}>{confirmLabel}</button>
        </div>
      </form>
    </dialog>
  );
}
