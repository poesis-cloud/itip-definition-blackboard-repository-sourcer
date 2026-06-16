package cloud.poesis.itip.sourcer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

class SourcerApplicationMainTest {

  @Test
  void mainDelegatesToSpringApplication() {
    try (MockedStatic<SpringApplication> mocked = mockStatic(SpringApplication.class)) {
      SourcerApplication.main(new String[] {});
      mocked.verify(() -> SpringApplication.run(any(Class.class), any(String[].class)));
    }
  }
}
