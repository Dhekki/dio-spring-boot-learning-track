package dio.budgeting;

import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class TransactionRulesTest {
    @Test
    void convertsCentsExactly() {
        for (long cents : new long[]{1, 99, 100, 1050, 8000, 9_999_999_999L}) {
            var output = TransactionOutput.from(new Transaction("Teste", cents, Category.GROCERIES));
            assertThat(output.value()).isEqualByComparingTo(BigDecimal.valueOf(cents, 2));
            assertThat(output.value().scale()).isEqualTo(2);
        }
    }

    @Test
    void savesTrimmedValidTransaction() {
        var repository = new InMemoryRepository();
        var output = new PersistTransactionUseCase(repository)
                .execute(new PersistTransactionInput("  Mercado  ", 8000, Category.GROCERIES));
        assertThat(repository.saved).hasSize(1);
        assertThat(repository.saved.getFirst().getDescription()).isEqualTo("Mercado");
        assertThat(output.value()).isEqualByComparingTo("80.00");
    }

    @Test
    void rejectsInvalidTransactionsBeforeSaving() {
        var repository = new InMemoryRepository();
        var useCase = new PersistTransactionUseCase(repository);
        assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute(new PersistTransactionInput(null, 1, Category.AUTO))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute(new PersistTransactionInput("  ", 1, Category.AUTO))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute(new PersistTransactionInput("Gasolina", 0, Category.AUTO))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute(new PersistTransactionInput("Gasolina", -1, Category.AUTO))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute(new PersistTransactionInput("Gasolina", 1, null))).isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.saved).isEmpty();
    }

    private static class InMemoryRepository implements TransactionRepository {
        final List<Transaction> saved = new ArrayList<>();
        public Transaction save(Transaction transaction) { saved.add(transaction); return transaction; }
        public List<Transaction> findAllByCategory(Category category) {
            return saved.stream().filter(t -> t.getCategory() == category).toList();
        }
    }
}
