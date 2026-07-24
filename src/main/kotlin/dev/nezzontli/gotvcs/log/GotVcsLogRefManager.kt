package dev.nezzontli.gotvcs.log

import com.intellij.vcs.log.RefGroup
import com.intellij.vcs.log.VcsLogRefManager
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.VcsRefType
import java.io.DataInput
import java.io.DataOutput

/**
 * got has no notion of ref grouping/favorites beyond plain
 * branches/remote-branches/tags, so this is a flat, ungrouped manager: one
 * [RefGroup] per ref, ordered by name.
 */
class GotVcsLogRefManager : VcsLogRefManager {

    private val nameComparator = Comparator.comparing<VcsRef, String> { it.name }

    override fun getBranchLayoutComparator(): Comparator<VcsRef> = nameComparator

    override fun getLabelsOrderComparator(): Comparator<VcsRef> = nameComparator

    override fun groupForBranchFilter(refs: MutableCollection<out VcsRef>): List<RefGroup> =
        refs.sortedWith(nameComparator).map { SingleRefGroup(it) }

    override fun groupForTable(refs: MutableCollection<out VcsRef>, compact: Boolean, showTagNames: Boolean): List<RefGroup> =
        refs.sortedWith(nameComparator).map { SingleRefGroup(it) }

    override fun serialize(out: DataOutput, type: VcsRefType) {
        out.writeByte((type as? GotRefType)?.ordinal ?: GotRefType.LOCAL_BRANCH.ordinal)
    }

    override fun deserialize(input: DataInput): VcsRefType {
        val ordinal = input.readByte().toInt()
        return GotRefType.entries.getOrElse(ordinal) { GotRefType.LOCAL_BRANCH }
    }

    override fun isFavorite(ref: VcsRef): Boolean = false

    override fun setFavorite(ref: VcsRef, favorite: Boolean) = Unit

    private class SingleRefGroup(private val ref: VcsRef) : RefGroup {
        override fun getName(): String = ref.name
        override fun getRefs(): List<VcsRef> = listOf(ref)
        override fun getColors(): List<java.awt.Color> = listOf(ref.type.backgroundColor)
    }
}
