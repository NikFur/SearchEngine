package searchengine.services;

import searchengine.model.Page;

public interface
LemmaIndexerService {
    void saveLemmas(Page page, String text);
}
