/**
 * Минимальный валидный PDF blob - 1 страница "Hello E2E". Используется
 * для upload-тестов AdminShamelaPage. Не используем real PDF binary
 * чтобы не было shaky binary fixture-файлов в репе.
 *
 * PDF структура - простейший single-page document с stream "Hello E2E".
 * Парсится PDFBox, PDF.js на фронте отрисует empty page.
 */
export function buildMinimalPdfBuffer(): Buffer {
  const pdf = [
    '%PDF-1.4',
    '1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj',
    '2 0 obj << /Type /Pages /Count 1 /Kids [3 0 R] >> endobj',
    '3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj',
    '4 0 obj << /Length 44 >> stream',
    'BT /F1 24 Tf 100 700 Td (Hello E2E) Tj ET',
    'endstream endobj',
    '5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj',
    'xref',
    '0 6',
    '0000000000 65535 f ',
    '0000000009 00000 n ',
    '0000000054 00000 n ',
    '0000000099 00000 n ',
    '0000000200 00000 n ',
    '0000000270 00000 n ',
    'trailer << /Size 6 /Root 1 0 R >>',
    'startxref',
    '320',
    '%%EOF',
  ].join('\n');
  return Buffer.from(pdf, 'latin1');
}
