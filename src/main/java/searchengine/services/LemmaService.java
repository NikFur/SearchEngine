package searchengine.services;

import java.util.Map;

public interface LemmaService {
     Map<String, Integer> extractLemmas(String text);
     void testMorphology();
}
