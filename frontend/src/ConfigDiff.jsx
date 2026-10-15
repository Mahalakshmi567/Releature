const FIELDS = {
  name: "Name", key: "Key", description: "Description", dataType: "Type", defaultValue: "Default value",
  value: "Value", rolloutPercentage: "Rollout %", userIds: "Target users", rules: "Rules", environments: "Environments",
};

function show(v) {
  if (v === undefined || v === null) return "none";
  const text = typeof v === "object" ? JSON.stringify(v) : String(v);
  if (text === "") return "(empty)";
  return text.length > 300 ? `${text.slice(0, 300)}…` : text;
}

// The fields whose value differs between two configurations.
export function changedFields(previous, next) {
  const prev = previous || {};
  const cur = next || {};
  return [...new Set([...Object.keys(prev), ...Object.keys(cur)])]
    .filter((k) => JSON.stringify(prev[k]) !== JSON.stringify(cur[k]))
    .map((k) => ({ field: FIELDS[k] || k, from: prev[k], to: cur[k] }));
}

/** Lists what differs between `previous` and `next` as "old → new". Either may be null (created / deleted). */
export default function ConfigDiff({ previous, next }) {
  return (
    <table className="diff">
      <tbody>
        {changedFields(previous, next).map((c) => (
          <tr key={c.field}>
            <td className="muted">{c.field}</td>
            <td>
              {previous != null && <span className="from">{show(c.from)}</span>}
              {previous != null && next != null && " → "}
              {next != null && <span className="to">{show(c.to)}</span>}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
