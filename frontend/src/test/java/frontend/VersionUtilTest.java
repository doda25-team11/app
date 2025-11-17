import com.doda25.team11.version.VersionUtil;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class VersionUtilTest {

    @Test
    void testVersionIsNotUnknown() {
        String version = VersionUtil.getVersion();
        assertNotNull(version);
        assertNotEquals("UNKNOWN", version);
        System.out.println("Library version: " + version);
    }
}