// MCP tool annotations, so clients can tell reads from writes (e.g. to ask the user before a write).
export const READ = { readOnlyHint: true };
export const CREATE = { readOnlyHint: false, destructiveHint: false };
export const CHANGE = { readOnlyHint: false, destructiveHint: true, idempotentHint: true };
