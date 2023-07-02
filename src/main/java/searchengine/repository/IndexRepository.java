package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.IndexEntity;
import searchengine.model.PageEntity;

import java.util.List;

public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {
    void deleteAllByPage(PageEntity pageEntity);
    List<IndexEntity> findAllByPage(PageEntity pageEntity);
}
