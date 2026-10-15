// Mirrors the server's rules so the dashboard hides what a user can't do. The server is the one that enforces them.
export const ROLES = ["VIEWER", "DEVELOPER", "RELEASE_MANAGER", "ADMIN"];

export const ROLE_LABELS = {
  VIEWER: "Viewer",
  DEVELOPER: "Developer",
  RELEASE_MANAGER: "Release manager",
  ADMIN: "Admin",
};

export function permissions(role) {
  const rank = ROLES.indexOf(role);
  return {
    editFlags: rank >= 1, // create flags, edit name and description
    deleteFlags: rank >= 2, // deleting removes a flag from production
    manageUsers: rank >= 3,
    // Production needs release manager; development and testing need developer.
    changeEnvironment: (envKey) => (envKey === "production" ? rank >= 2 : rank >= 1),
  };
}
