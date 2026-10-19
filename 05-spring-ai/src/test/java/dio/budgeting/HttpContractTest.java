package dio.budgeting;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "spring.docker.compose.enabled=false", "spring.ai.google.genai.api-key=test-only"
})
class HttpContractTest {
    @Autowired WebApplicationContext context;
    @MockitoBean TransactionRepository repository;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        reset(repository);
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test void createsAndListsAmountsInReais() throws Exception {
        mvc.perform(post("/transactions").contentType("application/json")
                .content("{\"description\":\"Mercado\",\"category\":\"GROCERIES\",\"amount\":8000}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.amount").value(80.00));
        when(repository.findAllByCategory(Category.GROCERIES)).thenReturn(List.of(new Transaction("Mercado", 8000, Category.GROCERIES)));
        mvc.perform(get("/transactions/GROCERIES")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(80.00));
    }

    @Test void rejectsInvalidRestInput() throws Exception {
        for (String body : List.of(
                "{\"description\":\"  \",\"category\":\"AUTO\",\"amount\":1}",
                "{\"description\":\"Gasolina\",\"category\":\"AUTO\",\"amount\":0}",
                "{\"description\":\"Gasolina\",\"amount\":1}",
                "{\"description\":\"Gasolina\",\"category\":\"OTHER\",\"amount\":1}",
                "{bad json")) {
            mvc.perform(post("/transactions").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(repository, never()).save(any());
        mvc.perform(get("/transactions/OTHER")).andExpect(status().isBadRequest());
    }

    @Test void rejectsEmptyAudioBeforeProvider() throws Exception {
        mvc.perform(multipart("/transactions/ai").file(new MockMultipartFile("file", "empty.wav", "audio/wav", new byte[0])))
                .andExpect(status().isBadRequest());
    }
}
