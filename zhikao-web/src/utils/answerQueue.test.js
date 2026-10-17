import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createAnswerQueue } from './answerQueue.js'

test('a slow acknowledgement cannot overwrite a newer edit; submit waits for both', async () => {
  let acknowledge
  const calls = [],
    answers = {},
    versions = {},
    saved = {}
  const q = createAnswerQueue({
    answers,
    versions,
    saved,
    send: async (data) => {
      calls.push({ ...data })
      if (calls.length === 1)
        await new Promise((r) => {
          acknowledge = r
        })
    },
  })
  q.edit('12', 'A')
  const flushing = q.flush()
  await new Promise((r) => setTimeout(r, 0))
  q.edit('12', 'B')
  assert.equal(q.flush(), flushing)
  acknowledge()
  await flushing
  assert.deepEqual(calls, [
    { paperQuestionId: '12', answer: 'A', expectedVersion: 0 },
    { paperQuestionId: '12', answer: 'B', expectedVersion: 1 },
  ])
  assert.equal(saved['12'], 'B')
  assert.equal(q.snapshot().pending, 0)
})

test('failed saves stay dirty and retry with the same expected version', async () => {
  const versions = { 1: 3 },
    saved = { 1: 'A' }
  let fail = true
  const q = createAnswerQueue({
    answers: { 1: 'A' },
    versions,
    saved,
    send: async (d) => {
      assert.equal(d.expectedVersion, 3)
      if (fail) throw new Error('offline')
    },
  })
  q.edit('1', 'B')
  await assert.rejects(q.flush(), /offline/)
  assert.equal(q.snapshot().pending, 1)
  assert.equal(versions['1'], 3)
  assert.equal(saved['1'], 'A')
  fail = false
  await q.flush()
  assert.equal(versions['1'], 4)
  assert.equal(saved['1'], 'B')
})
