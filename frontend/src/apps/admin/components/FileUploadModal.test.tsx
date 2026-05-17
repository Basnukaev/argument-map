import { describe, it, expect, vi, beforeAll } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import FileUploadModal from './FileUploadModal';
import { useToastStore } from '@/shared/stores/toastStore';

// jsdom не реализует HTMLDialogElement.showModal/close - мок (см. AddSourceModal.test)
beforeAll(() => {
  if (!HTMLDialogElement.prototype.showModal) {
    HTMLDialogElement.prototype.showModal = function () {
      this.open = true;
    };
  }
  if (!HTMLDialogElement.prototype.close) {
    HTMLDialogElement.prototype.close = function () {
      this.open = false;
      this.dispatchEvent(new Event('close'));
    };
  }
});

const BASE = 'http://test.local';
const ENDPOINT = `${BASE}/api/v1/library/imports/file`;
const BOOK_ID = '11111111-1111-1111-1111-111111111111';
const FILE_ID = '22222222-2222-2222-2222-222222222222';

function makePdf(name = 'test.pdf', size = 1024): File {
  // jsdom File с правильным MIME - бэкенд endpoint требует application/pdf
  const bytes = new Uint8Array(size).fill(0x25); // '%' маркер PDF
  return new File([bytes], name, { type: 'application/pdf' });
}

function renderModal() {
  const onClose = vi.fn();
  const onUploaded = vi.fn();
  const utils = render(
    <MemoryRouter>
      <FileUploadModal open onClose={onClose} onUploaded={onUploaded} />
    </MemoryRouter>,
  );
  return { ...utils, onClose, onUploaded };
}

describe('FileUploadModal', () => {
  beforeAll(() => {
    // изолируем тосты от других тестов на всякий случай
    useToastStore.getState().clear();
  });

  it('кнопка "Загрузить" disabled пока файл не выбран', () => {
    renderModal();
    const submit = screen.getByRole('button', { name: /Загрузить$/ });
    expect(submit).toBeDisabled();
  });

  it('happy path - выбор файла, отправка multipart, success toast + onClose', async () => {
    let hits = 0;
    server.use(
      http.post(ENDPOINT, () => {
        hits++;
        return HttpResponse.json({
          bookId: BOOK_ID,
          fileId: FILE_ID,
          pageCount: 42,
          contentHash: 'abc',
          sizeBytes: 1024,
          bucket: 'library-user-uploads',
          storageKey: 'key',
        });
      }),
    );
    const { onClose, onUploaded } = renderModal();

    const fileInput = screen.getByLabelText(/Выбрать файл/) as HTMLInputElement;
    await userEvent.upload(fileInput, makePdf('mybook.pdf', 2048));

    // preview filename появился
    expect(await screen.findByText('mybook.pdf')).toBeInTheDocument();
    // submit enabled
    const submit = screen.getByRole('button', { name: /Загрузить$/ });
    expect(submit).not.toBeDisabled();

    // заполняем title - чтобы убедиться, что значение реально в state
    // (контролируемый input)
    const titleInput = screen.getByRole('textbox', { name: /Название/ });
    await userEvent.type(titleInput, 'Бухари');
    expect((titleInput as HTMLInputElement).value).toBe('Бухари');

    await userEvent.click(submit);

    await waitForApi(() => expect(onUploaded).toHaveBeenCalledTimes(1));
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(hits).toBe(1);

    // success toast в store с pageCount-форматированным сообщением
    const toasts = useToastStore.getState().toasts;
    const lastToast = toasts[toasts.length - 1];
    expect(lastToast?.kind).toBe('success');
    expect(lastToast?.message).toMatch(/42/);
    expect(lastToast?.action?.label).toMatch(/Открыть/);
  });

  it('413 показывает локализованную ошибку "файл слишком большой"', async () => {
    server.use(
      http.post(ENDPOINT, () =>
        HttpResponse.json(
          {
            type: 'https://errors.argument-map.local/payload-too-large',
            title: 'Превышен максимальный размер файла',
            status: 413,
            detail: 'Размер файла превышает лимит',
          },
          { status: 413 },
        ),
      ),
    );
    renderModal();
    await userEvent.upload(
      screen.getByLabelText(/Выбрать файл/) as HTMLInputElement,
      makePdf('big.pdf', 100),
    );
    await userEvent.click(screen.getByRole('button', { name: /Загрузить$/ }));
    expect(
      await screen.findByText(/превышает лимит 50 MB/i),
    ).toBeInTheDocument();
  });

  it('415 показывает "неподдерживаемый формат"', async () => {
    server.use(
      http.post(ENDPOINT, () =>
        HttpResponse.json(
          {
            type: 'https://errors.argument-map.local/unsupported-media-type',
            title: 'Неподдерживаемый тип файла',
            status: 415,
            detail: 'тип text/plain не поддерживается',
          },
          { status: 415 },
        ),
      ),
    );
    renderModal();
    await userEvent.upload(
      screen.getByLabelText(/Выбрать файл/) as HTMLInputElement,
      makePdf('notpdf.pdf', 100),
    );
    await userEvent.click(screen.getByRole('button', { name: /Загрузить$/ }));
    expect(
      await screen.findByText(/Неподдерживаемый формат/i),
    ).toBeInTheDocument();
  });

  it('422 показывает "не удалось прочитать PDF"', async () => {
    server.use(
      http.post(ENDPOINT, () =>
        HttpResponse.json(
          {
            type: 'https://errors.argument-map.local/file-import-error',
            title: 'Ошибка импорта файла',
            status: 422,
            detail: 'PDF защищён паролем',
          },
          { status: 422 },
        ),
      ),
    );
    renderModal();
    await userEvent.upload(
      screen.getByLabelText(/Выбрать файл/) as HTMLInputElement,
      makePdf('corrupt.pdf', 100),
    );
    await userEvent.click(screen.getByRole('button', { name: /Загрузить$/ }));
    expect(
      await screen.findByText(/не удалось прочитать PDF/i),
    ).toBeInTheDocument();
  });
});
