package dio.budgeting;

import dio.budgeting.application.ListTransactionsByCategoryUseCase;
import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.infrastructure.ai.GeminiAudio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "spring.docker.compose.enabled=false"
})
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class GeminiIntegrationIT {
    @Autowired GeminiAudio audio;
    @Autowired ChatClient.Builder chat;
    @Autowired PersistTransactionUseCase persist;
    @Autowired ListTransactionsByCategoryUseCase list;
    @MockitoBean TransactionRepository repository;

    @Test void transcribesSampleAndProducesRealWav() throws Exception {
        byte[] sample = new ClassPathResource("audio/recording-1.m4a").getContentAsByteArray();
        String words = audio.transcribe(new MockMultipartFile("file", "recording-1.m4a", "audio/m4a", sample));
        assertThat(words).contains("80");
        assertThat(audio.speak("Teste de áudio.")).startsWith(new byte[]{'R', 'I', 'F', 'F'});
    }

    @Test void usesRealToolCallingForValidExpense() {
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        String answer = chat.defaultTools(persist, list).build().prompt()
                .user("Registre uma compra de 80 reais no mercado, categoria GROCERIES, descrição Mercado.")
                .call().content();
        assertThat(answer).isNotBlank();
        verify(repository).save(any());
    }
}
