package ru.basnukaev.argumentmap.hadith;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.domain.HadithStatus;
import ru.basnukaev.argumentmap.hadith.domain.Matn;
import ru.basnukaev.argumentmap.hadith.domain.Narrator;
import ru.basnukaev.argumentmap.hadith.domain.NarratorReliability;
import ru.basnukaev.argumentmap.hadith.domain.Sanad;
import ru.basnukaev.argumentmap.hadith.domain.SanadNarrator;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.repository.MatnRepository;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRepository;
import ru.basnukaev.argumentmap.hadith.repository.SanadRepository;

/**
 * Dev seed эталонного хадиса "Дела по намерениям" (Бухари №1 / Муслим
 * №1907) с полным, выверенным иснадом для визуализации графа.
 *
 * <p>Структура демонстрирует классическое свойство этого хадиса -
 * <b>«гариб у истока, машхур в ветвях»</b>: единая нить от Пророка ﷺ
 * через Умара → Алькаму → Мухаммада ат-Тайми → Яхью ибн Саида
 * аль-Ансари (общее звено / мадар), которая <b>расходится</b> у Яхьи на
 * три цепи:
 * <ul>
 *   <li>Бухари: Яхья → Суфьян ибн Уяйна → аль-Хумайди → аль-Бухари
 *       (явная сама' - حدثنا/أخبرني);</li>
 *   <li>Муватта Малика: Яхья → Малик (му'ан'ан - عن);</li>
 *   <li>Муслим: Яхья → Малик → Муслим (через аль-Каанаби).</li>
 * </ul>
 *
 * <p>Данные выверены через research-workflow (sunnah.com + рияль-
 * литература), reference - {@code scripts/hadith-seed-research.json}.
 * Idempotent: пропускается если хадисы уже есть. Не должен ронять
 * backend - все ошибки логируются как warn.
 */
@Component
@Profile({"local", "dev"})
public class DevHadithSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevHadithSeeder.class);

    private static final String NORMALIZED_MATN =
            "إنما الأعمال بالنيات وإنما لكل امرئ ما نوى فمن كانت هجرته إلى دنيا "
                    + "يصيبها أو إلى امرأة ينكحها فهجرته إلى ما هاجر إليه";

    private final HadithRepository hadithRepository;
    private final NarratorRepository narratorRepository;
    private final SanadRepository sanadRepository;
    private final MatnRepository matnRepository;

    public DevHadithSeeder(HadithRepository hadithRepository,
                           NarratorRepository narratorRepository,
                           SanadRepository sanadRepository,
                           MatnRepository matnRepository) {
        this.hadithRepository = hadithRepository;
        this.narratorRepository = narratorRepository;
        this.sanadRepository = sanadRepository;
        this.matnRepository = matnRepository;
    }

    @Override
    public void run(String... args) {
        if (hadithRepository.countFiltered(null, null, null) > 0) {
            log.debug("Dev hadith seed skipped - existing data found");
            return;
        }
        try {
            seed();
        } catch (Exception e) {
            log.warn("Dev hadith seed failed (acceptable - prod не affected): {}", e.getMessage());
        }
    }

    private void seed() {
        Instant now = Instant.now();
        Map<String, Narrator> n = new LinkedHashMap<>();

        n.put("umar", saveNarrator(now,
                "عُمَر بن الخَطّاب", "umar ibn al-khattab", "أبو حفص", "الفاروق",
                null, 23, "Мекка", "Медина", "Медина",
                NarratorReliability.SAHABI,
                "Второй праведный халиф, один из десяти обрадованных Раем. "
                        + "Сподвижники по иджме - 'удул (справедливые), их передача не "
                        + "оценивается джарх ва тадиль. Произнёс этот хадис с минбара, "
                        + "ссылаясь на услышанное от Пророка ﷺ.",
                "Умар ибн аль-Хаттаб", "Сподвижник"));

        n.put("alqama", saveNarrator(now,
                "عَلْقَمَة بن وَقّاص اللَّيْثِي", "alqamah ibn waqqas al-laythi",
                "أبو يحيى", "الليثي المدني",
                null, 80, "Медина", "Медина", "Медина",
                NarratorReliability.THIQA,
                "Из старших табиинов Медины. Ибн Хаджар в «Такриб ат-тахзиб»: "
                        + "ثقة (надёжный). Год смерти точно не зафиксирован (~65-86 г.х.), "
                        + "приведена средняя оценка.",
                "Алькама ибн Ваккас аль-Лейси", "Табиин (ст.)"));

        n.put("muhammad", saveNarrator(now,
                "مُحَمَّد بن إبراهيم بن الحارث التَّيْمِي", "muhammad ibn ibrahim al-taymi",
                "أبو عبد الله", "التيمي المدني",
                null, 120, "Медина", "Медина", "Медина",
                NarratorReliability.THIQA,
                "Мединский табиин из рода Тайм. Ибн Хаджар: ثقة له أفراد (надёжный, "
                        + "имеет единичные передачи). Передаёт от Алькамы; от него - Яхья "
                        + "ибн Саид аль-Ансари.",
                "Мухаммад ибн Ибрахим ат-Тайми", "Табиин"));

        n.put("yahya", saveNarrator(now,
                "يَحْيَى بن سَعِيد الأَنْصَارِي", "yahya ibn said al-ansari",
                "أبو سعيد", "قاضي المدينة",
                null, 143, "Медина", "аль-Хашимия (Ирак)", "Медина, затем аль-Хашимия",
                NarratorReliability.THIQA,
                "Общее звено (мадар) всех путей хадиса: все позднейшие передатчики "
                        + "(Малик, Суфьян ибн Уяйна и др.) получают его от Яхьи. Кади "
                        + "Медины. Ибн Хаджар: ثقة ثبت (надёжный, точный). Умер в 143 г.х.",
                "Яхья ибн Саид аль-Ансари", "Табиин (мл.)"));

        n.put("malik", saveNarrator(now,
                "مَالِك بن أَنَس الأَصْبَحِي", "malik ibn anas", "أبو عبد الله", "إمام دار الهجرة",
                93, 179, "Медина", "Медина", "Медина",
                NarratorReliability.THIQA,
                "Имам Медины, автор «аль-Муватты», эпоним маликитского мазхаба. "
                        + "Ибн Хаджар: رأس المتقنين (глава точных). Передаёт хадис от Яхьи; "
                        + "через него хадис приводит Муслим. Умер в 179 г.х.",
                "Малик ибн Анас", "Атба ат-табиин"));

        n.put("sufyan", saveNarrator(now,
                "سُفْيَان بن عُيَيْنَة الهِلَالِي", "sufyan ibn uyaynah", "أبو محمد", "الهلالي المكي",
                107, 198, "Куфа", "Мекка", "Мекка",
                NarratorReliability.THIQA,
                "Хафиз, мухаддис Мекки. Ибн Хаджар: ثقة حافظ فقيه إمام حجة. Передаёт "
                        + "от Яхьи ибн Саида; от него - аль-Хумайди (шейх аль-Бухари). "
                        + "Умер в 198 г.х.",
                "Суфьян ибн Уяйна", "Атба ат-табиин"));

        n.put("humaydi", saveNarrator(now,
                "عَبْد الله بن الزُّبَيْر الحُمَيْدِي", "abdullah ibn al-zubayr al-humaydi",
                "أبو بكر", "الحميدي الأسدي المكي",
                null, 219, "Мекка", "Мекка", "Мекка",
                NarratorReliability.THIQA,
                "Непосредственный шейх аль-Бухари в хадисе №1, старший ученик Суфьяна "
                        + "ибн Уяйны, автор первого по хронологии «Муснада». Аль-Бухари: "
                        + "если находил хадис у аль-Хумайди, не оставлял его. Умер в 219 г.х.",
                "Абдуллах ибн аз-Зубайр аль-Хумайди", "Шейх аль-Бухари"));

        n.put("bukhari", saveNarrator(now,
                "مُحَمَّد بن إسماعيل البُخَارِي", "muhammad ibn ismail al-bukhari",
                "أبو عبد الله", "أمير المؤمنين في الحديث",
                194, 256, "Бухара", "Хартанк (близ Самарканда)", "Хартанк",
                NarratorReliability.THIQA,
                "Автор «Сахих аль-Бухари» - самого достоверного свода после Корана. "
                        + "Амир аль-муминин в науке хадиса. Этим хадисом открывает свой "
                        + "Сахих (№1). Умер в 256 г.х.",
                "Мухаммад ибн Исмаиль аль-Бухари", "Имам-составитель"));

        n.put("muslim", saveNarrator(now,
                "مُسْلِم بن الحَجّاج النَّيْسَابُورِي", "muslim ibn al-hajjaj",
                "أبو الحسين", "القشيري النيسابوري",
                206, 261, "Нишапур", "Нишапур", "Нишапур",
                NarratorReliability.THIQA,
                "Автор «Сахих Муслим» - второго по достоверности свода. Приводит хадис "
                        + "в Китаб аль-имара (№1907) через Малика (путём аль-Каанаби). "
                        + "Современник аль-Бухари. Умер в 261 г.х.",
                "Муслим ибн аль-Хаджжадж", "Имам-составитель"));

        Hadith hadith = new Hadith(
                UUID.randomUUID(), null, 1, NORMALIZED_MATN,
                HadithStatus.CANONICAL, null, null, now);
        hadithRepository.save(hadith);

        // Цепь 1 - аль-Бухари №1 (основная, явная сама').
        saveSanad(hadith, now, "SAHIH", n.get("bukhari"), true,
                "{\"collectionRu\":\"Сахих аль-Бухари\",\"collectionAr\":\"صحيح البخاري\"}",
                List.of(
                        step(n, "umar", "سَمِعْتُ"),
                        step(n, "alqama", "سَمِعْتُ"),
                        step(n, "muhammad", "سَمِعَ"),
                        step(n, "yahya", "أَخْبَرَنِي"),
                        step(n, "sufyan", "حَدَّثَنَا"),
                        step(n, "humaydi", "حَدَّثَنَا"),
                        step(n, "bukhari", "حَدَّثَنَا")));

        // Цепь 2 - Муватта Малика (му'ан'ан).
        saveSanad(hadith, now, "SAHIH", n.get("malik"), false,
                "{\"collectionRu\":\"Муватта Малика\",\"collectionAr\":\"موطأ مالك\"}",
                List.of(
                        step(n, "umar", "عَنْ"),
                        step(n, "alqama", "عَنْ"),
                        step(n, "muhammad", "عَنْ"),
                        step(n, "yahya", "عَنْ"),
                        step(n, "malik", "عَنْ")));

        // Цепь 3 - Сахих Муслим №1907 (через Малика).
        saveSanad(hadith, now, "SAHIH", n.get("muslim"), false,
                "{\"collectionRu\":\"Сахих Муслим\",\"collectionAr\":\"صحيح مسلم\"}",
                List.of(
                        step(n, "umar", "عَنْ"),
                        step(n, "alqama", "عَنْ"),
                        step(n, "muhammad", "عَنْ"),
                        step(n, "yahya", "عَنْ"),
                        step(n, "malik", "عَنْ"),
                        step(n, "muslim", "حَدَّثَنَا")));

        saveMatn(hadith, now,
                "إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ، وَإِنَّمَا لِكُلِّ امْرِئٍ مَا نَوَى، فَمَنْ كَانَتْ "
                        + "هِجْرَتُهُ إِلَى دُنْيَا يُصِيبُهَا أَوْ إِلَى امْرَأَةٍ يَنْكِحُهَا فَهِجْرَتُهُ إِلَى مَا "
                        + "هَاجَرَ إِلَيْهِ.",
                NORMALIZED_MATN,
                "Поистине, дела (оцениваются) только по намерениям, и каждому человеку "
                        + "(достанется) лишь то, что он намеревался. Так, чья хиджра была к "
                        + "Аллаху и Его Посланнику - переселился к Аллаху и Его Посланнику, а "
                        + "чья хиджра была ради мирского или ради женщины, на которой хотел "
                        + "жениться - переселился к тому, к чему переселялся.",
                "Actions are but by intentions, and every person will have only what he "
                        + "intended.",
                1, true, "Базовая редакция Сахих аль-Бухари №1: множественное «النيّات».");

        saveMatn(hadith, now,
                "إِنَّمَا الأَعْمَالُ بِالنِّيَّةِ، وَإِنَّمَا لِامْرِئٍ مَا نَوَى، فَمَنْ كَانَتْ هِجْرَتُهُ إِلَى "
                        + "اللَّهِ وَرَسُولِهِ فَهِجْرَتُهُ إِلَى اللَّهِ وَرَسُولِهِ.",
                "الاعمال بالنية وانما لامرئ ما نوى",
                "Поистине, дела (оцениваются) только по намерению, и человеку (достанется) "
                        + "лишь то, что он намеревался.",
                null,
                1907, false,
                "Редакция Сахих Муслим №1907 (путь Малика): единственное число «النيّة»; "
                        + "оба пункта хиджры приведены полностью.");

        saveMatn(hadith, now,
                "الأَعْمَالُ بِالنِّيَّةِ، وَلِكُلِّ امْرِئٍ مَا نَوَى.",
                "الاعمال بالنية ولكل امرئ ما نوى",
                "Дела (оцениваются) по намерению, и каждому человеку (достанется) лишь то, "
                        + "что он намеревался.",
                null,
                6689, false,
                "Краткая редакция аль-Бухари (напр. №6689) - без вводной частицы «إنّما».");

        log.info("Dev hadith seed completed: 1 hadith + {} narrators + 3 sanads + 3 matns",
                n.size());
    }

    private Narrator saveNarrator(Instant now,
                                  String nameAr, String nameNormalized,
                                  String kunya, String laqab,
                                  Integer yearBirth, Integer yearDeath,
                                  String birthplace, String deathPlace, String residence,
                                  String reliability, String comment,
                                  String nameRu, String generation) {
        String metadata = "{\"nameRu\":\"" + nameRu + "\",\"generation\":\"" + generation + "\"}";
        Narrator narrator = new Narrator(
                UUID.randomUUID(), null, nameAr, nameNormalized,
                kunya, laqab, yearBirth, yearDeath,
                birthplace, deathPlace, residence,
                reliability, comment, 0, metadata, now);
        narratorRepository.save(narrator);
        return narrator;
    }

    private void saveSanad(Hadith hadith, Instant now, String grade,
                           Narrator collector, boolean primaryChain,
                           String metadata, List<ChainStep> steps) {
        Sanad sanad = new Sanad(
                UUID.randomUUID(), hadith.id(), grade,
                collector.id(), null, primaryChain, metadata, now);
        sanadRepository.save(sanad);
        for (int position = 0; position < steps.size(); position++) {
            ChainStep st = steps.get(position);
            sanadRepository.saveNarratorLink(
                    new SanadNarrator(sanad.id(), position, st.narrator().id(), st.phrase()));
        }
    }

    private void saveMatn(Hadith hadith, Instant now,
                          String textAr, String textArNormalized,
                          String textRu, String textEn,
                          Integer printedNumber, boolean isPrimary, String divergence) {
        Matn matn = new Matn(
                UUID.randomUUID(), hadith.id(), textAr, textArNormalized,
                textRu, textEn, null, printedNumber, null, null,
                isPrimary, divergence, null, now);
        matnRepository.save(matn);
    }

    private static ChainStep step(Map<String, Narrator> narrators, String slug, String phrase) {
        Narrator narrator = narrators.get(slug);
        if (narrator == null) {
            throw new IllegalStateException("Unknown narrator slug in seed: " + slug);
        }
        return new ChainStep(narrator, phrase);
    }

    /** Звено цепи: narrator + формула передачи (тахаммуль). */
    private record ChainStep(Narrator narrator, String phrase) {
    }
}
