import { z } from 'zod';

// ---- A2UI surfaces (§45b generative UI) ---------------------------------------
//
// Nova has two generative-UI paths and this is the newer one.
//
//   §45  present_ui  → zod "blocks" → GenerativeUi.kt renders a fixed set of
//                      ten card shapes. The model picks a type and fills fields.
//
//   §45b present_surface → here → A2UI protocol frames → the agent describes a
//                      component tree the Nova catalog renders natively, with
//                      real data bindings, progressive streaming and actions
//                      travelling back to the model.
//
// The split is deliberate. §45 is a fixed vocabulary: safe, predictable, and
// the right answer for a table of numbers. A2UI is an open vocabulary: the model
// composes whatever the catalog offers and the app renders it as real Compose.
//
// Both go through the same trust boundary. The model NEVER emits A2UI JSON
// directly. It calls present_surface with typed content — "a trip to Tokyo, a
// checklist with five items" — and this file builds the protocol frames. That
// means the server owns every id, every JSON Pointer, and the root of the tree.
// A model that hallucinates a component name, an id, or a data path produces a
// frame that is rejected here rather than a screen that fails to render.

/** Protocol revision. Pinned on both ends; see the Android `NovaA2uiProtocolVersion`. */
export const A2UI_VERSION = 'v0.9.1';

/** Must match `NovaA2uiCatalogId` in NovaA2uiCatalog.kt. An identifier, never fetched. */
export const A2UI_CATALOG_ID = 'https://nova.app/catalogs/agentic/v1/catalog.json';

/** Frames are JSONL over the existing SSE stream. One card list per turn, one surface. */
const MAX_CARDS = 8;
/** Ceiling on the whole data model, mirroring the §45 32 KB payload cap. */
const MAX_PAYLOAD_BYTES = 32_000;

// ---- model-facing schema -----------------------------------------------------
//
// The model describes CONTENT. It never names a component, an id or a path —
// this file does. Keeping those server-side is what makes the schema small
// enough for a model to get right on the first try.

const hourSchema = z.object({
  label: z.string().min(1).max(24),
  temp: z.string().min(1).max(16),
});
const eventSchema = z.object({
  time: z.string().min(1).max(24),
  title: z.string().min(1).max(160),
  place: z.string().max(160).optional(),
});
const promptSchema = z.object({
  label: z.string().min(1).max(80),
  name: z.string().min(1).max(80),
  context: z.record(z.string(), z.union([z.string(), z.number(), z.boolean()])).optional(),
});

const cardSchema = z.discriminatedUnion('kind', [
  z.object({
    kind: z.literal('trip'),
    eyebrow: z.string().max(60).optional(),
    destination: z.string().min(1).max(120),
    dates: z.string().max(80).optional(),
    leg: z.string().max(120).optional(),
    legStatus: z.string().max(40).optional(),
    note: z.string().max(300).optional(),
  }),
  z.object({
    kind: z.literal('stay'),
    eyebrow: z.string().max(60).optional(),
    name: z.string().min(1).max(160),
    address: z.string().max(200).optional(),
    checkIn: z.string().max(60).optional(),
    checkOut: z.string().max(60).optional(),
    imageUrl: z.string().max(500).regex(/^https:\/\//i, 'image_must_be_https').optional(),
  }),
  z.object({
    kind: z.literal('weather'),
    eyebrow: z.string().max(60).optional(),
    place: z.string().max(80).optional(),
    now: z.string().min(1).max(16),
    condition: z.string().max(80).optional(),
    high: z.string().max(16).optional(),
    low: z.string().max(16).optional(),
    precip: z.string().max(24).optional(),
    wind: z.string().max(40).optional(),
    hours: z.array(hourSchema).max(8).optional(),
  }),
  z.object({
    kind: z.literal('agenda'),
    eyebrow: z.string().max(60).optional(),
    heading: z.string().min(1).max(120),
    events: z.array(eventSchema).max(12).optional(),
  }),
  z.object({
    kind: z.literal('checklist'),
    eyebrow: z.string().max(60).optional(),
    items: z.array(z.string().min(1).max(120)).min(1).max(20),
    done: z.array(z.boolean()).max(20).optional(),
    note: z.string().max(120).optional(),
  }),
  z.object({
    kind: z.literal('approval'),
    provider: z.string().min(1).max(120),
    summary: z.string().max(200).optional(),
    amount: z.string().min(1).max(40),
    instrument: z.string().max(120).optional(),
    state: z.enum(['pending', 'verifying', 'approved', 'declined']).default('pending'),
    /** Called when the user confirms. The model should be listening for this name. */
    confirmName: z.string().max(80).optional(),
    declineName: z.string().max(80).optional(),
  }),
  z.object({
    kind: z.literal('actions'),
    eyebrow: z.string().max(60).optional(),
    prompts: z.array(promptSchema).min(1).max(4),
  }),
]);

export const surfaceInputSchema = z.object({ cards: z.array(cardSchema).min(1).max(MAX_CARDS) });
export type SurfaceCard = z.infer<typeof cardSchema>;

// ---- frame construction ------------------------------------------------------

type Json = Record<string, unknown>;

/** A JSON Pointer into the surface data model. A2UI bindings are pointers, never literals. */
const ptr = (path: string): Json => ({ path });

const envelope = (body: Json): Json => ({ version: A2UI_VERSION, ...body });

/**
 * Deterministic, collision-free ids.
 *
 * Ids are server-assigned, so a model can never reuse one and a card can never
 * clobber another. The index is enough: a surface is rebuilt wholesale each
 * turn, so there is no history to collide with.
 */
const id = (kind: string, index: number): string => `c${index}_${kind}`;

/** Component name per card kind. These are the names in Nova's catalog. */
const COMPONENT: Record<SurfaceCard['kind'], string> = {
  trip: 'NovaTrip',
  stay: 'NovaStay',
  weather: 'NovaWeather',
  agenda: 'NovaAgenda',
  checklist: 'NovaChecklist',
  approval: 'NovaApproval',
  actions: 'NovaActions',
};

/**
 * Kinds that accept an `eyebrow`.
 *
 * Not all of them, and the difference is deliberate: the approval card leads
 * with the provider's name, so a second label above it would say nothing. The
 * catalog is the record of which components declare which properties, and the
 * server is the only thing that can read it — so this list lives here rather
 * than being sent and ignored.
 */
const HAS_EYEBROW = new Set<SurfaceCard['kind']>(['trip', 'stay', 'weather', 'agenda', 'checklist', 'actions']);

const slug = (value: string): string => value.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '').slice(0, 40) || 'x';

/**
 * One action property. The `label` is not decoration: the app reads it back out
 * of the event context to prefill the composer, so a button reads back as the
 * sentence the model meant rather than a de-slugified action id.
 */
const action = (name: string, label: string, context: Record<string, unknown>): Json => ({
  event: { name, context: { ...context, label } },
});

/**
 * Turn validated cards into the three protocol frames that build one surface.
 *
 * Split into create → components → data on purpose. The client creates an empty
 * surface, lays the tree down with bindings that point at nothing yet, then
 * fills the data model. That ordering is what makes progressive rendering
 * work: the card frames appear immediately and fill in as the data lands, which
 * is exactly how the reference flow looks.
 */
export function envelopesFromSurfaceInput(input: unknown, surfaceId: string): Json[] {
  const parsed = surfaceInputSchema.safeParse(input);
  if (!parsed.success) return [];
  const cards = parsed.data.cards;
  if (JSON.stringify(cards).length > MAX_PAYLOAD_BYTES) return [];

  const components: Json[] = [];
  const model: Record<string, unknown> = {};
  const children: string[] = [];

  cards.forEach((card, index) => {
    const componentId = id(card.kind, index);
    children.push(componentId);
    const dataKey = slug(card.kind);
    const data: Record<string, unknown> = {};
    // Binds a property to the data model, and keeps the value there too, so the
    // literal never leaks into the component tree — one source of truth.
    const bind = (prop: string, value: unknown): Json | undefined => {
      if (value === undefined || value === null) return undefined;
      data[prop] = value;
      return ptr(`/${dataKey}/${prop}`);
    };

    const props: Record<string, unknown> = {};
    // Eyebrow is a static property: the label is part of the card's identity,
    // not something the data model should be able to rewrite. Sent only for
    // the kinds that declare it — see HAS_EYEBROW.
    if (HAS_EYEBROW.has(card.kind) && 'eyebrow' in card && card.eyebrow) props.eyebrow = card.eyebrow;

    const put = (prop: string, value: unknown): void => {
      const bound = bind(prop, value);
      if (bound) props[prop] = bound;
    };

    switch (card.kind) {
      case 'trip':
        put('destination', card.destination);
        put('dates', card.dates);
        put('leg', card.leg);
        put('legStatus', card.legStatus);
        put('note', card.note);
        break;
      case 'stay':
        put('name', card.name);
        put('address', card.address);
        put('checkIn', card.checkIn);
        put('checkOut', card.checkOut);
        put('imageUrl', card.imageUrl);
        break;
      case 'weather':
        put('place', card.place);
        put('now', card.now);
        put('condition', card.condition);
        put('high', card.high);
        put('low', card.low);
        put('precip', card.precip);
        put('wind', card.wind);
        put('hours', card.hours);
        break;
      case 'agenda':
        put('heading', card.heading);
        put('events', card.events);
        break;
      case 'checklist': {
        put('items', card.items);
        // Two-way binding. The `done` path is what makes the checklist
        // interactive rather than decorative: the client writes the toggled
        // array back to this exact pointer, and the agent receives a
        // dataModelUpdate it can reason about. Omit `done` and the component
        // renders read-only, which is the honest answer for a list the model
        // did not bind.
        put('done', card.done);
        put('note', card.note);
        break;
      }
      case 'approval': {
        put('provider', card.provider);
        put('summary', card.summary);
        put('amount', card.amount);
        put('instrument', card.instrument);
        put('state', card.state);
        // Static enum, not a binding: the client drives this state, and a model
        // that re-sent it mid-flow would fight the user's own taps.
        if (card.state) props.state = card.state;
        // `label` rides in the event context on purpose. The app turns a tap into
        // a line in the composer, and the only honest source for those words is
        // the ones the catalog already used on the button.
        if (card.confirmName) props.confirm = action(card.confirmName, 'Confirm', { surface: 'a2ui' });
        if (card.declineName) props.decline = action(card.declineName, 'Not now', { surface: 'a2ui' });
        break;
      }
      case 'actions': {
        put('prompts', card.prompts.map((p) => ({
          label: p.label,
          action: action(p.name, p.label, { ...(p.context ?? {}), surface: 'a2ui' }),
        })));
        break;
      }
    }

    components.push({ id: componentId, component: COMPONENT[card.kind], ...props });
    model[dataKey] = data;
  });

  if (!components.length) return [];

  return [
    // 1. An empty surface. `root` is mandatory and is the only structural node
    //    the model never sees.
    envelope({ createSurface: { surfaceId, catalogId: A2UI_CATALOG_ID } }),
    // 2. The tree, with bindings pointing at a data model that does not exist yet.
    envelope({
      updateComponents: {
        surfaceId,
        components: [{ id: 'root', component: 'Column', children }, ...components],
      },
    }),
    // 3. The data. Everything the bindings above point at.
    envelope({ updateDataModel: { surfaceId, value: model } }),
  ];
}

/**
 * Client → server frames the app is allowed to send.
 *
 * The app is a renderer, not a second agent. It may only echo actions back with
 * the catalog's surface in them. Anything else is dropped: a surface must not
 * be able to invent a message type the server has never agreed to.
 */
export function isAcceptableClientFrame(frame: unknown): frame is { name: string; surfaceId: string } {
  if (!frame || typeof frame !== 'object') return false;
  const record = frame as Record<string, unknown>;
  return typeof record.name === 'string' && typeof record.surfaceId === 'string' && record.name.length <= 80;
}
