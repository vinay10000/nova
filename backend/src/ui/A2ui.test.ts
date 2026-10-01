import { test } from 'node:test';
import assert from 'node:assert/strict';
import { envelopesFromSurfaceInput, isAcceptableClientFrame, A2UI_CATALOG_ID, A2UI_VERSION } from './A2ui.js';

// §45b A2UI surface frames.
//
// These are the contract between two independently versioned halves: the model
// describes content, this file builds the protocol, and the Android engine
// renders it. The assertions here are the ones that actually matter, because a
// silent mistake in any of them produces an empty chat bubble rather than an
// error: a missing root, a binding with no path, a frame the parser rejects.

type Envelope = Record<string, any>;

const frames = (input: unknown, id = 's_test') => envelopesFromSurfaceInput(input, id) as Envelope[];

/**
 * Pull one section out of a frame.
 *
 * Accepts either a whole surface (the three frames) or a single frame, because
 * most assertions care about one section and do not care which frame it is in —
 * except the ordering test, which indexes directly.
 */
const body = (source: Envelope | Envelope[], key: string): Envelope =>
  (Array.isArray(source) ? source.find((f) => key in f) : source)?.[key] as Envelope;

test('emits the three frames in order: create, components, data', () => {
  const out = frames({ cards: [{ kind: 'trip', destination: 'Tokyo, Japan', dates: 'Jun 3 – Jun 9' }] });
  assert.equal(out.length, 3, 'a surface is three frames, never one');
  assert.ok('createSurface' in out[0]);
  assert.ok('updateComponents' in out[1]);
  assert.ok('updateDataModel' in out[2]);
});

test('every frame carries the pinned protocol version', () => {
  const out = frames({ cards: [{ kind: 'trip', destination: 'Tokyo' }] });
  for (const frame of out) assert.equal(frame.version, A2UI_VERSION);
});

test('createSurface names the catalog the client registered', () => {
  const create = body(frames({ cards: [{ kind: 'trip', destination: 'Tokyo' }] }), 'createSurface');
  assert.equal(create.surfaceId, 's_test');
  assert.equal(create.catalogId, A2UI_CATALOG_ID);
});

test('the component tree is rooted and every card is its child', () => {
  const update = body(
    frames({
      cards: [
        { kind: 'trip', destination: 'Tokyo' },
        { kind: 'agenda', heading: 'Thursday' },
      ],
    }),
    'updateComponents',
  );
  const components = update.components as Envelope[];
  const root = components.find((c) => c.id === 'root');
  assert.ok(root, 'A2UI requires a component with id "root"');
  assert.equal(root.component, 'Column');
  assert.equal(root.children.length, 2, 'both cards hang off the root');

  // Every child id must exist, or the engine renders a hole.
  for (const childId of root.children as string[]) {
    assert.ok(components.some((c) => c.id === childId), `dangling child ${childId}`);
  }
});

test('card ids are unique even when two cards share a kind', () => {
  const update = body(
    frames({
      cards: [
        { kind: 'trip', destination: 'Tokyo' },
        { kind: 'trip', destination: 'Osaka' },
      ],
    }),
    'updateComponents',
  );
  const ids = (update.components as Envelope[]).map((c) => c.id);
  assert.equal(new Set(ids).size, ids.length, 'ids must not collide');
});

test('every dynamic property is a JSON Pointer, never a literal', () => {
  const update = body(frames({ cards: [{ kind: 'trip', destination: 'Tokyo, Japan', dates: 'Jun 3' }] }), 'updateComponents');
  const trip = (update.components as Envelope[]).find((c) => c.id.startsWith('c0_trip'))!;
  for (const key of ['destination', 'dates']) {
    assert.deepEqual(trip[key], { path: `/trip/${key}` }, `${key} must bind, not inline`);
  }
});

test('the data model contains a value for every binding', () => {
  const out = frames({ cards: [{ kind: 'trip', destination: 'Tokyo, Japan', leg: 'Flight MU7' }] });
  const data = body(out, 'updateDataModel');
  assert.deepEqual(data.value, { trip: { destination: 'Tokyo, Japan', leg: 'Flight MU7' } });
});

test('absent optional fields produce no binding and no data entry', () => {
  const out = frames({ cards: [{ kind: 'trip', destination: 'Tokyo' }] });
  const trip = (body(out, 'updateComponents').components as Envelope[]).find((c) => c.id.startsWith('c0_trip'))!;
  assert.ok(!('leg' in trip), 'an omitted field must not be bound to nothing');
  assert.ok(!('leg' in body(out, 'updateDataModel').value.trip));
});

test('eyebrow stays a static literal, never a binding', () => {
  const trip = (body(frames({ cards: [{ kind: 'trip', eyebrow: 'Your trip', destination: 'Tokyo' }] }), 'updateComponents')
    .components as Envelope[]).find((c) => c.id.startsWith('c0_trip'))!;
  assert.equal(trip.eyebrow, 'Your trip');
  assert.equal(typeof trip.eyebrow, 'string');
});

test('a checklist with done[] is two-way bound, without it is read-only', () => {
  const bound = body(frames({ cards: [{ kind: 'checklist', items: ['Jacket', 'Umbrella'], done: [true, false] }] }), 'updateComponents');
  const withDone = (bound.components as Envelope[]).find((c) => c.id.startsWith('c0_checklist'))!;
  assert.deepEqual(withDone.done, { path: '/checklist/done' }, 'a writable path is what makes the tick persist');

  const unbound = body(frames({ cards: [{ kind: 'checklist', items: ['Jacket'] }] }), 'updateComponents');
  const withoutDone = (unbound.components as Envelope[]).find((c) => c.id.startsWith('c0_checklist'))!;
  assert.ok(!('done' in withoutDone), 'no done means the client renders it read-only');
});

test('action prompts carry their own label back in the event context', () => {
  const update = body(
    frames({ cards: [{ kind: 'actions', prompts: [{ label: 'Add packing list', name: 'add_packing_list' }] }] }),
    'updateComponents',
  );
  const data = body(frames({ cards: [{ kind: 'actions', prompts: [{ label: 'Add packing list', name: 'add_packing_list' }] }] }), 'updateDataModel');
  const prompts = data.value.actions.prompts as Envelope[];
  assert.equal(prompts[0].action.event.name, 'add_packing_list');
  assert.equal(prompts[0].action.event.context.label, 'Add packing list', 'the app reads the label back from here');
  assert.ok(update.components.length > 0);
});

test('approval state is a static enum so a client tap is not overwritten', () => {
  const card = (body(frames({ cards: [{ kind: 'approval', provider: 'Kite Cabs', amount: '¥6,800', state: 'pending', confirmName: 'confirm_ride' }] }), 'updateComponents')
    .components as Envelope[]).find((c) => c.id.startsWith('c0_approval'))!;
  assert.equal(card.state, 'pending');
  assert.equal(card.confirm.event.name, 'confirm_ride');
  assert.equal(card.confirm.event.context.label, 'Confirm');
});

test('a stay photo must be https', () => {
  assert.equal(frames({ cards: [{ kind: 'stay', name: 'The Kanda House', imageUrl: 'http://insecure.example/x.jpg' }] }).length, 0);
  assert.equal(frames({ cards: [{ kind: 'stay', name: 'The Kanda House', imageUrl: 'https://cdn.example/x.jpg' }] }).length, 3);
});

test('unknown card kinds are rejected rather than rendered as a hole', () => {
  assert.equal(frames({ cards: [{ kind: 'invented' }] }).length, 0);
});

test('an empty or malformed card list produces no frames at all', () => {
  assert.equal(frames({ cards: [] }).length, 0);
  assert.equal(frames({}).length, 0);
  assert.equal(frames(null).length, 0);
  assert.equal(frames('not an object').length, 0);
});

test('a card missing its required field is rejected whole', () => {
  assert.equal(frames({ cards: [{ kind: 'trip' }] }).length, 0, 'a trip with no destination has nothing to draw');
  assert.equal(frames({ cards: [{ kind: 'checklist', items: [] }] }).length, 0);
});

test('the payload cap holds', () => {
  const cards = Array.from({ length: 8 }, (_, i) => ({
    kind: 'agenda' as const,
    heading: `Day ${i} ${'x'.repeat(100)}`,
    events: Array.from({ length: 12 }, (_, j) => ({ time: '10:00', title: 'y'.repeat(150), place: 'z'.repeat(150) })),
  }));
  assert.equal(frames({ cards }).length, 0, 'oversized turns are dropped, not truncated into a broken surface');
});

test('every frame is JSON-serialisable without a replacer', () => {
  for (const frame of frames({ cards: [{ kind: 'weather', now: '22°', hours: [{ label: 'Now', temp: '22°' }] }] })) {
    assert.deepEqual(JSON.parse(JSON.stringify(frame)), frame);
  }
});

test('the client may only echo an action it can name', () => {
  assert.equal(isAcceptableClientFrame({ name: 'add_packing_list', surfaceId: 's_1' }), true);
  assert.equal(isAcceptableClientFrame({ surfaceId: 's_1' }), false, 'an action with no name is not an action');
  assert.equal(isAcceptableClientFrame({ name: 'x' }), false, 'an action with no surface is not attributable');
  assert.equal(isAcceptableClientFrame(null), false);
  assert.equal(isAcceptableClientFrame('nope'), false);
});
