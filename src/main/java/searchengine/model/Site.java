package searchengine.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "sites")
@Data
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Integer id;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "status_time", nullable = false)
    private LocalDateTime statusTime;

    @Column(name = "last_error", columnDefinition = "MEDIUMTEXT")
    private String lastError;

    public long getStatusTimeInMillis() {
        return statusTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
