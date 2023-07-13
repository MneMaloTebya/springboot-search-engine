package searchengine.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.List;

@Repository
public interface PageRepository extends JpaRepository<PageEntity, Integer> {

    boolean existsByPathAndSite(String url, SiteEntity siteEntity);

    @Transactional
    void deleteAllBySite(SiteEntity siteEntity);
    PageEntity findByPathAndSite(String path, SiteEntity siteEntity);
    int countBySite(SiteEntity siteEntity);

    @Query(value = "SELECT p FROM PageEntity p\n" +
            "JOIN IndexEntity i ON p = i.page \n" +
            "JOIN LemmaEntity l ON l = i.lemma \n" +
            "JOIN SiteEntity s ON s = p.site \n" +
            "WHERE l.lemma = :lemma AND s = :siteData")
    List<PageEntity> findAllByLemmaAndSite(String lemma, SiteEntity siteEntity, Pageable pageable);

    @Query(value = "SELECT p FROM PageEntity p\n" +
            "JOIN IndexEntity i ON p.id = i.page.id\n" +
            "JOIN LemmaEntity l ON l.id = i.lemma.id \n" +
            "WHERE l.lemma = :lemma")
    List<PageEntity> findAllByLemma(String lemma, Pageable pageable);
}
