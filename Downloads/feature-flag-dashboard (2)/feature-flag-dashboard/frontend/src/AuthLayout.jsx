import Logo from "./Logo.jsx";

const HIGHLIGHTS = [
  "Separate development, testing and production",
  "Gradual rollouts and targeting by user or plan",
  "A full history of every change, with one-click rollback",
];

/** Shared frame for the sign-in and register pages: a brand panel next to the form. */
export default function AuthLayout({ title, subtitle, children, footer }) {
  return (
    <div className="auth">
      <aside className="auth-brand">
        <Logo light size={34} />
        <h2>Release features on your terms.</h2>
        <p>Decide who sees what, in every environment, without redeploying your application.</p>
        <ul>
          {HIGHLIGHTS.map((h) => <li key={h}>{h}</li>)}
        </ul>
      </aside>
      <main className="auth-main">
        <div className="auth-card">
          <div className="auth-mobile-logo"><Logo /></div>
          <h1>{title}</h1>
          <p className="sub">{subtitle}</p>
          {children}
          {footer}
        </div>
      </main>
    </div>
  );
}
