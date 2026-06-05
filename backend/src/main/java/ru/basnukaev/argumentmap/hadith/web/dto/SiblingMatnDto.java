package ru.basnukaev.argumentmap.hadith.web.dto;

import java.util.UUID;

/**
 * Primary-матн параллельной передачи (طريق) хадиса — для секции
 * «Тексты параллельных передач» (С59): юзер хочет видеть, как звучит то же
 * предание в других сборниках, не уходя со страницы.
 *
 * @param hadithId           id импортированного сиблинга (для перехода)
 * @param externalId         alminasa-id сиблинга («454-489»)
 * @param collectionNameAr   имя сборника (статическая карта по префиксу)
 * @param collectionNameRu   русское имя сборника
 * @param printedNumber      номер в печатном издании (nullable)
 * @param textAr             полный primary-матн сиблинга
 */
public record SiblingMatnDto(
        UUID hadithId,
        String externalId,
        String collectionNameAr,
        String collectionNameRu,
        Integer printedNumber,
        String textAr
) {
}
