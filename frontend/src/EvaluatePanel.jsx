import { useEffect, useRef, useState } from "react";
import { evaluate } from "./api.js";

const REASONS = {
  EXPLICIT_USER: "This user ID is on the target list, so they get the environment value regardless of the rollout percentage.",
  ATTRIBUTE_RULE: "A targeting rule matches this user, so they get the environment value regardless of the rollout percentage.",
  FULLY_ROLLED_OUT: "Rollout is at 100%, so everyone gets the environment value.",
  NOT_ROLLED_OUT: "Rollout is at 0%, so everyone not targeted gets the default value.",
  IN_ROLLOUT: "Not targeted. This user's bucket is inside the rollout, so they get the environment value.",
  OUTSIDE_ROLLOUT: "Not targeted. This user's bucket is outside the rollout, so they get the default value.",
};

// "plan=pro, country=IN" -> { plan: "pro", country: "IN" }
function parseAttributes(text) {
  const out = {};
  text.split(/[\n,]+/).forEach((pair) => {
    const i = pair.indexOf("=");
    if (i > 0) out[pair.slice(0, i).trim()] = pair.slice(i + 1).trim();
  });
  return out;
}

export default function EvaluatePanel({ flags, environments, onClose }) {
  const dialog = useRef(null);
  const [flagKey, setFlagKey] = useState(flags[0]?.key ?? "");
  const [envKey, setEnvKey] = useState(environments[0]?.key ?? "");
  const [userId, setUserId] = useState("");
  const [attributes, setAttributes] = useState("");
  const [result, setResult] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => { dialog.current?.showModal(); }, []);

  const run = async (e) => {
    e.preventDefault();
    setError("");
    setResult(null);
    try {
      setResult(await evaluate(envKey, flagKey, userId.trim(), parseAttributes(attributes)));
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <dialog ref={dialog} onClose={onClose} className="dialog">
      <form onSubmit={run}>
        <h2>Test evaluation</h2>
        <div className="row">
          <label>
            Flag
            <select value={flagKey} onChange={(e) => setFlagKey(e.target.value)}>
              {flags.map((f) => <option key={f.id} value={f.key}>{f.name}</option>)}
            </select>
          </label>
          <label>
            Environment
            <select value={envKey} onChange={(e) => setEnvKey(e.target.value)}>
              {environments.map((e) => <option key={e.key} value={e.key}>{e.name}</option>)}
            </select>
          </label>
        </div>
        <label>
          User ID
          <input className="mono" value={userId} onChange={(e) => setUserId(e.target.value)} placeholder="user-42" />
          <span className="hint">The same user ID always gets the same answer for a flag.</span>
        </label>
        <label>
          Attributes <span className="hint">(optional)</span>
          <input className="mono" value={attributes} onChange={(e) => setAttributes(e.target.value)} placeholder="plan=pro, country=IN" />
        </label>

        {error && <p className="banner" role="alert">{error}</p>}

        {result && (
          <div className="result">
            <dl>
              <dt>Value</dt><dd><code>{result.value === "" ? "(empty)" : result.value}</code></dd>
              <dt>Rollout</dt><dd>{result.rolloutPercentage}%</dd>
              <dt>Bucket</dt><dd>{result.bucket ?? "n/a"}</dd>
              {result.matchedRule && (<><dt>Rule</dt><dd>{result.matchedRule}</dd></>)}
              <dt>Why</dt><dd>{REASONS[result.reason]}</dd>
            </dl>
          </div>
        )}

        <div className="actions">
          <button type="button" className="btn" onClick={() => dialog.current?.close()}>Close</button>
          <button type="submit" className="btn primary">Evaluate</button>
        </div>
      </form>
    </dialog>
  );
}
