package com.Ottify.OTTify_batch.program.batch.tv.job.basicsave;

import com.Ottify.OTTify_batch.program.batch.movie.dto.OpenApiMovieDetailDto;
import com.Ottify.OTTify_batch.program.batch.movie.job.basicsave.dto.MovieJsonReadDto;
import com.Ottify.OTTify_batch.program.batch.movie.job.basicsave.reader.JsonLineMapper;
import com.Ottify.OTTify_batch.program.batch.tv.dto.OpenApiTVDetailDto;
import com.Ottify.OTTify_batch.program.batch.tv.job.basicsave.dto.TVJsonReadDto;
import com.Ottify.OTTify_batch.program.batch.tv.job.basicsave.reader.TVJsonLineMapper;
import com.Ottify.OTTify_batch.program.entity.Genre;
import com.Ottify.OTTify_batch.program.entity.Program;
import com.Ottify.OTTify_batch.program.entity.ProgramType;
import com.Ottify.OTTify_batch.program.repository.GenreRepository;
import com.Ottify.OTTify_batch.program.repository.ProgramRepository;
import jakarta.persistence.EntityManagerFactory;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.integration.async.AsyncItemProcessor;
import org.springframework.batch.integration.async.AsyncItemWriter;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.crossstore.ChangeSetPersister.NotFoundException;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class TvBasicSaveJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ProgramRepository programRepository;
    private final EntityManagerFactory entityManagerFactory;
    private final WebClient webClient;
    private final GenreRepository genreRepository;
    private final TaskExecutor apiExecutor;

    @Bean
    public Job tvBasicSaveJob(Step tvBasicSaveStep) {
        return new JobBuilder("tvBasicSaveJob",jobRepository)
                .start(tvBasicSaveStep)
                .build();
    }

    @Bean
    public Step tvBasicSaveStep() {
        return new StepBuilder("tvBasicSaveStep",jobRepository)
                .<TVJsonReadDto, Future<Program>>chunk(50,transactionManager)
                .reader(jsonTVItemReader())
                .processor(asyncTVApiProcessor())
                .writer(asyncJpaTVBasicSaveWriter())
                .faultTolerant()
                .skip(NotFoundException.class)
                .skipLimit(Integer.MAX_VALUE)
                .retry(Exception.class)
                .retryLimit(3)
                .backOffPolicy(new FixedBackOffPolicy() {{
                    setBackOffPeriod(2000); // 2초 대기
                }})
                .noRetry(NotFoundException.class)
                .allowStartIfComplete(true)
                .build();
    }


    /* reader */
    @Bean
    public FlatFileItemReader<TVJsonReadDto> jsonTVItemReader() {

        return new FlatFileItemReaderBuilder<TVJsonReadDto>()
                .name("tvIdReader")
                .resource(new ClassPathResource("json/tv_series_ids_01_18_2025.json"))
                .lineMapper(new TVJsonLineMapper())
                .strict(false) // strict 모드를 비활성화
                .build();
    }

    /* processor */
    @Bean
    public AsyncItemProcessor<TVJsonReadDto, Program> asyncTVApiProcessor(){
        final AsyncItemProcessor<TVJsonReadDto,Program> processor = new AsyncItemProcessor<>();
        processor.setDelegate(tvApiProcessor());
        processor.setTaskExecutor(apiExecutor);

        return processor;
    }


    @Bean
    public ItemProcessor<TVJsonReadDto,Program> tvApiProcessor(){
        return new ItemProcessor<TVJsonReadDto, Program>() {
            @Override
            public Program process(TVJsonReadDto item) throws Exception {
                long tvId= item.getId();

                log.info("API 호출 현재 TV: {}",tvId);

                OpenApiTVDetailDto openApiTVDetailDto = getApiProgram(tvId);

                String originalCountryName = (openApiTVDetailDto.getProductionCountries() == null
                        || openApiTVDetailDto.getProductionCountries().isEmpty())
                        ? null : openApiTVDetailDto.getProductionCountries().get(0).getName();

                Program program = getProgram(item, openApiTVDetailDto, originalCountryName);

                openApiTVDetailDto.getTmDbGenreInfos().forEach(genreInfo -> {
                    Genre genre = genreRepository.findByTmDbGenreId(genreInfo.getId()).orElseThrow();
                    program.addGenre(genre);
                });




                return program;

            }

            private Program getProgram(TVJsonReadDto item, OpenApiTVDetailDto openApiTVDetailDto,
                                       String originalCountryName) {

                String year = openApiTVDetailDto.getFirstAirDate()==null?null:
                        (openApiTVDetailDto.getFirstAirDate().length() >= 4 ? openApiTVDetailDto.getFirstAirDate().substring(0, 4) : null);

                Program program = Program.builder()
                        .tmDbProgramId(item.getId())
                        .title(openApiTVDetailDto.getName())
                        .originalTitle(openApiTVDetailDto.getOriginalName())
                        .createdDate(openApiTVDetailDto.getFirstAirDate())
                        .createdYear(year)
                        .originalCountry(originalCountryName)
                        .backDropPath(openApiTVDetailDto.getBackdrop_path())
                        .type(ProgramType.TV)
                        .posterPath(openApiTVDetailDto.getPoster_path())
                        .overView(openApiTVDetailDto.getOverview())
                        .tagLine(openApiTVDetailDto.getTagline())
                        .build();

                return program;
            }
        };
    }

    /* writer */

    @Bean
    public JpaItemWriter<Program> jpaTVBasicSaveWriter(EntityManagerFactory entityManagerFactory) {
        JpaItemWriter<Program> writer = new JpaItemWriter<>();
        writer.setEntityManagerFactory(entityManagerFactory);
        return writer;
    }


//    @Bean
//    public JdbcBatchItemWriter<Program> movieItemWriter(DataSource dataSource) {
//        JdbcBatchItemWriter<Program> writer = new JdbcBatchItemWriter<>();
//        writer.setDataSource(dataSource);
//        writer.setSql("INSERT INTO program (title, tm_db_program_id,average_rating,review_count) VALUES (:title, :tmDbProgramId,0,0)");
//        writer.setItemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>());
//
//        return writer;
//    }


//    @Bean
//    public AsyncItemWriter<Program> asyncItemWriter(){
//        final AsyncItemWriter<Program> writer = new AsyncItemWriter<>();
//        writer.setDelegate(movieItemWriter(dataSource));
//
//        return writer;
//    }

    @Bean
    public AsyncItemWriter<Program> asyncJpaTVBasicSaveWriter(){
        final AsyncItemWriter<Program> writer = new AsyncItemWriter<>();
        writer.setDelegate(jpaTVBasicSaveWriter(entityManagerFactory));

        return writer;
    }




    private OpenApiTVDetailDto getApiProgram(long id) throws NotFoundException {

        try{
            OpenApiTVDetailDto openApiProgramDto = webClient.get()
                    .uri("/tv/"+id+"?language=ko")
                    .retrieve()
                    .bodyToMono(OpenApiTVDetailDto.class)
                    .block();

            return openApiProgramDto;
        }catch (WebClientResponseException e){
            if(e.getMessage().contains("404 Not Found")){
                throw new NotFoundException();
            }
        }

        return null;

    }
}
