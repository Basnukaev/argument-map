import { useState } from 'react';
import { useNavigate } from 'react-router';
import type { LucideIcon } from 'lucide-react';
import {
  BookOpen,
  FileUp,
  Globe,
  Library,
  ShieldCheck,
  Sparkles,
  ArrowRight,
} from 'lucide-react';
import Header from '@/shared/components/layout/Header';
import Button from '@/shared/components/ui/Button';
import FileUploadModal from '@/apps/admin/components/FileUploadModal';
import { useT, type DictKey } from '@/shared/i18n';

/**
 * AdminDashboardPage (route `/admin`) — capabilities-дашборд админки.
 *
 * Раньше админ открывался прямо на AdminShamelaPage: видны были только
 * shamela-возможности, PDF-импорт прятался за «•••», аудит — тоже.
 * Теперь главная — явная витрина инструментов: сетка карточек, каждая
 * описывает ЧТО делает инструмент и ЧТО получится (тип данных в нашем
 * формате), с одной primary-CTA.
 *
 * Группировка: «Наполнение контентом» (импортёры) vs «Наблюдение»
 * (аудит). alminasa.ai — disabled-карточка «скоро» для roadmap-ориентира.
 */

type CardAction =
  | { kind: 'navigate'; to: string }
  | { kind: 'modal' }
  | { kind: 'disabled' };

interface ToolCard {
  id: string;
  icon: LucideIcon;
  titleKey: DictKey;
  descKey: DictKey;
  /** «Что получится» — тип данных в нашем формате после использования. */
  producesKey: DictKey;
  ctaKey: DictKey;
  action: CardAction;
}

const CONTENT_CARDS: ReadonlyArray<ToolCard> = [
  {
    id: 'shamela',
    icon: Library,
    titleKey: 'admin.dashboard.shamela.title',
    descKey: 'admin.dashboard.shamela.desc',
    producesKey: 'admin.dashboard.shamela.produces',
    ctaKey: 'admin.dashboard.shamela.cta',
    action: { kind: 'navigate', to: '/admin/shamela' },
  },
  {
    id: 'pdf',
    icon: FileUp,
    titleKey: 'admin.dashboard.pdf.title',
    descKey: 'admin.dashboard.pdf.desc',
    producesKey: 'admin.dashboard.pdf.produces',
    ctaKey: 'admin.dashboard.pdf.cta',
    action: { kind: 'modal' },
  },
  {
    id: 'archiveorg',
    icon: Globe,
    titleKey: 'admin.dashboard.archiveorg.title',
    descKey: 'admin.dashboard.archiveorg.desc',
    producesKey: 'admin.dashboard.archiveorg.produces',
    ctaKey: 'admin.dashboard.archiveorg.cta',
    action: { kind: 'navigate', to: '/admin/archive-org' },
  },
  {
    id: 'alminasa',
    icon: Sparkles,
    titleKey: 'admin.dashboard.alminasa.title',
    descKey: 'admin.dashboard.alminasa.desc',
    producesKey: 'admin.dashboard.alminasa.produces',
    ctaKey: 'admin.dashboard.alminasa.cta',
    action: { kind: 'navigate', to: '/admin/hadith-import' },
  },
];

const OBSERVE_CARDS: ReadonlyArray<ToolCard> = [
  {
    id: 'audit',
    icon: ShieldCheck,
    titleKey: 'admin.dashboard.audit.title',
    descKey: 'admin.dashboard.audit.desc',
    producesKey: 'admin.dashboard.audit.produces',
    ctaKey: 'admin.dashboard.audit.cta',
    action: { kind: 'navigate', to: '/admin/audit' },
  },
];

function AdminDashboardPage() {
  const t = useT();
  const navigate = useNavigate();
  const [uploadOpen, setUploadOpen] = useState(false);

  const handleAction = (action: CardAction) => {
    if (action.kind === 'navigate') navigate(action.to);
    else if (action.kind === 'modal') setUploadOpen(true);
  };

  return (
    <main className="min-h-screen bg-bg">
      <Header />

      <div className="mx-auto max-w-[1380px] px-3 py-6 sm:px-6 sm:py-8">
        <header className="mb-8">
          <div className="mb-1 text-[11px] font-semibold uppercase tracking-[0.12em] text-ink-500">
            {t('admin.dashboard.eyebrow')}
          </div>
          <h1 className="font-serif text-[28px] font-semibold leading-tight tracking-tight text-ink-900">
            {t('admin.dashboard.title')}
          </h1>
          <p className="mt-1.5 max-w-[680px] text-sm text-ink-500">
            {t('admin.dashboard.subtitle')}
          </p>
        </header>

        <DashboardSection
          title={t('admin.dashboard.section_content')}
          hint={t('admin.dashboard.section_content_hint')}
          cards={CONTENT_CARDS}
          onAction={handleAction}
        />

        <DashboardSection
          title={t('admin.dashboard.section_observe')}
          hint={t('admin.dashboard.section_observe_hint')}
          cards={OBSERVE_CARDS}
          onAction={handleAction}
        />
      </div>

      {uploadOpen && (
        <FileUploadModal open onClose={() => setUploadOpen(false)} />
      )}
    </main>
  );
}

// ====================================================================
//                          Sub-components
// ====================================================================

interface DashboardSectionProps {
  title: string;
  hint: string;
  cards: ReadonlyArray<ToolCard>;
  onAction: (action: CardAction) => void;
}

function DashboardSection({ title, hint, cards, onAction }: DashboardSectionProps) {
  return (
    <section className="mb-10">
      <div className="mb-3">
        <h2 className="text-sm font-semibold text-ink-900">{title}</h2>
        <p className="mt-0.5 text-xs text-ink-500">{hint}</p>
      </div>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {cards.map((card) => (
          <ToolCardView key={card.id} card={card} onAction={onAction} />
        ))}
      </div>
    </section>
  );
}

interface ToolCardViewProps {
  card: ToolCard;
  onAction: (action: CardAction) => void;
}

function ToolCardView({ card, onAction }: ToolCardViewProps) {
  const t = useT();
  const Icon = card.icon;
  const disabled = card.action.kind === 'disabled';

  return (
    <div
      className={`flex flex-col rounded-lg border bg-elevated p-5 shadow-sh1 transition-colors ${
        disabled
          ? 'border-dashed border-border-strong opacity-80'
          : 'border-border hover:border-border-strong'
      }`}
    >
      <div className="mb-3 flex items-center gap-3">
        <span
          className={`grid h-10 w-10 shrink-0 place-items-center rounded-md ${
            disabled ? 'bg-ink-100 text-ink-400' : 'bg-accent-50 text-accent-600'
          }`}
        >
          <Icon size={20} aria-hidden />
        </span>
        <h3 className="text-base font-semibold text-ink-900">{t(card.titleKey)}</h3>
        {disabled && (
          <span className="ms-auto inline-flex items-center rounded-full border border-border-strong bg-sunken px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wider text-ink-500">
            {t('admin.dashboard.soon_badge')}
          </span>
        )}
      </div>

      <p className="text-sm leading-relaxed text-ink-600">{t(card.descKey)}</p>

      <div className="mt-3 rounded-md border border-border bg-sunken px-3 py-2">
        <div className="text-[10px] font-semibold uppercase tracking-[0.08em] text-ink-500">
          {t('admin.dashboard.produces_label')}
        </div>
        <div className="mt-0.5 text-xs leading-relaxed text-ink-700">
          {t(card.producesKey)}
        </div>
      </div>

      <div className="mt-4 flex">
        <Button
          variant={disabled ? 'secondary' : 'primary'}
          iconRight={disabled ? undefined : ArrowRight}
          disabled={disabled}
          onClick={() => onAction(card.action)}
          full
        >
          {disabled && <BookOpen size={14} aria-hidden className="me-1" />}
          {t(card.ctaKey)}
        </Button>
      </div>
    </div>
  );
}

export default AdminDashboardPage;
