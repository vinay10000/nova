// F3 cloud code workspace on E2B free tier, via the official `e2b` SDK
// (create/files/commands go over the SDK's transports — plain REST only
// covers sandbox lifecycle). Stateless tools: create returns a sandboxId the
// model threads into write/exec/read/destroy calls. No secrets ever land in
// step metadata (stepMeta allowlist covers this).
import { Sandbox } from 'e2b';

export interface CodeSandboxProvider {
  id: string;
  create(): Promise<{ sandboxId: string }>;
  writeFile(sandboxId: string, path: string, content: string): Promise<unknown>;
  readFile(sandboxId: string, path: string): Promise<{ content: string }>;
  listFiles(sandboxId: string, path?: string): Promise<{ files: { name: string; path: string; isDir: boolean }[] }>;
  run(sandboxId: string, command: string, timeoutMs?: number): Promise<{ stdout: string; stderr: string; exitCode: number }>;
  destroy(sandboxId: string): Promise<void>;
}

/**
 * Sandboxes created per agent run, keyed by agentId. registerSandbox() is called by the
 * workspace_create/browser_task tools; executeAgent() drains the agent's set in a finally
 * block and kills every id — an agent that forgets workspace_destroy must never leak a
 * paid sandbox past its run. ponytail: keyed by agentId (not executionId) because
 * ToolContext carries agentId; two concurrent runs of the SAME agent share a bucket and
 * both get cleaned at the first finish — acceptable on the free tier, upgrade path is
 * threading executionId through ToolContext.
 */
const ownedSandboxes = new Map<string, Set<string>>();
export function registerSandbox(agentId: string, sandboxId: string): void {
  if (!/^[A-Za-z0-9_-]{4,128}$/.test(sandboxId)) return;
  const set = ownedSandboxes.get(agentId) ?? new Set<string>();
  set.add(sandboxId);
  ownedSandboxes.set(agentId, set);
}
export async function cleanupSandboxes(agentId: string): Promise<string[]> {
  const ids = [...(ownedSandboxes.get(agentId) ?? [])];
  ownedSandboxes.delete(agentId);
  await Promise.all(ids.map((id) => Sandbox.kill(id).catch(() => {})));
  return ids;
}
export function unregisterSandbox(agentId: string, sandboxId: string): void {
  const set = ownedSandboxes.get(agentId);
  if (!set) return;
  set.delete(sandboxId);
  if (!set.size) ownedSandboxes.delete(agentId);
}

function assertId(id: unknown): string {
  if (typeof id !== 'string' || !/^[A-Za-z0-9_-]{4,128}$/.test(id)) throw new Error('invalid_input: sandboxId is required');
  return id;
}

function assertPath(p: unknown): string {
  if (typeof p !== 'string' || !p.trim() || p.includes('..') || p.length > 500) {
    throw new Error('invalid_input: path must be a relative file path without ..');
  }
  return p.trim().replace(/^\//, '');
}

function requireKey(): void {
  if (!process.env.E2B_API_KEY) throw new Error('workspace_unconfigured: set E2B_API_KEY server-side');
}

export class E2BSandboxProvider implements CodeSandboxProvider {
  id = 'e2b';

  async create(): Promise<{ sandboxId: string }> {
    requireKey();
    const sbx = await Sandbox.create({ timeoutMs: 600_000 });
    return { sandboxId: sbx.sandboxId };
  }

  async writeFile(sandboxId: string, path: string, content: string): Promise<unknown> {
    requireKey();
    const id = assertId(sandboxId);
    const p = assertPath(path);
    if (content.length > 200_000) throw new Error('invalid_input: file too large (200KB cap)');
    const sbx = await Sandbox.connect(id);
    return sbx.files.write(p, content);
  }

  async readFile(sandboxId: string, path: string): Promise<{ content: string }> {
    requireKey();
    const id = assertId(sandboxId);
    const p = assertPath(path);
    const sbx = await Sandbox.connect(id);
    const content = await sbx.files.read(p);
    return { content: String(content ?? '').slice(0, 50_000) };
  }

  async listFiles(sandboxId: string, path?: string): Promise<{ files: { name: string; path: string; isDir: boolean }[] }> {
    requireKey();
    const id = assertId(sandboxId);
    const sbx = await Sandbox.connect(id);
    const entries = await sbx.files.list(path ? assertPath(path) : '.');
    return {
      files: (entries ?? []).slice(0, 200).map((e) => ({
        name: e.name,
        path: (e as { path?: string }).path ?? e.name,
        isDir: e.type === 'dir',
      })),
    };
  }

  async run(sandboxId: string, command: string, timeoutMs = 60_000): Promise<{ stdout: string; stderr: string; exitCode: number }> {
    requireKey();
    const id = assertId(sandboxId);
    if (typeof command !== 'string' || !command.trim() || command.length > 2000) {
      throw new Error('invalid_input: command is required (2KB cap)');
    }
    const t = Math.min(120_000, Math.max(5_000, timeoutMs));
    const sbx = await Sandbox.connect(id);
    const out = await sbx.commands.run(command.trim(), { timeoutMs: t });
    return {
      stdout: (out.stdout ?? '').slice(0, 20_000),
      stderr: (out.stderr ?? '').slice(0, 20_000),
      exitCode: out.exitCode ?? 0,
    };
  }

  async destroy(sandboxId: string): Promise<void> {
    requireKey();
    await Sandbox.kill(assertId(sandboxId)).catch(() => {});
  }
}

export function defaultSandbox(): CodeSandboxProvider {
  return new E2BSandboxProvider();
}
