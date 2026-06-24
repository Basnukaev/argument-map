package ru.basnukaev.argumentmap.hadith.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.hadith.domain.HadithDataHealth;
import ru.basnukaev.argumentmap.hadith.repository.HadithHealthRepository;
import ru.basnukaev.argumentmap.hadith.web.dto.HadithDataHealthResponse;

/**
 * Сборка снапшота «здоровья» данных хадис-корпуса (P1-2): мостит две
 * аггрегации репозитория в плоский ответ. Read-only.
 */
@Service
public class HadithHealthService {

    private final HadithHealthRepository healthRepository;

    public HadithHealthService(HadithHealthRepository healthRepository) {
        this.healthRepository = healthRepository;
    }

    @Transactional(readOnly = true)
    public HadithDataHealthResponse health() {
        HadithDataHealth.Hadiths h = healthRepository.countHadithGaps();
        HadithDataHealth.Narrators n = healthRepository.countNarratorGaps();
        return new HadithDataHealthResponse(
                h.total(),
                h.nullAuthenticity(),
                h.withoutSanad(),
                h.withoutMatn(),
                h.nullCollection(),
                n.total(),
                n.nullTabaqa(),
                n.unknownReliability(),
                n.nullGradeText());
    }
}
