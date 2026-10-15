import { useEffect, useState } from "react";
import { getRegistration, login, tokenStore } from "./api.js";
import AuthLayout from "./AuthLayout.jsx";
import PasswordField from "./PasswordField.jsx";

export default function LoginPage({ onLoggedIn, onShowRegister, notice }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [canRegister, setCanRegister] = useState(false);

  useEffect(() => {
    getRegistration().then((r) => setCanRegister(r.enabled)).catch(() => {});
  }, []);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const res = await login(username.trim(), password);
      tokenStore.set(res.token);
      onLoggedIn({ username: res.username, role: res.role });
    } catch (err) {
      setError(err.message);
      setPassword("");
      setBusy(false);
    }
  };

  return (
    <AuthLayout
      title="Welcome back"
      subtitle="Sign in to Releature to manage your feature flags."
      footer={canRegister && (
        <p className="switch-auth">
          New to Releature?
          <button type="button" className="link" onClick={onShowRegister}>Create an account</button>
        </p>
      )}
    >
      <form onSubmit={submit}>
        {notice && !error && <p className="note" role="status">{notice}</p>}
        <label>
          Username
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" autoFocus required />
        </label>
        <PasswordField label="Password" value={password} onChange={setPassword} autoComplete="current-password" />
        {error && <p className="banner" role="alert">{error}</p>}
        <button type="submit" className="btn primary block" disabled={busy || !username.trim() || !password}>
          {busy ? "Signing in…" : "Sign in"}
        </button>
      </form>
    </AuthLayout>
  );
}
