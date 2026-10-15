import { useEffect } from "react";

const VERBS = {
  FLAG_CREATED: "created",
  FLAG_UPDATED: "edited",
  FLAG_DELETED: "deleted",
  ENVIRONMENT_UPDATED: "changed",
  TARGETING_UPDATED: "changed targeting for",
};

function describe(c) {
  const verb = c.rollback ? "restored" : VERBS[c.action] || "changed";
  const where = c.environment ? ` in ${c.environment}` : "";
  return `${c.actor} ${verb} ${c.flagName}${where}`;
}

function Toast({ change, onDismiss }) {
  useEffect(() => {
    const t = setTimeout(onDismiss, 10000);
    return () => clearTimeout(t);
  }, [onDismiss]);
  return (
    <div className="toast" role="status">
      <div>
        <strong>{describe(change)}</strong>
        <div className="muted">{change.reason}</div>
      </div>
      <button className="link" onClick={onDismiss} aria-label="Dismiss">✕</button>
    </div>
  );
}

/** Pop-ups for changes other people make while you have the dashboard open. */
export default function ChangeToasts({ notices, onDismiss }) {
  if (notices.length === 0) return null;
  return (
    <div className="toasts" aria-live="polite">
      {notices.map((c) => <Toast key={c.id} change={c} onDismiss={() => onDismiss(c.id)} />)}
    </div>
  );
}
