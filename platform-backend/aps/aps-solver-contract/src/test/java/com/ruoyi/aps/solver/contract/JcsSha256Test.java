package com.ruoyi.aps.solver.contract;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JcsSha256Test
{
    private final JcsSha256 jcs = new JcsSha256();

    @Test
    void matchesRfc8785NumberAndUtf16OrderingVectors()
    {
        assertThat(jcs.canonicalText(bytes("{\"numbers\":[333333333.33333329,1E30,4.50,2e-3,0.000000000000000000000000001],\"literals\":[null,true,false]}")))
                .isEqualTo("{\"literals\":[null,true,false],\"numbers\":[333333333.3333333,1e+30,4.5,0.002,1e-27]}");
        assertThat(jcs.canonicalText(bytes("{\"😀\":4,\"€\":3,\"é\":2,\"a\":1}")))
                .isEqualTo("{\"a\":1,\"é\":2,\"€\":3,\"😀\":4}");
        assertThat(jcs.canonicalText(bytes("{\"negativeZero\":-0,\"large\":1e21,\"small\":1e-7}")))
                .isEqualTo("{\"large\":1e+21,\"negativeZero\":0,\"small\":1e-7}");
    }

    @Test
    void matchesNodeGoldenInputHashes()
            throws Exception
    {
        for (String file : List.of("valid-minimal-solver-input.json", "valid-same-start-input.json",
                "valid-material-quantity-shared-batch-input.json"))
        {
            Path input = findWorkspaceRoot().resolve("contracts/aps/examples/golden").resolve(file);
            byte[] json = Files.readAllBytes(input);
            Matcher declared = Pattern.compile("\\\"inputHash\\\"\\s*:\\s*\\\"([0-9a-f]{64})\\\"")
                    .matcher(new String(json, StandardCharsets.UTF_8));
            assertThat(declared.find()).as(file + " 声明 inputHash").isTrue();
            assertThat(jcs.inputHash(json)).as(file).isEqualTo(declared.group(1));
        }
    }

    @Test
    void codecWritesFixedUtcMillisAndStableHash()
    {
        SolverInput value = new SolverInput("1.0", "SOLVER_INPUT", uuid(1), uuid(2),
                Instant.parse("2026-09-15T00:00:00Z"), 1, 2, "0".repeat(64), "SHA-256", "JCS-RFC8785",
                "aps-cpsat-v1", new SolverInput.Scope("SITE_01", List.of(uuid(3))),
                new SolverInput.Horizon(Instant.parse("2026-09-15T00:00:00Z"), Instant.parse("2026-09-16T00:00:00Z"),
                        Instant.parse("2026-09-17T00:00:00Z"), Instant.parse("2026-09-15T00:00:00Z"), 60, "Asia/Shanghai"),
                null, new SolverInput.Parameters("FORWARD", 30, 1, 1, 0, 0), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        SolverInputCodec codec = new SolverInputCodec();

        var encoded = codec.encodeWithHash(value);
        assertThat(new String(encoded.bytes(), StandardCharsets.UTF_8)).contains("2026-09-15T00:00:00.000Z");
        assertThat(encoded.value().inputHash()).hasSize(64).isNotEqualTo("0".repeat(64));
        assertThat(codec.decode(encoded.bytes())).isEqualTo(encoded.value());
    }

    @Test
    void decodesGoldenSolverResultAndRecomputesCandidateHash() throws Exception
    {
        byte[] json = Files.readAllBytes(findWorkspaceRoot()
                .resolve("contracts/aps/examples/golden/valid-minimal-solver-result.json"));
        SolverInputCodec codec = new SolverInputCodec();

        SolverResult result = codec.decodeResult(json);

        assertThat(result.planStatus()).isEqualTo(SolverResult.PlanStatus.FEASIBLE);
        assertThat(result.solverStatus()).isEqualTo(SolverResult.SolverStatus.FEASIBLE);
        assertThat(codec.candidateHash(result.candidate())).isEqualTo(result.candidateHash());
    }

    private Path findWorkspaceRoot()
    {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null)
        {
            if (Files.isRegularFile(cursor.resolve("contracts/aps/contract-test-manifest.json"))) return cursor;
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("找不到 contracts/aps");
    }

    private byte[] bytes(String json) { return json.getBytes(StandardCharsets.UTF_8); }
    private String uuid(int no) { return String.format("00000000-0000-4000-8000-%012d", no); }
}
