package com.codelens.common.validators.l3;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

public class L3RequestExtractor {

    public static List<VerificationRequest> fromV3Json(String json, ConfidenceLevel threshold) {
        List<VerificationRequest> requests = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray methods = root.getAsJsonArray("methods");
            if (methods != null) {
                for (int i = 0; i < methods.size(); i++) {
                    JsonObject method = methods.get(i).getAsJsonObject();

                    JsonArray calls = method.getAsJsonArray("calls");
                    if (calls != null) {
                        for (int j = 0; j < calls.size(); j++) {
                            JsonObject call = calls.get(j).getAsJsonObject();
                            String target = getAsString(call, "target");
                            String tag = getAsString(call, "tag");
                            if (target != null) {
                                ConfidenceLevel confidence = "[FACT]".equals(tag) ? ConfidenceLevel.HIGH : threshold;
                                String claim = target;
                                requests.add(new VerificationRequest(claim, confidence, "method_call", json));
                            }
                        }
                    }

                    JsonArray risks = method.getAsJsonArray("risks");
                    if (risks != null) {
                        for (int j = 0; j < risks.size(); j++) {
                            JsonObject risk = risks.get(j).getAsJsonObject();
                            String desc = getAsString(risk, "description");
                            String tag = getAsString(risk, "tag");
                            if (desc != null) {
                                ConfidenceLevel confidence = "[FACT]".equals(tag) ? ConfidenceLevel.HIGH : threshold;
                                String claim = desc;
                                requests.add(new VerificationRequest(claim, confidence, "risk", json));
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return requests;
    }

    public static List<VerificationRequest> fromV2Json(String json, ConfidenceLevel threshold) {
        List<VerificationRequest> requests = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            JsonArray keyMethods = root.getAsJsonArray("keyMethods");
            if (keyMethods != null) {
                for (int i = 0; i < keyMethods.size(); i++) {
                    JsonObject km = keyMethods.get(i).getAsJsonObject();
                    String name = getAsString(km, "name");
                    if (name != null) {
                        requests.add(new VerificationRequest(name, threshold, "key_method", json));
                    }
                }
            }

            JsonArray deps = root.getAsJsonArray("dependencies");
            if (deps != null) {
                for (int i = 0; i < deps.size(); i++) {
                    JsonObject dep = deps.get(i).getAsJsonObject();
                    String name = getAsString(dep, "name");
                    if (name != null) {
                        requests.add(new VerificationRequest(name, threshold, "dependency", json));
                    }
                }
            }

            JsonArray risks = root.getAsJsonArray("risks");
            if (risks != null) {
                for (int i = 0; i < risks.size(); i++) {
                    JsonObject risk = risks.get(i).getAsJsonObject();
                    String desc = getAsString(risk, "description");
                    if (desc != null) {
                        requests.add(new VerificationRequest(desc, threshold, "risk", json));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return requests;
    }

    private static String getAsString(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        return element != null && !element.isJsonNull() ? element.getAsString() : null;
    }
}
