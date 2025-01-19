package com.Ottify.OTTify_batch.program.batch.movie.job.basicsave;


import com.Ottify.OTTify_batch.program.batch.movie.dto.OpenApiMovieDetailDto;
import com.Ottify.OTTify_batch.program.batch.movie.job.basicsave.dto.MovieJsonReadDto;
import com.Ottify.OTTify_batch.program.batch.movie.job.basicsave.reader.JsonLineMapper;
import com.Ottify.OTTify_batch.program.entity.Genre;
import com.Ottify.OTTify_batch.program.entity.Program;
import com.Ottify.OTTify_batch.program.entity.ProgramType;
import com.Ottify.OTTify_batch.program.repository.GenreRepository;
import com.Ottify.OTTify_batch.program.repository.ProgramRepository;
import jakarta.persistence.EntityManagerFactory;
import java.util.concurrent.Future;
import javax.sql.DataSource;
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
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.crossstore.ChangeSetPersister.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MovieBasicSaveJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ProgramRepository programRepository;
    private final EntityManagerFactory entityManagerFactory;
    private final WebClient webClient;
    private final GenreRepository genreRepository;

    @Bean
    public Job movieBasicSaveJob(Step movieBasicSaveStep) {
        return new JobBuilder("movieBasicSaveJob",jobRepository)
                .start(movieBasicSaveStep)
                .build();
    }

    @Bean
    public Step movieBasicSaveStep() {
        return new StepBuilder("movieBasicSaveStep",jobRepository)
                .<MovieJsonReadDto, Future<Program>>chunk(50,transactionManager)
                .reader(jsonMovieItemReader())
                .processor(asyncMovieApiProcessor())
                .writer(asyncJpaMovieBasicSaveWriter())
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
    public FlatFileItemReader<MovieJsonReadDto> jsonMovieItemReader() {

        return new FlatFileItemReaderBuilder<MovieJsonReadDto>()
                .name("movieIdReader")
                .resource(new ClassPathResource("json/movie_ids_01_17_2025.json"))
                .lineMapper(new JsonLineMapper())
                .strict(false) // strict 모드를 비활성화
                .build();
    }

    /* processor */
    @Bean
    public AsyncItemProcessor<MovieJsonReadDto, Program> asyncMovieApiProcessor(){
        final AsyncItemProcessor<MovieJsonReadDto,Program> processor = new AsyncItemProcessor<>();
        processor.setDelegate(movieApiProcessor());
        processor.setTaskExecutor(taskExecutor());

        return processor;
    }


    @Bean
    public ItemProcessor<MovieJsonReadDto,Program> movieApiProcessor(){
        return new ItemProcessor<MovieJsonReadDto, Program>() {
            @Override
            public Program process(MovieJsonReadDto item) throws Exception {
                long movieId= item.getId();

                log.info("API 호출 현재 영화: {}",movieId);

                OpenApiMovieDetailDto openApiMovieDetailDto = getApiProgram(movieId);

                String originalCountryName = (openApiMovieDetailDto.getProductionCountries() == null
                        || openApiMovieDetailDto.getProductionCountries().isEmpty())
                        ? null : openApiMovieDetailDto.getProductionCountries().get(0).getName();

                Program program = getProgram(item, openApiMovieDetailDto, originalCountryName);

                openApiMovieDetailDto.getTmDbGenreInfos().forEach(genreInfo -> {
                    Genre genre = genreRepository.findByTmDbGenreId(genreInfo.getId()).orElseThrow();
                    program.addGenre(genre);
                });




                return program;

            }

            private Program getProgram(MovieJsonReadDto item, OpenApiMovieDetailDto openApiMovieDetailDto,
                                       String originalCountryName) {
                Program program = Program.builder()
                        .tmDbProgramId(item.getId())
                        .title(openApiMovieDetailDto.getTitle())
                        .originalTitle(openApiMovieDetailDto.getOriginal_title())
                        .createdDate(openApiMovieDetailDto.getReleaseDate())
                        .createdYear(openApiMovieDetailDto.getReleaseDate().length() >= 4 ? openApiMovieDetailDto.getReleaseDate().substring(0, 4) : null)
                        .originalCountry(originalCountryName)
                        .backDropPath(openApiMovieDetailDto.getBackdrop_path())
                        .type(ProgramType.Movie)
                        .posterPath(openApiMovieDetailDto.getPoster_path())
                        .overView(openApiMovieDetailDto.getOverview())
                        .tagLine(openApiMovieDetailDto.getTagline())
                        .build();

                return program;
            }
        };
    }

    /* writer */

    @Bean
    public JpaItemWriter<Program> jpaMovieBasicSaveWriter(EntityManagerFactory entityManagerFactory) {
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
    public AsyncItemWriter<Program> asyncJpaMovieBasicSaveWriter(){
        final AsyncItemWriter<Program> writer = new AsyncItemWriter<>();
        writer.setDelegate(jpaMovieBasicSaveWriter(entityManagerFactory));

        return writer;
    }



    @Bean
    public TaskExecutor taskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
        executor.setConcurrencyLimit(50); // 동시 실행 스레드 제한
        return executor;
    }


    private OpenApiMovieDetailDto getApiProgram(long id) throws NotFoundException {

        try{
            OpenApiMovieDetailDto openApiProgramDto = webClient.get()
                    .uri("/movie/"+id+"?language=ko")
                    .retrieve()
                    .bodyToMono(OpenApiMovieDetailDto.class)
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

