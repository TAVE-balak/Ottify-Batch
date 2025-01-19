package com.Ottify.OTTify_batch.program.batch.dto;

import lombok.Getter;

@Getter
public class TmDbGenreInfo {
    private Long id;
    private String name;

    public void changeName(String name){
        this.name= name;

    }
}
