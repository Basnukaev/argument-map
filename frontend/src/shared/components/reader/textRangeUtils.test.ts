import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { computeRangeOffsets, applyHighlight, removeHighlights } from './textRangeUtils';

describe('computeRangeOffsets', () => {
  let container: HTMLDivElement;

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
  });
  afterEach(() => {
    document.body.removeChild(container);
  });

  it('возвращает char offsets для selection в simple text', () => {
    container.innerHTML = '<p>Hello world test</p>';
    const textNode = container.querySelector('p')!.firstChild!;
    const range = document.createRange();
    range.setStart(textNode, 6);
    range.setEnd(textNode, 11);

    const result = computeRangeOffsets(container, range);
    expect(result).toEqual({ start: 6, end: 11, quote: 'world' });
  });

  it('пропускает HTML теги при подсчёте offsets', () => {
    container.innerHTML = '<p>Hello <em>bold</em> text</p>';
    const lastText = container.querySelector('p')!.lastChild!;
    const range = document.createRange();
    range.setStart(lastText, 1);
    range.setEnd(lastText, 5);

    const result = computeRangeOffsets(container, range);
    expect(result?.quote).toBe('text');
    expect(result?.start).toBe(11);
    expect(result?.end).toBe(15);
  });

  it('возвращает null для collapsed range', () => {
    container.innerHTML = '<p>Hello</p>';
    const textNode = container.querySelector('p')!.firstChild!;
    const range = document.createRange();
    range.setStart(textNode, 2);
    range.setEnd(textNode, 2);

    expect(computeRangeOffsets(container, range)).toBeNull();
  });

  it('возвращает null если endpoints вне container', () => {
    container.innerHTML = '<p>Hello</p>';
    const otherDiv = document.createElement('div');
    otherDiv.textContent = 'outside';
    document.body.appendChild(otherDiv);
    try {
      const range = document.createRange();
      range.setStart(otherDiv.firstChild!, 0);
      range.setEnd(otherDiv.firstChild!, 3);
      expect(computeRangeOffsets(container, range)).toBeNull();
    } finally {
      document.body.removeChild(otherDiv);
    }
  });
});

describe('applyHighlight', () => {
  let container: HTMLDivElement;

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
  });
  afterEach(() => {
    document.body.removeChild(container);
  });

  it('оборачивает text в <mark> по char offsets', () => {
    container.innerHTML = '<p>Hello world</p>';
    applyHighlight(container, 6, 11);
    expect(container.querySelector('mark.citation-highlight')?.textContent).toBe('world');
  });

  it('охватывает несколько text nodes (создаёт несколько <mark>)', () => {
    container.innerHTML = '<p>aaa<em>bbb</em>ccc</p>';
    // text nodes: 'aaa'(0-3), 'bbb'(3-6), 'ccc'(6-9)
    // highlight 2-7: последний 'a' + все 'bbb' + первый 'c' = "abbbc"
    applyHighlight(container, 2, 7);
    const marks = container.querySelectorAll('mark.citation-highlight');
    expect(marks.length).toBe(3);
    expect(Array.from(marks).map((m) => m.textContent).join('')).toBe('abbbc');
  });
});

describe('removeHighlights', () => {
  let container: HTMLDivElement;
  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
  });
  afterEach(() => {
    document.body.removeChild(container);
  });

  it('удаляет mark.citation-highlight, восстанавливая plain text', () => {
    container.innerHTML = '<p>Hello world</p>';
    applyHighlight(container, 6, 11);
    expect(container.querySelector('mark')).not.toBeNull();
    removeHighlights(container);
    expect(container.querySelector('mark')).toBeNull();
    expect(container.querySelector('p')?.textContent).toBe('Hello world');
  });
});
