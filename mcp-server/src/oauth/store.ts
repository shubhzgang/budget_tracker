import { v4 as uuidv4 } from 'uuid';

export interface ClientInfo {
  clientId: string;
  clientName?: string;
  redirectUris: string[];
  registeredAt: Date;
}

export interface AuthCodeData {
  budgetTrackerJwt: string;
  codeChallenge: string;
  codeChallengeMethod: string;
  redirectUri: string;
  clientId: string;
  expiresAt: Date;
}

const AUTH_CODE_TTL_MS = 5 * 60 * 1000;
const TOKEN_TTL_MS = 24 * 60 * 60 * 1000;

export const registeredClients = new Map<string, ClientInfo>();
export const authorizationCodes = new Map<string, AuthCodeData>();
export const accessTokens = new Map<string, { jwt: string; expiresAt: Date }>();

export function registerClient(clientName: string | undefined, redirectUris: string[]): ClientInfo {
  const clientId = uuidv4();
  const info: ClientInfo = { clientId, clientName, redirectUris, registeredAt: new Date() };
  registeredClients.set(clientId, info);
  return info;
}

export function getClient(clientId: string): ClientInfo | undefined {
  return registeredClients.get(clientId);
}

export function storeAuthCode(data: Omit<AuthCodeData, 'expiresAt'>): string {
  const code = uuidv4();
  authorizationCodes.set(code, { ...data, expiresAt: new Date(Date.now() + AUTH_CODE_TTL_MS) });
  return code;
}

export function consumeAuthCode(code: string): AuthCodeData | undefined {
  const data = authorizationCodes.get(code);
  if (!data) return undefined;
  authorizationCodes.delete(code);
  if (data.expiresAt.getTime() < Date.now()) return undefined;
  return data;
}

export function storeToken(budgetTrackerJwt: string): string {
  const token = uuidv4();
  accessTokens.set(token, { jwt: budgetTrackerJwt, expiresAt: new Date(Date.now() + TOKEN_TTL_MS) });
  return token;
}

export function getJwtForToken(token: string): string | undefined {
  const entry = accessTokens.get(token);
  if (!entry) return undefined;
  if (entry.expiresAt.getTime() < Date.now()) {
    accessTokens.delete(token);
    return undefined;
  }
  return entry.jwt;
}

export function clearStores(): void {
  registeredClients.clear();
  authorizationCodes.clear();
  accessTokens.clear();
}
