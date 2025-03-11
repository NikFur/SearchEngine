package searchengine.services;

import searchengine.config.SiteConfig;

public interface IndexingService {
    boolean isIndexingInProgress();
    void startIndexing();
    void stopIndexing();
    boolean isValidUrl(String url);
    boolean indexPage(String url);
    void indexSite(SiteConfig siteConfig);  // Заменил IndexingSettings.Site -> SiteConfig
}
