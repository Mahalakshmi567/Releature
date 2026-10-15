/** The Releature mark (a toggle switch) with its name. */
export default function Logo({ light = false, size = 28 }) {
  return (
    <span className={`logo ${light ? "light" : ""}`}>
      <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true">
        <rect width="32" height="32" rx="8" className="logo-bg" />
        <rect x="6" y="11" width="20" height="10" rx="5" className="logo-track" />
        <circle cx="21" cy="16" r="4.5" className="logo-knob" />
      </svg>
      <span className="logo-word">Releature</span>
    </span>
  );
}
