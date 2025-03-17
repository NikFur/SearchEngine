package searchengine.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "lemma")
@Data
public class Lemma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", referencedColumnName = "id", nullable = false)
    private Site site;

    @Column(nullable = false)
    private String lemma;

    @Column(nullable = false)
    private Integer frequency;

    @OneToMany(mappedBy = "lemma", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SearchIndex> searchIndexes = new ArrayList<>();
}

