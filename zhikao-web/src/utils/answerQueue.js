/** Serialize writes and advance versions only after acknowledgement. A failed write stays dirty. */
export function createAnswerQueue({ answers, versions, saved, send, onChange = () => {} }) {
  let running = null
  const dirty = new Set()
  let failure = null
  const snapshot = () => ({ pending: dirty.size, saving: !!running, error: failure })
  const changed = () => onChange(snapshot())
  function edit(id, value) {
    answers[id] = value
    // Keep edits in flight dirty, even if they temporarily equal the last saved value.
    dirty.add(String(id))
    changed()
  }
  async function drain() {
    while (dirty.size) {
      const id = dirty.values().next().value
      const value = answers[id] ?? ''
      const expectedVersion = versions[id] ?? 0
      try {
        await send({ paperQuestionId: id, answer: value, expectedVersion })
        versions[id] = expectedVersion + 1
        saved[id] = value
        if (answers[id] === value) dirty.delete(id)
        failure = null
        changed()
      } catch (error) {
        failure = error
        changed()
        throw error
      }
    }
  }
  function flush() {
    if (running) return running
    failure = null
    running = Promise.resolve()
      .then(drain)
      .finally(() => {
        running = null
        changed()
      })
    changed()
    return running
  }
  return { edit, flush, snapshot }
}
