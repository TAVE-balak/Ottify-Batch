package com.Ottify.OTTify_batch.program.batch.tv.job.update;

import com.Ottify.OTTify_batch.program.batch.listener.JobListener;
import com.Ottify.OTTify_batch.program.batch.movie.dto.OpenApiMovieDetailDto;
import com.Ottify.OTTify_batch.program.batch.movie.job.update.dto.ChangeMovieResultDto;
import com.Ottify.OTTify_batch.program.batch.movie.job.update.dto.MovieChangeListDto;
import com.Ottify.OTTify_batch.program.batch.tv.dto.OpenApiTVDetailDto;
import com.Ottify.OTTify_batch.program.batch.tv.job.update.dto.ChangeTVResultDto;
import com.Ottify.OTTify_batch.program.batch.tv.job.update.dto.TVChangeListDto;
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
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.crossstore.ChangeSetPersister.NotFoundException;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class TVUpdateJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final WebClient webClient;
    private final ProgramRepository programRepository;
    private final GenreRepository genreRepository;
    private final EntityManagerFactory entityManagerFactory;
    private final TaskExecutor apiExecutor;
    private final JobListener jobListener;





    @Bean
    public Job tvUpdateJob() {
        return new JobBuilder("yvUpdateJob",jobRepository)
                .listener(jobListener)
                .start(tvUpdateStep())
                .build();
    }

    @Bean
    public Step tvUpdateStep() {
        return new StepBuilder("updateTVStep",jobRepository)
                .<Long, Future<Program>>chunk(50,transactionManager)
                .reader(changedTVIdReader(null))
                .processor(asyncUpdateTVProcessor())
                .writer(asyncJpaTVUpdateWriter())
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
    public ItemReader<Long> changedTVIdReader(@Value("#{jobParameters['date']}") String date) {
        return new ItemReader<Long>() {
            private int currentPage = 1;
            private Iterator<ChangeTVResultDto> tvResultIterator;
            private int totalPages;

            @Override
            public Long read() throws Exception {
                if (tvResultIterator == null || !tvResultIterator.hasNext()) {
                    if (totalPages > 0 && currentPage > totalPages) {
                        return null;
                    }

                    TVChangeListDto response = getChangedTVS(currentPage, date);

                    if (totalPages == 0) {
                        totalPages = response.getTotal_pages();
                    }

                    tvResultIterator = response.getResults().stream()
                            .filter(movie -> !movie.isAdult())
                            .iterator();

                    currentPage++;

                    if (!tvResultIterator.hasNext()) {
                        return null;
                    }
                }

                return tvResultIterator.hasNext() ? tvResultIterator.next().getId() : null;
            }
        };
    }

    public TVChangeListDto getChangedTVS(int page,String date) {
        return webClient.get()
                .uri("/tv/changes?page=" + page +"&start_date="+date+"&end_date="+date)
                .retrieve()
                .bodyToMono(TVChangeListDto.class)
                .block();
    }


    @Bean
    public AsyncItemProcessor<Long, Program> asyncUpdateTVProcessor(){
        final AsyncItemProcessor<Long,Program> processor = new AsyncItemProcessor<>();
        processor.setDelegate(tvUpdateProcessor());
        processor.setTaskExecutor(apiExecutor);

        return processor;
    }
    @Bean
    public ItemProcessor<Long, Program> tvUpdateProcessor() {
        return tvId -> {
            // API를 통해 영화 상세 정보 조회
            log.info("API 호출 현재 tv: {}",tvId);

            try{
                OpenApiTVDetailDto openApiTVDetailDto = getApiProgram(tvId);


                String originalCountryName = (openApiTVDetailDto.getProductionCountries() == null
                        || openApiTVDetailDto.getProductionCountries().isEmpty())
                        ? null : openApiTVDetailDto.getProductionCountries().get(0).getName();

                String year = openApiTVDetailDto.getFirstAirDate()==null?null:
                        (openApiTVDetailDto.getFirstAirDate().length() >= 4 ? openApiTVDetailDto.getFirstAirDate().substring(0, 4) : null);

                return programRepository.findByTmDbProgramIdAndTypeWithGenre(tvId, ProgramType.TV).map(program->{
                    program.update(openApiTVDetailDto.getName(), openApiTVDetailDto.getPoster_path(),
                            year,
                            openApiTVDetailDto.getFirstAirDate(),originalCountryName,openApiTVDetailDto.getOriginalName(),
                            openApiTVDetailDto.getOverview(),openApiTVDetailDto.getTagline(),openApiTVDetailDto.getBackdrop_path());


                    program.getProgramGenreList().clear();
                    openApiTVDetailDto.getTmDbGenreInfos().forEach(genreInfo -> {
                        Genre genre = genreRepository.findByTmDbGenreId(genreInfo.getId()).orElseThrow();
                        program.addGenre(genre);
                    });

                    return program;

                }).orElse(makeProgram(tvId,openApiTVDetailDto));
            }catch (WebClientResponseException e){
                if(e.getMessage().contains("404 Not Found")){
                    log.info("삭제된 TV 아이디 {}",tvId);
                    return programRepository.findByTmDbProgramIdAndType(tvId,ProgramType.TV)
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

    private OpenApiTVDetailDto getApiProgram(long tvId) throws NotFoundException {

        OpenApiTVDetailDto openApiTVDetailDto = webClient.get()
                .uri("/movie/"+tvId+"?language=ko")
                .retrieve()
                .bodyToMono(OpenApiTVDetailDto.class)
                .block();

        return openApiTVDetailDto;

    }


    @Bean
    public AsyncItemWriter<Program> asyncJpaTVUpdateWriter(){
        final AsyncItemWriter<Program> writer = new AsyncItemWriter<>();
        writer.setDelegate(jpaTVUpdateItemWriter(entityManagerFactory));

        return writer;
    }
    @Bean
    public JpaItemWriter<Program> jpaTVUpdateItemWriter(EntityManagerFactory entityManagerFactory) {
        JpaItemWriter<Program> writer = new JpaItemWriter<>();
        writer.setEntityManagerFactory(entityManagerFactory);
        return writer;
    }

    private Program makeProgram(Long movieId, OpenApiTVDetailDto openApiTVDetailDto){

        String originalCountryName = (openApiTVDetailDto.getProductionCountries() == null
                || openApiTVDetailDto.getProductionCountries().isEmpty())
                ? null : openApiTVDetailDto.getProductionCountries().get(0).getName();

        String year = openApiTVDetailDto.getFirstAirDate()==null?null:
                (openApiTVDetailDto.getFirstAirDate().length() >= 4 ? openApiTVDetailDto.getFirstAirDate().substring(0, 4) : null);

        Program program = Program.builder()
                .tmDbProgramId(movieId)
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

        openApiTVDetailDto.getTmDbGenreInfos().forEach(genreInfo -> {
            Genre genre = genreRepository.findByTmDbGenreId(genreInfo.getId()).orElseThrow();
            program.addGenre(genre);
        });

        return program;
    }
}
