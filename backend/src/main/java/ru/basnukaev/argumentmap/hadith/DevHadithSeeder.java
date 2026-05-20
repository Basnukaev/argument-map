package ru.basnukaev.argumentmap.hadith;

import java.time.Instant;
import java.util.List;
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
 * Vision 49d Phase 2/3 dev seed - sample хадис для UI testing
 * HadithListPage + HadithDetailPage в local profile.
 *
 * <p>Создаёт 1 sample хадис («Действия по намерениям») с 1 sanad
 * через 3 narrators (Умар → Алкама → Мухаммад ибн Ибрахим →
 * Яхья ибн Саид → имам Малик → Бухари) и 2 matns (вариации).
 *
 * <p>Idempotent через check на existence (count == 0 - первый запуск).
 */
@Component
@Profile({"local", "dev"})
public class DevHadithSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevHadithSeeder.class);

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
        // Idempotent check - skip если уже seeded.
        if (hadithRepository.countFiltered(null, null, null) > 0) {
            log.debug("Dev hadith seed skipped - existing data found");
            return;
        }

        try {
            seed();
        } catch (Exception e) {
            // Best-effort: dev seed не должен crash backend
            log.warn("Dev hadith seed failed (acceptable - prod не affected): {}",
                    e.getMessage());
        }
    }

    private void seed() {
        Instant now = Instant.now();

        // Создаём 5 narrators цепи хадиса «Действия по намерениям»
        Narrator umar = saveNarrator(now,
                "عمر بن الخطاب رضي الله عنه", "umar ibn al-khattab",
                "أبو حفص", "Faruq", null, 23, "Мекка", "Медина",
                NarratorReliability.THIQA, "Второй праведный халиф");
        Narrator alqama = saveNarrator(now,
                "علقمة بن وقاص الليثي", "alqamah ibn waqqas al-laythi",
                null, null, null, 80, "Медина", "Медина",
                NarratorReliability.THIQA, "Tabi'i");
        Narrator muhammad = saveNarrator(now,
                "محمد بن إبراهيم التيمي", "muhammad ibn ibrahim al-taymi",
                "أبو عبد الله", null, null, 120, "Медина", "Медина",
                NarratorReliability.THIQA, "Tabi'i");
        Narrator yahya = saveNarrator(now,
                "يحيى بن سعيد الأنصاري", "yahya ibn said al-ansari",
                "أبو سعيد", null, null, 143, "Медина", "Медина",
                NarratorReliability.THIQA, "Имам мединский");
        Narrator malik = saveNarrator(now,
                "مالك بن أنس", "malik ibn anas",
                "أبو عبد الله", null, 93, 179, "Медина", "Медина",
                NarratorReliability.THIQA, "Имам دار الهجرة");

        // Создаём sample hadith
        Hadith hadith = new Hadith(
                UUID.randomUUID(), null, 1,
                "إنما الأعمال بالنيات وإنما لكل امرئ ما نوى",
                HadithStatus.CANONICAL, null, null, now
        );
        hadithRepository.save(hadith);

        // Sanad: chain от Umar к Buhari
        Sanad sanad = new Sanad(
                UUID.randomUUID(), hadith.id(),
                "SAHIH", malik.id(), null, true,
                null, now
        );
        sanadRepository.save(sanad);

        // Position 0 = Umar (ближайший к Пророку ﷺ - сообщил от него)
        List<SanadNarrator> links = List.of(
                new SanadNarrator(sanad.id(), 0, umar.id(), "سمعت"),
                new SanadNarrator(sanad.id(), 1, alqama.id(), "عن"),
                new SanadNarrator(sanad.id(), 2, muhammad.id(), "حدثني"),
                new SanadNarrator(sanad.id(), 3, yahya.id(), "أخبرنا"),
                new SanadNarrator(sanad.id(), 4, malik.id(), "حدثنا")
        );
        for (SanadNarrator link : links) {
            sanadRepository.saveNarratorLink(link);
        }

        // Matn variants
        Matn primaryMatn = new Matn(
                UUID.randomUUID(), hadith.id(),
                "إنما الأعمال بالنيات وإنما لكل امرئ ما نوى",
                "innama al-amal bi-niyyat",
                "Дела оцениваются по намерениям, и каждому достанется лишь то, что он намеревался получить",
                null, null, null, null, null,
                true, null, null, now
        );
        matnRepository.save(primaryMatn);

        Matn variantMatn = new Matn(
                UUID.randomUUID(), hadith.id(),
                "الأعمال بالنية",
                "al-amal bi-niyyah",
                "Дела — по намерению",
                null, null, null, null, null,
                false, "Краткая версия opener", null, now
        );
        matnRepository.save(variantMatn);

        log.info("Dev hadith seed completed: 1 hadith + 5 narrators + 1 sanad + 2 matns");
    }

    private Narrator saveNarrator(Instant now,
                                  String nameAr, String nameNormalized,
                                  String kunya, String laqab,
                                  Integer yearBirth, Integer yearDeath,
                                  String birthplace, String residence,
                                  String reliability, String comment) {
        Narrator n = new Narrator(
                UUID.randomUUID(), null, nameAr, nameNormalized,
                kunya, laqab, yearBirth, yearDeath,
                birthplace, residence, residence,
                reliability, comment, 0, null, now
        );
        narratorRepository.save(n);
        return n;
    }
}
