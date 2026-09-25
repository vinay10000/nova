import assert from 'node:assert/strict';
import { MODEL_EFFORTS, isModelEffort, modelCatalog, resolveModelEffort } from './models.js';

assert.deepEqual([...MODEL_EFFORTS], ['minimal', 'low', 'medium', 'high']);

for (const effort of MODEL_EFFORTS) {
  assert.equal(isModelEffort(effort), true);
  assert.equal(resolveModelEffort(effort), effort);
}

for (const invalid of ['', 'none', 'xhigh', 'HIGH', 'default', null, undefined, 1]) {
  assert.equal(isModelEffort(invalid), false);
  assert.equal(resolveModelEffort(invalid), undefined);
}

const catalog = modelCatalog(['gemini-3.1-flash-lite', 'gemini-3.5-flash-lite', 'gemini-3.1-flash-lite']);
assert.deepEqual(catalog.map((model) => model.id), ['gemini-3.1-flash-lite', 'gemini-3.5-flash-lite']);
assert.deepEqual(catalog.map((model) => model.label), ['3.1 Flash', '3.5 Flash']);
assert.ok(catalog.every((model) => model.description));
assert.ok(catalog.every((model) => model.efforts.length === MODEL_EFFORTS.length));
assert.deepEqual(catalog[0]?.efforts, MODEL_EFFORTS);
assert.deepEqual(catalog[1]?.efforts, MODEL_EFFORTS);

console.log('model registry checks passed');
