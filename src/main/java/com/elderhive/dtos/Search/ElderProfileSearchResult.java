package com.elderhive.dtos.Search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Scoped to elder profiles the caller actually has standing on (owner, co-managing family
 *  member, or the elder themselves) - "relationship to the caller" only makes sense for
 *  profiles the caller is actually connected to, never a global unscoped elder search. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ElderProfileSearchResult {
    private Long id;
    private String name;
    private String relationship;
}
