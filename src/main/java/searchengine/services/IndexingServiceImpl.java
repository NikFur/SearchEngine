package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class IndexingServiceImpl implements IndexingService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final LemmaService lemmaService;
    private final SitesList sitesList;

    private final AtomicBoolean indexingInProgress = new AtomicBoolean(false);
    private ForkJoinPool forkJoinPool = new ForkJoinPool();

    public IndexingServiceImpl(SiteRepository siteRepository,
                               PageRepository pageRepository,
                               LemmaRepository lemmaRepository,
                               IndexRepository indexRepository,
                               LemmaService lemmaService,
                               SitesList sitesList) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.lemmaService = lemmaService;
        this.sitesList = sitesList;
    }

    @Override
    public boolean isIndexingInProgress() {
        return indexingInProgress.get();
    }

    @Transactional
    @Override
    public synchronized void startIndexing() {
        if (indexingInProgress.get()) {
            log.warn("[ИНДЕКСАЦИЯ] Уже запущена! Выход.");
            return;
        }

        log.info("[ИНДЕКСАЦИЯ] Полный перезапуск индексации!");
        indexingInProgress.set(true);

        indexRepository.deleteAll();
        lemmaRepository.deleteAll();
        pageRepository.deleteAll();
        siteRepository.deleteAll();

        if (forkJoinPool.isShutdown() || forkJoinPool.isTerminated()) {
            forkJoinPool = new ForkJoinPool();
        }

        for (SiteConfig siteConfig : sitesList.getSites()) {
            Site site = new Site();
            site.setUrl(siteConfig.getUrl());
            site.setName(siteConfig.getName());
            site.setStatus(Status.INDEXING);
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);

            log.info("[ИНДЕКСАЦИЯ] Старт индексации для сайта: {}", site.getUrl());
            forkJoinPool.submit(new CrawlTask(site.getUrl(), site));
        }
    }

    private class CrawlTask extends RecursiveAction {
        private final String url;
        private final Site site;

        public CrawlTask(String url, Site site) {
            this.url = url;
            this.site = site;
        }

        @Override
        protected void compute() {
            if (!indexingInProgress.get()) {
                log.info("[ОБХОД] Индексация остановлена, выходим из задачи для: {}", url);
                return;
            }

            try {
                log.info("[ОБХОД] Анализируем страницу: {}", url);
                Thread.sleep(500);

                URL parsedUrl = new URL(url);
                String uri = parsedUrl.getPath().isBlank() ? "/" : parsedUrl.getPath();

                if (pageRepository.existsByPathAndSite(uri, site)) {
                    log.info("[ОБХОД] Страница уже существует в БД: {}", uri);
                    return;
                }

                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (compatible; MySearchBot/1.0)")
                        .timeout(10000)
                        .get();

                log.info("[ОБХОД] Успешно скачан HTML-код страницы: {}", url);

                Page page = new Page();
                page.setSite(site);
                page.setPath(uri);
                page.setUri(uri);
                page.setContent(doc.html());
                page.setCode(200);
                pageRepository.save(page);
                log.info("[ОБХОД] Страница сохранена в БД: {}", uri);

                saveLemmas(page, doc.text());

                List<CrawlTask> subTasks = new ArrayList<>();
                doc.select("a[href]").stream()
                        .map(link -> link.absUrl("href"))
                        .filter(href -> href.startsWith(site.getUrl())
                                && !href.equals(site.getUrl())
                                && !href.contains("#"))
                        .distinct()
                        .forEach(linkUrl -> {
                            log.info("[ОБХОД] Найдена ссылка: {}", linkUrl);
                            subTasks.add(new CrawlTask(linkUrl, site));
                        });

                invokeAll(subTasks);

            } catch (Exception e) {
                log.error("[ОШИБКА] Ошибка при обработке {}: {}", url, e.getMessage(), e);
                site.setStatus(Status.FAILED);
                site.setLastError(e.getMessage());
                siteRepository.save(site);
            }
        }
    }

    private void saveLemmas(Page page, String text) {
        Map<String, Integer> lemmas = lemmaService.extractLemmas(text);
        log.info("[ЛЕММЫ] Найдено {} лемм на странице {}", lemmas.size(), page.getPath());

        lemmas.forEach((lemmaText, count) -> {
            try {
                Lemma lemma = lemmaRepository.findByLemmaAndSite(lemmaText, page.getSite())
                        .orElseGet(() -> {
                            Lemma newLemma = new Lemma();
                            newLemma.setLemma(lemmaText);
                            newLemma.setSite(page.getSite());
                            newLemma.setFrequency(0);
                            return newLemma;
                        });

                lemma.setFrequency(lemma.getFrequency() + count);
                lemmaRepository.save(lemma);

                SearchIndex index = new SearchIndex();
                index.setPage(page);
                index.setLemma(lemma);
                index.setRank((float) count);
                indexRepository.save(index);

                log.info("[ЛЕММЫ] Лемма '{}' сохранена, встречается {} раз (страница: {})",
                        lemmaText, count, page.getPath());
            } catch (Exception e) {
                log.error("[ОШИБКА] Ошибка при сохранении леммы '{}'", lemmaText, e);
            }
        });
    }

    @Override
    public synchronized void stopIndexing() {
        if (!indexingInProgress.get()) {
            log.warn("[ИНДЕКСАЦИЯ] Уже остановлена!");
            return;
        }

        log.info("[ИНДЕКСАЦИЯ] Остановка индексации...");
        indexingInProgress.set(false);

        forkJoinPool.shutdown();
        try {
            if (!forkJoinPool.awaitTermination(5, TimeUnit.SECONDS)) {
                forkJoinPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            forkJoinPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("[ИНДЕКСАЦИЯ] Все задачи остановлены!");
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
            Optional<Site> optionalSite = siteRepository.findFirstByUrl(siteConfig.getUrl());
            if (optionalSite.isEmpty()) {
                return false;
            }

            Site site = optionalSite.get();
            URL parsedUrl = new URL(url);
            String uri = parsedUrl.getPath().isBlank() ? "/" : parsedUrl.getPath();

            List<Page> existingPages = pageRepository.findByPathAndSite(uri, site);
            if (!existingPages.isEmpty()) {
                pageRepository.deleteAll(existingPages);
            }

            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; MySearchBot/1.0)")
                    .timeout(10000)
                    .get();

            Page page = new Page();
            page.setSite(site);
            page.setPath(uri);
            page.setUri(uri);
            page.setContent(doc.html());
            page.setCode(200);
            pageRepository.save(page);

            saveLemmas(page, doc.text());

            return true;
        } catch (Exception e) {
            log.error("[ОШИБКА] Ошибка при индексации страницы {}", url, e);
            return false;
        }
    }

    @Override
    public boolean isValidUrl(String url) {
        return sitesList.getSites().stream()
                .anyMatch(site -> url.startsWith(site.getUrl()));
    }
}
