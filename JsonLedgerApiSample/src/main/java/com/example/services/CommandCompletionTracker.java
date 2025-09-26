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

package com.example.services;

import com.example.client.ledger.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CommandCompletionTracker {

    public Long nextOffset;
    public Map<String, Completion1> completions;

    public CommandCompletionTracker(Long startOffset) {
        this.nextOffset = startOffset;
        this.completions = new HashMap<>();
    }

    public CommandCompletionTracker() {
        this(0L);
    }

    public void observeCompletion(CompletionResponse streamItem) {
        Object subResponse = streamItem.getActualInstance();
        if (subResponse instanceof CompletionResponseOneOf) {
            Completion1 completion = ((CompletionResponseOneOf) subResponse)
                    .getCompletion()
                    .getValue();
            String commandId = completion.getCommandId();
            this.completions.put(commandId, completion);
        } else if (subResponse instanceof CompletionResponseOneOf1) {
            // ignore
        } else if (subResponse instanceof CompletionResponseOneOf2) {
            this.nextOffset = ((CompletionResponseOneOf2) subResponse)
                    .getOffsetCheckpoint()
                    .getValue()
                    .getOffset();

            System.out.println("Latest known offset moved to " + this.nextOffset);
        } else {
            throw new UnsupportedOperationException("Did not know how to handle completion response item " + subResponse);
        }
    }

    public void observeCompletions(List<CompletionStreamResponse> completions) {
        for (CompletionStreamResponse completion : completions) {
            this.observeCompletion(completion.getCompletionResponse());
        }
    }

    public Optional<Status> resultCodeFor(String commandId) {

        Completion1 relevantCompletion = this.completions.get(commandId);

        if (relevantCompletion == null) {
            return Optional.empty();
        }
        Status status = relevantCompletion.getStatus();

        if (status == null) {
            return Optional.empty();
        }
        return Optional.of(status);
    }
}
