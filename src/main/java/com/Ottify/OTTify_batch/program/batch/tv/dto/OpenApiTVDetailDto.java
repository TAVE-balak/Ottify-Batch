package com.Ottify.OTTify_batch.program.batch.tv.dto;

import com.Ottify.OTTify_batch.program.batch.dto.OAProductionCountry;
import com.Ottify.OTTify_batch.program.batch.dto.TmDbGenreInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpenApiTVDetailDto {

    @JsonProperty("original_name")
    private String originalName;

    private String name;

    @JsonProperty("first_air_date")
    private String firstAirDate;

    private String poster_path;
    private String overview;
    private String tagline;
    private String backdrop_path;

    @JsonProperty("genres")
    public List<TmDbGenreInfo> tmDbGenreInfos;

    @JsonProperty("production_countries")
    private List<OAProductionCountry> productionCountries;


}
