const TOKEN_KEY = "flags.token";

// The token lives in sessionStorage, so it is gone when the tab closes.
export const tokenStore = {
  get: () => sessionStorage.getItem(TOKEN_KEY),
  set: (token) => sessionStorage.setItem(TOKEN_KEY, token),
  clear: () => sessionStorage.removeItem(TOKEN_KEY),
};

async function request(path, options = {}) {
  const headers = { "Content-Type": "application/json" };
  const token = tokenStore.get();
  if (token) headers.Authorization = `Bearer ${token}`;

  const res = await fetch(`/api${path}`, { ...options, headers });
  if (res.status === 204) return null;
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    const expired = res.status === 401 && !path.startsWith("/auth/login");
    if (expired) {
      tokenStore.clear();
      window.dispatchEvent(new Event("auth:expired"));
    }
    const err = new Error(expired ? "Your session has expired. Sign in again." : body.message || "Something went wrong. Try again.");
    err.fieldErrors = body.fieldErrors || {};
    throw err;
  }
  return body;
}

const json = (method, data) => ({ method, body: JSON.stringify(data) });

// auth
export const login = (username, password) => request("/auth/login", json("POST", { username, password }));
export const logout = () => request("/auth/logout", { method: "POST" });
export const me = () => request("/auth/me");

// flags (every change carries a reason, which is stored in the audit history)
export const listFlags = () => request("/flags");
export const createFlag = (flag) => request("/flags", json("POST", flag));
export const updateFlag = (id, flag) => request(`/flags/${id}`, json("PUT", flag));
export const deleteFlag = (id, reason) => request(`/flags/${id}?reason=${encodeURIComponent(reason)}`, { method: "DELETE" });

// environments
export const listEnvironments = () => request("/environments");
export const setEnvironmentValue = (id, envKey, changes) =>
  request(`/flags/${id}/environments/${envKey}`, json("PUT", changes));

// evaluation and targeting
export const evaluate = (envKey, flagKey, userId, attributes) =>
  request(`/evaluate/${envKey}/${flagKey}`, json("POST", { userId: userId || null, attributes }));
export const getTargeting = (id, envKey) => request(`/flags/${id}/environments/${envKey}/targeting`);
export const setTargeting = (id, envKey, targeting) =>
  request(`/flags/${id}/environments/${envKey}/targeting`, json("PUT", targeting));

// audit history and rollback
export const getRollbackPreview = (entryId) => request(`/audit/${entryId}/rollback`);
export const rollbackEntry = (entryId, reason) => request(`/audit/${entryId}/rollback`, json("POST", { reason }));
export const listAudit = ({ flagKey, environment, page = 0, size = 20 }) => {
  const q = new URLSearchParams({ page, size });
  if (flagKey) q.set("flagKey", flagKey);
  if (environment) q.set("environment", environment);
  return request(`/audit?${q}`);
};

// password and users
export const changePassword = (currentPassword, newPassword) =>
  request("/auth/password", json("POST", { currentPassword, newPassword }));
export const listUsers = () => request("/users");
export const createUser = (user) => request("/users", json("POST", user));
export const updateUser = (id, changes) => request(`/users/${id}`, json("PUT", changes));
export const resetUserPassword = (id, password) => request(`/users/${id}/password`, json("POST", { password }));

// registration (public)
export const register = (username, password) => request("/auth/register", json("POST", { username, password }));
export const getRegistration = () => request("/auth/registration");
