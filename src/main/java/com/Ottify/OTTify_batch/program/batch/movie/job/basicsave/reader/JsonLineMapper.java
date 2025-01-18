package com.Ottify.OTTify_batch.program.batch.movie.job.basicsave.reader;

import com.Ottify.OTTify_batch.program.batch.movie.job.basicsave.dto.MovieJsonReadDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.batch.item.file.LineMapper;

public class JsonLineMapper implements LineMapper<MovieJsonReadDto> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public MovieJsonReadDto mapLine(String line, int lineNumber) throws Exception {
        return objectMapper.readValue(line,MovieJsonReadDto.class);
    }
}

