package com.mustafakoceerr.tilepdf.core.pdf

import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.ForeignRef

/**
 * What to change in a file so it holds exactly the wanted annotations.
 * Maps are page index → own ids or foreign keys to remove.
 */
data class AnnotationChanges(
    val removals: Map<Int, Set<String>>,
    val additions: List<Annotation>,
    val foreignRemovals: Map<Int, Set<String>>,
) {
    val isEmpty: Boolean get() = removals.isEmpty() && additions.isEmpty() && foreignRemovals.isEmpty()
}

/**
 * Own annotations are immutable, so comparing ids is enough: ids in both
 * [existing] and [wanted] stay, the rest are removed or added. Foreign
 * removals keep only refs still present in [existingForeign].
 */
fun annotationChanges(
    existing: Map<Int, Set<String>>,
    wanted: List<Annotation>,
    existingForeign: Map<Int, Set<String>>,
    removedForeign: Set<ForeignRef>,
): AnnotationChanges {
    val wantedIds = wanted.mapTo(HashSet()) { it.id }
    val existingIds = existing.values.flatten().toSet()
    return AnnotationChanges(
        removals = existing
            .mapValues { (_, ids) -> ids - wantedIds }
            .filterValues { it.isNotEmpty() },
        additions = wanted.filter { it.id !in existingIds },
        foreignRemovals = removedForeign
            .filter { it.key in existingForeign[it.pageIndex].orEmpty() }
            .groupBy({ it.pageIndex }, { it.key })
            .mapValues { (_, keys) -> keys.toSet() },
    )
}
