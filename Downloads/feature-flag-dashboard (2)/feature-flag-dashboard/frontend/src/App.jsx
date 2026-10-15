import { useEffect, useState } from "react";
import { changePassword, logout, me, tokenStore } from "./api.js";
import { ROLE_LABELS, permissions } from "./permissions.js";
import Logo from "./Logo.jsx";
import LoginPage from "./LoginPage.jsx";
import RegisterPage from "./RegisterPage.jsx";
import FlagsPage from "./FlagsPage.jsx";
import AuditPage from "./AuditPage.jsx";
import UsersPage from "./UsersPage.jsx";
import PasswordDialog from "./PasswordDialog.jsx";
import ChangeToasts from "./ChangeToasts.jsx";
import { useChangeFeed } from "./changeFeed.js";

export default function App() {
  const [user, setUser] = useState(null); // { username, role }
  const [checking, setChecking] = useState(Boolean(tokenStore.get())); // verifying a saved token
  const [view, setView] = useState("flags");
  const [authScreen, setAuthScreen] = useState("login"); // "login" or "register" while signed out
  const [loginNotice, setLoginNotice] = useState("");
  const [changingPassword, setChangingPassword] = useState(false);
  // Open while signed in: tells the pages to refresh when anyone changes a flag.
  const feed = useChangeFeed(Boolean(user), user?.username);

  useEffect(() => {
    if (!tokenStore.get()) return;
    me().then(setUser).catch(() => tokenStore.clear()).finally(() => setChecking(false));
  }, []);

  // api.js fires this when the server says the token is no longer valid.
  useEffect(() => {
    const onExpired = () => {
      setUser(null);
      setAuthScreen("login");
      setLoginNotice("Your session ended. Please sign in again.");
    };
    window.addEventListener("auth:expired", onExpired);
    return () => window.removeEventListener("auth:expired", onExpired);
  }, []);

  const signedIn = (u) => {
    setUser(u);
    setAuthScreen("login");
    setLoginNotice("");
    setView("flags");
  };

  // Signing out returns to the sign-in page, so a different account can sign in right away.
  const signOut = async () => {
    try { await logout(); } catch { /* the token is cleared below either way */ }
    tokenStore.clear();
    setUser(null);
    setLoginNotice("");
  };

  if (checking) return <p className="empty" style={{ margin: "4rem auto", maxWidth: "20rem" }}>Loading…</p>;

  if (!user) {
    return authScreen === "register"
      ? <RegisterPage onRegistered={signedIn} onShowLogin={() => setAuthScreen("login")} />
      : <LoginPage onLoggedIn={signedIn} onShowRegister={() => setAuthScreen("register")} notice={loginNotice} />;
  }

  const can = permissions(user.role);
  const activeView = view === "users" && !can.manageUsers ? "flags" : view;

  return (
    <>
      <nav className="shell" aria-label="Main">
        <Logo size={24} />
        <div className="tabs">
          <button className="tab" aria-current={activeView === "flags" ? "page" : undefined} onClick={() => setView("flags")}>Flags</button>
          <button className="tab" aria-current={activeView === "audit" ? "page" : undefined} onClick={() => setView("audit")}>Audit history</button>
          {can.manageUsers && (
            <button className="tab" aria-current={activeView === "users" ? "page" : undefined} onClick={() => setView("users")}>Users</button>
          )}
        </div>
        <span className={`live ${feed.status === "live" ? "live-on" : "live-wait"}`} role="status"
          title={feed.status === "live" ? "Changes by other people appear here as they happen" : "Reconnecting to live updates"}>
          {feed.status === "live" ? "Live" : "Reconnecting…"}
        </span>
        <span className="muted">{user.username} · {ROLE_LABELS[user.role]}</span>
        <button className="link" onClick={() => setChangingPassword(true)}>Change password</button>
        <button className="link" onClick={signOut}>Sign out</button>
      </nav>

      {activeView === "flags" && <FlagsPage user={user} refreshKey={feed.refreshKey} />}
      {activeView === "audit" && <AuditPage user={user} refreshKey={feed.refreshKey} />}
      {activeView === "users" && <UsersPage user={user} />}

      <ChangeToasts notices={feed.notices} onDismiss={feed.dismiss} />

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
            setAuthScreen("login");
            setLoginNotice("Password changed. Sign in with your new password.");
          }}
        />
      )}
    </>
  );
}
