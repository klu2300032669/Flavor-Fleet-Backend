package com.flavorfleet.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

public class CurrencyDoubleDeserializer extends JsonDeserializer<Double> {
    @Override
    public Double deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getText();
        if (value != null && !value.trim().isEmpty()) { // Added check for empty string
            String cleanedValue = value.replaceAll("[^0-9.-]", "");
            try {
                return Double.parseDouble(cleanedValue);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid price format: " + value, e);
            }
        }
        return null;
    }
}
// Improvement: Added check for empty string to prevent potential issues