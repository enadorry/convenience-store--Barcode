package dev.enadorry.kaimonoscan;

/** Defaults also apply to unconfigured installs upgraded from versions before 0.7.4. */
final class CommunityConfig {
    static final String DEFAULT_URL="https://kaimono-community.fuuhaya155.workers.dev";
    static String endpoint(String saved,boolean explicitlyConfigured){
        String value=saved==null?"":saved.trim();
        return value.isEmpty()&&!explicitlyConfigured?DEFAULT_URL:value;
    }
    static boolean yahooEnabled(String clientId){return clientId!=null&&!clientId.trim().isEmpty();}
}
