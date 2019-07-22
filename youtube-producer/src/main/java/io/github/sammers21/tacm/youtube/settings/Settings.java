package io.github.sammers21.tacm.youtube.settings;

import io.vertx.core.json.JsonObject;

public class Settings {
    private String type;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }


    public static Settings parseJson(JsonObject json) {
        String type = json.getString("type");
        if ("SIMPLE_INTERVAL_CHECK".equals(type)) {
            return json.mapTo(SimpleIntervalCheck.class);
        }
        throw new IllegalStateException(String.format("Type %s is not supported", type));
    }

}
