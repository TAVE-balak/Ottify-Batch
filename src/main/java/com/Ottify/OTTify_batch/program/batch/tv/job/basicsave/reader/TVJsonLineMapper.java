package com.Ottify.OTTify_batch.program.batch.tv.job.basicsave.reader;

import com.Ottify.OTTify_batch.program.batch.movie.job.basicsave.dto.MovieJsonReadDto;
import com.Ottify.OTTify_batch.program.batch.tv.job.basicsave.dto.TVJsonReadDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.batch.item.file.LineMapper;

public class TVJsonLineMapper implements LineMapper<TVJsonReadDto> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public TVJsonReadDto mapLine(String line, int lineNumber) throws Exception {
        return objectMapper.readValue(line,TVJsonReadDto.class);
    }
}
