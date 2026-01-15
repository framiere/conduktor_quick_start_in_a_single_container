package com.example.messaging.operator.e2e;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

/**
 * Marks a test class as an E2E test that runs against a real Kubernetes cluster. These tests are executed via the Maven 'e2e' profile: mvn verify -Pe2e
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("e2e")
@TestInstance(Lifecycle.PER_CLASS)
public @interface E2ETest {
}
