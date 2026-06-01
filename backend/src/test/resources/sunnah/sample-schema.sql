-- Fixture: faithful подмножество реальной MySQL-схемы дампа sunnah.com
-- (github.com/sunnah-com/api, db/00-samplegitdb.sql) для SunnahDumpReaderIT
-- и SunnahImportServiceIT. Только колонки, которые читает reader; реальные
-- имена/типы. Намеренно БЕЗ проблемных '0000-00-00' DEFAULT (MySQL 8 strict).
--
-- Данные подобраны так, чтобы тесты были discriminating (не вакуумными):
--   * 2 сборника: bukhari (hasbooks='yes') + muslim (hasbooks='no')
--     → покрывает обе ветки yesNo();
--   * 2 книги bukhari, где arabicBookID != arabicBookNumber (книга bookID=2.0
--     имеет bookNumber=5) → доказывает JOIN ChapterData→BookData по bookID,
--     резолвящий arabicBookNumber (сломанный JOIN дал бы book_number "2");
--   * хадис с bookNumber='99' и babID без ChapterData → ветки b==null/ch==null
--     (хадис импортируется, но без bookName/chapterTitle), + пустой grade
--     → нет ключа grades;
--   * дробный babID 1.1 (HadithTable 1.10 vs ChapterData 1.1) → канонизация.

CREATE TABLE Collections (
  name         varchar(100) NOT NULL,
  collectionID int NOT NULL,
  hasbooks     varchar(3) NOT NULL,
  haschapters  varchar(3) NOT NULL,
  totalhadith  int,
  numhadith    int,
  arabicTitle  varchar(400),
  englishTitle varchar(200),
  shortintro   text,
  PRIMARY KEY (collectionID)
);

CREATE TABLE BookData (
  collection        varchar(50) NOT NULL,
  arabicBookID      decimal(3,1) NOT NULL,
  arabicBookNumber  int NOT NULL,
  arabicBookName    varchar(300),
  englishBookName   varchar(100),
  firstNumber       int,
  lastNumber        int,
  totalNumber       int,
  PRIMARY KEY (collection, arabicBookID)
);

CREATE TABLE ChapterData (
  collection       varchar(50) NOT NULL,
  arabicBookID     decimal(3,1) NOT NULL,
  babID            decimal(6,1) NOT NULL,
  arabicBabNumber  varchar(21),
  englishBabNumber varchar(21),
  arabicBabName    text,
  englishBabName   text,
  arabicIntro      text,
  englishIntro     text,
  arabicEnding     text,
  englishEnding    text,
  PRIMARY KEY (collection, arabicBookID, babID)
);

CREATE TABLE HadithTable (
  collection    varchar(50) NOT NULL,
  bookNumber    varchar(20) NOT NULL,
  babID         decimal(6,2) NOT NULL,
  hadithNumber  varchar(50) NOT NULL,
  arabicURN     int NOT NULL,
  englishURN    int NOT NULL,
  arabicText    text,
  englishText   text,
  arabicgrade1  varchar(2000),
  englishgrade1 varchar(2000),
  PRIMARY KEY (arabicURN)
);

INSERT INTO Collections (name, collectionID, hasbooks, haschapters, totalhadith, numhadith, arabicTitle, englishTitle, shortintro) VALUES
  ('bukhari', 1, 'yes', 'yes', 7563, 7291, 'صحيح البخاري', 'Sahih al-Bukhari', 'Sahih al-Bukhari intro'),
  ('muslim',  2, 'no',  'yes', 7470, 7470, 'صحيح مسلم',   'Sahih Muslim',     'Sahih Muslim intro');

-- Книга 1: bookID 1.0 == bookNumber 1 (совпадают). Книга 2: bookID 2.0 != bookNumber 5.
INSERT INTO BookData (collection, arabicBookID, arabicBookNumber, arabicBookName, englishBookName, firstNumber, lastNumber, totalNumber) VALUES
  ('bukhari', 1.0, 1, 'كتاب بدء الوحى', 'Revelation', 1, 7, 7),
  ('bukhari', 2.0, 5, 'كتاب الإيمان',  'Belief',     8, 58, 51);

INSERT INTO ChapterData (collection, arabicBookID, babID, arabicBabNumber, englishBabNumber, arabicBabName, englishBabName, arabicIntro, englishIntro, arabicEnding, englishEnding) VALUES
  ('bukhari', 1.0, 1.0, '1', '1', 'باب كيف كان بدء الوحي', 'How the Divine Revelation started', NULL, NULL, NULL, NULL),
  ('bukhari', 1.0, 1.1, '1b', '1b', 'باب فرعي', 'Sub-chapter', NULL, NULL, NULL, NULL),
  ('bukhari', 2.0, 1.0, '1', '1', 'باب الإيمان', 'Belief Chapter', NULL, NULL, NULL, NULL);

-- Хадис 8 → книга bookNumber '5' (через bookID 2.0) → JOIN-доказательство.
-- Хадис 3 → bookNumber '99' (нет BookData) + babID 9.0 (нет ChapterData) +
-- пустой grade → ветки b==null/ch==null/no-grades-key.
INSERT INTO HadithTable (collection, bookNumber, babID, hadithNumber, arabicURN, englishURN, arabicText, englishText, arabicgrade1, englishgrade1) VALUES
  ('bukhari', '1',  1.00, '1', 100010, 10, 'إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ', 'Actions are by intentions',       '', 'Sahih'),
  ('bukhari', '1',  1.10, '2', 100020, 20, 'حَدَّثَنَا عَبْدُ اللَّهِ',           'The commencement of revelation',  '', 'Sahih'),
  ('bukhari', '5',  1.00, '8', 100080, 80, 'بُنِيَ الْإِسْلَامُ عَلَى خَمْسٍ',     'Islam is built on five',          '', 'Hasan'),
  ('bukhari', '99', 9.00, '3', 100030, 30, 'نَصٌّ بِلَا كِتَابٍ وَلَا بَابٍ',      'Orphan hadith',                   '', '');
