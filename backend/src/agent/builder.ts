type JsonRecord = Record<string, unknown>;

export type NormalizedAgentSchedule = {
  type: 'once' | 'recurring' | 'run_now';
  frequency?: 'daily' | 'weekdays' | 'weekly' | 'once';
  time?: string;
  timezone?: string;
};

export type NormalizedAgentDraft =
  | { questions: string[] }
  | {
      name: string;
      description?: string;
      goal: string;
      instructions: string;
      tools: string[];
      schedule?: NormalizedAgentSchedule;
    };

export function normalizeAgentDraft(raw: unknown, prompt: string, knownTools: string[]): NormalizedAgentDraft {
  const source = record(raw);
  const questions = strings(source?.questions);
  if (questions.length) return { questions };

  const goal = text(source?.goal) || text(prompt);
  if (!goal) return { questions: ['What outcome should this agent achieve?'] };

  const allowed = new Set(knownTools);
  const tools = strings(source?.tools).filter((tool) => allowed.has(tool)).slice(0, 20);
  if (!tools.length) return { questions: ['Which available tool should this agent use?'] };

  const schedule = normalizeSchedule(source?.schedule);
  const description = text(source?.description)?.slice(0, 500);
  return {
    name: deriveName(text(source?.name), goal, prompt),
    ...(description ? { description } : {}),
    goal: goal.slice(0, 2000),
    instructions: (text(source?.instructions) || goal).slice(0, 4000),
    tools,
    ...(schedule ? { schedule } : {}),
  };
}

export function createFallbackAgentDraft(prompt: string, knownTools: string[]): NormalizedAgentDraft {
  const lower = prompt.toLowerCase();
  const explicit = knownTools.filter((tool) => lower.includes(tool.toLowerCase()));
  const tools = [...new Set([
    ...explicit,
    ...(['web search', 'search the web', 'internet search'].some((phrase) => lower.includes(phrase)) ? ['web_search'] : []),
  ])].filter((tool) => knownTools.includes(tool));
  return normalizeAgentDraft({ goal: prompt, tools, schedule: fallbackSchedule(prompt) }, prompt, knownTools);
}

function fallbackSchedule(value: string): NormalizedAgentSchedule | undefined {
  const match = value.match(/\bat\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?/i);
  let time: string | undefined;
  if (match) {
    let hour = Number(match[1]);
    const minute = Number(match[2] ?? '0');
    const meridiem = match[3]?.toLowerCase();
    if (hour <= 23 && minute <= 59 && (!meridiem || hour <= 12)) {
      if (meridiem === 'pm' && hour < 12) hour += 12;
      if (meridiem === 'am' && hour === 12) hour = 0;
      time = `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`;
    }
  }
  const lower = value.toLowerCase();
  const frequency = /weekday|monday.{0,20}friday|mon.{0,5}fri/.test(lower)
    ? 'weekdays'
    : /every day|daily/.test(lower)
      ? 'daily'
      : /every week|weekly/.test(lower)
        ? 'weekly'
        : undefined;
  if (frequency) return { type: 'recurring', frequency, ...(time ? { time } : {}) };
  return time ? { type: 'once', frequency: 'once', time } : undefined;
}

function normalizeSchedule(value: unknown): NormalizedAgentSchedule | undefined {
  if (typeof value === 'string') return scheduleFromCron(value);
  const source = record(value);
  if (!source) return undefined;
  const cron = text(source.cron);
  if (cron) return scheduleFromCron(cron);

  const type = text(source.type);
  const frequency = text(source.frequency);
  const time = normalizeTime(text(source.time));
  const timezone = text(source.timezone);
  if (!type && !frequency && !time && !timezone) return undefined;
  return {
    type: type === 'once' || type === 'recurring' || type === 'run_now' ? type : 'run_now',
    ...(frequency === 'daily' || frequency === 'weekdays' || frequency === 'weekly' || frequency === 'once' ? { frequency } : {}),
    ...(time ? { time } : {}),
    ...(timezone ? { timezone } : {}),
  };
}

function scheduleFromCron(value: string): NormalizedAgentSchedule | undefined {
  const fields = value.trim().split(/\s+/);
  if (fields.length !== 5) return undefined;
  const [minute, hour, day, month, weekday] = fields;
  if (!minute || !hour || !day || !month || !weekday) return undefined;
  if (!/^\d+$/.test(minute) || !/^\d+$/.test(hour)) return undefined;
  const time = `${hour.padStart(2, '0')}:${minute.padStart(2, '0')}`;
  if (weekday !== '*' && day === '*' && month === '*') {
    return {
      type: 'recurring',
      frequency: isWeekdays(weekday) ? 'weekdays' : 'weekly',
      time,
    };
  }
  if (weekday === '*' && day === '*' && month === '*') return { type: 'recurring', frequency: 'daily', time };
  return { type: 'once', frequency: 'once', time };
}

function isWeekdays(value: string): boolean {
  const days = new Set<number>();
  for (const part of value.split(',')) {
    const range = part.match(/^(\d)-(\d)$/);
    if (range) {
      const start = Number(range[1]);
      const end = Number(range[2]);
      for (let day = start; day <= end; day++) days.add(day);
    } else if (/^\d$/.test(part)) {
      days.add(Number(part));
    }
  }
  return days.size === 5 && [1, 2, 3, 4, 5].every((day) => days.has(day));
}

function normalizeTime(value: string): string | undefined {
  const match = value.match(/^(\d{1,2})(?::(\d{2}))?$/);
  if (!match) return undefined;
  const hour = Number(match[1]);
  const minute = Number(match[2] ?? '0');
  if (hour > 23 || minute > 59) return undefined;
  return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`;
}

function deriveName(explicit: string, goal: string, prompt: string): string {
  const source = explicit || firstClause(stripScheduleLead(goal)) || firstClause(stripScheduleLead(prompt)) || 'New agent';
  const shortened = source.length <= 80 ? source : `${source.slice(0, 77).replace(/\s+\S*$/, '')}…`;
  return shortened.charAt(0).toUpperCase() + shortened.slice(1);
}

function stripScheduleLead(value: string): string {
  return value
    .replace(/^(?:every\s+)?(?:day|weekday|weekdays|week|daily|weekly)(?:\s+at\s+[^,]+)?\s*,?\s*/i, '')
    .replace(/^at\s+[^,]+,\s*/i, '')
    .trim();
}

function firstClause(value: string): string {
  return value.split(/[.!?;]|\s+and\s+|\s+then\s+/i)[0]?.replace(/[,:]\s*$/, '').trim() ?? '';
}

function record(value: unknown): JsonRecord | undefined {
  return value !== null && typeof value === 'object' && !Array.isArray(value) ? value as JsonRecord : undefined;
}

function text(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}

function strings(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string').map((item) => item.trim()).filter(Boolean) : [];
}
