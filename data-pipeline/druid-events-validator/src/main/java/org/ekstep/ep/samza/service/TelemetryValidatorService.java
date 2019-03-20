package org.ekstep.ep.samza.service;

import static java.text.MessageFormat.format;

import java.io.*;
import java.text.MessageFormat;

import org.ekstep.ep.samza.core.Logger;
import org.ekstep.ep.samza.domain.Event;
import org.ekstep.ep.samza.task.TelemetryValidatorSink;
import org.ekstep.ep.samza.task.TelemetryValidatorSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.google.gson.JsonSyntaxException;

public class TelemetryValidatorService {
    static Logger LOGGER = new Logger(TelemetryValidatorService.class);
    private static final String DEFAULT_EVENT_SCHEMA_FILE = "envelope.json";
    private static final String TELEMETRY_EVENT_SCHEMA_BASE_PATH = "/schemas/telemetry";
    private static final String SUMMARY_EVENT_SCHEMA_BASE_PATH = "/schemas/summary";

    public TelemetryValidatorService() {
    }

    public void process(TelemetryValidatorSource source, TelemetryValidatorSink sink, JsonSchemaFactory jsonSchemaFactory) {
        Event event = null;
        try {
            event = source.getEvent();
            String schema = getSchema(event);
            if (schema == null) {
                LOGGER.info("SCHEMA FILE DOESN'T EXIST HENCE SKIPPING THE VALIDATION PROCESS AND SENDING TO SUCCESS TOPIC", event.mid());
                event.markSkipped();
                sink.toSuccessTopic(event);
                return;
            }
            JsonNode schemaJson = JsonLoader.fromString(schema);
            JsonNode eventJson = JsonLoader.fromString(event.getJson());
            JsonSchema jsonSchema = jsonSchemaFactory.getJsonSchema(schemaJson);
            ProcessingReport report = jsonSchema.validate(eventJson);
            if (report.isSuccess()) {
                LOGGER.info("VALIDATION SUCCESS", event.mid());
                event.markSuccess();
                sink.toSuccessTopic(event);
            } else {
                String fieldName = this.getInvalidFieldName(report.toString());
                LOGGER.error(null, "VALIDATION FAILED: " + report.toString());
                sink.toFailedTopic(event, "Invalid field:" + fieldName);
            }
        } catch (JsonSyntaxException e) {
            LOGGER.error(null, "INVALID EVENT: " + source.getMessage());
            sink.toMalformedEventsTopic(source.getMessage());
        } catch (Exception e) {
            LOGGER.error(null, format(
                    "EXCEPTION. PASSING EVENT THROUGH AND ADDING IT TO EXCEPTION TOPIC. EVENT: {0}, EXCEPTION:",
                    event), e);
            sink.toErrorTopic(event, e.getMessage());
        }
    }

    private String getInvalidFieldName(String errorInfo) {
        String[] message = errorInfo.split("reports:");
        String[] fields = message[1].split(",");
        String[] pointer = fields[3].split("\"pointer\":");
        return pointer[1].substring(0, pointer[1].length() - 1);
    }

    private String getSchema(Event event) {
        String telemetrySchemaFilePath;
        String summaryEventSchemaFilepath;
        StringBuilder sb = new StringBuilder();
        telemetrySchemaFilePath = MessageFormat.format("{0}/{1}/{2}", TELEMETRY_EVENT_SCHEMA_BASE_PATH, event.version(), event.schemaName());
        summaryEventSchemaFilepath = MessageFormat.format("{0}/{1}/{2}", SUMMARY_EVENT_SCHEMA_BASE_PATH, event.version(), event.schemaName());
        InputStream is = this.getClass().getResourceAsStream(event.isSummaryEvent() ? summaryEventSchemaFilepath : telemetrySchemaFilePath);
        BufferedReader br;
        if (is == null) {
            telemetrySchemaFilePath = MessageFormat.format("{0}/{1}/{2}", TELEMETRY_EVENT_SCHEMA_BASE_PATH, event.version(), DEFAULT_EVENT_SCHEMA_FILE);
            summaryEventSchemaFilepath = MessageFormat.format("{0}/{1}/{2}", SUMMARY_EVENT_SCHEMA_BASE_PATH, event.version(), DEFAULT_EVENT_SCHEMA_FILE);
            is = this.getClass().getResourceAsStream(event.isSummaryEvent() ? summaryEventSchemaFilepath : telemetrySchemaFilePath);
        }
        br = new BufferedReader(new InputStreamReader(is));
        String line;
        try {
            while ((line = br.readLine()) != null) {
                sb.append(line + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }
}
