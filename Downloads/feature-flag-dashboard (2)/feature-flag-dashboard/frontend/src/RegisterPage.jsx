import { useState } from "react";
import { register, tokenStore } from "./api.js";
import AuthLayout from "./AuthLayout.jsx";
import PasswordField from "./PasswordField.jsx";

export default function RegisterPage({ onRegistered, onShowLogin }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});
  const [busy, setBusy] = useState(false);

  const mismatch = confirm !== "" && confirm !== password;

  const submit = async (e) => {
    e.preventDefault();
    if (password !== confirm) {
      setError("The two passwords don't match.");
      return;
    }
    setBusy(true);
    setError("");
    setFieldErrors({});
    try {
      const res = await register(username.trim(), password);
      tokenStore.set(res.token);
      onRegistered({ username: res.username, role: res.role });
    } catch (err) {
      setError(err.message);
      setFieldErrors(err.fieldErrors || {});
      setBusy(false);
    }
  };

  return (
    <AuthLayout
      title="Create your account"
      subtitle="New accounts can view flags. An administrator can give you more access."
      footer={
        <p className="switch-auth">
          Already have an account?
          <button type="button" className="link" onClick={onShowLogin}>Sign in</button>
        </p>
      }
    >
      <form onSubmit={submit}>
        <label>
          Username
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" autoFocus required />
          <span className="hint">3 to 50 characters: letters, numbers, dot, dash or underscore.</span>
          {fieldErrors.username && <span className="field-error">{fieldErrors.username}</span>}
        </label>
        <PasswordField
          label="Password" value={password} onChange={setPassword} autoComplete="new-password"
          minLength={10} hint="At least 10 characters."
        />
        <PasswordField label="Confirm password" value={confirm} onChange={setConfirm} autoComplete="new-password" />
        {mismatch && <span className="field-error">The two passwords don't match.</span>}
        {error && <p className="banner" role="alert">{error}</p>}
        <button type="submit" className="btn primary block" disabled={busy || !username.trim() || !password || mismatch}>
          {busy ? "Creating account…" : "Create account"}
        </button>
      </form>
    </AuthLayout>
  );
}
