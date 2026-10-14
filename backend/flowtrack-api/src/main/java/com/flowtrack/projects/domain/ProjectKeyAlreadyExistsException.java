package com.flowtrack.projects.domain;

public class ProjectKeyAlreadyExistsException extends RuntimeException {

    public ProjectKeyAlreadyExistsException(String projectKey) {
        super("Project key already exists: " + projectKey);
    }
}
