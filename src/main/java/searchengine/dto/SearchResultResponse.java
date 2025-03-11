package searchengine.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class SearchResultResponse {
    private boolean result;
    private int count;
    private List<SearchResult> data;
}
