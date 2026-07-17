import { describe, it, expect } from 'vitest';
import { EMOJI_TAGS } from './EmojiPicker';

describe('EMOJI_TAGS', () => {
  it('has tag arrays for mapped emoji', () => {
    const tags = EMOJI_TAGS['😀'];
    expect(tags).toBeDefined();
    expect(tags).toContain('smile');
    expect(tags).toContain('face');
  });

  it('covers emoji with multiple descriptive tags', () => {
    const tags = EMOJI_TAGS['🍕'];
    expect(tags).toContain('pizza');
    expect(tags).toContain('food');
  });

  it('has no duplicate tags per emoji', () => {
    for (const [, tags] of Object.entries(EMOJI_TAGS)) {
      const unique = new Set(tags);
      expect(tags.length).toBe(unique.size);
    }
  });

  it('every tag entry is a non-empty array', () => {
    for (const [emoji, tags] of Object.entries(EMOJI_TAGS)) {
      expect(Array.isArray(tags)).toBe(true);
      expect(tags.length).toBeGreaterThan(0);
      expect(emoji.length).toBeGreaterThan(0);
    }
  });
});
