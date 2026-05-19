# OCR Pipeline (ADR-041)

Извлечение текста из сканов через Tesseract. Async pipeline, state
machine в `lib_pages.ocr_status`, graceful degradation если tesseract
не установлен.

## Зависимости

- **Tess4j 5.13.0** (Maven dep) — Java JNA wrapper над Tesseract C++
- **Tesseract сам — НЕ Maven artifact**, это system dependency. Перед
  первым запуском backend с OCR на Debian/WSL2:
  ```bash
  sudo apt install tesseract-ocr tesseract-ocr-ara tesseract-ocr-rus tesseract-ocr-eng
  ```
  на macOS: `brew install tesseract tesseract-lang`

## Конфигурация

- Path к `.traineddata` файлам — через `ocr.tessdata.path` property
  (env `OCR_TESSDATA_PATH`). Default
  `/usr/share/tesseract-ocr/4.00/tessdata`

## Async pipeline

- `OcrService.recognizeAsync` уходит в `ocrTaskExecutor` (core=2,
  max=4, queue=100). Маленький pool потому что Tesseract сам
  multi-threaded на одну страницу

## State machine

- `lib_pages.ocr_status`: PENDING (uploaded, ждёт) → PROCESSING →
  DONE / FAILED. Перезапуск из любого состояния допустим (idempotent
  на уровне БД)

## Graceful degradation

- Если Tesseract не установлен, backend стартует нормально; первый
  OCR-вызов помечает page FAILED + log.error

## Тестирование

- IT тест `OcrServiceIT` имеет `@EnabledIf("isTesseractAvailable")` —
  skip'нется автоматически если на хосте нет tesseract +
  eng.traineddata
