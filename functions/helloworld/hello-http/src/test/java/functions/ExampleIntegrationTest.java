/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package functions;

// [START functions_http_integration_test]

import static com.google.common.truth.Truth.assertThat;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.vavr.CheckedFunction0;
import io.vavr.control.Try;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import javassist.bytecode.ByteArray;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ExampleIntegrationTest {
  // Root URL pointing to the locally hosted function
  // The Functions Framework Maven plugin lets us run a function locally
  private static final String BASE_URL = "http://localhost:8080";

  private static Process emulatorProcess = null;
  private static HttpClient client = HttpClient.newHttpClient();

  @BeforeClass
  public static void setUp() throws IOException {
    // Get the sample's base directory (the one containing a pom.xml file)
    String baseDir = System.getProperty("baseDir");
    if (baseDir == null) {
      baseDir = System.getProperty("user.dir");
    }

    // Emulate the function locally by running the Functions Framework Maven plugin
    emulatorProcess = new ProcessBuilder()
        .command("mvn", "function:run")
        .directory(new File(baseDir))
        .start();
  }

  @AfterClass
  public static void tearDown() throws IOException {
    // DEBUG: Print stdout/stderr
    Logger.getAnonymousLogger().severe("--- OUT ---");
    ByteArrayOutputStream a = new ByteArrayOutputStream();
    a.write(
        emulatorProcess.getErrorStream().readNBytes(emulatorProcess.getErrorStream().available()));

    // DEBUG: Print stdout/stderr
    Logger.getAnonymousLogger().severe("--- ERR ---");
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    b.write(
        emulatorProcess.getInputStream().readNBytes(emulatorProcess.getInputStream().available()));
    String c = a.toString(StandardCharsets.UTF_8) + b.toString(StandardCharsets.UTF_8);

    // Terminate the running Functions Framework Maven plugin process
    emulatorProcess.destroy();

    // DEBUG
    String baseDir = System.getProperty("baseDir");
    if (baseDir == null) {
      c += "%n[BASE DIR used user.dir]%n";
      baseDir = System.getProperty("user.dir");
    }
    c += "%nBASE DIR: " + baseDir + "%n";
    throw new RuntimeException(c);
  }

  @Test
  public void helloHttp_shouldRunWithFunctionsFramework() throws Throwable {
    String functionUrl = BASE_URL + "/helloHttp";

    HttpRequest getRequest = HttpRequest.newBuilder().uri(URI.create(functionUrl)).GET().build();

    // The Functions Framework Maven plugin process takes time to start up
    // Use resilience4j to retry the test HTTP request until the plugin responds
    RetryRegistry registry = RetryRegistry.of(RetryConfig.custom()
        .maxAttempts(8)
        .intervalFunction(IntervalFunction.ofExponentialBackoff(200, 2))
        .retryExceptions(IOException.class)
        .build());
    Retry retry = registry.retry("my");

    // Perform the request-retry process
    CheckedFunction0<String> retriableFunc = Retry.decorateCheckedSupplier(retry, () -> {
      return client.send(getRequest,
          HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
    });
    String body = Try.of(retriableFunc).get();

    // Verify the function returned the right results
    assertThat(body).isEqualTo("Hello world!");
  }
}
// [END functions_http_integration_test]
