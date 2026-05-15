import {
  createContext,
  useContext,
  useId,
  type InputHTMLAttributes,
  type ReactNode,
  type TextareaHTMLAttributes,
} from 'react';

interface FieldCtx {
  id: string;
  hasError: boolean;
  required: boolean;
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
 * Field - обёртка input + label + hint + error. Даже одиночный
 * `<input />` пропускается через Field, чтобы стек label/hint/error
 * был визуально единообразным во всём продукте.
 *
 *   <Field label="Название" hint="Краткая формулировка" required>
 *     <Field.Input value={v} onChange={(e) => setV(e.target.value)} />
 *     <Field.Meta left="60 / 500" />
 *   </Field>
 *
 *   <Field label="Описание">
 *     <Field.Textarea rows={3} value={d} onChange={(e) => setD(e.target.value)} />
 *   </Field>
 */
function Field({
  label,
  hint,
  error,
  required,
  children,
  className = '',
}: FieldProps) {
  const id = useId();
  return (
    <Ctx.Provider value={{ id, hasError: !!error, required: !!required }}>
      <div className={`block ${className}`}>
        <label htmlFor={id} className="flex items-baseline gap-2 mb-1">
          <span className="text-xs font-semibold text-ink-900">{label}</span>
          {required && (
            <span
              aria-hidden="true"
              className="text-xs font-semibold text-err-500 uppercase tracking-wider"
            >
              *
            </span>
          )}
        </label>
        {hint && (
          <div className="text-xs text-ink-500 mb-1.5 leading-snug">{hint}</div>
        )}
        {children}
        {error && <div className="text-xs text-err-500 mt-1">{error}</div>}
      </div>
    </Ctx.Provider>
  );
}

interface FieldInputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> {
  leftIcon?: ReactNode;
}

function FieldInput({ leftIcon, className = '', ...rest }: FieldInputProps) {
  const ctx = useContext(Ctx);
  const borderClass = ctx?.hasError ? 'border-err-500' : 'border-ink-200';
  return (
    <div
      className={`flex items-center gap-1.5 h-9 px-3 rounded-sm bg-elevated border ${borderClass} focus-within:border-accent-500`}
    >
      {leftIcon}
      <input
        id={ctx?.id}
        aria-required={ctx?.required || undefined}
        aria-invalid={ctx?.hasError || undefined}
        className={`flex-1 bg-transparent outline-none text-sm text-ink-900 placeholder:text-ink-400 ${className}`}
        {...rest}
      />
    </div>
  );
}
Field.Input = FieldInput;

function FieldTextarea({
  className = '',
  rows = 3,
  ...rest
}: TextareaHTMLAttributes<HTMLTextAreaElement> & { rows?: number }) {
  const ctx = useContext(Ctx);
  const borderClass = ctx?.hasError ? 'border-err-500' : 'border-ink-200';
  return (
    <textarea
      id={ctx?.id}
      rows={rows}
      aria-required={ctx?.required || undefined}
      aria-invalid={ctx?.hasError || undefined}
      className={`w-full rounded-sm bg-elevated border px-3 py-2 text-sm text-ink-900 leading-relaxed placeholder:text-ink-400 outline-none resize-y ${borderClass} focus:border-accent-500 ${className}`}
      {...rest}
    />
  );
}
Field.Textarea = FieldTextarea;

function FieldMeta({ left, right }: { left?: ReactNode; right?: ReactNode }) {
  return (
    <div className="flex justify-between mt-1 text-xs text-ink-500 font-mono">
      <span>{left ?? ' '}</span>
      <span>{right ?? ' '}</span>
    </div>
  );
}
Field.Meta = FieldMeta;

export default Field;
