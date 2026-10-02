package dev.devdigi.music.features.playback.domain

class PlaybackQueue private constructor(
    entries: List<PlaybackTrack>,
    val currentIndex: Int?,
) {
    val entries: List<PlaybackTrack> =
        entries.toList()

    val current: PlaybackTrack?
        get() =
            currentIndex?.let(entries::get)

    init {
        require(
            if (entries.isEmpty()) {
                currentIndex == null
            } else {
                currentIndex != null &&
                    currentIndex in entries.indices
            },
        )
    }

    fun replace(
        entries: List<PlaybackTrack>,
        selectedIndex: Int?,
    ): PlaybackQueue {
        if (entries.isEmpty()) {
            require(selectedIndex == null)

            return empty()
        }

        require(
            selectedIndex != null &&
                selectedIndex in entries.indices,
        )

        return PlaybackQueue(
            entries = entries,
            currentIndex = selectedIndex,
        )
    }

    fun append(entries: List<PlaybackTrack>): PlaybackQueue {
        if (entries.isEmpty()) {
            return this
        }

        if (this.entries.isEmpty()) {
            return PlaybackQueue(
                entries = entries,
                currentIndex = 0,
            )
        }

        return PlaybackQueue(
            entries =
                this.entries +
                    entries,
            currentIndex =
            currentIndex,
        )
    }

    fun next(): PlaybackQueue {
        val index =
            currentIndex
                ?: return this

        if (index >= entries.lastIndex) {
            return this
        }

        return PlaybackQueue(
            entries = entries,
            currentIndex = index + 1,
        )
    }

    fun previous(): PlaybackQueue {
        val index =
            currentIndex
                ?: return this

        if (index <= 0) {
            return this
        }

        return PlaybackQueue(
            entries = entries,
            currentIndex = index - 1,
        )
    }

    fun removeAt(index: Int): PlaybackQueue {
        require(index in entries.indices)

        if (entries.size == 1) {
            return empty()
        }

        val current =
            requireNotNull(currentIndex)

        val remaining =
            entries.toMutableList().apply {
                removeAt(index)
            }

        val nextIndex =
            when {
                index < current -> {
                    current - 1
                }

                index > current -> {
                    current
                }

                current < remaining.size -> {
                    current
                }

                else -> {
                    remaining.lastIndex
                }
            }

        return PlaybackQueue(
            entries = remaining,
            currentIndex = nextIndex,
        )
    }

    fun clear(): PlaybackQueue = empty()

    override fun equals(other: Any?): Boolean =
        other is PlaybackQueue &&
            entries == other.entries &&
            currentIndex == other.currentIndex

    override fun hashCode(): Int =
        31 * entries.hashCode() +
            (currentIndex ?: 0)

    override fun toString(): String = "PlaybackQueue(entries=$entries, currentIndex=$currentIndex)"

    companion object {
        fun empty(): PlaybackQueue =
            PlaybackQueue(
                entries = emptyList(),
                currentIndex = null,
            )
    }
}
