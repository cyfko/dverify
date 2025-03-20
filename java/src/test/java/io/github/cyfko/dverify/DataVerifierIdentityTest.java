package io.github.cyfko.dverify;

import io.github.cyfko.dverify.exceptions.DataExtractionException;
import io.github.cyfko.dverify.exceptions.JsonEncodingException;
import io.github.cyfko.dverify.impl.kafka.*;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

public class DataVerifierIdentityTest {

    private static KafkaContainer kafkaContainer;
    private DataSigner signer;
    private DataVerifier verifier;
    private File tempDir;

    @BeforeAll
    public static void setUpClass() {
        kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest"))
                .withEmbeddedZookeeper();
        kafkaContainer.start();
    }

    @AfterAll
    public static void tearDownClass() {
        kafkaContainer.stop();
    }

    @BeforeEach
    public void setUp() throws IOException {
        String kafkaBootstrapServers = kafkaContainer.getBootstrapServers();

        Properties props = new Properties();
        props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
        props.setProperty(SignerConfig.GENERATED_TOKEN_CONFIG, Constant.GENERATED_TOKEN_IDENTITY);

        tempDir = Files.createTempDirectory("rocksdb_db_test_").toFile();
        props.setProperty(VerifierConfig.EMBEDDED_DB_PATH_CONFIG, tempDir.getAbsolutePath());
        props.setProperty(VerifierConfig.CLEANUP_INTERVAL_CONFIG, "1");

        signer = KafkaDataSigner.of(props);
        verifier = KafkaDataVerifier.of(props);
    }

    @AfterEach
    public void tearDown() {
        if (tempDir.exists()) {
            tempDir.delete();
        }
    }

    @Test
    public void sign_method_with_valid_data_should_returns_jwt() throws JsonEncodingException {
        UserData data = new UserData("john.doe@example.com");
        Duration duration = Duration.ofHours(2);

        String jwt = signer.sign(data, duration);

        assertNotNull(jwt);
        assertFalse(jwt.isEmpty());
    }

    @Test
    public void sign_method_with_invalid_data_should_throws_exception() {
        Object invalidData = null; // Simulating invalid data
        Duration duration = Duration.ofHours(2);

        Assertions.assertThrows(IllegalArgumentException.class, () -> signer.sign(invalidData, duration));
    }

    @Test
    public void sign_method_with_expired_duration_should_throws_exception() {
        UserData data = new UserData("john.doe@example.com");
        Duration duration = Duration.ofMinutes(-5); // Negative duration

        Assertions.assertThrows(IllegalArgumentException.class, () -> signer.sign(data, duration));
    }

    @Test
    public void sign_valid_data_should_returns_jwt() throws JsonEncodingException {
        UserData data = new UserData("john.doe@example.com");
        Duration duration = Duration.ofHours(2);

        String jwt = signer.sign(data, duration);

        assertNotNull(jwt, "JWT should not be null");
        assertFalse(jwt.isEmpty(), "JWT should not be empty");
    }

    @Test
    public void sign_invalid_data_should_throws_exception() {
        Object invalidData = null; // Simulating invalid data
        Duration duration = Duration.ofHours(2);

        assertThrows(IllegalArgumentException.class, () -> signer.sign(invalidData, duration));
    }

    @Test
    public void verify_valid_token_should_returns_payload() throws InterruptedException {
        UserData data = new UserData("john.doe@example.com");
        String jwt = signer.sign(data, Duration.ofHours(2)); // Generate a valid token
        Thread.sleep(10); // Wait 10 ms to ensure that the keys has been propagated to kafka

        UserData verifiedData = verifier.verify(jwt, UserData.class);

        assertNotNull(verifiedData);
        assertEquals(data.getEmail(), verifiedData.getEmail());
    }

    @Test
    public void verify_invalid_token_should_throws_exception() {
        String invalidToken = "invalid.jwt.token"; // Simulating an invalid token

        assertThrows(DataExtractionException.class, () -> verifier.verify(invalidToken, UserData.class));
    }

    @Test
    public void verify_expired_token_should_throws_exception() throws InterruptedException {
        UserData data = new UserData("john.doe@example.com");
        String jwt = signer.sign(data, Duration.ofMillis(1)); // Token with short duration
        Thread.sleep(10); // Wait for the token to expire

        assertThrows(DataExtractionException.class, () -> verifier.verify(jwt, UserData.class));
    }
}

