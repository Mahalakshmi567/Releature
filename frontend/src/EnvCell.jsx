import { useEffect, useState } from "react";

function targetSummary(env) {
  const parts = [];
  if (env.targetUserCount > 0) parts.push(`${env.targetUserCount} user${env.targetUserCount === 1 ? "" : "s"}`);
  if (env.targetRuleCount > 0) parts.push(`${env.targetRuleCount} rule${env.targetRuleCount === 1 ? "" : "s"}`);
  return parts.length ? `Targeting: ${parts.join(", ")}` : "Add targeting";
}

export default function EnvCell({ flag, env, onSave, onTarget, askReason, canChange = true }) {
  const [draft, setDraft] = useState(env.value);
  const [pct, setPct] = useState(String(env.rolloutPercentage));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const label = `${flag.name} in ${env.environmentName}`;
  const production = env.environmentKey === "production";

  useEffect(() => { setDraft(env.value); }, [env.value]);
  useEffect(() => { setPct(String(env.rolloutPercentage)); }, [env.rolloutPercentage]);

  const reset = () => { setDraft(env.value); setPct(String(env.rolloutPercentage)); };

  // Every change asks for a reason first; cancelling leaves things as they were.
  const apply = async (changes, summary) => {
    const reason = await askReason({
      title: label,
      description: production ? `Set ${summary}. This affects live users.` : `Set ${summary}.`,
      danger: production,
    });
    if (reason === null) {
      reset();
      return;
    }
    setBusy(true);
    setError("");
    try {
      await onSave(flag, env.environmentKey, { ...changes, reason });
    } catch (e) {
      setError(Object.values(e.fieldErrors || {})[0] || e.message);
      reset();
    } finally {
      setBusy(false);
    }
  };

  const saveValue = (v) => apply({ value: v }, `value to ${v === "" ? "(empty)" : v}`);

  const savePct = () => {
    const n = Number(pct);
    if (pct.trim() === "" || !Number.isInteger(n) || n < 0 || n > 100) {
      setError("Enter a whole number from 0 to 100.");
      return;
    }
    apply({ rolloutPercentage: n }, `rollout to ${n}% of users`);
  };

  const dirtyValue = draft !== env.value;
  const dirtyPct = pct !== String(env.rolloutPercentage);

  // Users who may not change this environment see its settings without controls.
  if (!canChange) {
    const summary = targetSummary(env);
    return (
      <div className="env-cell" title={env.environmentKey === "production" ? "Only release managers can change production" : "You can't change flags"}>
        {flag.dataType === "BOOLEAN"
          ? <span className={`pill ${env.value}`}>{env.value}</span>
          : <code>{env.value === "" ? "(empty)" : env.value}</code>}
        <span className="muted rollout">{env.rolloutPercentage}% of users</span>
        <span className="muted rollout">{summary === "Add targeting" ? "No targeting" : summary}</span>
      </div>
    );
  }

  return (
    <div className="env-cell">
      {flag.dataType === "BOOLEAN" ? (
        <button
          type="button" role="switch" aria-checked={env.value === "true"} aria-label={label}
          className={`switch ${env.value === "true" ? "on" : ""}`} disabled={busy}
          onClick={() => saveValue(env.value === "true" ? "false" : "true")}
        ><span /></button>
      ) : (
        <div className="env-input">
          <input
            className="mono" aria-label={label}
            type={flag.dataType === "NUMBER" ? "number" : "text"} step="any"
            value={draft} onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => { if (e.key === "Enter" && dirtyValue) saveValue(draft); }}
          />
          {dirtyValue && <button type="button" className="link" disabled={busy} onClick={() => saveValue(draft)}>Save</button>}
        </div>
      )}

      <div className="rollout">
        <input
          type="number" min="0" max="100" step="1" value={pct}
          aria-label={`Rollout percentage for ${label}`}
          onChange={(e) => setPct(e.target.value)}
          onKeyDown={(e) => { if (e.key === "Enter" && dirtyPct) savePct(); }}
        />
        <span>% of users</span>
        {dirtyPct && <button type="button" className="link" disabled={busy} onClick={savePct}>Save</button>}
      </div>

      <button type="button" className="link target-link" onClick={() => onTarget(flag, env)}>
        {targetSummary(env)}
      </button>

      {error && <span className="field-error">{error}</span>}
    </div>
  );
}
