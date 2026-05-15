import { type InputHTMLAttributes, type TextareaHTMLAttributes, type ReactNode, createContext, useContext, useId } from 'react';
import { clsx } from 'clsx';

interface FieldCtx {
  id: string;
  hasError: boolean;
}
const Ctx = createContext<FieldCtx | null>(null);

interface FieldProps {
  label: string;
  hint?: string;
  error?: string;
  required?: boolean;
  children: ReactNode;
  className?: string;
}

/**
 * Field — form input wrapper. Provides label + hint + error stack
 * around any of Field.Input / Field.Textarea / Field.Meta.
 *
 *   <Field label="Название" hint="Краткая формулировка" required>
 *     <Field.Input value={v} onChange={setV} />
 *     <Field.Meta left="0 / 500" />
 *   </Field>
 *
 * Even single-line inputs go through Field so the label/hint stack is
 * consistent.
 */
export function Field({ label, hint, error, required, children, className }: FieldProps) {
  const id = useId();
  return (
    <Ctx.Provider value={{ id, hasError: !!error }}>
      <div className={clsx('block', className)}>
        <label htmlFor={id} className="flex items-baseline gap-2 mb-1">
          <span className="text-xs font-semibold text-ink-900">{label}</span>
          {required && <span className="text-[10px] font-semibold text-err-500">required</span>}
        </label>
        {hint && <div className="text-xs text-ink-500 mb-1.5 leading-snug">{hint}</div>}
        {children}
        {error && <div className="text-xs text-err-500 mt-1">{error}</div>}
      </div>
    </Ctx.Provider>
  );
}

interface FieldInputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> {
  leftIcon?: ReactNode;
}

function FieldInput({ leftIcon, className, ...rest }: FieldInputProps) {
  const ctx = useContext(Ctx);
  return (
    <div
      className={clsx(
        'flex items-center gap-1.5 h-9 px-3 rounded bg-elevated border',
        ctx?.hasError ? 'border-err-500' : 'border-ink-200',
        'focus-within:border-accent-500 focus-within:shadow-[0_0_0_3px_color-mix(in_srgb,var(--c-accent-500)_14%,transparent)]',
      )}
    >
      {leftIcon}
      <input
        id={ctx?.id}
        className={clsx('flex-1 bg-transparent outline-none text-sm text-ink-900 placeholder:text-ink-400', className)}
        {...rest}
      />
    </div>
  );
}
Field.Input = FieldInput;

function FieldTextarea({ className, rows = 3, ...rest }: TextareaHTMLAttributes<HTMLTextAreaElement> & { rows?: number }) {
  const ctx = useContext(Ctx);
  return (
    <textarea
      id={ctx?.id}
      rows={rows}
      className={clsx(
        'w-full rounded bg-elevated border px-3 py-2',
        'text-sm text-ink-900 leading-relaxed',
        'placeholder:text-ink-400',
        'outline-none resize-y',
        ctx?.hasError ? 'border-err-500' : 'border-ink-200',
        'focus:border-accent-500 focus:shadow-[0_0_0_3px_color-mix(in_srgb,var(--c-accent-500)_14%,transparent)]',
        className,
      )}
      {...rest}
    />
  );
}
Field.Textarea = FieldTextarea;

function FieldMeta({ left, right }: { left?: ReactNode; right?: ReactNode }) {
  return (
    <div className="flex justify-between mt-1 text-[11px] text-ink-500 font-mono">
      <span>{left ?? ' '}</span>
      <span>{right ?? ' '}</span>
    </div>
  );
}
Field.Meta = FieldMeta;
