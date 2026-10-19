package dio.budgeting;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "spring.docker.compose.enabled=false",
        "spring.ai.google.genai.api-key=test-only"
})
class BudgetingApplicationTests {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    dio.budgeting.domain.TransactionRepository repository;

    @Test
    void contextLoads() {
    }

}
