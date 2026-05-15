interface AttachFieldsProps {
  quote: string;
  context: string;
  location: string;
  onQuoteChange: (v: string) => void;
  onContextChange: (v: string) => void;
  onLocationChange: (v: string) => void;
  disabled?: boolean;
}

/**
 * Опциональные поля привязки источника к узлу: точная цитата, место
 * в источнике, контекст. Переиспользуется в search-mode и create-mode
 * AddSourceModal.
 */
function AttachFields({
  quote,
  context,
  location,
  onQuoteChange,
  onContextChange,
  onLocationChange,
  disabled,
}: AttachFieldsProps) {
  return (
    <fieldset
      disabled={disabled}
      className="space-y-2 rounded-md border border-border bg-ink-50/40 p-3"
    >
      <legend className="px-1 text-xs font-medium text-ink-600">
        Поля привязки (опционально)
      </legend>
      <div>
        <label htmlFor="attach-quote" className="mb-1 block text-xs text-ink-600">
          Цитата
        </label>
        <textarea
          id="attach-quote"
          value={quote}
          onChange={(e) => onQuoteChange(e.target.value)}
          rows={2}
          placeholder="Конкретный фрагмент источника, который относится к этому узлу"
          className="block w-full rounded-md border border-border-strong bg-elevated px-3 py-2 text-sm text-ink-900 placeholder:text-ink-400 outline-none transition-colors focus:border-accent-500 focus:ring-2 focus:ring-accent-500/20"
        />
      </div>
      <div>
        <label htmlFor="attach-location" className="mb-1 block text-xs text-ink-600">
          Место в источнике
        </label>
        <input
          id="attach-location"
          type="text"
          value={location}
          onChange={(e) => onLocationChange(e.target.value)}
          maxLength={200}
          placeholder="Например: т.13 с.137, №1162, 2:256"
          className="block w-full rounded-md border border-border-strong bg-elevated px-3 py-2 text-sm text-ink-900 placeholder:text-ink-400 outline-none transition-colors focus:border-accent-500 focus:ring-2 focus:ring-accent-500/20"
        />
      </div>
      <div>
        <label htmlFor="attach-context" className="mb-1 block text-xs text-ink-600">
          Контекст
        </label>
        <input
          id="attach-context"
          type="text"
          value={context}
          onChange={(e) => onContextChange(e.target.value)}
          placeholder="В какой главе, при каком обсуждении и т.п."
          className="block w-full rounded-md border border-border-strong bg-elevated px-3 py-2 text-sm text-ink-900 placeholder:text-ink-400 outline-none transition-colors focus:border-accent-500 focus:ring-2 focus:ring-accent-500/20"
        />
      </div>
    </fieldset>
  );
}

export default AttachFields;
