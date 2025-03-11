package searchengine.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "search_index")
@Data
public class SearchIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", referencedColumnName = "id", nullable = false)
    private Page page;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lemma_id", referencedColumnName = "id", nullable = false)
    private Lemma lemma;

    @Column(name = "rank_value", nullable = false)
    private Float rank;
}
