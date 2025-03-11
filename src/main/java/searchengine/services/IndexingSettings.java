package searchengine.services;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import searchengine.config.SiteConfig;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "indexing-settings")
public class IndexingSettings {
    private List<SiteConfig> sites;
}
