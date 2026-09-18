import { describe, it, expect } from 'vitest';
import { createHash } from 'node:crypto';
import { verifyPkce } from './token.js';
import { escapeHtml } from './authorize.js';

describe('pkce', () => {
  it('verifies a valid S256 challenge', () => {
    const verifier = 'dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk';
    const challenge = createHash('sha256').update(verifier).digest().toString('base64url');
    expect(verifyPkce(verifier, challenge)).toBe(true);
  });

  it('rejects a wrong verifier', () => {
    expect(verifyPkce('wrong-verifier', 'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM')).toBe(false);
  });
});

describe('escapeHtml', () => {
  it('escapes attribute-breaking characters', () => {
    expect(escapeHtml('"><script>alert(1)</script>')).toBe(
      '&quot;&gt;&lt;script&gt;alert(1)&lt;/script&gt;',
    );
    expect(escapeHtml("a'b&c")).toBe('a&#39;b&amp;c');
  });
});
