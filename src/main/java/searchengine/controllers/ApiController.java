package searchengine.controllers;

import org.jsoup.Jsoup;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.*;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.IndexingService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final SearchService searchService;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;

    public ApiController(StatisticsService statisticsService,
                         IndexingService indexingService,
                         SearchService searchService,
                         PageRepository pageRepository,
                         SiteRepository siteRepository) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
        this.searchService = searchService;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
    }


    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @RequestMapping(value = "/startIndexing", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<?> startIndexing() {
        if (indexingService.isIndexingInProgress()) {
            return ResponseEntity.badRequest().body(new ErrorResponse(false, "Индексация уже запущена"));
        }
        indexingService.startIndexing();
        return ResponseEntity.ok(new SuccessResponse(true));
    }


    @RequestMapping(value = "/stopIndexing", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<?> stopIndexing() {
        if (!indexingService.isIndexingInProgress()) {
            return ResponseEntity.badRequest().body(new ErrorResponse(false, "Индексация не запущена"));
        }
        indexingService.stopIndexing();
        return ResponseEntity.ok(new SuccessResponse(true));
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam String query,
            @RequestParam(required = false) String site,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        if (query.isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorResponse(false, "Задан пустой поисковый запрос"));
        }
        SearchResponse response = searchService.search(query, site, offset, limit);
        return ResponseEntity.ok(new SearchResultResponse(true, response.getCount(), response.getData()));
    }

    @PostMapping("/indexPage")
    public ResponseEntity<?> indexPage(@RequestParam String url) {
        if (!indexingService.isValidUrl(url)) {
            return ResponseEntity.badRequest().body(new ErrorResponse(false,
                    "Страница вне указанных сайтов конфигурации."));
        }
        boolean result = indexingService.indexPage(url);
        return ResponseEntity.ok(new SuccessResponse(result));
    }

    @GetMapping("/getPage")
    public ResponseEntity<?> getPage(@RequestParam String url,
                                     @RequestParam(required = false) String query) {

        Optional<Site> optionalSite = siteRepository.findAll().stream()
                .filter(s -> url.startsWith(s.getUrl()))
                .findFirst();

        if (optionalSite.isEmpty()) {
            return ResponseEntity.badRequest().body(new ErrorResponse(false, "Сайт не найден в базе данных"));
        }

        Site site = optionalSite.get();
        String relativePath = url.replace(site.getUrl(), "");
        if (relativePath.isBlank()) relativePath = "/";

        List<Page> pages = pageRepository.findByPathAndSite(relativePath, site);
        if (pages.isEmpty()) {
            return ResponseEntity.badRequest().body(new ErrorResponse(false, "Страница не найдена"));
        }

        Page page = pages.get(0);
        String highlightedContent = (query != null && !query.isBlank())
                ? highlightSnippet(page.getContent(), query)
                : page.getContent();

        return ResponseEntity.ok(Map.of(
                "title", extractTitle(page.getContent()),
                "content", highlightedContent
        ));
    }

    private String highlightSnippet(String content, String query) {
        if (query == null || query.isBlank()) {
            return content;
        }
        String text = Jsoup.parse(content).text();
        return text.replaceAll("(?i)(" + query + ")", "<mark>$1</mark>");
    }

    private String extractTitle(String htmlContent) {
        if (htmlContent == null || htmlContent.isBlank()) {
            return "Без заголовка";
        }
        String title = Jsoup.parse(htmlContent).title();
        return title.isBlank() ? "Без заголовка" : title;
    }
}
