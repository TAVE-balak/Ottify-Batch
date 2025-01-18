package com.Ottify.OTTify_batch.program.repository;

import com.Ottify.OTTify_batch.program.entity.Genre;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GenreRepository extends JpaRepository<Genre,Long> {

    boolean existsByTmDbGenreId(Long tmDbGenreId);

    Optional<Genre> findByTmDbGenreId(Long tmDbGenreId);
}

