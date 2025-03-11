package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import searchengine.dto.SearchResponse;
import searchengine.dto.SearchResult;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaService lemmaService;

    @Override
    public SearchResponse search(String query, String siteUrl, int offset, int limit) {
        List<SearchResult> results;

        if (siteUrl != null) {
            List<Site> sites = siteRepository.findByUrl(siteUrl);
            if (sites.isEmpty()) {
                throw new IllegalArgumentException("Сайт не найден: " + siteUrl);
            }
            Site site = sites.get(0);
            results = findPages(query, site, offset, limit);
        } else {
            results = siteRepository.findAll().stream()
                    .flatMap(site -> findPages(query, site, offset, limit).stream())
                    .collect(Collectors.toList());
        }

        return new SearchResponse(results.size(), results);
    }

    private List<SearchResult> findPages(String query, Site site, int offset, int limit) {
        List<Page> pages = pageRepository.findPagesByLemma(site.getId(), query, limit, offset);

        return pages.stream().map(page -> {
            String snippet = createSnippet(page.getContent(), query);
            float relevance = calculateRelevance(page.getContent(), query);
            return new SearchResult(
                    site.getUrl(),
                    site.getName(),
                    page.getUri(),
                    extractTitle(page.getContent()),
                    snippet,
                    relevance
            );
        }).collect(Collectors.toList());
    }

    private String extractTitle(String content) {
        if (content == null || content.isBlank()) {
            return "Без заголовка";
        }
        Document doc = Jsoup.parse(content);
        String title = doc.title();
        if (!title.isBlank()) return title;

        Element h1 = doc.selectFirst("h1");
        if (h1 != null && !h1.text().isBlank()) {
            return h1.text();
        }

        String bodyText = doc.text();
        return bodyText.substring(0, Math.min(50, bodyText.length())) + "...";
    }

    // ✅ Обновлённый метод создания сниппета
    private String createSnippet(String content, String query) {
        final int snippetLength = 200;
        if (content == null || content.isBlank()) {
            return "Фрагмент текста отсутствует.";
        }

        String text = Jsoup.parse(content).text();
        Map<String, Integer> queryLemmas = lemmaService.extractLemmas(query);
        Set<String> queryLemmaSet = queryLemmas.keySet();

        int startPosition = findLemmaPosition(text, queryLemmaSet);
        if (startPosition == -1) {
            startPosition = 0;
        }

        int endPosition = Math.min(text.length(), startPosition + snippetLength);
        String snippet = text.substring(startPosition, endPosition).trim();

        // Подсветка слов в сниппете
        for (String lemma : queryLemmaSet) {
            snippet = snippet.replaceAll("(?i)(" + lemma + ")", "<span class=\"highlight\">$1</span>");
        }

        if (endPosition < text.length()) {
            snippet += "...";
        }

        return snippet;
    }



    private int findLemmaPosition(String cleanText, Set<String> queryLemmaSet) {
        String[] words = cleanText.split("\\s+");
        int position = 0;

        for (String word : words) {
            Set<String> wordLemmas = lemmaService.extractLemmas(word.toLowerCase()).keySet();
            boolean matches = wordLemmas.stream().anyMatch(queryLemmaSet::contains);
            if (matches) {
                return position;
            }
            position += word.length() + 1;
        }
        return -1;
    }

    private float calculateRelevance(String content, String query) {
        Map<String, Integer> queryLemmas = lemmaService.extractLemmas(query);
        Map<String, Integer> contentLemmas = lemmaService.extractLemmas(content);

        int totalOccurrences = queryLemmas.keySet().stream()
                .mapToInt(lemma -> contentLemmas.getOrDefault(lemma, 0))
                .sum();

        int totalWords = content.split("\\s+").length;

        return totalWords > 0 ? (float) totalOccurrences / totalWords : 0;
    }
}
