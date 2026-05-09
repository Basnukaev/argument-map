package ru.basnukaev.argumentmap.library.shamela.service;

/**
 * Подкласс {@link ShamelaImportException} для случаев "не найдено":
 * book id не существует в {@code lib_shamela_book} (для importBook /
 * mapBook), либо после распаковки master-zip отсутствует ожидаемый
 * SQLite-файл (теоретически возможно при нарушении контракта shamela API).
 *
 * <p>Отдельный тип нужен для чистого exception mapping в
 * {@code GlobalExceptionHandler} - 404 для not-found vs 500 для
 * general import errors. Без подкласса пришлось бы matching по
 * substring сообщения.
 *
 * <p>{@code instanceof ShamelaImportException} продолжает быть true
 * (Java наследование) - существующие catch на родителя ловят и этот
 * подкласс.
 */
public class ShamelaNotFoundException extends ShamelaImportException {

    public ShamelaNotFoundException(String message) {
        super(message);
    }

    public ShamelaNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
