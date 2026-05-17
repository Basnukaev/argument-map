package ru.basnukaev.argumentmap.exception;

import java.util.Set;

/**
 * Бросается {@code TopicImportService} когда {@code formatVersion} в
 * импортируемом payload не входит в whitelist поддерживаемых версий
 * (Этап 6 / ADR-037). Маппится в Problem Details через
 * {@code GlobalExceptionHandler} - 422 Unprocessable Entity.
 *
 * <p>В свойствах problem'а сохраняются полученная версия и whitelist
 * поддерживаемых для diagnostics на UI стороне.
 */
public class UnsupportedExportFormatException extends RuntimeException {

    private final String receivedVersion;
    private final Set<String> supportedVersions;

    public UnsupportedExportFormatException(String receivedVersion, Set<String> supportedVersions) {
        super("неподдерживаемая версия формата экспорта: '" + receivedVersion
                + "', поддерживаемые: " + supportedVersions);
        this.receivedVersion = receivedVersion;
        this.supportedVersions = supportedVersions;
    }

    public String getReceivedVersion() {
        return receivedVersion;
    }

    public Set<String> getSupportedVersions() {
        return supportedVersions;
    }
}
