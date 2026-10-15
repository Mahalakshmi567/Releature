import { useEffect, useState } from "react";
import { tokenStore } from "./api.js";

/**
 * Listens to the server's event stream (server-sent events). The browser's EventSource can't send the
 * Authorization header, so this reads the stream with fetch and parses the events itself.
 *
 * It reconnects on its own, waiting longer after each failure. "ready" arrives each time a stream opens.
 */
export function connectChangeFeed({ onStatus, onReady, onChange }) {
  let stopped = false;
  let controller = null;
  let timer = null;
  let failures = 0;

  function handle(block) {
    let event = "message";
    const data = [];
    for (const line of block.split("\n")) {
      if (line.startsWith(":")) continue; // a keep-alive comment
      if (line.startsWith("event:")) event = line.slice(6).trim();
      else if (line.startsWith("data:")) data.push(line.slice(5).replace(/^ /, ""));
    }
    if (event === "ready") {
      failures = 0;
      onStatus("live");
      onReady();
    } else if (event === "flag-changed" && data.length) {
      try { onChange(JSON.parse(data.join("\n"))); } catch { /* ignore a malformed event */ }
    }
  }

  async function run() {
    controller = new AbortController();
    let ended = false;
    try {
      const token = tokenStore.get();
      if (!token) return;
      const res = await fetch("/api/events", {
        headers: { Authorization: `Bearer ${token}`, Accept: "text/event-stream" },
        signal: controller.signal,
      });
      if (res.status === 401) {
        tokenStore.clear();
        window.dispatchEvent(new Event("auth:expired"));
        return;
      }
      if (!res.ok || !res.body) throw new Error(`Unexpected response ${res.status}`);

      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer = (buffer + decoder.decode(value, { stream: true })).replace(/\r\n/g, "\n");
        let end;
        while ((end = buffer.indexOf("\n\n")) !== -1) {
          handle(buffer.slice(0, end));
          buffer = buffer.slice(end + 2);
        }
      }
      ended = true; // the server closed the stream normally (it does every 10 minutes)
    } catch {
      if (stopped) return;
    }
    if (stopped) return;
    if (!ended) failures += 1;
    onStatus("reconnecting");
    const delay = ended ? 500 : Math.min(1000 * 2 ** (failures - 1), 15000);
    timer = setTimeout(run, delay);
  }

  onStatus("connecting");
  run();

  return {
    close() {
      stopped = true;
      clearTimeout(timer);
      controller?.abort();
    },
  };
}

/**
 * Keeps one stream open while signed in. Returns the connection status, a counter that goes up whenever the pages
 * should read fresh data, and notices about changes other people made.
 */
export function useChangeFeed(enabled, username) {
  const [status, setStatus] = useState("connecting");
  const [refreshKey, setRefreshKey] = useState(0);
  const [notices, setNotices] = useState([]);

  useEffect(() => {
    if (!enabled) return undefined;
    let refreshTimer;
    let connectedBefore = false;
    // Several changes close together cause one refresh.
    const refresh = () => {
      clearTimeout(refreshTimer);
      refreshTimer = setTimeout(() => setRefreshKey((k) => k + 1), 300);
    };
    const feed = connectChangeFeed({
      onStatus: setStatus,
      // After a reconnect, read everything again: changes may have happened while the stream was down.
      onReady: () => {
        if (connectedBefore) refresh();
        connectedBefore = true;
      },
      onChange: (change) => {
        refresh();
        if (change.actor !== username) setNotices((n) => [change, ...n.filter((x) => x.id !== change.id)].slice(0, 4));
      },
    });
    return () => {
      feed.close();
      clearTimeout(refreshTimer);
    };
  }, [enabled, username]);

  const dismiss = (id) => setNotices((n) => n.filter((x) => x.id !== id));
  return { status, refreshKey, notices, dismiss };
}
