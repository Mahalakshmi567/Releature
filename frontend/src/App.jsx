import { useEffect, useState } from "react";
import { changePassword, logout, me, tokenStore } from "./api.js";
import { ROLE_LABELS, permissions } from "./permissions.js";
import LoginPage from "./LoginPage.jsx";
import FlagsPage from "./FlagsPage.jsx";
import AuditPage from "./AuditPage.jsx";
import UsersPage from "./UsersPage.jsx";
import PasswordDialog from "./PasswordDialog.jsx";

export default function App() {
  const [user, setUser] = useState(null); // { username, role }
  const [checking, setChecking] = useState(Boolean(tokenStore.get())); // verifying a saved token
  const [view, setView] = useState("flags");
  const [changingPassword, setChangingPassword] = useState(false);

  useEffect(() => {
    if (!tokenStore.get()) return;
    me().then(setUser).catch(() => tokenStore.clear()).finally(() => setChecking(false));
  }, []);

  // api.js fires this when the server says the token is no longer valid.
  useEffect(() => {
    const onExpired = () => setUser(null);
    window.addEventListener("auth:expired", onExpired);
    return () => window.removeEventListener("auth:expired", onExpired);
  }, []);

  const signOut = async () => {
    try { await logout(); } catch { /* the token is cleared below either way */ }
    tokenStore.clear();
    setUser(null);
    setView("flags");
  };

  if (checking) return <p className="empty" style={{ margin: "4rem auto", maxWidth: "20rem" }}>Loading…</p>;
  if (!user) return <LoginPage onLoggedIn={setUser} />;

  const can = permissions(user.role);
  const activeView = view === "users" && !can.manageUsers ? "flags" : view;

  return (
    <>
      <nav className="shell" aria-label="Main">
        <span className="brand">Flags</span>
        <div className="tabs">
          <button className="tab" aria-current={activeView === "flags" ? "page" : undefined} onClick={() => setView("flags")}>Flags</button>
          <button className="tab" aria-current={activeView === "audit" ? "page" : undefined} onClick={() => setView("audit")}>Audit history</button>
          {can.manageUsers && (
            <button className="tab" aria-current={activeView === "users" ? "page" : undefined} onClick={() => setView("users")}>Users</button>
          )}
        </div>
        <span className="muted">{user.username} · {ROLE_LABELS[user.role]}</span>
        <button className="link" onClick={() => setChangingPassword(true)}>Change password</button>
        <button className="link" onClick={signOut}>Sign out</button>
      </nav>

      {activeView === "flags" && <FlagsPage user={user} />}
      {activeView === "audit" && <AuditPage user={user} />}
      {activeView === "users" && <UsersPage user={user} />}

      {changingPassword && (
        <PasswordDialog
          title="Change your password"
          description="You will be signed out everywhere and need to sign in again."
          askCurrent
          onClose={() => setChangingPassword(false)}
          onSubmit={async ({ current, next }) => {
            await changePassword(current, next);
            tokenStore.clear();
            setChangingPassword(false);
            setUser(null);
          }}
        />
      )}
    </>
  );
}
