package com.ruoyi.aps.solver.contract;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.erdtman.jcs.JsonCanonicalizer;

/** RFC 8785 / JCS 内容哈希；SolverInput 计算时排除顶层 inputHash。 */
public final class JcsSha256
{
    private static final ObjectMapper JSON = new ObjectMapper();

    public String inputHash(byte[] solverInputJson)
    {
        try
        {
            JsonNode root = JSON.readTree(solverInputJson);
            if (!root.isObject()) throw new IllegalArgumentException("SolverInput 必须是 JSON 对象");
            ((com.fasterxml.jackson.databind.node.ObjectNode) root).remove("inputHash");
            return hashCanonical(JSON.writeValueAsBytes(root));
        }
        catch (java.io.IOException exception)
        {
            throw new IllegalArgumentException("SolverInput JSON 无法解析", exception);
        }
    }

    public String hashCanonical(byte[] json)
    {
        try
        {
            byte[] canonical = new JsonCanonicalizer(json).getEncodedUTF8();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
            return java.util.HexFormat.of().formatHex(digest);
        }
        catch (java.io.IOException exception)
        {
            throw new IllegalArgumentException("JSON 不符合 JCS 输入约束", exception);
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("JDK 缺少 SHA-256", exception);
        }
    }

    public String canonicalText(byte[] json)
    {
        try
        {
            return new String(new JsonCanonicalizer(json).getEncodedUTF8(), StandardCharsets.UTF_8);
        }
        catch (java.io.IOException exception)
        {
            throw new IllegalArgumentException("JSON 不符合 JCS 输入约束", exception);
        }
    }
}
