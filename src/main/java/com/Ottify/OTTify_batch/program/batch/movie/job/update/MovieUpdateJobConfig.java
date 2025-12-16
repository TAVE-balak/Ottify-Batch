package com.Ottify.OTTify_batch.program.batch.movie.job.update;

import com.Ottify.OTTify_batch.program.batch.listener.JobListener;
import com.Ottify.OTTify_batch.program.batch.movie.dto.OpenApiMovieDetailDto;
import com.Ottify.OTTify_batch.program.batch.movie.job.update.dto.ChangeMovieResultDto;
import com.Ottify.OTTify_batch.program.batch.movie.job.update.dto.MovieChangeListDto;
import com.Ottify.OTTify_batch.program.entity.Genre;
import com.Ottify.OTTify_batch.program.entity.Program;
import com.Ottify.OTTify_batch.program.entity.ProgramType;
import com.Ottify.OTTify_batch.program.repository.GenreRepository;
import com.Ottify.OTTify_batch.program.repository.ProgramRepository;
import jakarta.persistence.EntityManagerFactory;
import java.util.Iterator;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.integration.async.AsyncItemProcessor;
import org.springframework.batch.integration.async.AsyncItemWriter;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
public class MovieUpdateJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final WebClient webClient;
    private final ProgramRepository programRepository;
    private final GenreRepository genreRepository;
    private final EntityManagerFactory entityManagerFactory;
    private final TaskExecutor apiExecutor;
    private final JobListener jobListener;





    @Bean
    public Job movieUpdateJob() {
        return new JobBuilder("movieUpdateJob",jobRepository)
                .listener(jobListener)
                .start(movieUpdateStep())
                .build();
    }

    @Bean
    public Step movieUpdateStep() {
        return new StepBuilder("updateMovieStep",jobRepository)
                .<Long, Future<Program>>chunk(50,transactionManager)
                .reader(changedMovieIdReader(null))
                .processor(asyncUpdateItemProcessor())
                .writer(asyncJpaUpdateWriter())
                .faultTolerant()
                .retry(Exception.class)
                .retryLimit(3)
                .backOffPolicy(new FixedBackOffPolicy() {{
                    setBackOffPeriod(2000); // 2초 대기
                }})
                .noRetry(NotFoundException.class)
                .allowStartIfComplete(false)
                .build();
    }


    // 어플리케이션 구동 시점에 빈이 주입 되도록 @StepScope 사용 !
    // 당연하겠지만 @Value 와 함께 쓰려면 @StepScope 가 필수 일듯!
    @Bean
    @StepScope
    public ItemReader<Long> changedMovieIdReader(@Value("#{jobParameters['date']}") String date) {
        return new ItemReader<Long>() {
            private int currentPage = 1;
            private Iterator<ChangeMovieResultDto> movieResultIterator;
            private int totalPages;

            @Override
            public Long read() throws Exception {
                if (movieResultIterator == null || !movieResultIterator.hasNext()) {
                    if (totalPages > 0 && currentPage > totalPages) {
                        return null;
                    }

                    MovieChangeListDto response = getChangedMovies(currentPage, date);

                    if (totalPages == 0) {
                        totalPages = response.getTotal_pages();
                    }

                    movieResultIterator = response.getResults().stream()
                            .filter(movie -> !movie.isAdult())
                            .iterator();

                    currentPage++;

                    if (!movieResultIterator.hasNext()) {
                        return null;
                    }
                }

                return movieResultIterator.hasNext() ? movieResultIterator.next().getId() : null;
            }
        };
    }

    public MovieChangeListDto getChangedMovies(int page,String date) {
        return webClient.get()
                .uri("/movie/changes?page=" + page +"&start_date="+date+"&end_date="+date)
                .retrieve()
                .bodyToMono(MovieChangeListDto.class)
                .block();
    }


    @Bean
    public AsyncItemProcessor<Long, Program> asyncUpdateItemProcessor(){
        final AsyncItemProcessor<Long,Program> processor = new AsyncItemProcessor<>();
        processor.setDelegate(movieUpdateProcessor());
        processor.setTaskExecutor(apiExecutor);

        return processor;
    }
    @Bean
    public ItemProcessor<Long, Program> movieUpdateProcessor() {
        return movieId -> {
            // API를 통해 영화 상세 정보 조회
            log.info("API 호출 현재 영화: {}",movieId);

            try{
                OpenApiMovieDetailDto openApiMovieDetailDto = getApiProgram(movieId);


                String originalCountryName = (openApiMovieDetailDto.getProductionCountries() == null
                        || openApiMovieDetailDto.getProductionCountries().isEmpty())
                        ? null : openApiMovieDetailDto.getProductionCountries().get(0).getName();

                return programRepository.findByTmDbProgramIdAndTypeWithGenre(movieId,ProgramType.Movie).map(program->{
                    program.update(openApiMovieDetailDto.getTitle(), openApiMovieDetailDto.getPoster_path(),
                            openApiMovieDetailDto.getReleaseDate().length() >= 4 ? openApiMovieDetailDto.getReleaseDate().substring(0, 4) : null,
                            openApiMovieDetailDto.getReleaseDate(),originalCountryName,openApiMovieDetailDto.getOriginal_title(),
                            openApiMovieDetailDto.getOverview(),openApiMovieDetailDto.getTagline(),openApiMovieDetailDto.getBackdrop_path());


                    program.getProgramGenreList().clear();
                    openApiMovieDetailDto.getTmDbGenreInfos().forEach(genreInfo -> {
                        Genre genre = genreRepository.findByTmDbGenreId(genreInfo.getId()).orElseThrow();
                        program.addGenre(genre);
                    });

                    return program;

                }).orElse(makeProgram(movieId,openApiMovieDetailDto));
            }catch (WebClientResponseException e){
                if(e.getMessage().contains("404 Not Found")){
                    log.info("삭제된 영화 아이디 {}",movieId);
                    return programRepository.findByTmDbProgramIdAndType(movieId,ProgramType.Movie)
                            .map(program -> {
                                program.makeWillDelete();
                                return program;
                            })
                            .orElse(null);
                }
            }

            return null;
        };
    }

    private OpenApiMovieDetailDto getApiProgram(long movieId) throws NotFoundException {

        OpenApiMovieDetailDto openApiMovieDetailDto = webClient.get()
                .uri("/movie/"+movieId+"?language=ko")
                .retrieve()
                .bodyToMono(OpenApiMovieDetailDto.class)
                .block();

        return openApiMovieDetailDto;

    }


    @Bean
    public AsyncItemWriter<Program> asyncJpaUpdateWriter(){
        final AsyncItemWriter<Program> writer = new AsyncItemWriter<>();
        writer.setDelegate(jpaMovieUpdateItemWriter(entityManagerFactory));

        return writer;
    }
    @Bean
    public JpaItemWriter<Program> jpaMovieUpdateItemWriter(EntityManagerFactory entityManagerFactory) {
        JpaItemWriter<Program> writer = new JpaItemWriter<>();
        writer.setEntityManagerFactory(entityManagerFactory);
        return writer;
    }

    private Program makeProgram(Long movieId, OpenApiMovieDetailDto openApiMovieDetailDto){

        String originalCountryName = (openApiMovieDetailDto.getProductionCountries() == null
                || openApiMovieDetailDto.getProductionCountries().isEmpty())
                ? null : openApiMovieDetailDto.getProductionCountries().get(0).getName();

        Program program = Program.builder()
                .tmDbProgramId(movieId)
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

        openApiMovieDetailDto.getTmDbGenreInfos().forEach(genreInfo -> {
            Genre genre = genreRepository.findByTmDbGenreId(genreInfo.getId()).orElseThrow();
            program.addGenre(genre);
        });

        return program;
    }


}
