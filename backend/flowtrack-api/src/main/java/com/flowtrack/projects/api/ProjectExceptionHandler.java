package com.flowtrack.projects.api;

import com.flowtrack.projects.domain.ProjectKeyAlreadyExistsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ProjectController.class)
public class ProjectExceptionHandler {

    @ExceptionHandler(ProjectKeyAlreadyExistsException.class)
    public ProblemDetail handleProjectKeyAlreadyExists(
            ProjectKeyAlreadyExistsException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );

        problem.setTitle("Project key already exists");

        return problem;
    }
}
