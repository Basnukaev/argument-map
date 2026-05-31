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

import ru.basnukaev.argumentmap.hadith.domain.Collection;
import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.domain.HadithStatus;
import ru.basnukaev.argumentmap.hadith.domain.Matn;
import ru.basnukaev.argumentmap.hadith.domain.Narrator;
import ru.basnukaev.argumentmap.hadith.domain.NarratorReliability;
import ru.basnukaev.argumentmap.hadith.domain.Sanad;
import ru.basnukaev.argumentmap.hadith.domain.SanadNarrator;
import ru.basnukaev.argumentmap.hadith.repository.CollectionRepository;
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

    // Курируемые оценки учёных в hd_hadiths.metadata.grades (jsonb) —
    // surface'ятся в detail endpoint через HadithController.parseGrades.
    private static final String GRADES_JSON = "{\"grades\":["
            + "{\"scholar\":\"аль-Бухари\",\"grade\":\"Сахих\",\"note\":\"Хадис №1, открывает «Сахих»; повторён ещё в 6 местах\"},"
            + "{\"scholar\":\"Муслим\",\"grade\":\"Сахих\",\"note\":\"Китаб аль-имара, №1907\"},"
            + "{\"scholar\":\"Муттафакун алейхи\",\"grade\":\"Сахих\",\"note\":\"Согласован аль-Бухари и Муслимом — высшая степень достоверности\"},"
            + "{\"scholar\":\"аль-Албани\",\"grade\":\"Сахих\",\"note\":\"Подтверждён везде, где встречается\"},"
            + "{\"scholar\":\"Критики (Яхья аль-Каттан и др.)\",\"grade\":\"Гариб, но сахих\",\"note\":\"Одиночная передача (фард) на четырёх уровнях, но цепь полностью достоверна; машхур ниже Яхьи ибн Саида\"}"
            + "]}";

    private final HadithRepository hadithRepository;
    private final NarratorRepository narratorRepository;
    private final SanadRepository sanadRepository;
    private final MatnRepository matnRepository;
    private final CollectionRepository collectionRepository;

    public DevHadithSeeder(HadithRepository hadithRepository,
                           NarratorRepository narratorRepository,
                           SanadRepository sanadRepository,
                           MatnRepository matnRepository,
                           CollectionRepository collectionRepository) {
        this.hadithRepository = hadithRepository;
        this.narratorRepository = narratorRepository;
        this.sanadRepository = sanadRepository;
        this.matnRepository = matnRepository;
        this.collectionRepository = collectionRepository;
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

        // Сборники (Phase 5 §11) — создаются один раз, разделяются всеми
        // хадисами seed'а. compiler_narrator_id → составитель в hd_narrators.
        Map<String, UUID> collections = seedCollections(now,
                n.get("bukhari"), n.get("muslim"), n.get("malik"));

        Hadith hadith = new Hadith(
                UUID.randomUUID(), collections.get("bukhari"), 1, NORMALIZED_MATN,
                HadithStatus.CANONICAL, null, GRADES_JSON, now);
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

        saveMatn(hadith, now, collections.get("bukhari"),
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

        saveMatn(hadith, now, collections.get("muslim"),
                "إِنَّمَا الأَعْمَالُ بِالنِّيَّةِ، وَإِنَّمَا لِامْرِئٍ مَا نَوَى، فَمَنْ كَانَتْ هِجْرَتُهُ إِلَى "
                        + "اللَّهِ وَرَسُولِهِ فَهِجْرَتُهُ إِلَى اللَّهِ وَرَسُولِهِ، وَمَنْ كَانَتْ هِجْرَتُهُ إِلَى دُنْيَا "
                        + "يُصِيبُهَا أَوِ امْرَأَةٍ يَتَزَوَّجُهَا فَهِجْرَتُهُ إِلَى مَا هَاجَرَ إِلَيْهِ.",
                "الاعمال بالنية وانما لامرئ ما نوى",
                "Редакция Сахих Муслим: единственное число «النيّة»; оба пункта хиджры "
                        + "(к Аллаху и Его Посланнику / ради мирского) приведены полностью.",
                null,
                1907, false,
                "Редакция Сахих Муслим №1907 (путь Малика через аль-Каанаби): единственное "
                        + "число «النيّة» вместо «النيّات», оба пункта хиджры в полном виде.");

        saveMatn(hadith, now, null,
                "الأَعْمَالُ بِالنِّيَّةِ، وَلِكُلِّ امْرِئٍ مَا نَوَى.",
                "الاعمال بالنية ولكل امرئ ما نوى",
                "Дела (оцениваются) по намерению, и каждому человеку (достанется) лишь то, "
                        + "что он намеревался.",
                null,
                null, false,
                "Краткая форма (часто цитируется в книгах усуль/фикха), без вводной частицы «إنّما».");

        seedIslamOnFive(now, collections);
        seedReligionSincerity(now, collections);

        log.info("Dev hadith seed completed: 3 hadiths "
                + "(Дела по намерениям / Ислам на пяти столпах / Религия — искренность)");
    }

    /** Хадис «Ислам построен на пяти» (Бухари №8 / Муслим №16, муттафакун алейхи). */
    private void seedIslamOnFive(Instant now, Map<String, UUID> collections) {
        Map<String, Narrator> n = new LinkedHashMap<>();
        n.put("ibn-umar", saveNarrator(now,
                "عَبْد الله بن عُمَر", "abdullah ibn umar", "أبو عبد الرحمن", null,
                null, 73, "Мекка", "Мекка", "Медина, затем Мекка",
                NarratorReliability.SAHABI,
                "Сподвижник, сын халифа Умара; один из самых плодовитых передатчиков, "
                        + "известен крайней точностью в следовании Сунне.",
                "Абдуллах ибн Умар", "Сподвижник"));
        n.put("ikrima", saveNarrator(now,
                "عِكْرِمَة بن خالد المخزومي", "ikrima ibn khalid", null, "аль-Махзуми",
                null, null, "Мекка", null, "Мекка",
                NarratorReliability.THIQA, "Достоверный (сикъа), мекканский табиин (Такриб Ибн Хаджара).",
                "Икрима ибн Халид аль-Махзуми", "Табиин"));
        n.put("hanzala", saveNarrator(now,
                "حَنْظَلَة بن أبي سفيان الجمحي", "hanzala ibn abi sufyan", null, "аль-Джумахи",
                null, 151, "Мекка", null, "Мекка",
                NarratorReliability.THIQA, "Достоверный (сикъа), мекканский передатчик, опора Бухари и Муслима.",
                "Ханзаля ибн Аби Суфьян аль-Джумахи", "Атба ат-табиин"));
        n.put("ubaydullah", saveNarrator(now,
                "عُبَيْد الله بن موسى العبسي", "ubaydullah ibn musa", "أبو محمد", "аль-Абси",
                null, 213, "Куфа", "Куфа", "Куфа",
                NarratorReliability.THIQA, "Достоверный (сикъа), учитель аль-Бухари, куфийский хафиз.",
                "Убайдуллах ибн Муса аль-Абси", "Шейх аль-Бухари"));
        n.put("saad", saveNarrator(now,
                "سَعْد بن عُبَيْدَة السلمي", "saad ibn ubayda", "أبو حمزة", "ас-Сулями",
                null, null, "Куфа", null, "Куфа",
                NarratorReliability.THIQA, "Достоверный (сикъа), куфийский табиин.",
                "Саад ибн Убайда ас-Сулями", "Табиин"));
        n.put("abu-malik", saveNarrator(now,
                "أبو مالك الأشجعي سعد بن طارق", "abu malik al-ashjai", "أبو مالك", "аль-Ашджаи",
                null, null, "Куфа", null, "Куфа",
                NarratorReliability.THIQA, "Достоверный (сикъа), куфийский передатчик (Саад ибн Тарик).",
                "Абу Малик аль-Ашджаи", "Атба ат-табиин"));
        n.put("abu-khalid", saveNarrator(now,
                "أبو خالد الأحمر سليمان بن حيان", "abu khalid al-ahmar", "أبو خالد", "аль-Ахмар",
                null, 190, "Куфа", null, "Куфа",
                NarratorReliability.SADUQ, "Правдивый (садукъ), иногда ошибался; хадисы в Сахихах приняты.",
                "Абу Халид аль-Ахмар (Сулейман ибн Хайян)", "Атба ат-табиин"));
        n.put("ibn-numayr", saveNarrator(now,
                "محمد بن عبد الله بن نمير الهمداني", "muhammad ibn abdullah ibn numayr",
                "أبو عبد الرحمن", "аль-Хамдани",
                null, 234, "Куфа", "Куфа", "Куфа",
                NarratorReliability.THIQA, "Достоверный (сикъа), куфийский хафиз, учитель Муслима.",
                "Мухаммад ибн Абдуллах ибн Нумайр аль-Хамдани", "Шейх Муслима"));

        String grades = "{\"grades\":["
                + "{\"scholar\":\"аль-Бухари\",\"grade\":\"Сахих\",\"note\":\"Сахих аль-Бухари №8, Китаб аль-иман\"},"
                + "{\"scholar\":\"Муслим\",\"grade\":\"Сахих\",\"note\":\"Сахих Муслим №16, Китаб аль-иман\"},"
                + "{\"scholar\":\"Муттафакун алейхи\",\"grade\":\"Сахих\",\"note\":\"Согласован Бухари и Муслимом\"}"
                + "]}";
        Hadith hadith = new Hadith(UUID.randomUUID(), collections.get("bukhari"), 8,
                "بني الإسلام على خمس شهادة أن لا إله إلا الله وأن محمدا رسول الله "
                        + "وإقام الصلاة وإيتاء الزكاة والحج وصوم رمضان",
                HadithStatus.CANONICAL, null, grades, now);
        hadithRepository.save(hadith);

        saveSanad(hadith, now, "SAHIH", n.get("ubaydullah"), true,
                "{\"collectionRu\":\"Сахих аль-Бухари\",\"collectionAr\":\"صحيح البخاري\"}",
                List.of(step(n, "ibn-umar", "عَنْ"), step(n, "ikrima", "عَنْ"),
                        step(n, "hanzala", "عَنْ"), step(n, "ubaydullah", "حَدَّثَنَا")));
        saveSanad(hadith, now, "SAHIH", n.get("ibn-numayr"), false,
                "{\"collectionRu\":\"Сахих Муслим\",\"collectionAr\":\"صحيح مسلم\"}",
                List.of(step(n, "ibn-umar", "عَنْ"), step(n, "saad", "عَنْ"),
                        step(n, "abu-malik", "عَنْ"), step(n, "abu-khalid", "عَنْ"),
                        step(n, "ibn-numayr", "حَدَّثَنَا")));

        saveMatn(hadith, now, collections.get("bukhari"),
                "بُنِيَ الإِسْلاَمُ عَلَى خَمْسٍ: شَهَادَةِ أَنْ لاَ إِلَهَ إِلاَّ اللَّهُ وَأَنَّ مُحَمَّدًا رَسُولُ اللَّهِ، "
                        + "وَإِقَامِ الصَّلاَةِ، وَإِيتَاءِ الزَّكَاةِ، وَالْحَجِّ، وَصَوْمِ رَمَضَانَ.",
                "buniya al-islam ala khams",
                "Ислам построен на пяти столпах: свидетельство, что нет божества кроме Аллаха и "
                        + "что Мухаммад — посланник Аллаха; молитва; закят; хадж; пост в рамадан.",
                null, 8, true, "Редакция аль-Бухари (№8): хадж упомянут раньше поста.");
        saveMatn(hadith, now, collections.get("muslim"),
                "بُنِيَ الإِسْلاَمُ عَلَى خَمْسَةٍ: عَلَى أَنْ يُوَحَّدَ اللَّهُ، وَإِقَامِ الصَّلاَةِ، وَإِيتَاءِ الزَّكَاةِ، "
                        + "وَصِيَامِ رَمَضَانَ، وَالْحَجِّ.",
                "buniya al-islam ala khamsa",
                "Редакция Муслима: единобожие Аллаха; молитва; закят; пост в рамадан; хадж.",
                null, 16, false, "Редакция Муслима (16a): первый столп — единобожие; пост раньше хаджа.");
    }

    /** Хадис «Религия — это искренность» (Муслим №55; 7-й в «Сорока ан-Навави»). */
    private void seedReligionSincerity(Instant now, Map<String, UUID> collections) {
        Map<String, Narrator> n = new LinkedHashMap<>();
        n.put("tamim", saveNarrator(now,
                "تَمِيم بن أوس الداري", "tamim al-dari", "أبو رقية", "ад-Дари",
                null, 40, "Палестина (Дарун)", "Бейт-Джибрин (Палестина)", "Медина, затем Шам",
                NarratorReliability.SAHABI,
                "Сподвижник; принял ислам ок. 9 г.х. (прежде христианин). Известен набожностью "
                        + "и долгим ночным молением.",
                "Тамим ибн Аус ад-Дари", "Сподвижник"));
        n.put("ata", saveNarrator(now,
                "عَطَاء بن يزيد الليثي الجندعي", "ata ibn yazid al-laythi", "أبو محمد", "аль-Лайси",
                null, 107, "Медина", "Дамаск (Шам)", "Медина, затем Дамаск",
                NarratorReliability.THIQA, "Ибн Хаджар: сикъа (надёжный). Достоверный табиин.",
                "Ата ибн Язид аль-Лайси аль-Джундаи", "Табиин"));
        n.put("suhayl", saveNarrator(now,
                "سُهَيْل بن أبي صالح السمان المدني", "suhayl ibn abi salih", "أبو يزيد", "аль-Мадани",
                null, 138, "Медина", "Медина", "Медина",
                NarratorReliability.SADUQ,
                "Ибн Хаджар: садукъ, память изменилась к концу жизни. Основной передатчик Муслима.",
                "Сухайль ибн Аби Салих ас-Самман", "Табиин (мл.)"));
        n.put("sufyan", saveNarrator(now,
                "سُفْيَان بن عيينة الهلالي", "sufyan ibn uyaynah", "أبو محمد", "аль-Хиляли аль-Макки",
                107, 198, "Куфа", "Мекка", "Мекка",
                NarratorReliability.THIQA, "Ибн Хаджар: сикъа хафиз факих имам худжжа. Мухаддис Мекки.",
                "Суфьян ибн Уяйна", "Атба ат-табиин"));
        n.put("ibn-abbad", saveNarrator(now,
                "محمد بن عباد بن الزبرقان المكي", "muhammad ibn abbad al-makki", "أبو عبد الله", "аль-Макки",
                null, 234, "Мекка", "Мекка", "Мекка",
                NarratorReliability.SADUQ, "Ибн Хаджар: садукъ, иногда ошибался. Шейх Муслима.",
                "Мухаммад ибн Аббад аль-Макки", "Шейх Муслима"));

        String grades = "{\"grades\":["
                + "{\"scholar\":\"Муслим\",\"grade\":\"Сахих\",\"note\":\"Сахих Муслим №55\"},"
                + "{\"scholar\":\"ан-Навави\",\"grade\":\"Сахих\",\"note\":\"7-й хадис «Сорока ан-Навави» с развёрнутым шархом\"},"
                + "{\"scholar\":\"Общее мнение мухаддисов\",\"grade\":\"Сахих\",\"note\":\"Иснад достоверен, передатчики надёжны, разрыва нет\"}"
                + "]}";
        Hadith hadith = new Hadith(UUID.randomUUID(), collections.get("muslim"), 55,
                "الدين النصيحة قلنا لمن قال لله ولكتابه ولرسوله ولأئمة المسلمين وعامتهم",
                HadithStatus.CANONICAL, null, grades, now);
        hadithRepository.save(hadith);

        saveSanad(hadith, now, "SAHIH", n.get("ibn-abbad"), true,
                "{\"collectionRu\":\"Сахих Муслим\",\"collectionAr\":\"صحيح مسلم\"}",
                List.of(step(n, "tamim", "عَنْ"), step(n, "ata", "عَنْ"),
                        step(n, "suhayl", "عَنْ"), step(n, "sufyan", "عَنْ"),
                        step(n, "ibn-abbad", "حَدَّثَنَا")));

        saveMatn(hadith, now, collections.get("muslim"),
                "الدِّينُ النَّصِيحَةُ. قُلْنَا لِمَنْ؟ قَالَ: لِلَّهِ وَلِكِتَابِهِ وَلِرَسُولِهِ "
                        + "وَلِأَئِمَّةِ الْمُسْلِمِينَ وَعَامَّتِهِمْ.",
                "ad-din an-nasiha",
                "«Религия — это искренность (насиха)». Мы спросили: «По отношению к кому?» Он "
                        + "сказал: «К Аллаху, Его Писанию, Его Посланнику, предводителям мусульман "
                        + "и простым людям из их числа».",
                null, 55, true,
                "Основная редакция Сахих Муслим (55a). Есть параллельные версии 55b/55c с тем же текстом.");
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

    private void saveMatn(Hadith hadith, Instant now, UUID collectionId,
                          String textAr, String textArNormalized,
                          String textRu, String textEn,
                          Integer printedNumber, boolean isPrimary, String divergence) {
        Matn matn = new Matn(
                UUID.randomUUID(), hadith.id(), textAr, textArNormalized,
                textRu, textEn, collectionId, printedNumber, null, null,
                isPrimary, divergence, null, now);
        matnRepository.save(matn);
    }

    private Map<String, UUID> seedCollections(Instant now, Narrator bukhari,
                                              Narrator muslim, Narrator malik) {
        Map<String, UUID> ids = new LinkedHashMap<>();
        ids.put("bukhari", saveCollection(now, "bukhari",
                "صحيح البخاري", "Sahih al-Bukhari", "Сахих аль-Бухари", bukhari.id()));
        ids.put("muslim", saveCollection(now, "muslim",
                "صحيح مسلم", "Sahih Muslim", "Сахих Муслим", muslim.id()));
        ids.put("muwatta", saveCollection(now, "muwatta",
                "موطأ مالك", "Muwatta Malik", "Муватта Малика", malik.id()));
        return ids;
    }

    private UUID saveCollection(Instant now, String slug, String nameAr,
                                String nameEn, String nameRu, UUID compilerNarratorId) {
        Collection c = new Collection(UUID.randomUUID(), slug, nameAr, nameEn, nameRu,
                compilerNarratorId, null, null, now);
        collectionRepository.save(c);
        return c.id();
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
