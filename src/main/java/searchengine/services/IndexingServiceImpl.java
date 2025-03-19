package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.model.*;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.tasks.CrawlTask;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final SitesList sitesList;
    private final TransactionalPersistenceService persistenceService;

    private final AtomicBoolean indexingInProgress = new AtomicBoolean(false);
    private ForkJoinPool forkJoinPool = new ForkJoinPool();

    @Override
    public boolean isIndexingInProgress() {
        return indexingInProgress.get();
    }

    @Transactional
    @Override
    public void startIndexing() {
        if (indexingInProgress.get()) {
            log.warn("[ИНДЕКСАЦИЯ] Уже запущена!");
            return;
        }

        indexingInProgress.set(true);

        pageRepository.deleteAll();
        siteRepository.deleteAll();

        forkJoinPool = new ForkJoinPool();

        for (SiteConfig config : sitesList.getSites()) {
            Site site = new Site();
            site.setUrl(config.getUrl());
            site.setName(config.getName());
            site.setStatus(Status.INDEXING);
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);

            CrawlTask task = new CrawlTask(
                    site.getUrl(),
                    site,
                    indexingInProgress,
                    persistenceService
            );
            forkJoinPool.submit(task);
        }
    }

    @Override
    public void stopIndexing() {
        if (!indexingInProgress.get()) {
            log.warn("[ИНДЕКСАЦИЯ] Уже остановлена!");
            return;
        }

        indexingInProgress.set(false);
        forkJoinPool.shutdownNow();

        try {
            if (!forkJoinPool.awaitTermination(10, TimeUnit.SECONDS)) {
                forkJoinPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            forkJoinPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("[ИНДЕКСАЦИЯ] Полностью остановлена.");
    }

    @Override
    public boolean indexPage(String url) {
        try {
            Optional<SiteConfig> optionalConfig = sitesList.getSites().stream()
                    .filter(configSite -> url.startsWith(configSite.getUrl()))
                    .findFirst();
            if (optionalConfig.isEmpty()) {
                return false;
            }

            SiteConfig siteConfig = optionalConfig.get();
            Site site = siteRepository.findFirstByUrl(siteConfig.getUrl())
                    .orElseGet(() -> {
                        Site newSite = new Site();
                        newSite.setUrl(siteConfig.getUrl());
                        newSite.setName(siteConfig.getName());
                        newSite.setStatus(Status.INDEXED);
                        newSite.setStatusTime(LocalDateTime.now());
                        return siteRepository.save(newSite);
                    });

            String uri = new URL(url).getPath().isBlank() ? "/" : new URL(url).getPath();

            Connection.Response response = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; MySearchBot/1.0)")
                    .timeout(10000)
                    .ignoreContentType(true)
                    .execute();

            String contentType = response.contentType();
            if (contentType == null ||
                    (!contentType.startsWith("text/") &&
                            !contentType.startsWith("application/xml") &&
                            !contentType.contains("+xml"))) {
                log.warn("[ПРЕДУПРЕЖДЕНИЕ] Неподдерживаемый тип контента: {} на {}", contentType, url);
                return false;
            }

            Document doc = response.parse();

            persistenceService.savePageWithLemmas(
                    site, uri, doc.html(), response.statusCode(), doc.text()
            );

            return true;
        } catch (Exception e) {
            log.error("[ОШИБКА] При индексации страницы {}: {}", url, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isValidUrl(String url) {
        return sitesList.getSites().stream()
                .anyMatch(site -> url.startsWith(site.getUrl()));
    }
}
