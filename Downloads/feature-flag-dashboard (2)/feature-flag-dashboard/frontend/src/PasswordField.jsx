import { useState } from "react";

/** A password input with a show/hide button. */
export default function PasswordField({ label, hint, value, onChange, autoComplete, minLength, autoFocus }) {
  const [shown, setShown] = useState(false);
  return (
    <label>
      {label}
      <div className="pw">
        <input
          type={shown ? "text" : "password"} value={value} onChange={(e) => onChange(e.target.value)}
          autoComplete={autoComplete} minLength={minLength} maxLength={72} autoFocus={autoFocus} required
        />
        <button type="button" className="pw-toggle link" aria-pressed={shown} onClick={() => setShown(!shown)}>
          {shown ? "Hide" : "Show"}
        </button>
      </div>
      {hint && <span className="hint">{hint}</span>}
    </label>
  );
}
