package searchengine.services;

import lombok.SneakyThrows;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class LemmaServiceImpl implements LemmaService {

    private final LuceneMorphology russianMorph;
    private final LuceneMorphology englishMorph;

    @SneakyThrows
    public LemmaServiceImpl() {
        this.russianMorph = new RussianLuceneMorphology();
        this.englishMorph = new EnglishLuceneMorphology();
    }

    public void testMorphology() {
        try {
            String testWordRu = "бегу"; // Тестовое русское слово
            String testWordEn = "running"; // Тестовое английское слово

            List<String> ruLemmas = russianMorph.getNormalForms(testWordRu);
            List<String> enLemmas = englishMorph.getNormalForms(testWordEn);

            System.out.println("✅ TEST: Русская лемма для '" + testWordRu + "': " + ruLemmas);
            System.out.println("✅ TEST: Английская лемма для '" + testWordEn + "': " + enLemmas);
        } catch (Exception e) {
            System.err.println("❌ Ошибка в тесте LuceneMorphology: " + e.getMessage());
            e.printStackTrace();
        }
    }


    @Override
    public Map<String, Integer> extractLemmas(String text) {

        Map<String, Integer> lemmas = new HashMap<>();

        // Очищаем текст от всех символов, кроме букв и пробелов
        String cleanText = text.toLowerCase().replaceAll("[^а-яa-z\\s]", "").trim();

        // Разбиваем текст на слова
        String[] words = cleanText.split("\\s+");

        for (String word : words) {
            if (word.matches(".*\\d+.*")) { // Пропускаем слова с числами (номера телефонов и даты)
                System.out.println("DEBUG: Пропущено слово с цифрами -> " + word);
                continue;
            }
            if (word.length() < 2) { // Пропускаем односимвольные слова
                continue;
            }


            List<String> normalForms = new ArrayList<>();

            try {
                if (word.matches("[а-я]+")) {
                    normalForms = russianMorph.getNormalForms(word);
                } else if (word.matches("[a-z]+")) {
                    normalForms = englishMorph.getNormalForms(word);
                } else {
                    continue;
                }

            } catch (Exception e) {
                System.err.println("❌ Ошибка лемматизации слова: " + word + " -> " + e.getMessage());
                e.printStackTrace();
                continue;
            }

            for (String lemma : normalForms) {
                lemmas.put(lemma, lemmas.getOrDefault(lemma, 0) + 1);
            }
        }
        return lemmas;
    }
}