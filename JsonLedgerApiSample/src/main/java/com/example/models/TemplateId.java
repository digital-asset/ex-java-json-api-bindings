/*
 * Copyright (c) 2025, by Digital Asset
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
 * REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY
 * AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
 * INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
 * LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR
 * OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
 * PERFORMANCE OF THIS SOFTWARE.
 */

package com.example.models;

public record TemplateId(
        String packageIdOrName,
        String moduleName,
        String typeName
) {
    // TODO(#27): use codegen provided values like `TransferFactory.INTERFACE_ID`
    public static final TemplateId TRANSFER_PREAPPROVAL_ID = new TemplateId("#splice-amulet", "Splice.AmuletRules", "TransferPreapproval");
    public static final TemplateId TRANSFER_PREAPPROVAL_PROPOSAL_ID = new TemplateId("#splice-wallet", "Splice.Wallet.TransferPreapproval", "TransferPreapprovalProposal");
    public static final TemplateId HOLDING_INTERFACE_ID = new TemplateId("#splice-api-token-holding-v1", "Splice.Api.Token.HoldingV1", "Holding");
    public static final TemplateId TRANSFER_FACTORY_INTERFACE_ID = new TemplateId("#splice-api-token-transfer-instruction-v1", "Splice.Api.Token.TransferInstructionV1", "TransferFactory");
    public static final TemplateId TRANSFER_INSTRUCTION_INTERFACE_ID = new TemplateId("#splice-api-token-transfer-instruction-v1", "Splice.Api.Token.TransferInstructionV1", "TransferInstruction");

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
