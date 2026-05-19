# Hadith Grades + Authority.type (миграция 47)

`HadithGradeService.addGrade` валидирует семантическую роль authority —
оценивать хадис (`SAHIH/HASAN/DAIF/MAUDU`) может только учёный, не
издательство и не тахкик.

## Whitelist в `domain.AuthorityType`

`SCHOLAR / MUHAQQIQ / PUBLISHER / AUTHOR / OTHER`, default `SCHOLAR`
(БД-уровень).

## Где enforce

`HadithGradeService.addGrade` после
`authorityRepository.findById(scholarId)` проверяет
`scholar.type().equals(SCHOLAR)` — иначе
`InvalidScholarAuthorityException` → 400 `invalid-scholar-authority`
(properties `authorityId`, `actualType`, `expectedType=SCHOLAR`).

## CHECK constraint

В `authorities` гарантирует что только whitelisted значения попадают
в БД. `AuthorityService.createAuthority(..., type)` валидирует
whitelist на уровне сервиса (быстрый 400 `invalid-authority-type`
без круглого тура к БД).

## ETL/импорт

- `ShamelaAuthorityResolver` явно ставит `AuthorityType.AUTHOR`
  (резолвинг авторов книг shamela)
- `TopicImportService` оставляет `null` → DB default `SCHOLAR`
  (старые экспорты не несут type-семантику)

## Backward compat

Legacy-перегрузка `createAuthority(name, bio, era, madhab, metadata)`
без type → SCHOLAR default. Тесты и shamela ETL работают без правок.

## Publishers/muhaqqiqs separate tables

`lib_publishers` и `lib_muhaqqiqs` (ADR-028) — **не** дублируются с
`authorities`, это разные доменные сущности. `Authority.type=PUBLISHER`
может использоваться если PUBLISHER должен ссылаться откуда-то ещё
кроме `lib_books.publisher_id`.
