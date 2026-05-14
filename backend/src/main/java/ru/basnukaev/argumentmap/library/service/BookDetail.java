package ru.basnukaev.argumentmap.library.service;

import java.util.List;

import ru.basnukaev.argumentmap.domain.Authority;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.Muhaqqiq;
import ru.basnukaev.argumentmap.library.domain.PublicationPlace;
import ru.basnukaev.argumentmap.library.domain.Publisher;

/**
 * Aggregate для GET /books/{id} - book + chapters tree + resolved nested
 * refs (authority / muhaqqiq / publisher / publicationPlace). Refs nullable -
 * BookDetail используется в BookHeader UI который рендерит structured
 * labels (Автор / Тахкик / Издатель / Место) с переводимыми label'ами
 * через i18n
 */
public record BookDetail(
        Book book,
        List<ChapterNode> rootChapters,
        Authority authority,
        Muhaqqiq muhaqqiq,
        Publisher publisher,
        PublicationPlace publicationPlace
) {
}
