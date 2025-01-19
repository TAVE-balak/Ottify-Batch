package com.Ottify.OTTify_batch.program.batch.tv.job.update.dto;

import com.Ottify.OTTify_batch.program.batch.movie.job.update.dto.ChangeMovieResultDto;
import java.util.List;
import lombok.Getter;

@Getter
public class TVChangeListDto {
    private List<ChangeTVResultDto> results;
    private int page;
    private int total_pages;
    private int total_results;
}
