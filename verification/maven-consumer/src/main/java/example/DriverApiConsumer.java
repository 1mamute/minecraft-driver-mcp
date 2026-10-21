package example;

import io.github.ummamute.driver.api.DriverExtension;
import io.github.ummamute.driver.api.ToolRegistrar;

/** Verifies that a Java mod can compile against the published extension API. */
public final class DriverApiConsumer implements DriverExtension {
    @Override
    public void registerTools(ToolRegistrar registrar) {
        // This fixture only checks dependency consumption; it registers no tools.
    }
}
