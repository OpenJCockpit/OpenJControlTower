import { execFileSync } from 'node:child_process';
import { existsSync, mkdtempSync, readdirSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { expect, test } from '@playwright/test';
import { as, users } from './support';

const gitUrl = 'http://git-server:8080/git/e2e-repo.git';
const git = { username: 'e2e-git', password: 'e2e-only-git' };
const git_ = (...args: string[]) => execFileSync('git', args, { encoding: 'utf8' });
const detailed = (topic: string) =>
  `${topic}: platform engineers in three product teams, measured by a 30% shorter lead time per change.`;

test('Git URLs with embedded credentials are refused', async ({ request }) => {
  const headers = await as(request, users.editor);
  const response = await request.post('/api/projects', {
    headers,
    data: {
      name: 'Leaky URL',
      gitUrl: 'https://user:token@git.example.org/team/repo.git',
      credentialType: 'NONE',
      cockpitProjectId: 'cockpit',
      workflowId: 'wf',
    },
  });
  expect(response.status()).toBe(400);
  expect((await response.json()).error).toContain('may not contain credentials');
});

test('an editor runs discovery from repository to dispatch', async ({ request }) => {
  const headers = await as(request, users.editor);
  const repository = {
    name: 'E2E project',
    gitUrl,
    credentialType: 'USERNAME_PASSWORD',
    gitUsername: git.username,
    cockpitProjectId: 'cockpit-e2e',
    workflowId: 'wf-e2e',
  };
  let projectId = '';
  let interviewId = '';
  let specs: Array<{ id: string; title: string; status: string; branch: string | null; dependsOn: string[] }> = [];

  await test.step('register the Git repository', async () => {
    // Connection check: a single smart-HTTP request from the backend, before anything is stored.
    const wrong = await request.post('/api/projects/connection-check', {
      headers,
      data: { gitUrl, credentialType: 'USERNAME_PASSWORD', gitUsername: git.username, secret: 'wrong-password' },
    });
    expect(await wrong.json()).toMatchObject({ reachable: false, outcome: 'AUTH_FAILED', message: 'The server rejected the credentials (HTTP 401).' });
    const right = await request.post('/api/projects/connection-check', {
      headers,
      data: { gitUrl, credentialType: 'USERNAME_PASSWORD', gitUsername: git.username, secret: git.password },
    });
    expect(await right.json()).toMatchObject({ reachable: true, outcome: 'OK' });

    const created = await request.post('/api/projects', { headers, data: { ...repository, secret: git.password } });
    expect(created.status()).toBe(201);
    const project = await created.json();
    projectId = project.id;
    expect(project).toMatchObject({ name: 'E2E project', hasSecret: true, defaultBranch: 'main' });
    // The secret is write-only.
    expect(JSON.stringify(project)).not.toContain(git.password);

    // One repository per OpenJCockpit project.
    const duplicate = await request.post('/api/projects', { headers, data: { ...repository, secret: git.password } });
    expect(duplicate.status()).toBe(409);

    // Editing without a secret keeps the stored one, and the check can reuse it.
    const reuse = await request.post('/api/projects/connection-check', {
      headers,
      data: { gitUrl, credentialType: 'USERNAME_PASSWORD', gitUsername: git.username, projectId },
    });
    expect(await reuse.json()).toMatchObject({ reachable: true, outcome: 'OK' });
    const saved = await request.put(`/api/projects/${projectId}`, { headers, data: repository });
    expect(saved.status()).toBe(200);
    expect(await saved.json()).toMatchObject({ hasSecret: true });
  });

  await test.step('complete the project interview, including an adaptive follow-up', async () => {
    const templates = (await (await request.get('/api/templates', { headers })).json()) as Array<{ id: string; name: string; kind: string }>;
    const template = templates.find(t => t.kind === 'PROJECT' && t.name === 'Project discovery');
    expect(template, 'seeded project template').toBeDefined();
    const started = await request.post('/api/interviews', { headers, data: { projectId, templateId: template!.id } });
    expect(started.status()).toBe(201);
    let interview = await started.json();
    interviewId = interview.id;
    const answer = async (text: string) => {
      const response = await request.post(`/api/interviews/${interviewId}/answers`, { headers, data: { answer: text } });
      expect(response.status()).toBe(200);
      return response.json();
    };

    expect(interview.currentQuestion).toBe('What is the measurable project outcome and who benefits?');
    interview = await answer('Faster delivery');
    // Too vague for a required question: the assistant asks a follow-up instead of advancing.
    expect(interview.currentQuestion).toContain('Can you make this more concrete');
    interview = await answer(detailed('Outcome'));

    for (const next of [
      'What is included and explicitly out of scope?',
      'Which architecture, integrations, technologies and constraints apply?',
      'Which ISO 25010, security and acceptance requirements are important?',
    ]) {
      expect(interview.currentQuestion).toBe(next);
      interview = await answer(detailed(next));
    }
    expect(interview.status).toBe('COMPLETE');
  });

  await test.step('generate three dependent spec drafts', async () => {
    const generated = await request.post(`/api/interviews/${interviewId}/generate`, { headers, data: {} });
    expect(generated.status()).toBe(200);
    specs = await generated.json();
    expect(specs).toHaveLength(3);
    expect(specs.map(s => s.status)).toEqual(['DRAFT', 'DRAFT', 'DRAFT']);
    const listed = await (await request.get(`/api/projects/${projectId}/specs`, { headers })).json();
    expect(listed).toHaveLength(3);
  });

  await test.step('approve the root spec, which pushes its own branch with one commit per file', async () => {
    const root = specs.find(s => s.title.includes('Define acceptance contract'))!;
    expect(root.dependsOn).toEqual([]);
    const approved = await request.post(`/api/specs/${root.id}/approve`, { headers });
    expect(approved.status()).toBe(200);
    const spec = await approved.json();
    expect(spec.status).toBe('APPROVED');
    expect(spec.branch).toMatch(/^spec\/SPEC-/);

    const clone = mkdtempSync(`${tmpdir()}/e2e-clone-`);
    const authenticated = gitUrl.replace('http://', `http://${git.username}:${git.password}@`);
    git_('clone', '--quiet', '--branch', spec.branch, authenticated, clone);
    const folderRoot = `${clone}/specs`;
    const folder = readdirSync(folderRoot).find(name => name.startsWith('001-define-acceptance-contract'));
    expect(folder, 'spec folder on the pushed branch').toBeDefined();
    for (const file of ['spec.md', 'plan.md', 'tasks.md', 'review.md'])
      expect(existsSync(`${folderRoot}/${folder}/${file}`), file).toBe(true);
    expect(readFileSync(`${folderRoot}/${folder}/spec.md`, 'utf8')).toContain('workflow_id: wf-e2e');
    expect(readFileSync(`${folderRoot}/_index.md`, 'utf8')).toContain(`(${folder}/spec.md)`);
    // Five commits on top of main: four spec files and the index, each committed separately.
    expect(git_('-C', clone, 'rev-list', '--count', 'origin/main..HEAD').trim()).toBe('5');
    expect(git_('-C', clone, 'log', '-1', '--format=%an')).toContain('OpenJControlTower');
  });

  await test.step('the dependency graph marks only the approved root as ready', async () => {
    const graph = await (await request.get(`/api/projects/${projectId}/graph`, { headers })).json();
    expect(graph.nodes).toHaveLength(3);
    expect(graph.nodes.filter((n: { ready: boolean }) => n.ready)).toHaveLength(1);
  });

  await test.step('dispatch requires confirmation and never blindly retries', async () => {
    const proposal = await (await request.get(`/api/projects/${projectId}/queue/proposal`, { headers })).json();
    expect(proposal).toHaveLength(1);
    const dispatch = (repositoryPublished: boolean) =>
      request.post(`/api/projects/${projectId}/queue/dispatch`, { headers, data: { repositoryPublished } });
    expect((await dispatch(false)).status()).toBe(400);
    const outcomes = await (await dispatch(true)).json();
    // OpenJCockpit is disabled in this stack: the outcome is recorded as UNKNOWN and taken off the queue.
    expect(outcomes).toHaveLength(1);
    expect(outcomes[0].result).toContain('UNKNOWN');
    expect(await (await request.get(`/api/projects/${projectId}/queue/proposal`, { headers })).json()).toHaveLength(0);
  });

  await test.step('a feature interview can follow the completed project interview', async () => {
    const interviews = await (await request.get(`/api/projects/${projectId}/interviews`, { headers })).json();
    expect(interviews.find((i: { id: string }) => i.id === interviewId).status).toBe('GENERATED');
    const templates = (await (await request.get('/api/templates', { headers })).json()) as Array<{ id: string; kind: string }>;
    const feature = templates.find(t => t.kind === 'FEATURE')!;
    const started = await request.post('/api/interviews', {
      headers,
      data: { projectId, templateId: feature.id, featureRequest: 'Export the dependency graph as PNG' },
    });
    expect(started.status()).toBe(201);
    expect((await started.json()).currentQuestion).toBe('Who needs this feature and which problem does it solve?');
  });
});
