package searchengine.services;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class LemmaServiceImpl implements LemmaService {

    private final LuceneMorphology russianMorph;
    private final LuceneMorphology englishMorph;

    @SneakyThrows
    public LemmaServiceImpl() {
        this.russianMorph = new RussianLuceneMorphology();
        this.englishMorph = new EnglishLuceneMorphology();
    }


    @Override
    public Map<String, Integer> extractLemmas(String text) {

        Map<String, Integer> lemmas = new HashMap<>();

        String cleanText = text.toLowerCase().replaceAll("[^а-яa-z\\s]", "").trim();

        String[] words = cleanText.split("\\s+");

        for (String word : words) {
            if (word.matches(".*\\d+.*")) {
                log.debug("DEBUG: Пропущено слово с цифрами -> {}", word);
                continue;
            }
            if (word.length() < 2) {
                continue;
            }


            List<String> normalForms;
            try {
                if (word.matches("[а-я]+")) {
                    normalForms = russianMorph.getNormalForms(word);
                } else if (word.matches("[a-z]+")) {
                    normalForms = englishMorph.getNormalForms(word);
                } else {
                    continue;
                }

            } catch (Exception e) {
                log.error("Ошибка лемматизации слова: {} -> {}", word, e.getMessage(), e);
                continue;
            }

            for (String lemma : normalForms) {
                lemmas.put(lemma, lemmas.getOrDefault(lemma, 0) + 1);
            }
        }
        return lemmas;
    }
}