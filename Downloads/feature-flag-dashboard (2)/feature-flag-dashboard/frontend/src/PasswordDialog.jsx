import { useEffect, useRef, useState } from "react";

/** Sets a password. With askCurrent it is the user's own password change; without, an administrator's reset. */
export default function PasswordDialog({ title, description, askCurrent, onSubmit, onClose }) {
  const dialog = useRef(null);
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => { dialog.current?.showModal(); }, []);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await onSubmit({ current, next });
    } catch (err) {
      setError(Object.values(err.fieldErrors || {})[0] || err.message);
      setBusy(false);
    }
  };

  return (
    <dialog ref={dialog} onClose={onClose} className="dialog">
      <form onSubmit={submit}>
        <h2>{title}</h2>
        {description && <p className="note">{description}</p>}
        {askCurrent && (
          <label>
            Current password
            <input type="password" value={current} onChange={(e) => setCurrent(e.target.value)} autoComplete="current-password" required autoFocus />
          </label>
        )}
        <label>
          New password
          <input type="password" value={next} onChange={(e) => setNext(e.target.value)} autoComplete="new-password" minLength={10} maxLength={72} required autoFocus={!askCurrent} />
          <span className="hint">At least 10 characters.</span>
        </label>
        {error && <p className="banner" role="alert">{error}</p>}
        <div className="actions">
          <button type="button" className="btn" onClick={() => dialog.current?.close()}>Cancel</button>
          <button type="submit" className="btn primary" disabled={busy}>{busy ? "Saving…" : "Save password"}</button>
        </div>
      </form>
    </dialog>
  );
}
