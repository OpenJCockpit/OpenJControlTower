import { execFileSync } from 'node:child_process';
import { existsSync, mkdtempSync, readdirSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { expect, test } from '@playwright/test';
import { alert, openSection, signIn, users } from './support';

const gitUrl = 'http://git-server:8080/git/e2e-repo.git';
const git = { username: 'e2e-git', password: 'e2e-only-git' };
const git_ = (...args: string[]) => execFileSync('git', args, { encoding: 'utf8' });
const detailed = (topic: string) =>
  `${topic}: platform engineers in three product teams, measured by a 30% shorter lead time per change.`;

test('Git URLs with embedded credentials are refused', async ({ page }) => {
  await signIn(page, users.editor);
  await openSection(page, 'Repositories');
  await page.getByLabel('Project name').fill('Leaky URL');
  await page.getByLabel('Git repository URL').fill('https://user:token@git.example.org/team/repo.git');
  await page.getByLabel('OpenJCockpit project ID').fill('cockpit');
  await page.getByLabel('OpenJCockpit workflow ID').fill('wf');
  await page.getByRole('button', { name: 'Connect repository' }).click();
  await expect(alert(page)).toContainText('may not contain credentials');
});

test('an editor runs discovery from repository to dispatch', async ({ page }) => {
  await signIn(page, users.editor);

  await test.step('register the Git repository', async () => {
    await openSection(page, 'Repositories');
    await page.getByLabel('Project name').fill('E2E project');
    await page.getByLabel('Git repository URL').fill(gitUrl);
    await page.getByLabel('Authentication').selectOption('USERNAME_PASSWORD');
    await page.getByLabel('Git username').fill(git.username);
    await page.getByLabel('OpenJCockpit project ID').fill('cockpit-e2e');
    await page.getByLabel('OpenJCockpit workflow ID').fill('wf-e2e');

    // Connection check: a single smart-HTTP request from the backend, before anything is stored.
    const checkResult = page.getByRole('status');
    await page.getByLabel('Password', { exact: true }).fill('wrong-password');
    await page.getByRole('button', { name: 'Check connection' }).click();
    await expect(checkResult).toHaveText('The server rejected the credentials (HTTP 401).');
    await page.getByLabel('Password', { exact: true }).fill(git.password);
    await page.getByRole('button', { name: 'Check connection' }).click();
    await expect(checkResult).toHaveText('Connected: the repository is reachable and the credentials are accepted.');
    await page.getByRole('button', { name: 'Connect repository' }).click();
    await expect(alert(page)).toContainText('Repository registered.');
    await expect(page.locator('.repo-row', { hasText: 'E2E project' })).toContainText('Username and password stored');

    // The password is write-only: editing shows an empty field and saving keeps the stored one.
    await page.getByRole('button', { name: 'Edit E2E project' }).click();
    await expect(page.getByLabel('Password', { exact: true })).toHaveValue('');
    await page.getByRole('button', { name: 'Check connection' }).click();
    await expect(page.getByRole('status')).toContainText('Connected');
    await page.getByRole('button', { name: 'Save repository' }).click();
    await expect(alert(page)).toContainText('Repository saved.');
    await expect(page.locator('.repo-row', { hasText: 'E2E project' })).toContainText('Username and password stored');
  });

  await test.step('complete the project interview, including an adaptive follow-up', async () => {
    await openSection(page, 'Discovery interviews');
    await expect(page.getByLabel('Active project')).toHaveValue(/.+/);
    await page.getByLabel('Interview template').selectOption({ label: 'Project discovery (PROJECT)' });
    await page.getByRole('button', { name: 'Start interview' }).click();
    const question = page.locator('.session h3');
    const answer = page.getByPlaceholder('Give a concrete answer…');
    const submit = page.getByRole('button', { name: 'Submit answer' });

    await expect(question).toHaveText('What is the measurable project outcome and who benefits?');
    await answer.fill('Faster delivery');
    await submit.click();
    // Too vague for a required question: the assistant asks a follow-up instead of advancing.
    await expect(question).toContainText('Can you make this more concrete');
    await answer.fill(detailed('Outcome'));
    await submit.click();

    for (const next of [
      'What is included and explicitly out of scope?',
      'Which architecture, integrations, technologies and constraints apply?',
      'Which ISO 25010, security and acceptance requirements are important?',
    ]) {
      await expect(question).toHaveText(next);
      await answer.fill(detailed(next));
      await submit.click();
    }
    await expect(question).toHaveText('Interview completed');
  });

  await test.step('generate three dependent spec drafts', async () => {
    await page.getByRole('button', { name: 'Generate spec drafts' }).click();
    await expect(page.getByRole('heading', { level: 1, name: 'Specifications' })).toBeVisible();
    await expect(page.locator('details.spec')).toHaveCount(3);
    await expect(page.locator('details.spec .state-draft')).toHaveCount(3);
  });

  await test.step('approve the root spec, which pushes its own branch with one commit per file', async () => {
    const root = page.locator('details.spec', { hasText: 'Define acceptance contract' });
    await root.locator('summary').click();
    await expect(root).toContainText('Dependencies: None');
    await root.getByRole('button', { name: 'Approve and push branch' }).click();
    await expect(alert(page)).toContainText('Spec pushed to branch spec/SPEC-');
    await expect(root.locator('.state-approved')).toBeVisible();
    const branch = (await root.locator('summary small').textContent())!.match(/branch (spec\/SPEC-[0-9a-f-]+)/)![1];

    const clone = mkdtempSync(`${tmpdir()}/e2e-clone-`);
    const authenticated = gitUrl.replace('http://', `http://${git.username}:${git.password}@`);
    git_('clone', '--quiet', '--branch', branch, authenticated, clone);
    const specs = `${clone}/specs`;
    const folder = readdirSync(specs).find(name => name.startsWith('001-define-acceptance-contract'));
    expect(folder, 'spec folder on the pushed branch').toBeDefined();
    for (const file of ['spec.md', 'plan.md', 'tasks.md', 'review.md'])
      expect(existsSync(`${specs}/${folder}/${file}`), file).toBe(true);
    expect(readFileSync(`${specs}/${folder}/spec.md`, 'utf8')).toContain('workflow_id: wf-e2e');
    expect(readFileSync(`${specs}/_index.md`, 'utf8')).toContain(`(${folder}/spec.md)`);
    // Five commits on top of main: four spec files and the index, each committed separately.
    expect(git_('-C', clone, 'rev-list', '--count', 'origin/main..HEAD').trim()).toBe('5');
    expect(git_('-C', clone, 'log', '-1', '--format=%an')).toContain('OpenJControlTower');
  });

  await test.step('the dependency graph marks only the approved root as ready', async () => {
    await openSection(page, 'Dependency graph');
    const graph = page.getByRole('img', { name: 'Spec dependency graph' });
    await expect(graph.locator('g')).toHaveCount(3);
    await expect(graph.getByText(/READY/)).toHaveCount(1);
  });

  await test.step('dispatch requires confirmation and never blindly retries', async () => {
    await openSection(page, 'Dispatch queue');
    await expect(page.getByText('1 ready')).toBeVisible();
    const dispatch = page.getByRole('button', { name: 'Dispatch ready specs' });
    await expect(dispatch).toBeDisabled();
    await page.getByRole('checkbox').check();
    await dispatch.click();
    // OpenJCockpit is disabled in this stack: the outcome is recorded as UNKNOWN and taken off the queue.
    await expect(alert(page)).toContainText('UNKNOWN');
    await expect(page.getByText('0 ready')).toBeVisible();
  });

  await test.step('a feature interview can follow the completed project interview', async () => {
    await openSection(page, 'Discovery interviews');
    await expect(page.getByRole('button', { name: /Project discovery.*GENERATED/ })).toBeVisible();
    await page.getByLabel('Interview template').selectOption({ label: 'Feature discovery (FEATURE)' });
    await page.getByPlaceholder('Describe the feature you\'d like to create…').fill('Export the dependency graph as PNG');
    await page.getByRole('button', { name: 'Start interview' }).click();
    await expect(page.locator('.session h3')).toHaveText('Who needs this feature and which problem does it solve?');
  });
});
