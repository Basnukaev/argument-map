package ru.basnukaev.argumentmap.domain;

/**
 * Whitelist оценок хадиса для multi-grading. Отделён от {@link Reliability}
 * (legacy single-value на Source) потому что multi-grading требует
 * MAUDU («выдуманный»), которого нет в Reliability enum. Source.reliability
 * не трогаем - оставляем для backward compatibility.
 */
public enum HadithGradeValue {
    SAHIH,
    HASAN,
    DAIF,
    MAUDU
}
