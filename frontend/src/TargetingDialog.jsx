import { useEffect, useRef, useState } from "react";
import { getTargeting, setTargeting } from "./api.js";

const splitList = (text) => text.split(/[\n,]+/).map((s) => s.trim()).filter(Boolean);

export default function TargetingDialog({ flag, env, askReason, onClose, onSaved }) {
  const dialog = useRef(null);
  const [loaded, setLoaded] = useState(false);
  const [users, setUsers] = useState("");
  const [rules, setRules] = useState([]); // { attribute, values: "pro, enterprise" }
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => { dialog.current?.showModal(); }, []);

  useEffect(() => {
    getTargeting(flag.id, env.environmentKey)
      .then((t) => {
        setUsers(t.userIds.join("\n"));
        setRules(t.rules.map((r) => ({ attribute: r.attribute, values: r.values.join(", ") })));
        setLoaded(true);
      })
      .catch((e) => setError(e.message));
  }, [flag.id, env.environmentKey]);

  const updateRule = (i, patch) => setRules((rs) => rs.map((r, idx) => (idx === i ? { ...r, ...patch } : r)));

  const save = async (e) => {
    e.preventDefault();
    const production = env.environmentKey === "production";
    const reason = await askReason({
      title: `Save targeting for ${flag.name}`,
      description: production
        ? "This is Production. Targeted users get the production value regardless of the rollout percentage."
        : "Targeted users get this environment's value regardless of the rollout percentage.",
      danger: production,
      confirmLabel: "Save targeting",
    });
    if (reason === null) return;
    setSaving(true);
    setError("");
    try {
      await setTargeting(flag.id, env.environmentKey, {
        reason,
        userIds: splitList(users),
        rules: rules
          .filter((r) => r.attribute.trim() || r.values.trim())
          .map((r) => ({ attribute: r.attribute.trim(), values: splitList(r.values) })),
      });
      onSaved();
    } catch (err) {
      setError(err.message);
      setSaving(false);
    }
  };

  return (
    <dialog ref={dialog} onClose={onClose} className="dialog">
      <form onSubmit={save}>
        <h2>Targeting: {flag.name} <span className="hint">({env.environmentName})</span></h2>

        <p className="note">
          Users on the list or matching a rule always get <code>{env.value === "" ? "(empty)" : env.value}</code>, the{" "}
          {env.environmentName} value, even if they fall outside the {env.rolloutPercentage}% rollout.
          Everyone else follows the rollout.
        </p>

        {!loaded && !error && <p className="hint">Loading…</p>}

        <label>
          User IDs
          <textarea
            className="mono" rows={4} value={users} onChange={(e) => setUsers(e.target.value)}
            placeholder={"user-42\nuser-77"} disabled={!loaded}
          />
          <span className="hint">One per line or comma separated. Matching is exact and case-sensitive.</span>
        </label>

        <div className="section-title">Attribute rules</div>
        <span className="hint">A user matching any rule is targeted. Values match ignoring case.</span>
        <div style={{ marginTop: "0.5rem" }}>
          {rules.map((r, i) => (
            <div className="rule-row" key={i}>
              <input
                className="mono" placeholder="plan" aria-label="Attribute" value={r.attribute}
                onChange={(e) => updateRule(i, { attribute: e.target.value })}
              />
              <input
                placeholder="pro, enterprise" aria-label="Allowed values" value={r.values}
                onChange={(e) => updateRule(i, { values: e.target.value })}
              />
              <button type="button" className="link danger" onClick={() => setRules((rs) => rs.filter((_, idx) => idx !== i))}>
                Remove
              </button>
            </div>
          ))}
          <button type="button" className="btn" disabled={!loaded} onClick={() => setRules((rs) => [...rs, { attribute: "", values: "" }])}>
            Add rule
          </button>
        </div>

        {error && <p className="banner" role="alert">{error}</p>}

        <div className="actions">
          <button type="button" className="btn" onClick={() => dialog.current?.close()}>Cancel</button>
          <button type="submit" className="btn primary" disabled={!loaded || saving}>
            {saving ? "Saving…" : "Save targeting"}
          </button>
        </div>
      </form>
    </dialog>
  );
}
