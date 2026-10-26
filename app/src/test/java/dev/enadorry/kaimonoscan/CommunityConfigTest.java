package dev.enadorry.kaimonoscan;
import org.junit.Test;
import static org.junit.Assert.*;

public class CommunityConfigTest {
    @Test public void freshInstallAndOldUnconfiguredInstallUseCloudflare() throws Exception {
        assertEquals(CommunityConfig.DEFAULT_URL,CommunityConfig.endpoint(null,false));
        assertEquals(CommunityConfig.DEFAULT_URL,CommunityConfig.endpoint("",false));
        assertEquals(CommunityConfig.DEFAULT_URL,CommunityApi.base(CommunityConfig.DEFAULT_URL));
    }
    @Test public void customEndpointSurvivesUpgradeAndExplicitSave(){
        assertEquals("https://custom.example",CommunityConfig.endpoint("https://custom.example",false));
        assertEquals("https://custom.example",CommunityConfig.endpoint("https://custom.example",true));
    }
    @Test public void explicitDisableSurvivesLaterLaunches(){
        assertEquals("",CommunityConfig.endpoint("",true));
        assertEquals("",CommunityConfig.endpoint("  ",true));
    }
    @Test public void idIsOptionalAndBlankIdsNeverEnableYahooRequests(){
        assertFalse(CommunityConfig.yahooEnabled(null));
        assertFalse(CommunityConfig.yahooEnabled(""));
        assertFalse(CommunityConfig.yahooEnabled(" \n "));
        assertTrue(CommunityConfig.yahooEnabled("configured-id"));
    }
}
