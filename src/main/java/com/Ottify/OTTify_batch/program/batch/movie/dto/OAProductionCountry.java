package com.Ottify.OTTify_batch.program.batch.movie.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class OAProductionCountry {
    @JsonProperty("iso_3166_1")
    private String iso31661;
    private String name;
}
