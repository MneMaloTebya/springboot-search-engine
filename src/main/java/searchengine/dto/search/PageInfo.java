package searchengine.dto.search;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import searchengine.model.PageEntity;

@Data
public class PageInfo {
    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private float relevance;
    @JsonIgnore
    private PageEntity pageEntity;
}
