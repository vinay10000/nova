// §25-§27 — keep integration layer replaceable. Native first, platform second, browser third.
export interface IntegrationAdapter { id: string; call(action: string, input: unknown, auth: { token: string }): Promise<unknown>; }
export class NativeAdapter implements IntegrationAdapter { id = 'native';
  async call(action: string, input: unknown) { return { note: `TODO native ${action}`, input }; } }
export class N8nAdapter implements IntegrationAdapter { id = 'n8n';
  async call(action: string, input: unknown) { return { note: `TODO n8n webhook ${action}`, input }; } }
