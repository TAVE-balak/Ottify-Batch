package com.Ottify.OTTify_batch.program.batch.movie.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpenApiMovieDetailDto {

    private String title;
    private String poster_path;
    private String overview;
    private String tagline;
    private String original_title;
    private String backdrop_path;
    @JsonProperty("release_date")
    private String releaseDate;

    @JsonProperty("genres")
    public List<TmDbGenreInfo> tmDbGenreInfos;

    @JsonProperty("production_countries")
    private List<OAProductionCountry> productionCountries;

    @Builder
    OpenApiMovieDetailDto(String title){
        this.title = title;
    }


}





