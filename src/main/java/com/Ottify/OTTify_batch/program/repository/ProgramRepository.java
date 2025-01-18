package com.Ottify.OTTify_batch.program.repository;

import com.Ottify.OTTify_batch.program.entity.Program;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProgramRepository extends JpaRepository<Program,Long> {

    Optional<Program> findByTmDbProgramId(Long tmDbProgramId);

    void deleteByTmDbProgramId(Long tmDbProgramId);

    @Query("SELECT p FROM Program p LEFT JOIN FETCH p.programGenreList WHERE p.tmDbProgramId = :movieId")
    Optional<Program> findByTmDbProgramIdWithGenres(@Param("movieId") Long movieId);

}
