package searchengine.services;

public interface IndexingService {
    boolean isIndexingInProgress();
    void startIndexing();
    void stopIndexing();
    boolean isValidUrl(String url);
    boolean indexPage(String url);
}
