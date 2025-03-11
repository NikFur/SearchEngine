package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.context.ApplicationContext;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class IndexingServiceImpl implements IndexingService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final LemmaService lemmaService;
    private final SitesList sitesList;
    private final ApplicationContext applicationContext;

    private volatile boolean indexingInProgress = false;
    private volatile boolean indexingStopped = false; // Флаг остановки индексации
    private ExecutorService executor = Executors.newCachedThreadPool();

    public IndexingServiceImpl(SiteRepository siteRepository,
                               PageRepository pageRepository,
                               LemmaRepository lemmaRepository,
                               IndexRepository indexRepository,
                               LemmaService lemmaService,
                               SitesList sitesList,
                               ApplicationContext applicationContext) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.lemmaService = lemmaService;
        this.sitesList = sitesList;
        this.applicationContext = applicationContext;
    }

    @Override
    public synchronized boolean isIndexingInProgress() {
        return indexingInProgress;
    }

    @Transactional
    @Override
    public synchronized void startIndexing() {
        if (indexingInProgress) {
            System.out.println("[ИНДЕКСАЦИЯ] Уже запущена! Выход.");
            return;
        }

        System.out.println("[ИНДЕКСАЦИЯ] Старт индексации!");
        indexingInProgress = true;
        indexingStopped = false;

        if (executor.isShutdown() || executor.isTerminated()) {
            executor = Executors.newCachedThreadPool();
        }

        // Используем сайты, уже сохранённые в базе.
        List<SiteConfig> configuredSites = sitesList.getSites();

        for (SiteConfig siteConfig : configuredSites) {
            System.out.println("[ИНДЕКСАЦИЯ] Обработка сайта: " + siteConfig.getUrl());

            // Получаем список сайтов с данным URL (избегая дубликатов)
            List<Site> sites = siteRepository.findByUrl(siteConfig.getUrl());
            Site site;
            if (!sites.isEmpty()) {
                site = sites.get(0);
                System.out.println("[ИНДЕКСАЦИЯ] Сайт найден, обновляем статус: " + siteConfig.getUrl());
            } else {
                site = new Site();
                site.setUrl(siteConfig.getUrl());
                site.setName(siteConfig.getName());
                site.setStatus(Status.INDEXING);
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
                System.out.println("[ИНДЕКСАЦИЯ] Сайт не найден, создаем новый: " + siteConfig.getUrl());
            }

            // Обновляем статус и сбрасываем ошибку
            site.setStatus(Status.INDEXING);
            site.setStatusTime(LocalDateTime.now());
            site.setLastError(null);
            siteRepository.save(site);

            executor.submit(() -> {
                System.out.println("[ИНДЕКСАЦИЯ] Начинаем индексацию для сайта: " + site.getUrl());
                indexSite(siteConfig);
            });
        }
    }

    @Transactional
    @Override
    public void indexSite(SiteConfig siteConfig) {
        System.out.println("[ИНДЕКСАЦИЯ] Запуск индексации для сайта: " + siteConfig.getUrl());

        // Получаем сайт по URL (используем findByUrl, который возвращает список, и берем первую запись)
        List<Site> sites = siteRepository.findByUrl(siteConfig.getUrl());
        Site site;
        if (!sites.isEmpty()) {
            site = sites.get(0);
            System.out.println("[ИНДЕКСАЦИЯ] Сайт найден, продолжаем индексацию: " + siteConfig.getUrl());
        } else {
            // Эта ситуация маловероятна, так как сайт должен уже быть сохранён при запуске индексации
            site = new Site();
            site.setUrl(siteConfig.getUrl());
            site.setName(siteConfig.getName());
            System.out.println("[ИНДЕКСАЦИЯ] Сайт не найден, создаем новый: " + siteConfig.getUrl());
        }

        // Обновляем статус, время и сбрасываем ошибки
        site.setStatus(Status.INDEXING);
        site.setStatusTime(LocalDateTime.now());
        site.setLastError(null);
        siteRepository.save(site);

        try {
            System.out.println("[ИНДЕКСАЦИЯ] Начинаем обход страниц для сайта: " + site.getUrl());
            crawlPages(site.getUrl(), site);
            site.setStatus(Status.INDEXED);
            System.out.println("[ИНДЕКСАЦИЯ] Индексация завершена для сайта: " + site.getUrl());
        } catch (Exception e) {
            site.setStatus(Status.FAILED);
            site.setLastError(e.getMessage());
            System.out.println("[ИНДЕКСАЦИЯ] Ошибка при индексации сайта: " + site.getUrl());
            e.printStackTrace();
        } finally {
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
            System.out.println("[ИНДЕКСАЦИЯ] Завершено обновление статуса сайта: " + site.getUrl());
        }
    }

    @Transactional
    public void crawlPages(String url, Site site) {
        if (!indexingInProgress || indexingStopped) {
            System.out.println("[ОБХОД] Индексация остановлена. Выход.");
            return;
        }

        try {
            System.out.println("[ОБХОД] Анализируем страницу: " + url);
            Thread.sleep(500);

            if (!indexingInProgress || indexingStopped) {
                System.out.println("[ОБХОД] Индексация остановлена после задержки. Выход.");
                return;
            }

            URL parsedUrl = new URL(url);
            String uri = parsedUrl.getPath();
            if (uri == null || uri.isBlank()) {
                uri = "/";
            }

            if (pageRepository.existsByPathAndSite(uri, site)) {
                System.out.println("[ОБХОД] Страница уже существует в БД: " + uri);
                return;
            }

            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; MySearchBot/1.0)")
                    .timeout(10000)
                    .get();

            System.out.println("[ОБХОД] Успешно скачан HTML-код страницы: " + url);

            // Сохраняем страницу
            Page page = new Page();
            page.setSite(site);
            page.setPath(uri);
            page.setUri(uri);
            page.setContent(doc.html());
            page.setCode(200);
            pageRepository.save(page);
            System.out.println("[ОБХОД] Страница сохранена в БД: " + uri);

            // Извлекаем и сохраняем леммы
            Map<String, Integer> lemmas = lemmaService.extractLemmas(doc.text());
            System.out.println("[ОБХОД] Найдено " + lemmas.size() + " лемм на странице.");

            lemmas.forEach((lemmaText, count) -> {
                try {
                    Lemma lemma = lemmaRepository.findByLemmaAndSite(lemmaText, site)
                            .orElseGet(() -> {
                                Lemma newLemma = new Lemma();
                                newLemma.setLemma(lemmaText);
                                newLemma.setSite(site);
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

                    System.out.println("[ОБХОД] Лемма сохранена: " + lemmaText + " (встречается " + count + " раз)");
                } catch (Exception e) {
                    System.out.println("[ОШИБКА] Ошибка при сохранении леммы: " + lemmaText);
                    e.printStackTrace();
                }
            });

            // Рекурсивный обход ссылок
            doc.select("a[href]").stream()
                    .map(link -> link.absUrl("href"))
                    .filter(href -> href.startsWith(site.getUrl()))
                    .distinct()
                    .forEach(linkUrl -> {
                        if (!indexingStopped) {
                            System.out.println("[ОБХОД] Найдена ссылка: " + linkUrl);
                            crawlPages(linkUrl, site);
                        }
                    });

        } catch (Exception e) {
            System.out.println("[ОШИБКА] Ошибка при обработке страницы: " + url);
            e.printStackTrace();
            site.setStatus(Status.FAILED);
            site.setLastError(e.getMessage());
            siteRepository.save(site);
        }
    }

    @Transactional
    @Override
    public synchronized void stopIndexing() {
        if (!indexingInProgress) {
            System.out.println("[ИНДЕКСАЦИЯ] Уже остановлена!");
            return;
        }

        System.out.println("[ИНДЕКСАЦИЯ] Остановка индексации...");
        indexingInProgress = false;
        indexingStopped = true;

        executor.shutdownNow();
        System.out.println("[ИНДЕКСАЦИЯ] Все потоки остановлены!");
    }

    @Transactional
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

            List<Site> sites = siteRepository.findByUrl(siteConfig.getUrl());
            if (sites.isEmpty()) {
                return false;
            }

            Site site = sites.get(0);

            // Правильно извлекаем URI из URL
            URL parsedUrl = new URL(url);
            String uri = parsedUrl.getPath();
            if (uri.isBlank()) uri = "/";

            // Удаление существующих страниц с тем же URI (при индексировании отдельной страницы)
            List<Page> existingPages = pageRepository.findByPathAndSite(uri, site);
            if (!existingPages.isEmpty()) {
                pageRepository.deleteAll(existingPages);
            }

            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; MySearchBot/1.0)")
                    .timeout(10000)
                    .get();

            // Создание новой страницы
            Page page = new Page();
            page.setSite(site);
            page.setPath(uri);
            page.setUri(uri);
            page.setContent(doc.html());
            page.setCode(200);
            pageRepository.save(page);

            Map<String, Integer> lemmas = lemmaService.extractLemmas(doc.text());
            Map<String, Lemma> lemmaCache = new HashMap<>();

            lemmas.forEach((lemmaText, count) -> {
                Lemma lemma = lemmaCache.computeIfAbsent(lemmaText, key ->
                        lemmaRepository.findByLemmaAndSite(key, site)
                                .orElseGet(() -> {
                                    Lemma newLemma = new Lemma();
                                    newLemma.setLemma(key);
                                    newLemma.setSite(site);
                                    newLemma.setFrequency(0);
                                    return newLemma;
                                })
                );
                lemma.setFrequency(lemma.getFrequency() + count);
                lemmaRepository.save(lemma);

                SearchIndex index = new SearchIndex();
                index.setPage(page);
                index.setLemma(lemma);
                index.setRank((float) count);
                indexRepository.save(index);
            });

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean isValidUrl(String url) {
        return sitesList.getSites().stream()
                .anyMatch(site -> url.startsWith(site.getUrl()));
    }
}
