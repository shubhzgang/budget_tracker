import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { EmojiPicker, EMOJI_TAGS } from './EmojiPicker';

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

describe('EmojiPicker input', () => {
  const lastChange = (spy: ReturnType<typeof vi.fn>) =>
    spy.mock.calls[spy.mock.calls.length - 1][0];

  it('propagates a pasted emoji via onChange', () => {
    const onChange = vi.fn();
    render(<EmojiPicker value="😀" onChange={onChange} />);

    const input = screen.getByLabelText('Emoji');
    fireEvent.change(input, { target: { value: '🧪' } });

    expect(onChange).toHaveBeenCalledWith('🧪');
  });

  it('trims surrounding whitespace from the pasted value', () => {
    const onChange = vi.fn();
    render(<EmojiPicker value="" onChange={onChange} />);

    fireEvent.change(screen.getByLabelText('Emoji'), { target: { value: '  🎉  ' } });

    expect(lastChange(onChange)).toBe('🎉');
  });

  it('keeps only the first emoji when multiple are pasted', () => {
    const onChange = vi.fn();
    render(<EmojiPicker value="" onChange={onChange} />);

    fireEvent.change(screen.getByLabelText('Emoji'), { target: { value: '🍕🍔' } });

    expect(lastChange(onChange)).toBe('🍕');
  });

  it('keeps ZWJ sequences as a single emoji', () => {
    const onChange = vi.fn();
    render(<EmojiPicker value="" onChange={onChange} />);

    fireEvent.change(screen.getByLabelText('Emoji'), { target: { value: '👨‍👩‍👧 extra' } });

    expect(lastChange(onChange)).toBe('👨‍👩‍👧');
  });

  it('clears the value for blank input', () => {
    const onChange = vi.fn();
    render(<EmojiPicker value="😀" onChange={onChange} />);

    fireEvent.change(screen.getByLabelText('Emoji'), { target: { value: '   ' } });

    expect(lastChange(onChange)).toBe('');
  });
});
