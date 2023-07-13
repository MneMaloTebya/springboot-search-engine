package searchengine.services;

import org.springframework.http.ResponseEntity;

public interface SearchService {
    ResponseEntity search(String query, String siteUrl, int offset, int limit);
}
