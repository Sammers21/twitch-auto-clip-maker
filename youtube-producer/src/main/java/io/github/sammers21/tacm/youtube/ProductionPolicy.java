package io.github.sammers21.tacm.youtube;

import java.util.Objects;

public class ProductionPolicy {

    private String policy_for_streamer;

    private Integer clips_per_release;

    public ProductionPolicy(String policy_for_streamer, Integer clips_per_release) {
        this.policy_for_streamer = policy_for_streamer;
        this.clips_per_release = clips_per_release;
    }

    public String Policy_for_streamer() {
        return policy_for_streamer;
    }

    public void setPolicy_for_streamer(String policy_for_streamer) {
        this.policy_for_streamer = policy_for_streamer;
    }

    public Integer Clips_per_release() {
        return clips_per_release;
    }

    public void setClips_per_release(Integer clips_per_release) {
        this.clips_per_release = clips_per_release;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductionPolicy that = (ProductionPolicy) o;
        return Objects.equals(policy_for_streamer, that.policy_for_streamer) &&
                Objects.equals(clips_per_release, that.clips_per_release);
    }

    @Override
    public int hashCode() {
        return Objects.hash(policy_for_streamer, clips_per_release);
    }
}
