import assert from 'node:assert/strict';
import { createFallbackAgentDraft, normalizeAgentDraft } from './builder.js';
import { isTransientGeminiError } from '../ai/GeminiProvider.js';

const draft = normalizeAgentDraft(
  {
    goal: 'Every weekday at 9:00 AM, identify the top five most discussed stories on Hacker News, summarize them, and deliver a concise digest.',
    tools: ['web_search'],
    schedule: { cron: '0 9 * * 1-5' },
  },
  'Create a weekday Hacker News digest.',
  ['web_search'],
) as Exclude<ReturnType<typeof normalizeAgentDraft>, { questions: string[] }>;

assert.match(draft.name, /Hacker News/);
assert.ok(draft.name.length <= 80);
assert.equal(draft.instructions, draft.goal);
assert.deepEqual(draft.tools, ['web_search']);
assert.deepEqual(draft.schedule, { type: 'recurring', frequency: 'weekdays', time: '09:00' });

const questions = normalizeAgentDraft({ questions: ['Which source should it use?'] }, '', ['web_search']);
assert.deepEqual(questions, { questions: ['Which source should it use?'] });
assert.equal(isTransientGeminiError(new Error('Request timeout')), true);

const fallback = createFallbackAgentDraft(
  'Every weekday at 9:00 AM, use web search to summarize the top Hacker News stories.',
  ['web_search'],
) as Exclude<ReturnType<typeof normalizeAgentDraft>, { questions: string[] }>;
assert.deepEqual(fallback.tools, ['web_search']);
assert.deepEqual(fallback.schedule, { type: 'recurring', frequency: 'weekdays', time: '09:00' });

console.log('agent builder checks passed');
