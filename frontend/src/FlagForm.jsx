import { useEffect, useRef, useState } from "react";

const TYPES = ["BOOLEAN", "STRING", "NUMBER"];
const DEFAULTS = { BOOLEAN: "false", STRING: "", NUMBER: "0" };

const toKey = (name) =>
  name.toLowerCase().trim().replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "");

export default function FlagForm({ flag, onSave, onCancel }) {
  const editing = Boolean(flag);
  const dialog = useRef(null);
  const [name, setName] = useState(flag?.name ?? "");
  const [key, setKey] = useState(flag?.key ?? "");
  const [keyTouched, setKeyTouched] = useState(editing);
  const [dataType, setDataType] = useState(flag?.dataType ?? "BOOLEAN");
  const [defaultValue, setDefaultValue] = useState(flag?.defaultValue ?? "false");
  const [description, setDescription] = useState(flag?.description ?? "");
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});
  const [saving, setSaving] = useState(false);

  useEffect(() => { dialog.current?.showModal(); }, []);

  const onNameChange = (value) => {
    setName(value);
    if (!keyTouched) setKey(toKey(value));
  };

  const onTypeChange = (value) => {
    setDataType(value);
    setDefaultValue(DEFAULTS[value]);
  };

  const submit = async (e) => {
    e.preventDefault();
    setSaving(true);
    setError("");
    setFieldErrors({});
    try {
      await onSave(editing ? { name, defaultValue, description, reason } : { name, key, dataType, defaultValue, description, reason });
    } catch (err) {
      setError(err.message);
      setFieldErrors(err.fieldErrors || {});
      setSaving(false);
    }
  };

  return (
    <dialog ref={dialog} onClose={onCancel} className="dialog">
      <form onSubmit={submit}>
        <h2>{editing ? "Edit flag" : "Create flag"}</h2>

        <label>
          Name
          <input value={name} onChange={(e) => onNameChange(e.target.value)} placeholder="New Checkout" required maxLength={100} autoFocus />
          {fieldErrors.name && <span className="field-error">{fieldErrors.name}</span>}
        </label>

        <label>
          Key
          <input
            className="mono"
            value={key}
            onChange={(e) => { setKey(e.target.value); setKeyTouched(true); }}
            placeholder="new_checkout"
            required
            disabled={editing}
          />
          <span className="hint">{editing ? "The key can't change after creation, because code reads the flag by this key." : "Your application reads the flag by this key."}</span>
          {fieldErrors.key && <span className="field-error">{fieldErrors.key}</span>}
        </label>

        <div className="row">
          <label>
            Data type
            <select value={dataType} onChange={(e) => onTypeChange(e.target.value)} disabled={editing}>
              {TYPES.map((t) => <option key={t} value={t}>{t.charAt(0) + t.slice(1).toLowerCase()}</option>)}
            </select>
          </label>

          <label>
            Default value
            {dataType === "BOOLEAN" ? (
              <select value={defaultValue} onChange={(e) => setDefaultValue(e.target.value)}>
                <option value="false">false</option>
                <option value="true">true</option>
              </select>
            ) : (
              <input
                className="mono"
                type={dataType === "NUMBER" ? "number" : "text"}
                step="any"
                value={defaultValue}
                onChange={(e) => setDefaultValue(e.target.value)}
              />
            )}
            <span className="hint">{editing ? "Doesn't change values already set in each environment." : "Starting value in every environment."}</span>
            {fieldErrors.defaultValue && <span className="field-error">{fieldErrors.defaultValue}</span>}
          </label>
        </div>

        <label>
          Description <span className="hint">(optional)</span>
          <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={2} maxLength={500} placeholder="What does this flag control?" />
        </label>

        <label>
          Reason for change
          <textarea
            value={reason} onChange={(e) => setReason(e.target.value)} rows={2} maxLength={500} required
            placeholder={editing ? "Why are you editing this flag?" : "Why are you creating this flag?"}
          />
          <span className="hint">Saved in the audit history with your username and the time.</span>
          {fieldErrors.reason && <span className="field-error">{fieldErrors.reason}</span>}
        </label>

        {error && <p className="banner" role="alert">{error}</p>}

        <div className="actions">
          <button type="button" className="btn" onClick={() => dialog.current?.close()}>Cancel</button>
          <button type="submit" className="btn primary" disabled={saving}>
            {saving ? "Saving…" : editing ? "Save changes" : "Create flag"}
          </button>
        </div>
      </form>
    </dialog>
  );
}
