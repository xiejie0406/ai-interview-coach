package com.ruoyi.interview.domain.platform;

/** JSON Schema 或等价结构化输出契约的不可变版本引用。 */
public record SchemaRef(ImmutableVersionRef value) {

    public SchemaRef {
        DomainPreconditions.requireNonNull(value, "schemaVersionRef");
    }
}
