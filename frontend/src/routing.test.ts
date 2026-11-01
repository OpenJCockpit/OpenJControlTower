import { describe, expect, it } from 'vitest';
import { pageFromPath, pagePaths, projectFromSearch, urlFor } from './routing';

describe('routing', () => {
  it('maps every page to a unique path and back', () => {
    const paths=Object.values(pagePaths);
    expect(new Set(paths).size).toBe(paths.length);
    for (const [page,path] of Object.entries(pagePaths)) expect(pageFromPath(path)).toBe(page);
  });

  it('ignores trailing slashes and falls back to the overview for unknown paths', () => {
    expect(pageFromPath('/specs/')).toBe('specs');
    expect(pageFromPath('/does-not-exist')).toBe('overview');
  });

  it('keeps the selected project in the URL', () => {
    expect(urlFor('graph','')).toBe('/graph');
    expect(urlFor('queue','a b')).toBe('/queue?project=a%20b');
    expect(projectFromSearch('?project=a%20b')).toBe('a b');
    expect(projectFromSearch('')).toBe('');
  });
});
