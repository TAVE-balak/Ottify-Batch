package com.Ottify.OTTify_batch.program.batch.movie.job.basicsave.dto;

import lombok.Getter;

@Getter
public class MovieJsonReadDto {

    private boolean adult;
    private Long id;
    private String original_title;
    private double popularity;
    private boolean video;
}
