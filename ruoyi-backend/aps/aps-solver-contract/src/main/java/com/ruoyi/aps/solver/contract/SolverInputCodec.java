package com.ruoyi.aps.solver.contract;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** SolverInput 的严格 JSON 编解码和摘要生成入口。 */
public final class SolverInputCodec
{
    private static final DateTimeFormatter UTC_MILLIS = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private final ObjectMapper json;
    private final JcsSha256 hashes = new JcsSha256();

    public SolverInputCodec()
    {
        JavaTimeModule time = new JavaTimeModule();
        time.addSerializer(Instant.class, new JsonSerializer<>() {
            @Override public void serialize(Instant value, JsonGenerator generator,
                    com.fasterxml.jackson.databind.SerializerProvider serializers) throws IOException
            {
                generator.writeString(UTC_MILLIS.format(value));
            }
        });
        time.addDeserializer(Instant.class, new JsonDeserializer<>() {
            @Override public Instant deserialize(JsonParser parser, DeserializationContext context) throws IOException
            {
                String value = parser.getValueAsString();
                if (value == null || !value.matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z$"))
                    throw context.weirdStringException(value, Instant.class, "时间必须是 UTC 毫秒格式");
                return Instant.parse(value);
            }
        });
        json = new ObjectMapper().registerModule(time).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public EncodedInput encodeWithHash(SolverInput source)
    {
        try
        {
            byte[] unhashed = json.writeValueAsBytes(source);
            String hash = hashes.inputHash(unhashed);
            SolverInput value = withHash(source, hash);
            byte[] bytes = json.writeValueAsBytes(value);
            if (!hash.equals(hashes.inputHash(bytes))) throw new IllegalStateException("SolverInput hash 无法稳定复算");
            return new EncodedInput(value, bytes);
        }
        catch (IOException exception)
        {
            throw new IllegalArgumentException("SolverInput 无法序列化", exception);
        }
    }

    public SolverInput decode(byte[] bytes)
    {
        try
        {
            SolverInput value = json.readValue(bytes, SolverInput.class);
            if (value == null) throw new IllegalArgumentException("SolverInput JSON 不能为空");
            return value;
        }
        catch (IOException exception) { throw new IllegalArgumentException("SolverInput JSON 不兼容", exception); }
    }

    public SolverResult decodeResult(byte[] bytes)
    {
        try
        {
            SolverResult value = json.readValue(bytes, SolverResult.class);
            if (value == null) throw new IllegalArgumentException("SolverResult JSON 不能为空");
            return value;
        }
        catch (IOException exception) { throw new IllegalArgumentException("SolverResult JSON 不兼容", exception); }
    }

    public String candidateHash(PlanCandidate candidate)
    {
        try { return hashes.hashCanonical(json.writeValueAsBytes(candidate)); }
        catch (IOException exception) { throw new IllegalArgumentException("候选计划无法序列化", exception); }
    }

    private SolverInput withHash(SolverInput source, String hash)
    {
        return new SolverInput(source.schemaVersion(), source.contractType(), source.requestId(), source.planVersionId(),
                source.capturedAt(), source.definitionRevision(), source.executionRevision(), hash,
                source.hashAlgorithm(), source.canonicalization(), source.modelVersion(), source.scope(), source.horizon(),
                source.baseVersion(), source.parameters(), source.resources(), source.availabilityWindows(), source.tasks(),
                source.dependencies(), source.materialSupplies(), source.materialDemands(), source.sharedBatchCandidates(),
                source.locks(), source.actualOccupancies());
    }

    public record EncodedInput(SolverInput value, byte[] bytes)
    {
        public EncodedInput { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }
}
