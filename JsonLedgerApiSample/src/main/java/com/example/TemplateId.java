package com.example;

public record TemplateId(
        String packageIdOrName,
        String moduleName,
        String typeName
) {
    public static final TemplateId HOLDING_INTERFACE_ID = new TemplateId("#splice-api-token-holding-v1", "Splice.Api.Token.HoldingV1", "Holding");

    public static TemplateId parse(String input) {
        String[] parts = input.split(":");
        if (parts.length != 3) {
            return null;
        }
        return new TemplateId(parts[0], parts[1], parts[2]);
    }

    public String getRaw() {
        return packageIdOrName + ":" + moduleName + ":" + typeName;
    }

    public boolean matchesModuleAndTypeName(TemplateId other) {
        return this.moduleName.equals(other.moduleName)
                && this.typeName.equals(other.typeName);
    }

    public boolean matchesModuleAndTypeName(String otherRaw) {
        TemplateId other = parse(otherRaw);
        return other != null && this.matchesModuleAndTypeName(other);
    }
}
