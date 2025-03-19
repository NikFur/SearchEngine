package searchengine.services.tasks;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.services.TransactionalPersistenceService;

import java.net.URL;
import java.util.List;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RequiredArgsConstructor
public class CrawlTask extends RecursiveAction {

    private final String url;
    private final Site site;
    private final AtomicBoolean indexingInProgress;
    private final TransactionalPersistenceService persistenceService;

    @Override
    protected void compute() {
        if (!indexingInProgress.get()) {
            log.info("[ОБХОД] Индексация остановлена: {}", url);
            return;
        }

        try {
            log.info("[ОБХОД] Анализ страницы: {}", url);
            Thread.sleep(500);

            URL parsedUrl = new URL(url);
            String uri = parsedUrl.getPath().isBlank() ? "/" : parsedUrl.getPath();

            if (persistenceService.existsByPathAndSite(uri, site)) {
                log.info("[ОБХОД] Страница уже в БД: {}", uri);
                return;
            }

            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; MySearchBot/1.0)")
                    .timeout(10000)
                    .get();

            persistenceService.savePageWithLemmas(
                    site, uri, doc.html(), 200, doc.text()
            );

            List<CrawlTask> subTasks = doc.select("a[href]").stream()
                    .map(link -> link.absUrl("href"))
                    .filter(href -> href.startsWith(site.getUrl()) &&
                            !href.equals(site.getUrl()) &&
                            !href.contains("#"))
                    .distinct()
                    .map(linkUrl -> new CrawlTask(linkUrl, site, indexingInProgress, persistenceService))
                    .toList();

            invokeAll(subTasks);

        } catch (Exception e) {
            log.error("[ОШИБКА] Ошибка при обработке {}: {}", url, e.getMessage(), e);
            persistenceService.updateSiteStatus(site, Status.FAILED, e.getMessage());
        }
    }
}

