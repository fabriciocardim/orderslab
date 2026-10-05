import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useNoChangeTimeout } from './useNoChangeTimeout';

describe('useNoChangeTimeout', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  const advance = (ms: number) => act(() => { vi.advanceTimersByTime(ms); });

  it('para após o limite sem mudança do fingerprint', () => {
    const { result } = renderHook(() => useNoChangeTimeout('a', false, 30_000));
    advance(29_000);
    expect(result.current.stopped).toBe(false);
    advance(1_500);
    expect(result.current.stopped).toBe(true);
  });

  it('mudança do fingerprint reinicia a contagem', () => {
    const { result, rerender } = renderHook(({ fp }) => useNoChangeTimeout(fp, false, 30_000), { initialProps: { fp: 'a' } });
    advance(29_000);
    rerender({ fp: 'b' });
    advance(29_000);
    expect(result.current.stopped).toBe(false);
    advance(2_000);
    expect(result.current.stopped).toBe(true);
  });

  it('estado final nunca é "parado por inatividade"', () => {
    const { result } = renderHook(() => useNoChangeTimeout('a', true, 30_000));
    advance(60_000);
    expect(result.current.stopped).toBe(false);
  });

  it('resume() recomeça a contagem', () => {
    const { result } = renderHook(() => useNoChangeTimeout('a', false, 30_000));
    advance(31_000);
    expect(result.current.stopped).toBe(true);
    act(() => result.current.resume());
    expect(result.current.stopped).toBe(false);
    advance(31_000);
    expect(result.current.stopped).toBe(true);
  });
});
