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
  run(sandboxId: string, command: string, timeoutMs?: number): Promise<{ stdout: string; stderr: string; exitCode: number }>;
  destroy(sandboxId: string): Promise<void>;
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
