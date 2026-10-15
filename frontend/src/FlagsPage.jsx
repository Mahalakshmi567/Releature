import { useCallback, useEffect, useState } from "react";
import { createFlag, deleteFlag, listEnvironments, listFlags, setEnvironmentValue, updateFlag } from "./api.js";
import FlagForm from "./FlagForm.jsx";
import EnvCell from "./EnvCell.jsx";
import EvaluatePanel from "./EvaluatePanel.jsx";
import TargetingDialog from "./TargetingDialog.jsx";
import ReasonDialog from "./ReasonDialog.jsx";
import { permissions } from "./permissions.js";

function DefaultValue({ flag }) {
  if (flag.dataType === "BOOLEAN") return <span className={`pill ${flag.defaultValue}`}>{flag.defaultValue}</span>;
  return <code>{flag.defaultValue === "" ? "(empty)" : flag.defaultValue}</code>;
}

export default function FlagsPage({ user }) {
  const can = permissions(user.role);
  const [flags, setFlags] = useState([]);
  const [environments, setEnvironments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [form, setForm] = useState(undefined); // undefined = closed, null = create, object = edit
  const [testing, setTesting] = useState(false);
  const [targeting, setTargeting] = useState(null); // { flag, env } while the targeting dialog is open
  const [reasonRequest, setReasonRequest] = useState(null);

  // Resolves with the reason the user typed, or null if they cancelled.
  const askReason = (options) => new Promise((resolve) => setReasonRequest({ ...options, resolve }));
  const answerReason = (value) => {
    reasonRequest?.resolve(value);
    setReasonRequest(null);
  };

  const load = useCallback(async () => {
    try {
      const [f, e] = await Promise.all([listFlags(), listEnvironments()]);
      setFlags(f);
      setEnvironments(e);
      setError("");
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const save = async (values) => {
    if (form) await updateFlag(form.id, values);
    else await createFlag(values);
    setForm(undefined);
    await load();
  };

  const changeValue = async (flag, envKey, changes) => {
    const updated = await setEnvironmentValue(flag.id, envKey, changes);
    setFlags((prev) => prev.map((f) => (f.id === updated.id ? updated : f)));
  };

  const remove = async (flag) => {
    const reason = await askReason({
      title: `Delete "${flag.name}"`,
      description: `This removes the flag from all environments. Code that reads ${flag.key} will fall back to its own default. The deletion stays in the audit history.`,
      confirmLabel: "Delete flag",
      danger: true,
    });
    if (reason === null) return;
    try {
      await deleteFlag(flag.id, reason);
      await load();
    } catch (e) {
      setError(e.message);
    }
  };

  return (
    <main className="page">
      <header className="top">
        <div>
          <h1>Feature flags</h1>
          <p className="sub">Each environment has its own value. Changing one never touches the others.</p>
        </div>
        <div className="top-actions">
          <button className="btn" onClick={() => setTesting(true)} disabled={flags.length === 0}>Test evaluation</button>
          {can.editFlags && <button className="btn primary" onClick={() => setForm(null)}>Create flag</button>}
        </div>
      </header>

      {error && <p className="banner" role="alert">{error}</p>}

      {loading ? (
        <p className="empty">Loading flags…</p>
      ) : flags.length === 0 ? (
        <div className="empty">
          <p><strong>No flags yet.</strong></p>
          <p>{can.editFlags ? <>Create your first flag, for example <code>new_checkout</code> with a default of false.</> : "Ask a developer to create the first flag."}</p>
        </div>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Name</th><th>Key</th><th>Type</th><th>Default</th>
                {environments.map((e) => <th key={e.key}>{e.name}</th>)}
                <th />
              </tr>
            </thead>
            <tbody>
              {flags.map((f) => (
                <tr key={f.id}>
                  <td>
                    <div className="name">{f.name}</div>
                    {f.description && <div className="desc">{f.description}</div>}
                  </td>
                  <td><code>{f.key}</code></td>
                  <td>{f.dataType.charAt(0) + f.dataType.slice(1).toLowerCase()}</td>
                  <td><DefaultValue flag={f} /></td>
                  {f.environments.map((ev) => (
                    <td key={ev.environmentKey}>
                      <EnvCell
                        flag={f} env={ev} onSave={changeValue} askReason={askReason}
                        canChange={can.changeEnvironment(ev.environmentKey)}
                        onTarget={(flag, env) => setTargeting({ flag, env })}
                      />
                    </td>
                  ))}
                  <td className="row-actions">
                    {can.editFlags && <button className="link" onClick={() => setForm(f)}>Edit</button>}
                    {can.deleteFlags && <button className="link danger" onClick={() => remove(f)}>Delete</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {form !== undefined && <FlagForm flag={form} onSave={save} onCancel={() => setForm(undefined)} />}
      {testing && <EvaluatePanel flags={flags} environments={environments} onClose={() => setTesting(false)} />}
      {targeting && (
        <TargetingDialog
          flag={targeting.flag} env={targeting.env} askReason={askReason}
          onClose={() => setTargeting(null)}
          onSaved={async () => { setTargeting(null); await load(); }}
        />
      )}
      {reasonRequest && (
        <ReasonDialog
          title={reasonRequest.title} description={reasonRequest.description}
          danger={reasonRequest.danger} confirmLabel={reasonRequest.confirmLabel}
          onSubmit={answerReason} onCancel={() => answerReason(null)}
        />
      )}
    </main>
  );
}
