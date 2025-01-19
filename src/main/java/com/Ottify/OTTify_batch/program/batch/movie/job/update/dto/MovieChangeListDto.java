package com.Ottify.OTTify_batch.program.batch.movie.job.update.dto;

import java.util.List;
import lombok.Getter;

@Getter
public class MovieChangeListDto {
    private List<ChangeMovieResultDto> results;
    private int page;
    private int total_pages;
    private int total_results;
}
