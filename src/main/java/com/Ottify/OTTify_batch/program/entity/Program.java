package com.Ottify.OTTify_batch.program.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Program {

    @Id
    @Column(name = "program_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String posterPath;
    private double averageRating;
    private int reviewCount;
    private Long tmDbProgramId;

    @Enumerated(EnumType.STRING)
    private ProgramType type;

    private String createdYear;

    private String createdDate;
    private String originalCountry;
    private String originalTitle;
    @Column(length = 3000)
    private String overView;
    private String tagLine;
    private String backDropPath;
    private boolean willDeleted;

    @Builder
    public Program(String title, String posterPath, Long tmDbProgramId,
                   ProgramType type, String createdYear, String createdDate, String originalCountry,
                   String originalTitle,
                   String overView, String tagLine, String backDropPath) {

        this.title = title;
        this.posterPath = posterPath;
        this.averageRating = 0;
        this.reviewCount = 0;
        this.tmDbProgramId = tmDbProgramId;
        this.type = type;
        this.createdYear = createdYear;
        this.createdDate = createdDate;
        this.originalCountry = originalCountry;
        this.originalTitle = originalTitle;
        this.overView = overView;
        this.tagLine = tagLine;
        this.backDropPath = backDropPath;
        this.willDeleted= false;
    }

    public void addReviewCount(){
        this.reviewCount++;
    }

    @OneToMany(mappedBy = "program", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProgramGenre> programGenreList = new ArrayList<>();


    public void addGenre(Genre genre) {
        ProgramGenre programGenre = ProgramGenre.builder().genre(genre).program(this).build();
        programGenreList.add(programGenre);
    }

    public void update(String title, String posterPath,
                       String createdYear, String createdDate, String originalCountry,
                       String originalTitle,
                       String overView, String tagLine, String backDropPath){
        this.title = title;
        this.posterPath = posterPath;
        this.createdYear = createdYear;
        this.createdDate = createdDate;
        this.originalCountry = originalCountry;
        this.originalTitle = originalTitle;
        this.overView = overView;
        this.tagLine = tagLine;
        this.backDropPath = backDropPath;
    }

    public void makeWillDelete(){
        this.willDeleted = true;
    }
}

