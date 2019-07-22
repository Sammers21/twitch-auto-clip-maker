package io.github.sammers21.tacm.youtube.settings;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SimpleIntervalCheck extends Settings {

    @JsonProperty("max_clips_per_release")
    private Integer max;

    @JsonProperty("min_clips_per_release")
    private Integer min;

    @JsonProperty("max_releases_per_day")
    private Integer maxReleasesPerDay;

    public Integer getMaxReleasesPerDay() {
        return maxReleasesPerDay;
    }

    public void setMaxReleasesPerDay(Integer maxReleasesPerDay) {
        this.maxReleasesPerDay = maxReleasesPerDay;
    }

    public Integer getMax() {
        return max;
    }

    public void setMax(Integer max) {
        this.max = max;
    }

    public Integer getMin() {
        return min;
    }

    public void setMin(Integer min) {
        this.min = min;
    }

}
