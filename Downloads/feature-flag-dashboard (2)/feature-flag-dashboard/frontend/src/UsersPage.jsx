import { useCallback, useEffect, useState } from "react";
import { createUser, listUsers, resetUserPassword, updateUser } from "./api.js";
import { ROLES, ROLE_LABELS } from "./permissions.js";
import PasswordDialog from "./PasswordDialog.jsx";

const ROLE_HELP = {
  VIEWER: "Can see flags, history and evaluation results.",
  DEVELOPER: "Can also create flags and change development and testing.",
  RELEASE_MANAGER: "Can also change production and delete flags.",
  ADMIN: "Can also manage users and roles.",
};

export default function UsersPage({ user: me }) {
  const [users, setUsers] = useState([]);
  const [error, setError] = useState("");
  const [form, setForm] = useState({ username: "", password: "", role: "DEVELOPER" });
  const [fieldErrors, setFieldErrors] = useState({});
  const [resetting, setResetting] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setUsers(await listUsers());
      setError("");
    } catch (e) {
      setError(e.message);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const add = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError("");
    setFieldErrors({});
    try {
      await createUser(form);
      setForm({ username: "", password: "", role: form.role });
      await load();
    } catch (err) {
      setError(err.message);
      setFieldErrors(err.fieldErrors || {});
    } finally {
      setBusy(false);
    }
  };

  const change = async (u, changes) => {
    try {
      await updateUser(u.id, { role: u.role, enabled: u.enabled, ...changes });
      setError("");
    } catch (err) {
      setError(err.message);
    }
    await load();
  };

  return (
    <main className="page">
      <header className="top">
        <div>
          <h1>Users</h1>
          <p className="sub">Choose who can change flags, and who can change production.</p>
        </div>
      </header>

      {error && <p className="banner" role="alert">{error}</p>}

      <div className="table-wrap">
        <table>
          <thead><tr><th>Username</th><th>Role</th><th>Active</th><th>Created</th><th /></tr></thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id}>
                <td>{u.username}{u.username === me.username && <span className="muted"> (you)</span>}</td>
                <td>
                  <select
                    className="inline-select" value={u.role} aria-label={`Role of ${u.username}`}
                    onChange={(e) => change(u, { role: e.target.value })}
                  >
                    {ROLES.map((r) => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
                  </select>
                </td>
                <td>
                  <input
                    type="checkbox" checked={u.enabled} aria-label={`${u.username} is active`}
                    onChange={(e) => change(u, { enabled: e.target.checked })}
                  />
                </td>
                <td className="muted">{new Date(u.createdAt).toLocaleDateString()}</td>
                <td className="row-actions">
                  <button className="link" onClick={() => setResetting(u)}>Reset password</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <form className="user-form" onSubmit={add}>
        <h2>Add a user</h2>
        <div className="row">
          <label>
            Username
            <input value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} autoComplete="off" required />
            {fieldErrors.username && <span className="field-error">{fieldErrors.username}</span>}
          </label>
          <label>
            Temporary password
            <input type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} autoComplete="new-password" minLength={10} maxLength={72} required />
          </label>
        </div>
        <label>
          Role
          <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>
            {ROLES.map((r) => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
          </select>
          <span className="hint">{ROLE_HELP[form.role]}</span>
        </label>
        <button type="submit" className="btn primary" disabled={busy}>{busy ? "Adding…" : "Add user"}</button>
      </form>

      {resetting && (
        <PasswordDialog
          title={`Reset password for ${resetting.username}`}
          description="They are signed out everywhere and need the new password to sign in again."
          onClose={() => setResetting(null)}
          onSubmit={async ({ next }) => { await resetUserPassword(resetting.id, next); setResetting(null); }}
        />
      )}
    </main>
  );
}
