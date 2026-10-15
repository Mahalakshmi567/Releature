import { useEffect, useState } from "react";
import { listAudit, listEnvironments, listFlags } from "./api.js";
import ConfigDiff from "./ConfigDiff.jsx";
import RollbackDialog from "./RollbackDialog.jsx";
import { permissions } from "./permissions.js";

const ACTIONS = {
  FLAG_CREATED: "Flag created",
  FLAG_UPDATED: "Flag edited",
  FLAG_DELETED: "Flag deleted",
  ENVIRONMENT_UPDATED: "Environment changed",
  TARGETING_UPDATED: "Targeting changed",
};

// Created and deleted flags can't be restored from history.
const RESTORABLE = new Set(["FLAG_UPDATED", "ENVIRONMENT_UPDATED", "TARGETING_UPDATED"]);

const when = (iso) => new Date(iso).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });

export default function AuditPage({ user }) {
  const can = permissions(user.role);
  const [flags, setFlags] = useState([]);
  const [environments, setEnvironments] = useState([]);
  const [flagKey, setFlagKey] = useState("");
  const [environment, setEnvironment] = useState("");
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [error, setError] = useState("");
  const [version, setVersion] = useState(0); // bumped to reload after a rollback
  const [restoring, setRestoring] = useState(null);

  useEffect(() => {
    Promise.all([listFlags(), listEnvironments()])
      .then(([f, e]) => { setFlags(f); setEnvironments(e); })
      .catch((e) => setError(e.message));
  }, []);

  useEffect(() => {
    listAudit({ flagKey, environment, page })
      .then((d) => { setData(d); setError(""); })
      .catch((e) => setError(e.message));
  }, [flagKey, environment, page, version]);

  const pages = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1;

  return (
    <main className="page">
      <header className="top">
        <div>
          <h1>Audit history</h1>
          <p className="sub">Who changed what, when, and why. Entries can't be edited or deleted; a rollback adds a new entry.</p>
        </div>
      </header>

      <div className="filters">
        <label>
          Flag
          <select value={flagKey} onChange={(e) => { setFlagKey(e.target.value); setPage(0); }}>
            <option value="">All flags</option>
            {flags.map((f) => <option key={f.id} value={f.key}>{f.name}</option>)}
          </select>
        </label>
        <label>
          Environment
          <select value={environment} onChange={(e) => { setEnvironment(e.target.value); setPage(0); }}>
            <option value="">All environments</option>
            {environments.map((e) => <option key={e.key} value={e.key}>{e.name}</option>)}
          </select>
        </label>
      </div>

      {error && <p className="banner" role="alert">{error}</p>}

      {!data ? (
        <p className="empty">Loading history…</p>
      ) : data.items.length === 0 ? (
        <p className="empty">No changes recorded yet.</p>
      ) : (
        <>
          <ul className="audit-list">
            {data.items.map((e) => (
              <li className="audit-item" key={e.id}>
                <div className="audit-head">
                  <span className="badge">{ACTIONS[e.action] || e.action}</span>
                  {e.rollbackOf && <span className="badge rollback">Rollback</span>}
                  <strong>{e.flagName}</strong>
                  <code>{e.flagKey}</code>
                  {e.environment && <span className="muted">in {e.environment}</span>}
                </div>
                <div className="muted">{e.actor} · {when(e.createdAt)}</div>
                {e.rollbackOf && (
                  <div className="muted">
                    Restored the configuration from before the change by {e.rollbackOf.actor} on {when(e.rollbackOf.createdAt)}.
                  </div>
                )}
                <p className="reason"><span className="muted">Reason:</span> {e.reason}</p>
                <ConfigDiff previous={e.previous} next={e.newConfig} />
                {RESTORABLE.has(e.action) && e.previous != null && can.editFlags && (!e.environment || can.changeEnvironment(e.environment)) && (
                  <div className="audit-actions">
                    <button className="link" onClick={() => setRestoring(e)}>Restore previous configuration</button>
                  </div>
                )}
              </li>
            ))}
          </ul>
          <div className="pager">
            <button className="btn" disabled={page === 0} onClick={() => setPage(page - 1)}>Newer</button>
            <span className="muted">Page {page + 1} of {pages}</span>
            <button className="btn" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Older</button>
          </div>
        </>
      )}

      {restoring && (
        <RollbackDialog
          entry={restoring}
          onClose={() => setRestoring(null)}
          onDone={() => { setRestoring(null); setPage(0); setVersion((v) => v + 1); }}
        />
      )}
    </main>
  );
}
