package dev.enadorry.kaimonoscan;

import java.util.HashSet;
import java.util.Set;

/** Pure scan-session state: repeated camera callbacks are not additional purchases. */
final class ScanGate {
    private static final long COOLDOWN_MS=400;
    private static final long EMPTY_WINDOW_MS=1000;
    private final Set<String> blocked=new HashSet<>();
    private long acceptedAt=-1, seenAt=-1, emptySince=-1;
    private int emptyFrames;

    boolean accept(String jan,long now) {
        if(!Pricing.validJan(jan))return false;
        seenAt=now;
        pauseTracking();
        if(blocked.contains(jan))return false;
        if(acceptedAt>=0 && now-acceptedAt<COOLDOWN_MS)return false;
        acceptedAt=now;
        blocked.add(jan);
        return true;
    }
    /** Empty observed frames, not an elapsed timeout or a paused camera, rearm codes. */
    boolean frame(boolean hasPossiblePoints,long now) {
        if(hasPossiblePoints){pauseTracking();return false;}
        if(seenAt<0||now<seenAt||now-seenAt<250){pauseTracking();return false;}
        if(emptySince<0)emptySince=now;
        emptyFrames++;
        if(!blocked.isEmpty()&&emptyFrames>=3&&now-emptySince>=EMPTY_WINDOW_MS&&now-seenAt>=1250){
            blocked.clear();pauseTracking();return true;
        }
        return false;
    }
    void pauseTracking(){emptySince=-1;emptyFrames=0;}
    void restorePrevious(String jan,long now){if(Pricing.validJan(jan)){blocked.add(jan);seenAt=now;acceptedAt=now;pauseTracking();}}
    void allowRepeat(){blocked.clear();pauseTracking();}
}
