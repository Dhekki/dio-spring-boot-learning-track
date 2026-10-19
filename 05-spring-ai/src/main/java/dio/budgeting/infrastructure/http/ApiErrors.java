package dio.budgeting.infrastructure.http;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidInput(IllegalArgumentException error) {
        var detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setDetail(error.getMessage());
        return detail;
    }
}
