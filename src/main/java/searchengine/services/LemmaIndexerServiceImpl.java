package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.SearchIndex;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LemmaIndexerServiceImpl implements LemmaIndexerService {

    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final LemmaService lemmaService;

    @Transactional
    @Override
    public void saveLemmas(Page page, String text) {
        Map<String, Integer> lemmas = lemmaService.extractLemmas(text);
        lemmas.forEach((lemmaText, count) -> {
            Lemma lemma = lemmaRepository.findByLemmaAndSite(lemmaText, page.getSite())
                    .orElseGet(() -> {
                        Lemma newLemma = new Lemma();
                        newLemma.setLemma(lemmaText);
                        newLemma.setSite(page.getSite());
                        newLemma.setFrequency(0);
                        try {
                            return lemmaRepository.saveAndFlush(newLemma);
                        } catch (Exception e) {
                            return lemmaRepository.findByLemmaAndSite(lemmaText, page.getSite()).get();
                        }
                    });

            lemma.setFrequency(lemma.getFrequency() + count);
            lemmaRepository.save(lemma);

            SearchIndex index = new SearchIndex();
            index.setPage(page);
            index.setLemma(lemma);
            index.setRank((float) count);
            indexRepository.save(index);
        });
    }

}

